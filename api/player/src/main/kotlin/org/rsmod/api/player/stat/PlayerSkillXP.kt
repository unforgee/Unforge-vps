package org.rsmod.api.player.stat

import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.utils.skills.CombatLevel
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.stat.StatType

public object PlayerSkillXP {
    // Stateless service - safe to instantiate in an object. Perk xp bonuses are resolved here so
    // every `statAdvance` caller benefits automatically.
    private val perks = PerkService()

    public fun internalAddXP(player: Player, stat: StatType, xp: Double, rate: Double): Int {
        val perkBps = perks.skillXpBps(player, stat)
        val scaled = if (perkBps > 0) xp * (10_000 + perkBps) / 10_000.0 else xp
        return player.addXP(stat, scaled, rate)
    }

    private fun Player.addXP(stat: StatType, xp: Double, rate: Double): Int {
        val fineXp = PlayerStatMap.toFineXP(xp * rate)
        if (fineXp.isInfinite()) {
            throw IllegalArgumentException("Total XP being added is too high! (xp=$xp, rate=$rate)")
        }
        val addedFineXp = statMap.addXP(stat, fineXp)
        if (addedFineXp == 0) {
            // UpdateStat packet is sent even if stat is maxed.
            updateStat(stat)
            return 0
        }
        checkLevelUp(stat)
        updateStat(stat)
        return PlayerStatMap.normalizeFineXP(addedFineXp)
    }

    private fun PlayerStatMap.addXP(stat: StatType, fineXp: Double): Int {
        val currXp = getFineXP(stat)
        val sumXp = min(PlayerStatMap.MAX_FINE_XP.toDouble(), currXp + fineXp).toInt()
        val addedXp = sumXp - currXp
        if (addedXp > 0) {
            setFineXP(stat, sumXp)
        }
        return addedXp
    }

    @OptIn(InternalApi::class)
    private fun Player.checkLevelUp(stat: StatType) {
        val baseLevel = statBase(stat)
        if (baseLevel >= stat.maxLevel) {
            return
        }
        val nextLevelXp = PlayerSkillXPTable.getXPFromLevel(baseLevel + 1)
        val currentXp = statMap.getXP(stat)
        if (currentXp >= nextLevelXp) {
            val newLevel = min(stat.maxLevel, PlayerSkillXPTable.getLevelFromXP(currentXp))
            statMap.setBaseLevel(stat, newLevel.toByte())

            val setCurrLevel = stat(stat) == baseLevel
            if (setCurrLevel) {
                statMap.setCurrentLevel(stat, newLevel.toByte())
            }

            engineQueueChangeStat(stat)
            engineQueueAdvanceStat(stat)
        }

        val combatLevel = calculateCombatLevel(this)
        if (combatLevel != this.combatLevel) {
            appearance.combatLevel = combatLevel
            // TODO: Should this update the entire combat tab or just the combat level vars?
            PlayerInterfaceUpdates.updateCombatLevel(this)
        }
    }

    public fun calculateCombatLevel(player: Player): Int =
        CombatLevel.calculate(
            attack = player.baseAttackLvl,
            strength = player.baseStrengthLvl,
            defence = player.baseDefenceLvl,
            hitpoints = player.baseHitpointsLvl,
            ranged = player.baseRangedLvl,
            magic = player.baseMagicLvl,
            prayer = player.basePrayerLvl,
        )
}
