package org.rsmod.content.pvmsupport

import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.game.entity.Player

/**
 * Cooldowns for the PvM support spellbook.
 *
 * Cooldowns are absolute deadlines owned by the server, never a client-side timer, so a modified
 * client can neither shorten nor skip one.
 *
 * They are keyed by the **account name**, not by the live `Player` object, so logging out and back
 * in does not clear them - reconnecting is not a way to reset a cooldown. (They are
 * process-lifetime state: a server restart clears them, which is the same trade-off the rest of the
 * repo makes for ephemeral per-player timers.)
 *
 * Each spell has a hard [SupportSpell.minCooldownTicks] floor that gear can never break through.
 * Reduction comes from the five cooldown affixes in [WornBonuses.CooldownReductions] and only from
 * the bucket matching the spell's [SupportCooldownCategory].
 */
public object SupportCooldowns {
    /** Total cooldown-reduction cap with ordinary gear: 25 %. */
    public const val DEFAULT_CAP_BPS: Int = 2_500

    /** Total cooldown-reduction cap in endgame gear: 35 %. */
    public const val ENDGAME_CAP_BPS: Int = 3_500

    private const val MILLIS_PER_TICK: Long = 600

    private val deadlines = ConcurrentHashMap<String, MutableMap<SupportSpell, Long>>()

    public fun remainingTicks(player: Player, spell: SupportSpell): Int {
        val deadline = deadlines[player.username]?.get(spell) ?: return 0
        return remainingTicksUntil(deadline, System.currentTimeMillis(), spell.baseCooldownTicks)
    }

    public fun isReady(player: Player, spell: SupportSpell): Boolean =
        remainingTicks(player, spell) == 0

    /** Starts [spell]'s cooldown using the player's worn cooldown-reduction affixes. */
    public fun start(
        player: Player,
        spell: SupportSpell,
        bonuses: WornBonuses,
        instances: EquipmentInstanceRegistry,
    ) {
        val ticks = cooldownTicks(player, spell, bonuses, instances)
        deadlines.computeIfAbsent(player.username) { mutableMapOf() }[spell] =
            System.currentTimeMillis() + ticks * MILLIS_PER_TICK
    }

    /** Clears every cooldown of [player] (used when a spell is refunded, e.g. a failed cast). */
    public fun clear(player: Player) {
        deadlines.remove(player.username)
    }

    /** Effective cooldown for [player] in ticks, after affixes, caps and the per-spell floor. */
    public fun cooldownTicks(
        player: Player,
        spell: SupportSpell,
        bonuses: WornBonuses,
        instances: EquipmentInstanceRegistry,
    ): Int {
        val reduction = bonuses.cooldownReductions(player).bpsFor(spell.category)
        return effectiveCooldownTicks(spell, reduction, isEndgameGear(player, instances))
    }

    /**
     * Pure cooldown maths - the part worth testing without a live player.
     *
     * [reductionBps] is clamped to the cap for [endgame], applied once to the base cooldown, and
     * the result floored at the spell's minimum so a cooldown can never reach zero.
     */
    public fun effectiveCooldownTicks(
        spell: SupportSpell,
        reductionBps: Int,
        endgame: Boolean,
    ): Int {
        val cap = if (endgame) ENDGAME_CAP_BPS else DEFAULT_CAP_BPS
        val bps = reductionBps.coerceIn(0, cap)
        val reduced = spell.baseCooldownTicks - spell.baseCooldownTicks * bps / 10_000
        return reduced.coerceAtLeast(spell.minCooldownTicks)
    }

    /** Pure remaining-tick maths, so the deadline handling is testable too. */
    public fun remainingTicksUntil(deadline: Long, now: Long, maxTicks: Int): Int {
        if (deadline <= now) {
            return 0
        }
        val ticks = ((deadline - now) + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK
        return ticks.coerceIn(1, maxTicks.toLong()).toInt()
    }

    /** `true` when any worn item is a Legendary/Mythic/Jackpot instance. */
    public fun isEndgameGear(player: Player, instances: EquipmentInstanceRegistry): Boolean {
        for (obj in player.worn.objs) {
            if (obj == null) {
                continue
            }
            val instance = instances[obj.instanceId] ?: continue
            if (instance.rarity.ordinal >= EquipmentRarity.Legendary.ordinal) {
                return true
            }
        }
        return false
    }

    private fun WornBonuses.CooldownReductions.bpsFor(category: SupportCooldownCategory): Int =
        when (category) {
            SupportCooldownCategory.Support -> supportSpellBps
            SupportCooldownCategory.Healing -> healingBps
            SupportCooldownCategory.SpellbookSwitch -> spellbookSwitchBps
            SupportCooldownCategory.DamageBuff -> damageBuffBps
            SupportCooldownCategory.DefenceBuff -> defenceBuffBps
        }
}
