package org.rsmod.api.companion

import org.rsmod.api.db.DatabaseConnection

/**
 * Persistence adapter for companion skill-track experience (`companion_skill_xp` table). The caller
 * owns the transaction and commits it. Rows are keyed by companion id so they share the companion's
 * cascade-delete lifecycle.
 */
public class CompanionSkillRepository(private val connection: DatabaseConnection) {
    public fun save(
        companionId: Long,
        ownerCharacterId: Long,
        skill: CompanionSkill,
        experience: Long,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO companion_skill_xp (companion_id, owner_character_id, skill, experience) VALUES (?, ?, ?, ?) ON CONFLICT(companion_id, skill) DO UPDATE SET experience=excluded.experience, owner_character_id=excluded.owner_character_id"
            )
            .use { statement ->
                statement.setLong(1, companionId)
                statement.setLong(2, ownerCharacterId)
                statement.setString(3, skill.name)
                statement.setLong(4, experience)
                statement.executeUpdate()
            }
    }

    /** companion id -> (skill -> experience) for every companion owned by [ownerCharacterId]. */
    public fun load(ownerCharacterId: Long): Map<Long, Map<CompanionSkill, Long>> =
        connection
            .prepareStatement(
                "SELECT companion_id, skill, experience FROM companion_skill_xp WHERE owner_character_id=?"
            )
            .use { statement ->
                statement.setLong(1, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    val result = mutableMapOf<Long, MutableMap<CompanionSkill, Long>>()
                    while (rows.next()) {
                        val companionId = rows.getLong(1)
                        val skill = CompanionSkill.valueOf(rows.getString(2))
                        result.getOrPut(companionId) { mutableMapOf() }[skill] = rows.getLong(3)
                    }
                    result
                }
            }
}
