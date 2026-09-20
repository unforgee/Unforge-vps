package org.rsmod.api.equipment.instance

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.service.ResponseDbGatewayService
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase

/**
 * End-to-end coverage of event-history restore and corruption handling against a real
 * Flyway-migrated SQLite database and the production [ResponseDbGatewayService] loop:
 * - a successful restore rebuilds the recorded snapshot, seals identity fields and appends its own
 *   `RESTORED` audit event through the mutation gateway
 * - any broken link in the hash/revision/fingerprint chain fails closed before anything is restored
 * - missing or tampered snapshot payloads are rejected (`no-snapshot`, `snapshot-decode-failed`,
 *   `snapshot-fingerprint-mismatch`)
 * - `CORRUPTED` items reject normal mutations until `REPAIRED` or restored.
 */
class EquipmentInstanceRestoreTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var db: SqliteDatabase
    private lateinit var gateway: ResponseDbGatewayService
    private lateinit var mutations: EquipmentInstanceMutationService
    private lateinit var service: EquipmentInstanceService
    private val repository = EquipmentInstanceRepository()
    private val registry = EquipmentInstanceRegistry()
    private val results = CopyOnWriteArrayList<ItemMutationResult>()

    @BeforeEach
    fun setup() {
        val dbFile = tempDir.resolve("game.db")
        Flyway.configure()
            .dataSource("jdbc:sqlite:${dbFile.toAbsolutePath()}", null, null)
            .locations("classpath:db/migration", "classpath:plugin/**/migration")
            .load()
            .migrate()
        db =
            SqliteDatabase().apply {
                connect(
                    SqliteConnection(DatabaseConfig("jdbc:sqlite:", dbFile.toString(), null, null))
                )
            }
        gateway = ResponseDbGatewayService(db)
        runBlocking { gateway.startup() }
        val dbManager = GameDbManager(gateway)
        mutations =
            EquipmentInstanceMutationService(
                db = dbManager,
                repository = repository,
                registry = registry,
            )
        service =
            EquipmentInstanceService(
                db = dbManager,
                repository = repository,
                registry = registry,
                mutations = mutations,
            )
    }

    @AfterEach
    fun teardown() {
        runCatching { runBlocking { gateway.fastForwardShutdown() } }
        runCatching { db.close() }
    }

    private fun drainUntil(timeoutMs: Long = 20_000, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val callbacks = mutableListOf<ResponseDbGatewayService.PendingCallback<*>>()
        while (!done() && System.currentTimeMillis() < deadline) {
            runBlocking { gateway.run() }
            callbacks.clear()
            gateway.take(callbacks, 50)
            callbacks.forEach { it() }
            if (!done()) Thread.sleep(20)
        }
        assertTrue(done(), "db gateway callback never arrived")
    }

    private fun persisted(): EquipmentInstance {
        // Reload in the same transaction so the registry snapshot carries the db-stamped
        // `created_at` (create returns 0), keeping registry and row comparable.
        val stored = runBlocking {
            db.withTransaction { connection ->
                val created = repository.create(connection, instance())
                repository.load(connection, created.instanceId)!!
            }
        }
        registry.put(stored)
        return stored
    }

    /** Applies [steps] sequential quality mutations, returning the live snapshot. */
    private fun mutateQualities(
        stored: EquipmentInstance,
        vararg qualities: Int,
    ): EquipmentInstance {
        var live = stored
        for ((index, quality) in qualities.withIndex()) {
            val before = results.size
            mutations.mutate(
                live,
                ItemMutationRequest(
                    idempotencyKey = "mut-$index-${live.instanceId}",
                    expectedRevision = live.revision,
                    actor = ItemActor(ItemActorType.PLAYER, 7L),
                    operation = ItemMutationOperation.REFORGED,
                    source = "test",
                ),
                transform = { it.copy(quality = quality) },
                onResult = { results += it },
            )
            drainUntil { results.size > before }
            live = (results.last() as ItemMutationResult.Success).instance
        }
        return live
    }

    private fun history(instanceId: Long): List<ItemMutationEvent> = runBlocking {
        db.withTransaction { connection ->
            repository.loadHistory(connection, instanceId, cursor = -1L, limit = 10_000)
        }
    }

    private fun reloaded(instanceId: Long): EquipmentInstance = runBlocking {
        db.withTransaction { connection -> repository.load(connection, instanceId) }!!
    }

    /** Direct SQL tamper, bypassing the gateway - simulates db-level corruption. */
    private fun tamper(sql: String, bind: (java.sql.PreparedStatement) -> Unit = {}) {
        runBlocking {
            db.withTransaction { connection ->
                connection.prepareStatement(sql).use { s ->
                    bind(s)
                    s.executeUpdate()
                }
            }
        }
    }

    private fun admin() = ItemActor(ItemActorType.ADMIN, null)

    // --- happy path ---

    @Test
    fun `restore rebuilds the recorded snapshot seals identity and appends a RESTORED event`() {
        val stored = persisted()
        val live = mutateQualities(stored, 90, 50)
        val target = history(stored.instanceId).first() // rev 0->1, quality 90
        val before = results.size

        service.restoreFromEvent(
            instanceId = stored.instanceId,
            eventId = target.eventId,
            actor = admin(),
            expectedRevision = live.revision,
        ) {
            results += it
        }
        drainUntil { results.size > before }

        val success = results.last() as ItemMutationResult.Success
        val restored = success.instance
        // The recorded post-state (quality 90) is back, on a bumped revision.
        assertEquals(90, restored.quality)
        assertEquals(live.revision + 1, restored.revision)
        assertEquals(target.afterFingerprint, restored.fingerprint)
        // Identity was sealed from the live row, never taken from the snapshot.
        assertEquals(live.instanceId, restored.instanceId)
        assertEquals(live.instanceUuid, restored.instanceUuid)
        assertEquals(live.createdAtEpochMillis, restored.createdAtEpochMillis)
        assertEquals(live.templateObj, restored.templateObj)
        assertEquals(live.rollSeed, restored.rollSeed)
        assertEquals(restored, registry[stored.instanceId])
        assertEquals(restored, reloaded(stored.instanceId))

        // The restore itself is an audited mutation; the chain stays intact.
        val events = history(stored.instanceId)
        assertEquals(3, events.size)
        val restoreEvent = events.last()
        assertEquals(ItemMutationOperation.RESTORED, restoreEvent.operation)
        assertEquals(live.revision, restoreEvent.beforeRevision)
        assertEquals(restored.revision, restoreEvent.afterRevision)
        assertEquals(restored.fingerprint, restoreEvent.afterFingerprint)
        assertEquals(emptyList<String>(), EquipmentInstanceInvariants.eventChainViolations(events))
    }

    @Test
    fun `repeating the same restore replays the stored result without a second event`() {
        val stored = persisted()
        val live = mutateQualities(stored, 90, 50)
        val target = history(stored.instanceId).first()
        val before = results.size

        repeat(2) {
            service.restoreFromEvent(
                instanceId = stored.instanceId,
                eventId = target.eventId,
                actor = admin(),
                expectedRevision = live.revision,
            ) {
                results += it
            }
            drainUntil { results.size > before + it }
        }

        val first = results[results.size - 2] as ItemMutationResult.Success
        val replay = results.last() as ItemMutationResult.Success
        assertEquals(first.instance, replay.instance)
        assertEquals(
            1,
            history(stored.instanceId).count { it.operation == ItemMutationOperation.RESTORED },
            "replay must not append a second RESTORED event",
        )
    }

    @Test
    fun `a stale expected revision surfaces a conflict before any history lookup`() {
        val stored = persisted()
        mutateQualities(stored, 90)
        val target = history(stored.instanceId).first()
        val before = results.size

        service.restoreFromEvent(
            instanceId = stored.instanceId,
            eventId = target.eventId,
            actor = admin(),
            expectedRevision = 0L, // live revision is already 1
        ) {
            results += it
        }
        drainUntil { results.size > before }

        val conflict = results.last() as ItemMutationResult.RevisionConflict
        assertEquals(1L, conflict.current.revision)
    }

    // --- chain integrity: fail closed ---

    @Test
    fun `a tampered event hash rejects the restore`() {
        val stored = persisted()
        mutateQualities(stored, 90, 50)
        val target = history(stored.instanceId).first()
        tamper(
            "UPDATE equipment_instance_events SET event_hash = 'bogus' " +
                "WHERE equipment_instance_id = ? AND after_revision = 2"
        ) {
            it.setLong(1, stored.instanceId)
        }
        assertRestoreRejected(stored.instanceId, target.eventId, "event-chain-broken")
    }

    @Test
    fun `a revision gap in the chain rejects the restore`() {
        val stored = persisted()
        mutateQualities(stored, 90, 50, 30)
        val target = history(stored.instanceId).first()
        // Removing the middle event leaves a revision gap (1 -> beforeRevision 2).
        tamper(
            "DELETE FROM equipment_instance_events " +
                "WHERE equipment_instance_id = ? AND after_revision = 2"
        ) {
            it.setLong(1, stored.instanceId)
        }
        val error = assertRestoreRejected(stored.instanceId, target.eventId, "event-chain-broken")
        assertTrue(
            error.details.getValue("first").endsWith("revision-gap"),
            "unexpected violation: ${error.details}",
        )
    }

    @Test
    fun `a broken fingerprint link rejects the restore`() {
        val stored = persisted()
        mutateQualities(stored, 90, 50)
        val target = history(stored.instanceId).first()
        // Rewrite the last event's before_fingerprint and re-hash it so only the *link* breaks.
        val last = history(stored.instanceId).last()
        val rehashed = ItemEventHasher.hash(last.copy(beforeFingerprint = "tampered"))
        tamper(
            "UPDATE equipment_instance_events " +
                "SET before_fingerprint = 'tampered', event_hash = ? " +
                "WHERE equipment_instance_id = ? AND after_revision = ?"
        ) {
            it.setString(1, rehashed)
            it.setLong(2, stored.instanceId)
            it.setLong(3, last.afterRevision)
        }
        val error = assertRestoreRejected(stored.instanceId, target.eventId, "event-chain-broken")
        assertTrue(
            error.details.getValue("first").endsWith("fingerprint-link-broken"),
            "unexpected violation: ${error.details}",
        )
    }

    // --- snapshot payload integrity ---

    @Test
    fun `an event without a snapshot is not restorable`() {
        val stored = persisted()
        mutateQualities(stored, 90)
        val target = history(stored.instanceId).first()
        // Pre-V34 events legitimately carry NULL snapshots: they must fail closed.
        tamper(
            "UPDATE equipment_instance_events SET snapshot = NULL " +
                "WHERE equipment_instance_id = ? AND event_id = ?"
        ) {
            it.setLong(1, stored.instanceId)
            it.setString(2, target.eventId.toString())
        }
        assertRestoreRejected(stored.instanceId, target.eventId, "no-snapshot")
    }

    @Test
    fun `an undecodable snapshot rejects the restore`() {
        val stored = persisted()
        mutateQualities(stored, 90)
        val target = history(stored.instanceId).first()
        tamper(
            "UPDATE equipment_instance_events SET snapshot = 'garbage' " +
                "WHERE equipment_instance_id = ? AND event_id = ?"
        ) {
            it.setLong(1, stored.instanceId)
            it.setString(2, target.eventId.toString())
        }
        assertRestoreRejected(stored.instanceId, target.eventId, "snapshot-decode-failed")
    }

    @Test
    fun `a snapshot that does not match the recorded fingerprint rejects the restore`() {
        val stored = persisted()
        val live = mutateQualities(stored, 90)
        val target = history(stored.instanceId).first()
        // Swap in a *different* valid snapshot: it decodes, but its fingerprint cannot match the
        // event's recorded afterFingerprint.
        val foreign = EquipmentInstanceSnapshotCodec.encode(live.copy(quality = 7))
        tamper(
            "UPDATE equipment_instance_events SET snapshot = ? " +
                "WHERE equipment_instance_id = ? AND event_id = ?"
        ) {
            it.setString(1, foreign)
            it.setLong(2, stored.instanceId)
            it.setString(3, target.eventId.toString())
        }
        assertRestoreRejected(stored.instanceId, target.eventId, "snapshot-fingerprint-mismatch")
    }

    @Test
    fun `an unknown event id is rejected`() {
        val stored = persisted()
        mutateQualities(stored, 90)
        assertRestoreRejected(stored.instanceId, UUID.randomUUID(), "event-not-found")
    }

    @Test
    fun `a non-RESTORED operation cannot use the restore path`() {
        val stored = persisted()
        var delivered: ItemMutationResult? = null
        mutations.restoreToEvent(
            stored,
            UUID.randomUUID(),
            ItemMutationRequest(
                idempotencyKey = "not-a-restore",
                expectedRevision = null,
                actor = admin(),
                operation = ItemMutationOperation.REFORGED,
                source = "test",
            ),
        ) {
            delivered = it
        }
        val error = delivered as ItemMutationResult.ValidationError
        assertEquals("operation-mismatch", error.code)
    }

    // --- corruption handling ---

    @Test
    fun `corrupt marks the item and blocks normal mutations until repaired`() {
        val stored = persisted()
        val before = results.size
        service.corrupt(stored.instanceId, admin(), payload = "test-corruption") { results += it }
        drainUntil { results.size > before }
        val corrupted = (results.last() as ItemMutationResult.Success).instance
        assertEquals(EquipmentInstanceState.CORRUPTED, corrupted.state)

        // A normal mutation is now gated by the lifecycle state.
        mutations.mutate(
            corrupted,
            ItemMutationRequest(
                idempotencyKey = "blocked-${stored.instanceId}",
                expectedRevision = corrupted.revision,
                actor = ItemActor(ItemActorType.PLAYER, 7L),
                operation = ItemMutationOperation.REFORGED,
                source = "test",
            ),
            transform = { it.copy(quality = 5) },
            onResult = { results += it },
        )
        drainUntil { results.size > before + 1 }
        val blocked = results.last() as ItemMutationResult.ValidationError
        assertEquals("invalid-state", blocked.code)

        // Repair brings it back through the gateway with its own audited event.
        service.repair(stored.instanceId, admin()) { results += it }
        drainUntil { results.size > before + 2 }
        val repaired = (results.last() as ItemMutationResult.Success).instance
        assertEquals(EquipmentInstanceState.ACTIVE, repaired.state)

        val ops = history(stored.instanceId).map { it.operation }
        assertEquals(listOf(ItemMutationOperation.CORRUPTED, ItemMutationOperation.REPAIRED), ops)
        assertEquals(
            emptyList<String>(),
            EquipmentInstanceInvariants.eventChainViolations(history(stored.instanceId)),
        )
    }

    @Test
    fun `repair rejects items that are not in a damaged state`() {
        val stored = persisted()
        val before = results.size
        service.repair(stored.instanceId, admin()) { results += it }
        drainUntil { results.size > before }
        val error = results.last() as ItemMutationResult.ValidationError
        assertEquals("repairable-state-required", error.code)
    }

    @Test
    fun `restore brings a corrupted item back to its recorded state`() {
        val stored = persisted()
        val live = mutateQualities(stored, 90)
        val target = history(stored.instanceId).first()

        service.corrupt(stored.instanceId, admin()) { results += it }
        drainUntil { results.size >= 2 }
        val corrupted = (results.last() as ItemMutationResult.Success).instance
        assertEquals(EquipmentInstanceState.CORRUPTED, corrupted.state)

        val before = results.size
        service.restoreFromEvent(
            instanceId = stored.instanceId,
            eventId = target.eventId,
            actor = admin(),
            expectedRevision = corrupted.revision,
        ) {
            results += it
        }
        drainUntil { results.size > before }
        val restored = (results.last() as ItemMutationResult.Success).instance
        assertEquals(EquipmentInstanceState.ACTIVE, restored.state)
        assertEquals(live.quality, restored.quality)
        assertEquals(live.fingerprint, restored.fingerprint)
        assertEquals(corrupted.revision + 1, restored.revision)
        assertEquals(
            emptyList<String>(),
            EquipmentInstanceInvariants.eventChainViolations(history(stored.instanceId)),
        )
    }

    // --- helpers ---

    private fun assertRestoreRejected(
        instanceId: Long,
        eventId: UUID,
        code: String,
    ): ItemMutationResult.ValidationError {
        val live = checkNotNull(registry[instanceId])
        val before = results.size
        service.restoreFromEvent(instanceId, eventId, admin(), expectedRevision = live.revision) {
            results += it
        }
        drainUntil { results.size > before }
        val error = results.last() as ItemMutationResult.ValidationError
        assertEquals(code, error.code, "unexpected result: ${results.last()}")
        // Nothing was persisted: the live row and registry snapshot are untouched.
        assertEquals(live, reloaded(instanceId))
        return error
    }

    private fun instance(): EquipmentInstance {
        val affix = EquipmentAffixCatalog.byId.getValue("stab-power")
        return EquipmentInstance(
            instanceId = 0L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Uncommon,
            rollSeed = 239L,
            affixes =
                listOf(
                    EquipmentAffixRoll(
                        slot = 0,
                        definitionId = affix.id,
                        family = affix.family,
                        stat = affix.stat,
                        unit = affix.unit,
                        polarity = affix.polarity,
                        magnitude = 1,
                    )
                ),
            sockets = emptyList(),
            uniqueEffectIds = emptyList(),
            source = "test",
        )
    }
}
