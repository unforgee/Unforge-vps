package org.rsmod.api.talents

import java.nio.file.Path
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
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase

/**
 * Round-trip coverage for the V23 company schema through [CompanyRepository]: membership rows, the
 * shared points/ranks flush, audit entries, single-company enforcement and the cascade behaviour of
 * member/company deletion - against a real SQLite database, not a stub.
 */
class CompanyPersistenceTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dbFile: Path
    private val openDbs = mutableListOf<SqliteDatabase>()

    private val dbUrl: String
        get() = "jdbc:sqlite:${dbFile.toAbsolutePath()}"

    @BeforeEach
    fun setup() {
        dbFile = tempDir.resolve("game.db")
    }

    private fun migrate(): MigrateResult =
        Flyway.configure()
            .dataSource(dbUrl, null, null)
            .locations("classpath:db/migration", "classpath:plugin/**/migration")
            .load()
            .migrate()

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

    private suspend fun insertCompany(db: SqliteDatabase, name: String? = "Test Co"): Int =
        db.withTransaction { connection ->
            CompanyRepository(connection)
                .insertCompany(realmId = 1, name = name, leaderCharacterId = 42)
        }

    @Test
    fun `membership round-trips through the members table`() = runBlocking {
        migrate()
        val db = openDb()
        val companyId = insertCompany(db)
        assertTrue(companyId > 0)

        db.withTransaction { connection ->
            val repository = CompanyRepository(connection)
            assertTrue(repository.insertMember(42, companyId, CompanyRole.LEADER))
            assertTrue(repository.insertMember(43, companyId, CompanyRole.MEMBER))
        }
        db.withTransaction { connection ->
            val repository = CompanyRepository(connection)
            val leader = repository.loadMembership(42)
            assertNotNull(leader)
            assertEquals(companyId, leader!!.companyId)
            assertEquals(CompanyRole.LEADER, leader.role)
            assertEquals(CompanyRole.MEMBER, repository.loadMembership(43)!!.role)
            assertEquals(2, repository.countMembers(companyId))
            assertNull(repository.loadMembership(99))
        }
    }

    @Test
    fun `a character belongs to at most one company`() = runBlocking {
        migrate()
        val db = openDb()
        val first = insertCompany(db, "First")
        val second = insertCompany(db, "Second")
        db.withTransaction { connection ->
            val repository = CompanyRepository(connection)
            assertTrue(repository.insertMember(42, first, CompanyRole.LEADER))
            // The character_id PK makes a second membership impossible.
            assertTrue(
                runCatching { repository.insertMember(42, second, CompanyRole.MEMBER) }.isFailure
            )
            assertEquals(first, repository.loadMembership(42)!!.companyId)
        }
    }

    @Test
    fun `company state save survives a reload - the restart contract`() = runBlocking {
        migrate()
        val db = openDb()
        val companyId = insertCompany(db)

        // Simulate a live session: +3 points, train two ranks, queue an audit entry, flush.
        db.withTransaction { connection ->
            val state = CompanyState(companyId, "Test Co")
            state.points = 7
            state.ranks["shared-knowledge"] = 2
            state.ranks["war-chest-fund"] = 1
            state.dirty = true
            state.pendingAudit += CompanyAuditEntry(42, "TALENT_TRAIN", "shared-knowledge rank 2")
            CompanyRepository(connection).saveState(state)
            assertEquals(false, state.dirty)
            assertTrue(state.pendingAudit.isEmpty())
        }

        // "Restart": a brand-new load must see identical state.
        db.withTransaction { connection ->
            val loaded = CompanyRepository(connection).loadCompany(companyId)
            assertNotNull(loaded)
            assertEquals(7, loaded!!.points)
            assertEquals(2, loaded.ranks["shared-knowledge"])
            assertEquals(1, loaded.ranks["war-chest-fund"])
            assertNull(loaded.ranks["banner-of-unity"])
        }

        // And the audit row was recorded.
        db.withTransaction { connection ->
            connection
                .prepareStatement("SELECT action, detail FROM company_audit WHERE company_id = ?")
                .use { statement ->
                    statement.setInt(1, companyId)
                    val rows = statement.executeQuery()
                    assertTrue(rows.next())
                    assertEquals("TALENT_TRAIN", rows.getString("action"))
                }
        }
    }

    @Test
    fun `removing a member clears their membership row`() = runBlocking {
        migrate()
        val db = openDb()
        val companyId = insertCompany(db)
        db.withTransaction { connection ->
            val repository = CompanyRepository(connection)
            repository.insertMember(42, companyId, CompanyRole.LEADER)
            assertTrue(repository.deleteMember(42))
            assertNull(repository.loadMembership(42))
        }
    }

    @Test
    fun `dissolving a company cascades members and talents`() = runBlocking {
        migrate()
        val db = openDb()
        val companyId = insertCompany(db)
        db.withTransaction { connection ->
            val repository = CompanyRepository(connection)
            repository.insertMember(42, companyId, CompanyRole.LEADER)
            repository.insertMember(43, companyId, CompanyRole.MEMBER)
            val state = CompanyState(companyId, "Test Co")
            state.ranks["shared-knowledge"] = 1
            state.dirty = true
            repository.saveState(state)

            repository.deleteMembersOf(companyId)
            repository.deleteCompany(companyId)

            assertNull(repository.loadMembership(42))
            assertNull(repository.loadCompany(companyId))
        }
    }
}
