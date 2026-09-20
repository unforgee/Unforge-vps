package org.rsmod.api.ironman

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.testing.factory.player.TestPlayerFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.GroupRank
import org.rsmod.game.ironman.HardcoreStatus
import org.rsmod.game.type.mod.ModLevelTypeBuilder
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.map.CoordGrid

/**
 * Verifies the V19 migration + repository round-trip for game mode state against a real SQLite
 * database: new accounts start unselected, selections persist across a save/load ("relogin"/
 * "restart"), and rows created before the migration are backfilled to REGULAR+selected.
 */
class GameModePersistenceTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dbFile: Path
    private lateinit var realm: Realm
    private lateinit var repository: CharacterAccountRepository
    private val openDbs = mutableListOf<SqliteDatabase>()

    private val dbUrl: String
        get() = "jdbc:sqlite:${dbFile.toAbsolutePath()}"

    @BeforeEach
    fun setup() {
        dbFile = tempDir.resolve("game.db")
        realm = Realm("test").apply { updateConfig(testRealmConfig()) }
        val modLevelTypes =
            ModLevelTypeList(mutableMapOf(0 to ModLevelTypeBuilder("player").build(0)))
        repository =
            CharacterAccountRepository(
                objectMapper = ObjectMapper(),
                realm = realm,
                applier = CharacterAccountApplier(modLevelTypes),
                varpTypes = VarpTypeList(mutableMapOf()),
            )
    }

    private fun testRealmConfig() =
        RealmConfig(
            id = 1,
            loginMessage = null,
            loginBroadcast = null,
            baseXpRate = 1.0,
            globalXpRate = 1.0,
            spawnCoord = CoordGrid.ZERO,
            respawnCoord = CoordGrid.ZERO,
            devMode = true,
            requireRegistration = false,
            ignorePasswords = true,
            autoAssignDisplayNames = true,
        )

    private fun migrate(target: String? = null): MigrateResult {
        val configured =
            Flyway.configure()
                .dataSource(dbUrl, null, null)
                .locations("classpath:db/migration", "classpath:plugin/**/migration")
        if (target != null) {
            configured.target(target)
        }
        return configured.load().migrate()
    }

    private fun openDb(): SqliteDatabase =
        SqliteDatabase()
            .apply {
                connect(
                    SqliteConnection(DatabaseConfig("jdbc:sqlite:", dbFile.toString(), null, null))
                )
            }
            .also(openDbs::add)

    @AfterEach
    fun teardown() {
        openDbs.forEach { runCatching { it.close() } }
        openDbs.clear()
    }

    private fun testPlayer(init: Player.() -> Unit = {}): Player =
        TestPlayerFactory().create(coords = CoordGrid.ZERO) {
            uuid = 1L
            observerUUID = 1L
            modLevel = ModLevelTypeBuilder("player").build(0)
            init()
        }

    @Test
    fun `new characters start unselected with no game mode`() = runBlocking {
        migrate()
        val db = openDb()
        db.withTransaction { connection ->
            val accountId = repository.insertOrSelectAccountId(connection, "newbie", "hash")!!
            val characterId = repository.insertAndSelectCharacterId(connection, accountId)!!
            assertNotNull(characterId)

            val metadata = repository.selectAndCreateMetadataList(connection, "newbie")
            assertNotNull(metadata)
            val data = metadata!!.accountData
            assertNull(data.gameMode)
            assertEquals(false, data.gameModeSelected)
            assertEquals(HardcoreStatus.DISABLED, data.hardcoreStatus)
            assertNull(data.groupId)
        }
        Unit
    }

    @Test
    fun `selected game mode round-trips through save and load`() = runBlocking {
        migrate()
        val db = openDb()
        db.withTransaction { connection ->
            val accountId = repository.insertOrSelectAccountId(connection, "iron", "hash")!!
            val characterId = repository.insertAndSelectCharacterId(connection, accountId)!!

            val player = testPlayer {
                gameMode = GameMode.HARDCORE_ULTIMATE_IRONMAN
                gameModeSelected = true
                gameModeSelectedAt = LocalDateTime.of(2025, 1, 1, 12, 0)
                hardcoreStatus = HardcoreStatus.ACTIVE
            }
            repository.save(connection, player, accountId, characterId)

            val metadata = repository.selectAndCreateMetadataList(connection, "iron")!!
            val data = metadata.accountData
            assertEquals(GameMode.HARDCORE_ULTIMATE_IRONMAN, data.gameMode)
            assertTrue(data.gameModeSelected)
            assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0), data.gameModeSelectedAt)
            assertEquals(HardcoreStatus.ACTIVE, data.hardcoreStatus)
        }
        Unit
    }

    @Test
    fun `demoted hardcore state persists`() = runBlocking {
        migrate()
        val db = openDb()
        db.withTransaction { connection ->
            val accountId = repository.insertOrSelectAccountId(connection, "hc", "hash")!!
            val characterId = repository.insertAndSelectCharacterId(connection, accountId)!!

            val player = testPlayer {
                gameMode = GameMode.ULTIMATE_IRONMAN
                gameModeSelected = true
                hardcoreStatus = HardcoreStatus.DEMOTED
                hardcoreDeathCount = 1
            }
            repository.save(connection, player, accountId, characterId)

            val data = repository.selectAndCreateMetadataList(connection, "hc")!!.accountData
            assertEquals(GameMode.ULTIMATE_IRONMAN, data.gameMode)
            assertEquals(HardcoreStatus.DEMOTED, data.hardcoreStatus)
            assertEquals(1, data.hardcoreDeathCount)
        }
        Unit
    }

    @Test
    fun `group membership and storage persist`() = runBlocking {
        migrate()
        val db = openDb()
        db.withTransaction { connection ->
            val accountId = repository.insertOrSelectAccountId(connection, "gim", "hash")!!
            val characterId = repository.insertAndSelectCharacterId(connection, accountId)!!

            connection
                .prepareStatement(
                    "INSERT INTO ironman_groups (realm_id, name, leader_character_id) " +
                        "VALUES (?, 'theboys', ?)"
                )
                .use {
                    it.setInt(1, 1)
                    it.setInt(2, characterId)
                    it.executeUpdate()
                }

            val player = testPlayer {
                gameMode = GameMode.GROUP_IRONMAN
                gameModeSelected = true
                groupId = 1
                groupRank = GroupRank.LEADER
                groupJoinedAt = LocalDateTime.of(2025, 2, 2, 8, 30)
                groupSettingsVersion = 1
            }
            repository.save(connection, player, accountId, characterId)

            // Group storage row write-through, matching GroupIronmanService.savePlayerGroupData.
            connection
                .prepareStatement(
                    "INSERT INTO ironman_group_storage (group_id, slot, obj, count, vars) " +
                        "VALUES (1, 0, 4151, 1, 0)"
                )
                .use { it.executeUpdate() }
            connection
                .prepareStatement(
                    "INSERT INTO ironman_group_audit " +
                        "(group_id, character_id, action, obj, count) VALUES (1, ?, 'STORAGE_DEPOSIT', 4151, 1)"
                )
                .use {
                    it.setInt(1, characterId)
                    it.executeUpdate()
                }

            val data = repository.selectAndCreateMetadataList(connection, "gim")!!.accountData
            assertEquals(GameMode.GROUP_IRONMAN, data.gameMode)
            assertEquals(1, data.groupId)
            assertEquals(GroupRank.LEADER, data.groupRank)

            val stored =
                connection
                    .prepareStatement(
                        "SELECT obj, count FROM ironman_group_storage WHERE group_id = 1 AND slot = 0"
                    )
                    .use {
                        val rs = it.executeQuery()
                        if (rs.next()) rs.getInt("obj") to rs.getInt("count") else null
                    }
            assertEquals(4151 to 1, stored)
        }
        Unit
    }

    @Test
    fun `accounts existing before V19 are migrated to REGULAR and selected`() = runBlocking {
        // Migrate only up to the last pre-ironman migration, insert a "legacy" character with
        // no game mode columns, then run the rest of the migrations and verify the backfill.
        migrate(target = "18")
        var db = openDb()
        db.withTransaction { connection ->
            val accountId = repository.insertOrSelectAccountId(connection, "legacy", "hash")!!
            repository.insertAndSelectCharacterId(connection, accountId)!!
        }
        db.close()

        migrate()

        db = openDb()
        db.withTransaction { connection ->
            val data = repository.selectAndCreateMetadataList(connection, "legacy")!!.accountData
            assertEquals(GameMode.REGULAR, data.gameMode)
            assertTrue(data.gameModeSelected)
            assertNotNull(data.gameModeSelectedAt)
        }
        Unit
    }
}
