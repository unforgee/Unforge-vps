package org.rsmod.api.talents

import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.util.setNullableString

/**
 * JDBC adapter for the `companies`/`company_members`/`company_talents`/`company_audit` tables.
 *
 * The caller owns the transaction - the character save pipeline wraps [saveState] inside the
 * member's save, while structural calls ([insertMember], [deleteMember], [insertAudit]...) run
 * inside `GameDbManager` requests on the db thread.
 */
public class CompanyRepository(private val connection: DatabaseConnection) {
    // ---- Load ------------------------------------------------------------------

    /** The membership row for [characterId], or `null` when the character is company-less. */
    public fun loadMembership(characterId: Int): CompanyMembership? =
        connection
            .prepareStatement("SELECT company_id, role FROM company_members WHERE character_id = ?")
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        return null
                    }
                    CompanyMembership(
                        characterId = characterId,
                        companyId = rows.getInt("company_id"),
                        role = CompanyRole.fromDb(rows.getString("role")) ?: CompanyRole.MEMBER,
                    )
                }
            }

    /** The company's id/name/points + trained ranks, or `null` when the id is unknown. */
    public fun loadCompany(companyId: Int): CompanyState? {
        val company =
            connection
                .prepareStatement("SELECT name, talent_points FROM companies WHERE id = ?")
                .use { statement ->
                    statement.setInt(1, companyId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            return null
                        }
                        rows.getString("name") to rows.getInt("talent_points")
                    }
                } ?: return null
        val state = CompanyState(companyId, company.first)
        state.points = company.second
        connection
            .prepareStatement("SELECT talent_id, rank FROM company_talents WHERE company_id = ?")
            .use { statement ->
                statement.setInt(1, companyId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        state.ranks[rows.getString("talent_id")] = rows.getInt("rank")
                    }
                }
            }
        return state
    }

    // ---- Save (inside the member's character-save transaction) --------------------

    /**
     * Flushes [state]'s points, ranks and pending audit rows. The caller holds [state]'s monitor
     * and only calls this while [CompanyState.dirty] or pending audit exists.
     */
    public fun saveState(state: CompanyState) {
        if (state.dirty) {
            connection.prepareStatement("UPDATE companies SET talent_points = ? WHERE id = ?").use {
                it.setInt(1, state.points)
                it.setInt(2, state.id)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM company_talents WHERE company_id = ?").use {
                it.setInt(1, state.id)
                it.executeUpdate()
            }
            connection
                .prepareStatement(
                    "INSERT INTO company_talents (company_id, talent_id, rank) VALUES (?, ?, ?)"
                )
                .use { statement ->
                    for ((talentId, rank) in state.ranks) {
                        statement.setInt(1, state.id)
                        statement.setString(2, talentId)
                        statement.setInt(3, rank)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            state.dirty = false
        }
        if (state.pendingAudit.isNotEmpty()) {
            connection
                .prepareStatement(
                    "INSERT INTO company_audit (company_id, character_id, action, detail) " +
                        "VALUES (?, ?, ?, ?)"
                )
                .use { statement ->
                    for (entry in state.pendingAudit) {
                        statement.setInt(1, state.id)
                        statement.setInt(2, entry.characterId)
                        statement.setString(3, entry.action)
                        statement.setNullableString(4, entry.detail)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            state.pendingAudit.clear()
        }
    }

    // ---- Structural mutations (already on the db thread) --------------------------

    /** Inserts a company row and returns its generated id (`-1` on failure). */
    public fun insertCompany(realmId: Int, name: String?, leaderCharacterId: Int): Int =
        connection
            .prepareStatement(
                "INSERT INTO companies (realm_id, name, leader_character_id) VALUES (?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            )
            .use { statement ->
                statement.setInt(1, realmId)
                statement.setNullableString(2, name?.ifBlank { null })
                statement.setInt(3, leaderCharacterId)
                statement.executeUpdate()
                val keys = statement.generatedKeys
                if (keys.next()) keys.getInt(1) else -1
            }

    public fun insertMember(characterId: Int, companyId: Int, role: CompanyRole): Boolean =
        connection
            .prepareStatement(
                "INSERT INTO company_members (character_id, company_id, role) VALUES (?, ?, ?)"
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setInt(2, companyId)
                statement.setString(3, role.dbName)
                statement.executeUpdate() == 1
            }

    public fun deleteMember(characterId: Int): Boolean =
        connection.prepareStatement("DELETE FROM company_members WHERE character_id = ?").use {
            it.setInt(1, characterId)
            it.executeUpdate() == 1
        }

    public fun deleteMembersOf(companyId: Int) {
        connection.prepareStatement("DELETE FROM company_members WHERE company_id = ?").use {
            it.setInt(1, companyId)
            it.executeUpdate()
        }
    }

    public fun countMembers(companyId: Int): Int =
        connection
            .prepareStatement("SELECT COUNT(*) FROM company_members WHERE company_id = ?")
            .use { statement ->
                statement.setInt(1, companyId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }

    public fun deleteCompany(companyId: Int) {
        connection.prepareStatement("DELETE FROM companies WHERE id = ?").use {
            it.setInt(1, companyId)
            it.executeUpdate()
        }
    }

    /** Immediate audit write for structural ops that bypass the pending queue. */
    public fun insertAudit(companyId: Int, characterId: Int, action: String, detail: String?) {
        connection
            .prepareStatement(
                "INSERT INTO company_audit (company_id, character_id, action, detail) " +
                    "VALUES (?, ?, ?, ?)"
            )
            .use {
                it.setInt(1, companyId)
                it.setInt(2, characterId)
                it.setString(3, action)
                it.setNullableString(4, detail)
                it.executeUpdate()
            }
    }
}
