package org.rsmod.content.other.devhub

import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.stat.StatType

/**
 * Maps a Skills-tab category row to the stats its menu lists.
 *
 * Membership is by lowercase display name. Any stat no set claims (a future cache's new skill, or a
 * renamed one) lands in Support rather than disappearing, so every stat stays reachable - and
 * [DevHubCategory.AllSkills] lists everything regardless.
 */
internal object DevHubSkillGroups {
    private val COMBAT =
        setOf("attack", "strength", "defence", "ranged", "prayer", "magic", "hitpoints")

    private val GATHERING = setOf("mining", "fishing", "woodcutting", "farming", "hunter")

    private val ARTISAN =
        setOf(
            "cooking",
            "smithing",
            "fletching",
            "firemaking",
            "herblore",
            "crafting",
            "construction",
            "runecraft",
            "runecrafting",
        )

    /** Stats behind a Skills-tab category row; null for any other category. */
    fun <T : StatType> forCategory(category: DevHubCategory, stats: List<T>): List<T>? =
        when (category) {
            DevHubCategory.AllSkills -> stats
            DevHubCategory.CombatSkills -> stats.filter { it.key() in COMBAT }
            DevHubCategory.GatheringSkills -> stats.filter { it.key() in GATHERING }
            DevHubCategory.ArtisanSkills -> stats.filter { it.key() in ARTISAN }
            DevHubCategory.SupportSkills ->
                stats.filter {
                    it.key() !in COMBAT && it.key() !in GATHERING && it.key() !in ARTISAN
                }
            else -> null
        }

    private fun StatType.key(): String = displayName.lowercase()
}

/**
 * Sets a stat's base level, current level, and xp to exactly [level], immediately and permanently.
 *
 * This is the `InitialStatsScript` recipe rather than the `::master` one: writing fine xp + both
 * levels directly handles raising, lowering, and no-op uniformly (`statRevert` throws when the
 * delta is zero). Persistence needs no extra step - the stats table is saved from `statMap` on
 * logout.
 */
@OptIn(InternalApi::class)
internal fun Player.setDevHubStatLevel(stat: StatType, level: Int) {
    statMap.setFineXP(stat, PlayerSkillXPTable.getFineXPFromLevel(level))
    statMap.setCurrentLevel(stat, level.toByte())
    statMap.setBaseLevel(stat, level.toByte())
    markStatUpdate(stat)
    appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
    PlayerInterfaceUpdates.updateCombatLevel(this)
}
