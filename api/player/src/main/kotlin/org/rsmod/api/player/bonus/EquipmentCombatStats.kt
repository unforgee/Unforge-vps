package org.rsmod.api.player.bonus

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.Wearpos

/**
 * The single authoritative view of a player's equipment-driven combat effectiveness for the PvM
 * role system (tank/support/dps).
 *
 * Everything here derives from [WornBonuses] - the existing item-bonus aggregation - so item stats
 * are never duplicated. Results are cached per player and keyed on a cheap worn-content hash:
 * equipping, unequipping or swapping any worn object (including a new equipment-instance affix set
 * on the same item type) invalidates the entry automatically. Buffs, prayers and level drains are
 * deliberately **not** folded into the snapshot; they are added live by the call sites exactly as
 * before.
 */
@Singleton
public class EquipmentCombatStats @Inject constructor(private val wornBonuses: WornBonuses) {
    private val cache = WeakHashMap<Player, Cached>()

    public fun stats(player: Player): Stats {
        val hash = wornHash(player)
        val cached = cache[player]
        if (cached != null && cached.wornHash == hash) {
            return cached.stats
        }
        val stats = wornBonuses.calculate(player).toStats()
        cache[player] = Cached(hash, stats)
        publish(player, stats)
        return stats
    }

    public fun invalidate(player: Player) {
        cache.remove(player)
    }

    /**
     * Hash of every worn slot's content. `InvObj.instanceId` is included because affix-bearing
     * instances change the stats without changing the item id.
     */
    private fun wornHash(player: Player): Int {
        var hash = 0
        for (wearpos in Wearpos.entries) {
            val obj = player.worn[wearpos.slot] ?: continue
            hash = 31 * hash + obj.id
            hash = 31 * hash + (obj.instanceId xor (obj.instanceId ushr 32)).toInt()
            hash = 31 * hash + obj.count
        }
        return hash
    }

    private fun WornBonuses.Bonuses.toStats(): Stats =
        Stats(
            meleePower = meleeStr,
            rangedPower = rangedStr,
            magicPower = magicDmg,
            healingPower = healingPower,
            meleeAccuracy = maxOf(offStab, offSlash, offCrush),
            rangedAccuracy = offRange,
            magicAccuracy = offMagic,
            meleeDefence = maxOf(defStab, defSlash, defCrush),
            rangedDefence = defRange,
            magicDefence = defMagic,
            maxHealthBonus = maximumHealth,
            attackSpeedModifierBps = attackSpeedModifierBps,
            threatBonusBps = threatBonusBps,
            damageReductionBps = damageReductionBps,
            prayer = prayer,
        )

    private data class Cached(val wornHash: Int, val stats: Stats)

    public data class Stats(
        val meleePower: Int,
        val rangedPower: Int,
        val magicPower: Int,
        val healingPower: Int,
        val meleeAccuracy: Int,
        val rangedAccuracy: Int,
        val magicAccuracy: Int,
        val meleeDefence: Int,
        val rangedDefence: Int,
        val magicDefence: Int,
        val maxHealthBonus: Int,
        val attackSpeedModifierBps: Int,
        val threatBonusBps: Int,
        val damageReductionBps: Int,
        val prayer: Int,
    ) {
        /**
         * Healing effectiveness for a build whose offense is [stylePower] (the character's own
         * style power, whichever weapon style it uses), where [scalingBps] is the configured style
         * contribution rate.
         *
         * ```
         * effective = healingPower + stylePower * scalingBps / 10_000
         * ```
         */
        public fun effectiveHealingPower(stylePower: Int, scalingBps: Int): Int =
            healingPower + stylePower * scalingBps / 10_000

        /** Total threat multiplier applied to generated threat (`1.0` = no gear bonus). */
        public val threatMultiplier: Double
            get() = 1.0 + threatBonusBps / 10_000.0
    }

    public companion object {
        /**
         * The last computed [Stats] per player, readable from `object` hit processors that cannot
         * participate in injection. Populated whenever [stats] resolves - which every
         * combat-relevant path (threat, support spells, debug output) already does - so a stale
         * `null` only means "no equipment influence yet", never wrong data.
         */
        private val snapshots = WeakHashMap<Player, Stats>()

        /**
         * Hard cap for equipment-driven incoming damage reduction (basis points). `2500` = 25% -
         * gear alone can never produce an immortal build.
         */
        public const val MAX_DAMAGE_REDUCTION_BPS: Int = 2_500

        public fun snapshot(player: Player): Stats? = snapshots[player]

        internal fun publish(player: Player, stats: Stats) {
            snapshots[player] = stats
        }
    }
}
