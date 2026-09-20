package org.rsmod.api.combat.weapon

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.commons.styles.AttackStyle
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.perk.PerkService
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList

public class WeaponSpeeds
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val styles: AttackStyles,
    private val instances: EquipmentInstanceRegistry,
    private val perks: PerkService,
    private val wornBonuses: WornBonuses,
) {
    /**
     * Leftover hundredths of a cycle per player, carried between successive attacks so that a
     * fractional attack rate (e.g. 3.80t) averages out over time instead of rounding away.
     */
    private val centiCarry = WeakHashMap<Player, Int>()

    public fun base(player: Player): Int {
        val weapon = player.righthand ?: return constants.combat_default_attackrate
        return objTypes[weapon].param(params.attackrate)
    }

    /** [base] in centi-ticks (hundredths of a game cycle). */
    public fun baseCenti(player: Player): Int = base(player) * CENTI_TICK

    public fun actual(player: Player, style: AttackStyle?): Int =
        (actualCenti(player, style) + CENTI_TICK / 2) / CENTI_TICK

    // Avoids needing the [AttackStyle] dependency when calling `actual` unless explicitly required.
    public fun actual(player: Player): Int = actual(player, player.currentAttackStyle())

    /**
     * Effective attack rate in centi-ticks: the weapon's base rate with the player's summed gear
     * speed (`AttackSpeedPercent` affixes plus the template [WornBonuses.attackSpeedModifierBps]),
     * the weapon's `AttackSpeedTicks` affixes and the `RapidRanged` style bonus applied last.
     */
    public fun actualCenti(player: Player, style: AttackStyle?): Int {
        val withGear = applyGearSpeed(player, baseCenti(player))
        val withInstances = applyInstanceSpeed(player.righthand, withGear)
        // Swiftness perk shaves a fraction off the attack cycle.
        val speedBps = perks.attackSpeedBps(player) + perks.soloBossAttackSpeedBps(player)
        val perked = withInstances - withInstances * speedBps / 10_000
        return if (style == AttackStyle.RapidRanged) {
            max(1, perked - CENTI_TICK)
        } else {
            max(1, perked)
        }
    }

    public fun actualCenti(player: Player): Int = actualCenti(player, player.currentAttackStyle())

    /**
     * The delay for the player's next attack in whole cycles.
     *
     * The centi-tick remainder is carried over between attacks, so a 3.80t weapon produces a
     * 4,4,4,3,4,... cadence that averages out to its true rate instead of always rounding to 4.
     */
    public fun nextAttackDelay(player: Player, style: AttackStyle?): Int {
        val carried = actualCenti(player, style) + (centiCarry[player] ?: 0)
        centiCarry[player] = carried % CENTI_TICK
        return max(1, carried / CENTI_TICK)
    }

    public fun nextAttackDelay(player: Player): Int =
        nextAttackDelay(player, player.currentAttackStyle())

    /**
     * Shaves the player's summed gear speed off [speedCenti].
     *
     * [WornBonuses.attackSpeedModifierBps] already folds together every worn item's template speed
     * bonus and its equipment-instance `AttackSpeedPercent` affixes, so applying it once here
     * covers armour, weapons and ammo alike.
     */
    private fun applyGearSpeed(player: Player, speedCenti: Int): Int {
        val bps = wornBonuses.attackSpeedModifierBps(player)
        return if (bps == 0) speedCenti else max(1, speedCenti - speedCenti * bps / 10_000)
    }

    /**
     * Applies the weapon instance's `AttackSpeedTicks` affixes to [speedCenti].
     *
     * Percent-based speed is handled by [applyGearSpeed], which sums across every worn slot; only
     * the whole-tick affix is weapon-specific. Positive magnitudes mean a faster weapon: tick
     * bonuses subtract whole ticks (scaled to centi). [ModifierPolarity.Curse] affixes invert the
     * direction so cursed rolls slow the weapon down.
     */
    private fun applyInstanceSpeed(weapon: InvObj?, speedCenti: Int): Int {
        val instance = weapon?.let { instances[it.instanceId] } ?: return speedCenti
        var modified = speedCenti
        for (affix in instance.affixes) {
            val magnitude =
                if (affix.polarity == ModifierPolarity.Curse) -abs(affix.magnitude)
                else affix.magnitude
            when (affix.stat) {
                EquipmentStat.AttackSpeedTicks -> modified -= magnitude * CENTI_TICK
                else -> Unit
            }
        }
        return max(1, modified)
    }

    private fun Player.currentAttackStyle(): AttackStyle? = styles.get(this)

    private companion object {
        private const val CENTI_TICK = 100
    }
}
