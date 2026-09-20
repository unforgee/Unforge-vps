package org.rsmod.content.areas.tutorialisland.scripts.rules

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.advanced.onAdvanceStat
import org.rsmod.api.script.advanced.onChangeStat
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Global Tutorial Island rules while `!tut2_completed`:
 * - trainable skills hard-capped at level 3
 * - no Hitpoints XP / level advances from combat
 * - HP floors at 1 (no death)
 */
class TutorialIslandRulesScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (stat in CAPPED_STATS) {
            onAdvanceStat(stat) { enforceCap(stat) }
        }
        onAdvanceStat(stats.hitpoints) { blockHitpointsXp() }
        onChangeStat(stats.hitpoints) { enforceHpFloor() }
    }

    private fun ProtectedAccess.enforceCap(stat: StatType) {
        if (!progression.isActive(player)) return
        val base = player.statBase(stat)
        if (base <= CAP_LEVEL) return
        val xp = PlayerSkillXPTable.getXPFromLevel(CAP_LEVEL)
        player.statMap.setXP(stat, xp)
        player.statMap.setBaseLevel(stat, CAP_LEVEL.toByte())
        player.statMap.setCurrentLevel(stat, CAP_LEVEL.toByte())
        mes("You can't advance beyond level $CAP_LEVEL while on Tutorial Island.")
    }

    private fun ProtectedAccess.blockHitpointsXp() {
        if (!progression.isActive(player)) return
        // Keep HP base at 10; strip any XP that would advance HP on the island.
        val xp = PlayerSkillXPTable.getXPFromLevel(10)
        player.statMap.setXP(stats.hitpoints, xp)
        player.statMap.setBaseLevel(stats.hitpoints, 10.toByte())
        if (player.hitpoints > 10) {
            player.statMap.setCurrentLevel(stats.hitpoints, 10.toByte())
        }
    }

    private fun ProtectedAccess.enforceHpFloor() {
        if (!progression.isActive(player)) return
        if (player.hitpoints > 0) return
        player.statMap.setCurrentLevel(stats.hitpoints, 1.toByte())
        player.clearQueue(queues.death)
        mes("You would have died, but the gods of Tutorial Island protect you.")
    }

    companion object {
        private const val CAP_LEVEL = 3

        private val CAPPED_STATS =
            listOf(
                stats.attack,
                stats.strength,
                stats.defence,
                stats.ranged,
                stats.prayer,
                stats.magic,
                stats.mining,
                stats.smithing,
                stats.fishing,
                stats.cooking,
                stats.firemaking,
                stats.woodcutting,
            )
    }
}
