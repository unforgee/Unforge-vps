package org.rsmod.api.equipment.instance

import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase

/**
 * V32 round-trip coverage for [EquipmentInstanceRepository] against a real Flyway-migrated SQLite
 * database: snapshot create/load (all Evolution columns), uuid lookup, owner queries,
 * optimistic-lock saves, the append-only event log with its hash chain, idempotency-key replay
 * detection and fingerprint validation.
 */
class EquipmentInstanceRepositoryTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dbFile: Path
    private lateinit var db: SqliteDatabase
    private val repository = EquipmentInstanceRepository()

    private val dbUrl: String
        get() = "jdbc:sqlite:${dbFile.toAbsolutePath()}"

    @BeforeEach
    fun setup() {
        dbFile = tempDir.resolve("game.db")
        Flyway.configure()
            .dataSource(dbUrl, null, null)
            .locations("classpath:db/migration", "classpath:plugin/**/migration")
            .load()
            .migrate()
        db =
            SqliteDatabase().apply {
                connect(
                    SqliteConnection(DatabaseConfig("jdbc:sqlite:", dbFile.toString(), null, null))
                )
            }
    }

    @AfterEach
    fun teardown() {
        runCatching { db.close() }
    }

    @Test
    fun `create then load round-trips every evolution column`() = runBlocking {
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        assertNotEquals(0L, stored.instanceId)
        assertTrue(stored.instanceUuid.isNotEmpty())
        assertTrue(stored.fingerprint.isNotEmpty())

        val loaded =
            db.withTransaction { connection -> repository.load(connection, stored.instanceId) }
        assertNotNull(loaded)
        // `created_at` is stamped by the database on insert; every other field round-trips.
        assertEquals(stored.copy(createdAtEpochMillis = loaded!!.createdAtEpochMillis), loaded)
        assertEquals(ItemBinding.CHARACTER_BOUND, loaded!!.binding)
        assertEquals(EquipmentInstanceState.ACTIVE, loaded.state)
        assertEquals(77L, loaded.ownerCharacterId)
        assertEquals(1234L, loaded.ownerAccountId)
        assertEquals(12, loaded.itemLevel)
        assertEquals(4, loaded.masteryLevel)
        assertEquals(9_500L, loaded.experience)
        assertEquals(2, loaded.evolutionStage)
        assertEquals("reaver", loaded.evolutionBranch)
        assertEquals(5L, loaded.revision)
        assertEquals(listOf(9001L, 9002L), loaded.lineageParentIds)
        assertEquals("recipe-forge-1", loaded.lineageRecipeId)
        assertEquals("drop-abyss-3", loaded.lineageDropId)
        assertEquals(1, loaded.affixes.size)
        assertEquals("stab-power", loaded.affixes.single().definitionId)
        assertEquals(1603, loaded.sockets.single().socketedObj)
    }

    @Test
    fun `loadByUuid resolves the same snapshot`() = runBlocking {
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        val byUuid =
            db.withTransaction { connection ->
                repository.loadByUuid(connection, stored.instanceUuid)
            }
        assertEquals(stored.instanceId, byUuid?.instanceId)
        assertNull(
            db.withTransaction { connection ->
                repository.loadByUuid(connection, UUID.randomUUID().toString())
            }
        )
    }

    @Test
    fun `loadOwnedByCharacter returns only that owner's instances`() = runBlocking {
        val mine = db.withTransaction { connection -> repository.create(connection, instance()) }
        db.withTransaction { connection ->
            repository.create(connection, instance(ownerCharacterId = 88L, ownerAccountId = 4321L))
        }
        val owned =
            db.withTransaction { connection -> repository.loadOwnedByCharacter(connection, 77L) }
        assertEquals(listOf(mine.instanceId), owned.map { it.instanceId })
    }

    @Test
    fun `saveWithRevision commits only when the stored revision matches`() = runBlocking {
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        val updated = stored.copy(quality = 55, revision = stored.revision + 1)

        // A stale writer loses - the row still carries `revision`.
        val staleWon =
            db.withTransaction { connection ->
                repository.saveWithRevision(connection, updated, expectedRevision = 999L)
            }
        assertFalse(staleWon)

        val won =
            db.withTransaction { connection ->
                repository.saveWithRevision(connection, updated, expectedRevision = stored.revision)
            }
        assertTrue(won)

        val reloaded =
            db.withTransaction { connection -> repository.load(connection, stored.instanceId) }
        assertEquals(55, reloaded?.quality)
        assertEquals(stored.revision + 1, reloaded?.revision)
    }

    @Test
    fun `events append in revision order and keep a verifiable hash chain`() = runBlocking {
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        var previousHash: String? = null
        val events =
            (1..3).map { step ->
                val unsigned =
                    ItemMutationEvent(
                        eventId = UUID.randomUUID(),
                        instanceId = stored.instanceId,
                        operation =
                            if (step == 1) ItemMutationOperation.CREATED
                            else ItemMutationOperation.REFORGED,
                        actor = ItemActor(ItemActorType.PLAYER, 7L),
                        source = "test",
                        beforeRevision = (step - 1).toLong() + stored.revision,
                        afterRevision = step.toLong() + stored.revision,
                        beforeFingerprint = "fp-${step - 1}",
                        afterFingerprint = "fp-$step",
                        payload = "step-$step",
                        previousEventHash = previousHash,
                        eventHash = "",
                        createdAtEpochMillis = 1_700_000_000_000 + step,
                    )
                unsigned.copy(eventHash = ItemEventHasher.hash(unsigned)).also {
                    previousHash = it.eventHash
                }
            }
        db.withTransaction { connection ->
            for ((index, event) in events.withIndex()) {
                repository.appendEvent(connection, event, idempotencyKey = "key-$index")
            }
        }

        val history =
            db.withTransaction { connection ->
                repository.loadHistory(connection, stored.instanceId)
            }
        assertEquals(3, history.size)
        assertEquals(events.map { it.eventId }, history.map { it.eventId })
        assertEquals(emptyList<String>(), EquipmentInstanceInvariants.eventChainViolations(history))

        // Pagination: cursor is an exclusive after_revision bound.
        val page =
            db.withTransaction { connection ->
                repository.loadHistory(
                    connection,
                    stored.instanceId,
                    cursor = history.first().afterRevision,
                    limit = 1,
                )
            }
        assertEquals(listOf(events[1].eventId), page.map { it.eventId })

        // Idempotency replay detection survives in the persisted row.
        val replayed =
            db.withTransaction { connection ->
                repository.findEventByIdempotencyKey(connection, stored.instanceId, "key-1")
            }
        assertEquals(events[1].eventId, replayed?.eventId)
        assertNull(
            db.withTransaction { connection ->
                repository.findEventByIdempotencyKey(connection, stored.instanceId, "missing")
            }
        )
    }

    @Test
    fun `validateFingerprint detects a tampered row`() = runBlocking {
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        assertTrue(
            db.withTransaction { connection ->
                repository.validateFingerprint(connection, stored.instanceId)
            }
        )

        // Direct SQL tamper, bypassing the mutation gateway entirely.
        db.withTransaction { connection ->
            connection
                .prepareStatement("UPDATE equipment_instances SET quality = 1 WHERE id = ?")
                .use { s ->
                    s.setLong(1, stored.instanceId)
                    s.executeUpdate()
                }
        }
        assertFalse(
            db.withTransaction { connection ->
                repository.validateFingerprint(connection, stored.instanceId)
            }
        )
    }

    @Test
    fun `a legacy row without a stamped fingerprint still loads`() = runBlocking {
        // Simulate a pre-Evolution persisted row: every V32 column exists (migration supplies
        // defaults + a backfilled uuid) but the baseline fingerprint has not been stamped yet.
        val stored = db.withTransaction { connection -> repository.create(connection, instance()) }
        db.withTransaction { connection ->
            connection
                .prepareStatement("UPDATE equipment_instances SET fingerprint = '' WHERE id = ?")
                .use { s ->
                    s.setLong(1, stored.instanceId)
                    s.executeUpdate()
                }
        }
        val loaded =
            db.withTransaction { connection -> repository.load(connection, stored.instanceId) }
        assertNotNull(loaded)
        assertEquals("", loaded!!.fingerprint)
        assertEquals(emptyList<String>(), EquipmentInstanceInvariants.violations(loaded))
    }

    private fun instance(
        ownerCharacterId: Long = 77L,
        ownerAccountId: Long = 1234L,
    ): EquipmentInstance {
        val affix = EquipmentAffixCatalog.byId.getValue("stab-power")
        return EquipmentInstance(
            instanceId = 0L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Rare,
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
            sockets = listOf(EquipmentSocket(0, "Universal", socketedObj = 1603, magnitude = 2)),
            uniqueEffectIds = emptyList(),
            source = "test",
            itemLevel = 12,
            quality = 80,
            state = EquipmentInstanceState.ACTIVE,
            binding = ItemBinding.CHARACTER_BOUND,
            ownerAccountId = ownerAccountId,
            ownerCharacterId = ownerCharacterId,
            masteryLevel = 4,
            experience = 9_500L,
            evolutionStage = 2,
            evolutionBranch = "reaver",
            revision = 5L,
            lineageParentIds = listOf(9001L, 9002L),
            lineageRecipeId = "recipe-forge-1",
            lineageDropId = "drop-abyss-3",
        )
    }
}
