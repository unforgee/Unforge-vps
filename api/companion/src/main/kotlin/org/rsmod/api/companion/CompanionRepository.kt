package org.rsmod.api.companion

import org.rsmod.api.db.DatabaseConnection

/** Persistence adapter. The caller owns the transaction and commits it. */
public class CompanionRepository(private val connection: DatabaseConnection) :
    CompanionPersistence {
    override fun save(companion: Companion) {
        connection
            .prepareStatement(
                "INSERT INTO companions (id, owner_character_id, slot, name, companion_class, npc_id, level, experience, talent_points, active, state, hitpoints, maximum_hitpoints, incapacitated_until, encounter_id, gear_instance_ids, ability_loadout, attack_style, spellbook, autocast_spell_id, pack_capacity, combat_mode, evolution_stage, behaviour) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name=excluded.name, companion_class=excluded.companion_class, npc_id=excluded.npc_id, level=excluded.level, experience=excluded.experience, talent_points=excluded.talent_points, active=excluded.active, state=excluded.state, hitpoints=excluded.hitpoints, maximum_hitpoints=excluded.maximum_hitpoints, incapacitated_until=excluded.incapacitated_until, encounter_id=excluded.encounter_id, gear_instance_ids=excluded.gear_instance_ids, ability_loadout=excluded.ability_loadout, attack_style=excluded.attack_style, spellbook=excluded.spellbook, autocast_spell_id=excluded.autocast_spell_id, pack_capacity=excluded.pack_capacity, combat_mode=excluded.combat_mode, evolution_stage=excluded.evolution_stage, behaviour=excluded.behaviour WHERE companions.owner_character_id=excluded.owner_character_id AND companions.slot=excluded.slot"
            )
            .use { statement ->
                statement.setLong(1, companion.id)
                bind(statement, companion, 2)
                check(statement.executeUpdate() == 1) {
                    "Companion ${companion.id} was not found for update"
                }
            }
        connection.prepareStatement("DELETE FROM companion_talents WHERE companion_id=?").use {
            it.setLong(1, companion.id)
            it.executeUpdate()
        }
        connection
            .prepareStatement(
                "INSERT INTO companion_talents (companion_id, talent_id, ranks) VALUES (?, ?, ?)"
            )
            .use { statement ->
                companion.talents.forEach { talent ->
                    statement.setLong(1, companion.id)
                    statement.setString(2, talent.definitionId)
                    statement.setInt(3, talent.ranks)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
    }

    override fun load(ownerCharacterId: Long): List<Companion> =
        connection
            .prepareStatement(
                "SELECT id, slot, name, companion_class, npc_id, level, experience, talent_points, active, state, hitpoints, maximum_hitpoints, incapacitated_until, encounter_id, gear_instance_ids, ability_loadout, attack_style, spellbook, autocast_spell_id, pack_capacity, combat_mode, evolution_stage, behaviour FROM companions WHERE owner_character_id=? ORDER BY slot"
            )
            .use { statement ->
                statement.setLong(1, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val id = rows.getLong(1)
                            val talents =
                                connection
                                    .prepareStatement(
                                        "SELECT talent_id, ranks FROM companion_talents WHERE companion_id=? ORDER BY talent_id"
                                    )
                                    .use { talentsStatement ->
                                        talentsStatement.setLong(1, id)
                                        talentsStatement.executeQuery().use { talentRows ->
                                            buildList {
                                                while (talentRows.next()) add(
                                                    CompanionTalent(
                                                        talentRows.getString(1),
                                                        talentRows.getInt(2),
                                                    )
                                                )
                                            }
                                        }
                                    }
                            add(
                                Companion(
                                    id,
                                    ownerCharacterId,
                                    rows.getInt(2),
                                    rows.getString(3),
                                    CompanionClass.valueOf(rows.getString(4)),
                                    rows.getInt(5),
                                    rows.getInt(6),
                                    rows.getLong(7),
                                    rows.getInt(8),
                                    talents,
                                    rows.getString(16).split(',').filter(String::isNotBlank),
                                    rows.getInt(9) != 0,
                                    CompanionState.valueOf(rows.getString(10)),
                                    rows.getInt(11),
                                    rows.getInt(12),
                                    rows.getLong(13).takeUnless { rows.wasNull() },
                                    rows.getLong(14).takeUnless { rows.wasNull() },
                                    rows
                                        .getString(15)
                                        .split(',')
                                        .filter(String::isNotBlank)
                                        .map(String::toLong),
                                    CompanionAttackStyle.valueOf(rows.getString(17)),
                                    CompanionSpellbook.valueOf(rows.getString(18)),
                                    rows.getInt(19),
                                    rows.getInt(20).takeUnless { it == 0 } ?: 10,
                                    CompanionCombatMode.valueOf(rows.getString(21)),
                                    rows.getInt(22),
                                    CompanionBehaviour.deserialize(rows.getString(23)),
                                )
                            )
                        }
                    }
                }
            }

    private fun bind(statement: java.sql.PreparedStatement, companion: Companion, offset: Int) {
        statement.setLong(offset, companion.ownerCharacterId)
        statement.setInt(offset + 1, companion.slot)
        statement.setString(offset + 2, companion.name)
        statement.setString(offset + 3, companion.companionClass.name)
        statement.setInt(offset + 4, companion.npcId)
        statement.setInt(offset + 5, companion.level)
        statement.setLong(offset + 6, companion.experience)
        statement.setInt(offset + 7, companion.talentPoints)
        statement.setInt(offset + 8, if (companion.active) 1 else 0)
        statement.setString(offset + 9, companion.state.name)
        statement.setInt(offset + 10, companion.hitpoints)
        statement.setInt(offset + 11, companion.maximumHitpoints)
        if (companion.incapacitatedUntilEpochMillis == null)
            statement.setNull(offset + 12, java.sql.Types.INTEGER)
        else statement.setLong(offset + 12, companion.incapacitatedUntilEpochMillis)
        if (companion.encounterId == null) statement.setNull(offset + 13, java.sql.Types.INTEGER)
        else statement.setLong(offset + 13, companion.encounterId)
        statement.setString(offset + 14, companion.gearInstanceIds.joinToString(","))
        statement.setString(offset + 15, companion.abilityLoadout.joinToString(","))
        statement.setString(offset + 16, companion.attackStyle.name)
        statement.setString(offset + 17, companion.spellbook.name)
        statement.setInt(offset + 18, companion.autocastSpellId)
        statement.setInt(offset + 19, companion.packCapacity)
        statement.setString(offset + 20, companion.combatMode.name)
        statement.setInt(offset + 21, companion.evolutionStage)
        statement.setString(offset + 22, companion.behaviour.serialize())
    }
}
