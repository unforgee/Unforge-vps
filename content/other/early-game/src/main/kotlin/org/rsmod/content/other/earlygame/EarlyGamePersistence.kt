package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

internal object EarlyGameCodec {
    fun encode(values: Set<String>): String = values.sorted().joinToString(";")

    fun decode(value: String?): Set<String> =
        value.orEmpty().split(';').filter { it.isNotBlank() }.toSet()

    fun encodeCounts(values: Map<String, Int>): String =
        values.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeCounts(value: String?): Map<String, Int> =
        value
            .orEmpty()
            .split(';')
            .mapNotNull { token ->
                val pair = token.split('=', limit = 2)
                if (pair.size == 2)
                    pair[0].takeIf { it.isNotBlank() }?.let { it to (pair[1].toIntOrNull() ?: 0) }
                else null
            }
            .toMap()
}

internal class EarlyGameRepository(private val connection: DatabaseConnection) {
    fun load(characterId: Int): EarlyGameState? {
        val statement =
            connection.prepareStatement(
                "SELECT * FROM early_game_progression WHERE character_id = ?"
            )
        statement.use {
            it.setInt(1, characterId)
            it.executeQuery().use { rs ->
                if (!rs.next()) return null
                return EarlyGameState(
                    starterRelic =
                        rs.getString("starter_relic")?.let { value ->
                            runCatching { StarterRelic.valueOf(value) }.getOrNull()
                        },
                    relicSelected = rs.getInt("relic_selected") != 0,
                    lastRelicChangeEpoch =
                        rs.getLong("last_relic_change_epoch").takeIf { !rs.wasNull() },
                    completedMilestones =
                        EarlyGameCodec.decode(rs.getString("completed_milestones")),
                    claimedMilestoneRewards =
                        EarlyGameCodec.decode(rs.getString("claimed_milestone_rewards")),
                    firstBondUnlocked = rs.getInt("first_bond_unlocked") != 0,
                    firstBondCompleted = rs.getInt("first_bond_completed") != 0,
                    starterCompanionSelected = rs.getInt("starter_companion_selected") != 0,
                    starterCompanionId = rs.getString("starter_companion_id"),
                    companionTutorialCompleted = rs.getInt("companion_tutorial_completed") != 0,
                    companionBasics = EarlyGameCodec.decode(rs.getString("companion_basics")),
                    discoveries = EarlyGameCodec.decode(rs.getString("discoveries")),
                    discoveryPoints = rs.getInt("discovery_points"),
                    discoveryRewardMilestones =
                        EarlyGameCodec.decode(rs.getString("discovery_reward_milestones")),
                    worldFindStatistics =
                        EarlyGameCodec.decodeCounts(rs.getString("world_find_statistics")),
                    worldFindAvailableAtEpoch = rs.getLong("world_find_available_at_epoch"),
                    trackerMinimized = rs.getInt("tracker_minimized") != 0,
                    trackerHidden = rs.getInt("tracker_hidden") != 0,
                    legacyPlayer = rs.getInt("legacy_player") != 0,
                    trialActive = rs.getInt("trial_active") != 0,
                    trialAttempts = rs.getInt("trial_attempts"),
                )
            }
        }
    }

    fun save(characterId: Int, state: EarlyGameState) {
        val statement =
            connection.prepareStatement(
                """
            INSERT INTO early_game_progression
              (character_id, starter_relic, relic_selected, last_relic_change_epoch,
               completed_milestones, claimed_milestone_rewards, first_bond_unlocked,
               first_bond_completed, starter_companion_selected, starter_companion_id,
               companion_tutorial_completed, companion_basics, discoveries, discovery_points,
               discovery_reward_milestones, world_find_statistics, world_find_available_at_epoch,
               tracker_minimized,
               tracker_hidden, legacy_player, trial_active, trial_attempts, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(character_id) DO UPDATE SET
              starter_relic = excluded.starter_relic,
              relic_selected = excluded.relic_selected,
              last_relic_change_epoch = excluded.last_relic_change_epoch,
              completed_milestones = excluded.completed_milestones,
              claimed_milestone_rewards = excluded.claimed_milestone_rewards,
              first_bond_unlocked = excluded.first_bond_unlocked,
              first_bond_completed = excluded.first_bond_completed,
              starter_companion_selected = excluded.starter_companion_selected,
              starter_companion_id = excluded.starter_companion_id,
              companion_tutorial_completed = excluded.companion_tutorial_completed,
              companion_basics = excluded.companion_basics,
              discoveries = excluded.discoveries,
              discovery_points = excluded.discovery_points,
              discovery_reward_milestones = excluded.discovery_reward_milestones,
              world_find_statistics = excluded.world_find_statistics,
              world_find_available_at_epoch = excluded.world_find_available_at_epoch,
              tracker_minimized = excluded.tracker_minimized,
              tracker_hidden = excluded.tracker_hidden,
              legacy_player = excluded.legacy_player,
              trial_active = excluded.trial_active,
              trial_attempts = excluded.trial_attempts,
              updated_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
        statement.use {
            var index = 1
            it.setInt(index++, characterId)
            it.setString(index++, state.starterRelic?.name)
            it.setInt(index++, if (state.relicSelected) 1 else 0)
            if (state.lastRelicChangeEpoch == null) it.setObject(index++, null)
            else it.setLong(index++, state.lastRelicChangeEpoch)
            it.setString(index++, EarlyGameCodec.encode(state.completedMilestones))
            it.setString(index++, EarlyGameCodec.encode(state.claimedMilestoneRewards))
            it.setInt(index++, if (state.firstBondUnlocked) 1 else 0)
            it.setInt(index++, if (state.firstBondCompleted) 1 else 0)
            it.setInt(index++, if (state.starterCompanionSelected) 1 else 0)
            it.setString(index++, state.starterCompanionId)
            it.setInt(index++, if (state.companionTutorialCompleted) 1 else 0)
            it.setString(index++, EarlyGameCodec.encode(state.companionBasics))
            it.setString(index++, EarlyGameCodec.encode(state.discoveries))
            it.setInt(index++, state.discoveryPoints)
            it.setString(index++, EarlyGameCodec.encode(state.discoveryRewardMilestones))
            it.setString(index++, EarlyGameCodec.encodeCounts(state.worldFindStatistics))
            it.setLong(index++, state.worldFindAvailableAtEpoch)
            it.setInt(index++, if (state.trackerMinimized) 1 else 0)
            it.setInt(index++, if (state.trackerHidden) 1 else 0)
            it.setInt(index++, if (state.legacyPlayer) 1 else 0)
            it.setInt(index++, if (state.trialActive) 1 else 0)
            it.setInt(index, state.trialAttempts)
            it.executeUpdate()
        }
    }
}

public data class EarlyGameCharacterData(public val state: EarlyGameState) :
    CharacterDataStage.Segment

public class EarlyGameCharacterApplier
@Inject
constructor(private val progression: EarlyGameProgressionService) :
    CharacterDataStage.Applier<EarlyGameCharacterData> {
    override fun apply(player: Player, data: EarlyGameCharacterData) {
        progression.restore(player.characterId, data.state)
    }
}

public class EarlyGameCharacterPipeline
@Inject
constructor(
    private val applier: EarlyGameCharacterApplier,
    private val progression: EarlyGameProgressionService,
) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val state = EarlyGameRepository(connection).load(metadata.characterId) ?: EarlyGameState()
        metadata.add(applier, EarlyGameCharacterData(state))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        progression.state(characterId)?.let {
            EarlyGameRepository(connection).save(characterId, it)
        }
    }
}
