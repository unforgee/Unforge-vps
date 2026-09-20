package org.rsmod.content.skills.herblore

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.combat.effects.StatusService
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.PotionStat
import org.rsmod.content.skills.herblore.configs.PotionTable
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drinking: the `Drink` op applies the dose from [PotionTable], consumes one dose and hands back
 * the next one down - or the empty container on the last dose.
 *
 * Objs in the cache's `potion` content group are looked up in the table; a potion the table does
 * not describe is left untouched (its dose is not spent) and logged at startup, rather than faked.
 * The table's own objs are registered individually too, so the server's potions keep working even
 * if one is missing the group tag.
 */
class DrinkPotionScript
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val statuses: StatusService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val drinkAnim = seqTypes[DRINK_ANIM]

        onOpHeld1(content.potion) { drink(it.type, it.slot, drinkAnim) }

        for (id in PotionTable.ids) {
            val type = objTypes[id] ?: continue
            onOpHeld1(type) { drink(type, it.slot, drinkAnim) }
        }

        warnOnUncoveredPotions()
    }

    private suspend fun ProtectedAccess.drink(
        type: UnpackedObjType,
        slot: Int,
        drinkAnim: SeqType?,
    ) {
        val potion = PotionTable[type.id] ?: return

        val deleted = invDel(inv, type, count = 1, slot = slot)
        if (!deleted.success) {
            return
        }

        objTypes[potion.next]?.let { invAdd(inv, it, count = 1, slot = slot) }

        if (drinkAnim != null) {
            anim(drinkAnim)
        }
        mes("You drink the potion.")
        apply(potion.effects)
        delay(DRINK_DELAY)
    }

    private fun ProtectedAccess.apply(effects: List<PotionEffect>) {
        for (effect in effects) {
            when (effect) {
                is PotionEffect.Boost ->
                    statBoost(stat(effect.stat), effect.constant, effect.percent)
                is PotionEffect.Restore ->
                    statHeal(stat(effect.stat), effect.constant, effect.percent)
                is PotionEffect.Drain -> drain(effect)
                is PotionEffect.RestoreAll -> {
                    for (restored in RESTORE_ALL_STATS) {
                        statHeal(restored, effect.constant, effect.percent)
                    }
                }
                is PotionEffect.RunEnergy -> {
                    val restored = effect.percent * RUN_ENERGY_PER_PERCENT
                    player.runEnergy = min(constants.run_max_energy, player.runEnergy + restored)
                }
                PotionEffect.CurePoison -> statuses.remove(player, StatusEffect.POISON)
            }
        }
    }

    private fun ProtectedAccess.drain(effect: PotionEffect.Drain) {
        statDrain(stat(effect.stat), effect.constant, effect.percent)

        // Zamorak brew drains hitpoints. A dose must never be able to kill the drinker.
        if (effect.stat == PotionStat.HITPOINTS && player.hitpoints < MIN_HITPOINTS) {
            statHeal(stats.hitpoints, MIN_HITPOINTS - player.hitpoints, 0)
        }
    }

    /** Flags potions the table does not describe, which therefore drink for no effect. */
    private fun warnOnUncoveredPotions() {
        val potions = objTypes.values.filter { it.isContentType(content.potion) }
        val missing = potions.filter { it.id !in PotionTable.ids }
        if (missing.isEmpty()) {
            return
        }
        logger.warn {
            "Potions with no entry in PotionTable ${missing.size}/${potions.size}: " +
                missing.take(MAX_LOGGED_MISSING).joinToString { it.name }
        }
    }

    private companion object {
        private const val DRINK_ANIM: Int = 829 // human_eat - OSRS uses the eat animation to drink
        private const val DRINK_DELAY: Int = 3
        private const val MIN_HITPOINTS: Int = 1
        private const val MAX_LOGGED_MISSING: Int = 25

        /** `Player.runEnergy` is 0-10000, so one percentage point is 100. */
        private const val RUN_ENERGY_PER_PERCENT: Int = constants.run_max_energy / 100

        /**
         * Every stat a restore potion brings back up, matching the OSRS restore list: everything
         * except hitpoints, and with prayer handled by its own effect.
         */
        private val RESTORE_ALL_STATS: List<StatType> =
            listOf(
                stats.attack,
                stats.defence,
                stats.strength,
                stats.ranged,
                stats.magic,
                stats.cooking,
                stats.woodcutting,
                stats.fletching,
                stats.fishing,
                stats.firemaking,
                stats.crafting,
                stats.smithing,
                stats.mining,
                stats.herblore,
                stats.agility,
                stats.thieving,
                stats.slayer,
                stats.farming,
                stats.runecrafting,
                stats.hunter,
                stats.construction,
            )

        private val logger = InlineLogger()
    }
}

private fun stat(value: PotionStat): StatType =
    when (value) {
        PotionStat.ATTACK -> stats.attack
        PotionStat.STRENGTH -> stats.strength
        PotionStat.DEFENCE -> stats.defence
        PotionStat.RANGED -> stats.ranged
        PotionStat.MAGIC -> stats.magic
        PotionStat.PRAYER -> stats.prayer
        PotionStat.HITPOINTS -> stats.hitpoints
        PotionStat.AGILITY -> stats.agility
        PotionStat.FISHING -> stats.fishing
        PotionStat.HUNTER -> stats.hunter
    }
