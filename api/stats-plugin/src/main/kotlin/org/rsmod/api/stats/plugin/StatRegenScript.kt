package org.rsmod.api.stats.plugin

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.timers
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hands
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class StatRegenScript
@Inject
constructor(
    private val statTypes: StatTypeList,
    private val wornBonuses: WornBonuses,
    private val perks: PerkService,
    private val random: GameRandom,
) : PluginScript() {
    private val regenStats by lazy { statTypes.values.toRegenStats() }

    override fun ScriptContext.startup() {
        onPlayerLogin { player.initRegenTimers() }

        onPlayerSoftTimer(timers.stat_regen) { player.statRegen() }
        onPlayerSoftTimer(timers.stat_boost_restore) { player.statBoostRestore() }
        onPlayerSoftTimer(timers.health_regen) { player.healthRegen() }

        onPlayerSoftTimer(timers.rapidrestore_regen) { player.statRegen() }
    }

    private fun Player.initRegenTimers() {
        softTimer(timers.stat_regen, constants.stat_regen_interval)
        softTimer(timers.stat_boost_restore, constants.stat_boost_restore_interval)
        softTimer(timers.health_regen, constants.health_regen_interval)
    }

    private fun Player.statRegen() {
        for (stat in regenStats) {
            val base = statBase(stat)
            val current = stat(stat)
            if (current < base) {
                statAdd(stat, constant = 1, percent = 0)
            }
        }
    }

    private fun Player.statBoostRestore() {
        for (stat in regenStats) {
            val base = statBase(stat)
            val current = stat(stat)
            if (current > base) {
                statSub(stat, constant = 1, percent = 0)
            }
        }
    }

    private fun Player.healthRegen() {
        // Equipment-instance `MaximumHealth` affixes raise the regen cap above the base level.
        val cap = wornBonuses.maximumHitpoints(this)
        if (hitpoints >= cap) {
            return
        }
        val amount = if (hands.isType(objs.regen_bracelet)) 2 else 1
        // Regeneration perk: per-level chance of one bonus hitpoint per regen tick.
        val bonus = if (random.of(10_000) < perks.regenBonusChanceBps(this)) 1 else 0
        statAdd(stats.hitpoints, constant = min(amount + bonus, cap - hitpoints), percent = 0)
    }

    private fun Collection<StatType>.toRegenStats(): List<StatType> {
        return filter { !it.isType(stats.prayer) && !it.isType(stats.hitpoints) }
    }
}
