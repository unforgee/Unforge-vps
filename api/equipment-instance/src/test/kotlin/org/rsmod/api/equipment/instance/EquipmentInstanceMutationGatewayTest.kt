package org.rsmod.api.equipment.instance

import java.nio.file.Path
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
 * End-to-end coverage of the mutation gateway against a real Flyway-migrated SQLite database and
 * the production [ResponseDbGatewayService] loop: atomic snapshot+event persistence, optimistic
 * locking (both the in-memory check and the `WHERE revision = ?` backstop), idempotent replay and
 * registry synchronisation.
 */
class EquipmentInstanceMutationGatewayTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var db: SqliteDatabase
    private lateinit var gateway: ResponseDbGatewayService
    private lateinit var service: EquipmentInstanceMutationService
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
        service =
            EquipmentInstanceMutationService(
                db = GameDbManager(gateway),
                repository = repository,
                registry = registry,
            )
    }

    @AfterEach
    fun teardown() {
        runCatching { runBlocking { gateway.fastForwardShutdown() } }
        runCatching { db.close() }
    }

    /** Runs the real gateway loop until [done] holds (or fails after a bounded wait). */
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
        val stored = runBlocking {
            db.withTransaction { connection -> repository.create(connection, instance()) }
        }
        registry.put(stored)
        return stored
    }

    private fun request(
        key: String,
        expectedRevision: Long?,
        operation: ItemMutationOperation = ItemMutationOperation.REFORGED,
    ) =
        ItemMutationRequest(
            idempotencyKey = key,
            expectedRevision = expectedRevision,
            actor = ItemActor(ItemActorType.PLAYER, 7L),
            operation = operation,
            source = "test",
        )

    @Test
    fun `a mutation persists the snapshot and its audit event atomically`() {
        val stored = persisted()
        service.mutate(
            stored,
            request("k1", expectedRevision = 0L),
            transform = { it.copy(quality = 90) },
            onResult = { results += it },
        )
        drainUntil { results.isNotEmpty() }

        val result = results.single()
        val success = result as ItemMutationResult.Success
        assertEquals(1L, success.instance.revision)
        assertEquals(90, success.instance.quality)
        assertTrue(EquipmentInstanceFingerprint.verify(success.instance))
        assertEquals(success.instance, registry[stored.instanceId])

        // The audit event landed in the same transaction and continues the chain.
        val history = runBlocking {
            db.withTransaction { connection ->
                repository.loadHistory(connection, stored.instanceId)
            }
        }
        assertEquals(1, history.size)
        val event = history.single()
        assertEquals(ItemMutationOperation.REFORGED, event.operation)
        assertEquals(0L, event.beforeRevision)
        assertEquals(1L, event.afterRevision)
        assertEquals(event.afterFingerprint, success.instance.fingerprint)
        assertEquals(emptyList<String>(), EquipmentInstanceInvariants.eventChainViolations(history))
    }

    @Test
    fun `replaying the same idempotency key returns the stored result without a second event`() {
        val stored = persisted()
        service.mutate(
            stored,
            request("dup", expectedRevision = 0L),
            transform = { it.copy(quality = 90) },
            onResult = { results += it },
        )
        drainUntil { results.isNotEmpty() }
        val first = results.single() as ItemMutationResult.Success

        // Replay the exact same request against the now-updated snapshot.
        service.mutate(
            first.instance,
            request("dup", expectedRevision = 1L),
            transform = { it.copy(quality = 10) },
            onResult = { results += it },
        )
        drainUntil { results.size >= 2 }
        val replayed = results[1] as ItemMutationResult.Success
        assertEquals(first.instance, replayed.instance)

        val history = runBlocking {
            db.withTransaction { connection ->
                repository.loadHistory(connection, stored.instanceId)
            }
        }
        assertEquals(1, history.size, "replay must not append a second event")
    }

    @Test
    fun `reusing an idempotency key for a different operation is rejected`() {
        val stored = persisted()
        service.mutate(
            stored,
            request("dup", expectedRevision = 0L),
            transform = { it.copy(quality = 90) },
            onResult = { results += it },
        )
        drainUntil { results.isNotEmpty() }
        val first = results.single() as ItemMutationResult.Success

        service.mutate(
            first.instance,
            request("dup", expectedRevision = 1L, operation = ItemMutationOperation.UPGRADED),
            transform = { it.copy(quality = 10) },
            onResult = { results += it },
        )
        drainUntil { results.size >= 2 }
        val error = results[1] as ItemMutationResult.ValidationError
        assertEquals("idempotency-key-reuse", error.code)
    }

    @Test
    fun `a stale expected revision surfaces a conflict with the live snapshot`() {
        val stored = persisted()
        service.mutate(
            stored,
            request("k1", expectedRevision = 0L),
            transform = { it.copy(quality = 90) },
            onResult = { results += it },
        )
        drainUntil { results.isNotEmpty() }
        val first = results.single() as ItemMutationResult.Success

        // A second client still holding revision 0 tries to mutate the live snapshot.
        service.mutate(
            first.instance,
            request("k2", expectedRevision = 0L),
            transform = { it.copy(quality = 50) },
            onResult = { results += it },
        )
        drainUntil { results.size >= 2 }
        val conflict = results[1] as ItemMutationResult.RevisionConflict
        assertEquals(1L, conflict.current.revision)
    }

    @Test
    fun `the persisted revision backstop rejects a mutation that raced a commit`() {
        val stored = persisted()

        // Simulate a write that committed after `stale` was read but before this mutation.
        val winner = stored.copy(quality = 11, revision = stored.revision + 1)
        runBlocking {
            db.withTransaction { connection ->
                repository.saveWithRevision(connection, winner, expectedRevision = stored.revision)
            }
        }
        registry.put(winner)

        // The stale snapshot passes prepare (expectedRevision matches the stale copy), but the
        // conditional UPDATE must refuse to overwrite the newer row.
        service.mutate(
            stored,
            request("k1", expectedRevision = stored.revision),
            transform = { it.copy(quality = 99) },
            onResult = { results += it },
        )
        drainUntil { results.isNotEmpty() }

        val conflict = results.single() as ItemMutationResult.RevisionConflict
        assertEquals(1L, conflict.current.revision)

        // The committed row was never overwritten and no orphan event exists.
        val reloaded = runBlocking {
            db.withTransaction { connection -> repository.load(connection, stored.instanceId) }
        }
        assertEquals(11, reloaded?.quality)
        val history = runBlocking {
            db.withTransaction { connection ->
                repository.loadHistory(connection, stored.instanceId)
            }
        }
        assertEquals(0, history.size)
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
