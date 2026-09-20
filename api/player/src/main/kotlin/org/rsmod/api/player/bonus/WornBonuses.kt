package org.rsmod.api.player.bonus

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.varps
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.GearSpeedBonus
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos

public class WornBonuses
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val instances: EquipmentInstanceRegistry,
    private val perks: PerkService,
    private val augmenters: Set<WornBonusesAugmenter>,
) {
    /**
     * Effective maximum hitpoints in stat levels: the base hitpoints level plus the summed
     * [EquipmentStat.MaximumHealth] affix magnitudes of worn equipment instances and the Vitality
     * perk bonus.
     */
    public fun maximumHitpoints(player: Player): Int {
        val base =
            player.baseHitpointsLvl + calculate(player).maximumHealth + perks.maxHealthBonus(player)
        return base + base * perks.soloBossMaxHealthBps(player) / 10_000
    }

    public fun attackSpeedModifierBps(player: Player): Int =
        calculate(player).attackSpeedModifierBps

    /** Summed support-book cooldown reductions of every worn item - see [CooldownReductions]. */
    public fun cooldownReductions(player: Player): CooldownReductions =
        calculate(player).cooldownReductions

    public fun strengthBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.meleeStr
    }

    public fun rangedStrengthBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.rangedStr
    }

    /**
     * Returns the player's base magic damage bonus.
     *
     * **Note:** This does **not** include [Bonuses.magicDmgMultiplier] or
     * [Bonuses.magicDmgAdditive]. Those values are used for display purposes in the bonus
     * interface, but often have conditional restrictions that must be enforced in the combat
     * formulas themselves.
     *
     * For example, Virtus equipment displays a `+3%` magic damage bonus even when the player is not
     * on the Ancient spellbook - a restriction that should be applied in the actual combat formula.
     */
    public fun magicDamageBonusBase(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.magicDmg
    }

    public fun offensiveStabBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.offStab
    }

    public fun offensiveSlashBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.offSlash
    }

    public fun offensiveCrushBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.offCrush
    }

    public fun offensiveRangedBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.offRange
    }

    public fun offensiveMagicBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.offMagic
    }

    public fun defensiveCrushBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.defCrush
    }

    public fun defensiveStabBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.defStab
    }

    public fun defensiveSlashBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.defSlash
    }

    public fun defensiveMagicBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.defMagic
    }

    public fun defensiveRangedBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.defRange
    }

    public fun prayerBonus(player: Player): Int {
        val bonuses = calculate(player)
        return bonuses.prayer
    }

    /**
     * The player's own resolved bonuses: [equipmentBonuses] plus set- and weapon-specific modifiers
     * (Tumeken's shadow, Dinh's bulwark, Virtus, elite void mage), finally passed through every
     * registered [WornBonusesAugmenter].
     */
    public fun calculate(player: Player): Bonuses {
        var bonuses = equipmentBonuses(player)
        val weapon = objTypes.getOrNull(player.righthand)

        if (EquipmentChecks.isTumekensShadow(player.righthand)) {
            // TODO: 4.0 while in tombs of amascut. This is purely for the visual bonus, the actual
            //  combat formula should use a separate resolved multiplier that matches.
            val multiplier = 3.0

            // Note: Multiplying `offMagic` here means it will affect magic accuracy even in pvp.
            // Unsure if this is the case in the official game.
            bonuses =
                bonuses.copy(
                    magicDmgMultiplier = (multiplier * 10).toInt(),
                    offMagic = (bonuses.offMagic * multiplier).toInt(),
                )
        }

        val attackStyle = player.vars[varps.com_mode]
        val usingDinhsBulwark = weapon != null && weapon.isCategoryType(categories.dinhs_bulwark)
        if (usingDinhsBulwark && attackStyle == constants.dinhs_attackstyle_pummel) {
            val relativeDefenceBonuses =
                bonuses.defStab + bonuses.defSlash + bonuses.defCrush + bonuses.defRange
            val meleeStrIncrease = ((relativeDefenceBonuses - 800) / 12) - 38
            bonuses = bonuses.copy(meleeStr = bonuses.meleeStr + max(0, meleeStrIncrease))
        }

        // Note: The Virtus modifiers use `magicDmgAdditive`, which is applied only for visual
        // purposes. Although the modifier has an "Ancient spellbook" restriction in combat,
        // the bonus is always shown in the bonus interface regardless of that restriction.

        if (EquipmentChecks.isVirtusMask(player.hat)) {
            bonuses = bonuses.copy(magicDmgAdditive = bonuses.magicDmgAdditive + 3)
        }

        if (EquipmentChecks.isVirtusRobeTop(player.torso)) {
            bonuses = bonuses.copy(magicDmgAdditive = bonuses.magicDmgAdditive + 3)
        }

        if (EquipmentChecks.isVirtusRobeBottom(player.legs)) {
            bonuses = bonuses.copy(magicDmgAdditive = bonuses.magicDmgAdditive + 3)
        }

        if (player.isWearingEliteMageVoid()) {
            bonuses = bonuses.copy(magicDmg = bonuses.magicDmg + 50)
        }

        // TODO: +10 off ranged and +1 ranged str with dizana's quiver.
        //  Verify if this is visible in equipment bonus interface. If it's not then it can be
        //  handled purely in combat formulae and not here.

        for (augmenter in augmenters) {
            bonuses = augmenter.augment(player, bonuses)
        }
        return bonuses
    }

    /**
     * The raw summed bonuses of every worn item: equipment `param` values plus equipment-instance
     * affixes, with no set- or weapon-specific adjustments and no [WornBonusesAugmenter] pass.
     *
     * This is the shareable "armor stats only" view used by systems that want to apply a player's
     * equipment bonuses to another entity (e.g. companion combat) without inheriting player-weapon
     * mechanics such as Tumeken's shadow or Dinh's bulwark.
     */
    public fun equipmentBonuses(player: Player): Bonuses {
        var offStab = 0
        var offSlash = 0
        var offCrush = 0
        var offMagic = 0
        var offRange = 0
        var defStab = 0
        var defSlash = 0
        var defCrush = 0
        var defRange = 0
        var defMagic = 0
        var meleeStr = 0
        var rangedStr = 0
        var magicDmg = 0
        var prayer = 0
        var undead = 0
        var slayer = 0
        var magicDmgAdditive = 0
        var magicDmgMultiplier = 1.0
        var undeadMeleeOnly = false
        var slayerMeleeOnly = false
        var maximumHealth = 0
        var healingPower = 0
        var threatBonusBps = 0
        var damageReductionBps = 0
        var attackSpeedModifierBps = 0
        var supportCooldownBps = 0
        var healingCooldownBps = 0
        var spellbookSwitchCooldownBps = 0
        var damageBuffCooldownBps = 0
        var defenceBuffCooldownBps = 0

        val weapon = objTypes.getOrNull(player.righthand)

        val usingChargebow = weapon != null && weapon.isCategoryType(categories.chargebow)
        val usingThrown = weapon != null && weapon.isCategoryType(categories.throwing_weapon)
        val ignoreQuiverBonuses = usingChargebow || usingThrown

        for (wearpos in Wearpos.entries) {
            val obj = player.worn[wearpos.slot] ?: continue

            if (wearpos == Wearpos.Quiver && ignoreQuiverBonuses) {
                continue
            }

            val type = objTypes[obj]
            attackSpeedModifierBps += GearSpeedBonus.bps(type)
            offStab += type.param(params.attack_stab)
            offSlash += type.param(params.attack_slash)
            offCrush += type.param(params.attack_crush)
            offMagic += type.param(params.attack_magic)
            offRange += type.param(params.attack_ranged)
            defStab += type.param(params.defence_stab)
            defSlash += type.param(params.defence_slash)
            defCrush += type.param(params.defence_crush)
            defRange += type.param(params.defence_ranged)
            defMagic += type.param(params.defence_magic)
            meleeStr += type.param(params.melee_strength)
            rangedStr += type.param(params.ranged_strength)
            rangedStr += type.param(params.additive_ranged_strength)
            magicDmg += type.param(params.magic_damage)
            prayer += type.param(params.item_prayer_bonus)
            undead += type.param(params.bonus_undead_buff)
            slayer += type.param(params.bonus_slayer_buff)
            undeadMeleeOnly = type.param(params.bonus_undead_meleeonly)
            slayerMeleeOnly = type.param(params.bonus_slayer_meleeonly)
            instances[obj.instanceId]?.affixes?.forEach { affix ->
                fun percent(base: Int): Int = base + (base * affix.magnitude / 10_000)
                when (affix.stat) {
                    EquipmentStat.AttackStab ->
                        offStab =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(offStab)
                            else offStab + affix.magnitude
                    EquipmentStat.AttackSlash ->
                        offSlash =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(offSlash)
                            else offSlash + affix.magnitude
                    EquipmentStat.AttackCrush ->
                        offCrush =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(offCrush)
                            else offCrush + affix.magnitude
                    EquipmentStat.AttackMagic ->
                        offMagic =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(offMagic)
                            else offMagic + affix.magnitude
                    EquipmentStat.AttackRanged ->
                        offRange =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(offRange)
                            else offRange + affix.magnitude
                    EquipmentStat.DefenceStab ->
                        defStab =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(defStab)
                            else defStab + affix.magnitude
                    EquipmentStat.DefenceSlash ->
                        defSlash =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(defSlash)
                            else defSlash + affix.magnitude
                    EquipmentStat.DefenceCrush ->
                        defCrush =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(defCrush)
                            else defCrush + affix.magnitude
                    EquipmentStat.DefenceMagic ->
                        defMagic =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(defMagic)
                            else defMagic + affix.magnitude
                    EquipmentStat.DefenceRanged ->
                        defRange =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(defRange)
                            else defRange + affix.magnitude
                    EquipmentStat.Strength ->
                        meleeStr =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(meleeStr)
                            else meleeStr + affix.magnitude
                    EquipmentStat.RangedStrength ->
                        rangedStr =
                            if (
                                affix.unit ==
                                    org.rsmod.api.equipment.instance.ModifierUnit.BasisPoints
                            )
                                percent(rangedStr)
                            else rangedStr + affix.magnitude
                    EquipmentStat.Prayer -> prayer += affix.magnitude
                    EquipmentStat.MagicDamage -> magicDmg += affix.magnitude
                    EquipmentStat.MaximumHealth -> {
                        if (
                            instances[obj.instanceId]?.category in
                                setOf(
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Head,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Back,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Neck,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Torso,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.LeftHand,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Legs,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Hands,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Feet,
                                    org.rsmod.api.equipment.instance.EquipmentCategory.Ring,
                                )
                        )
                            maximumHealth += affix.magnitude
                    }
                    EquipmentStat.HealingPower -> healingPower += affix.magnitude
                    EquipmentStat.ThreatGeneration -> threatBonusBps += affix.magnitude
                    EquipmentStat.DamageReduction -> damageReductionBps += affix.magnitude
                    EquipmentStat.AttackSpeedPercent -> attackSpeedModifierBps += affix.magnitude
                    EquipmentStat.SupportSpellCooldownReduction ->
                        supportCooldownBps += affix.magnitude
                    EquipmentStat.HealingCooldownReduction -> healingCooldownBps += affix.magnitude
                    EquipmentStat.SpellbookSwitchCooldownReduction ->
                        spellbookSwitchCooldownBps += affix.magnitude
                    EquipmentStat.DamageBuffCooldownReduction ->
                        damageBuffCooldownBps += affix.magnitude
                    EquipmentStat.DefenceBuffCooldownReduction ->
                        defenceBuffCooldownBps += affix.magnitude
                    else -> Unit
                }
            }
        }

        // TODO: Apply toxic blowpipe dart bonuses.

        return Bonuses(
            offStab = offStab,
            offSlash = offSlash,
            offCrush = offCrush,
            offMagic = offMagic,
            offRange = offRange,
            defStab = defStab,
            defSlash = defSlash,
            defCrush = defCrush,
            defRange = defRange,
            defMagic = defMagic,
            meleeStr = meleeStr,
            rangedStr = rangedStr,
            magicDmg = magicDmg,
            prayer = prayer,
            undead = undead,
            slayer = slayer,
            magicDmgAdditive = magicDmgAdditive,
            magicDmgMultiplier = (magicDmgMultiplier * 10).toInt(),
            undeadMeleeOnly = undeadMeleeOnly,
            slayerMeleeOnly = slayerMeleeOnly,
            maximumHealth = maximumHealth,
            healingPower = healingPower,
            threatBonusBps = threatBonusBps,
            damageReductionBps = damageReductionBps,
            attackSpeedModifierBps = attackSpeedModifierBps,
            cooldownReductions =
                CooldownReductions(
                    supportSpellBps = supportCooldownBps,
                    healingBps = healingCooldownBps,
                    spellbookSwitchBps = spellbookSwitchCooldownBps,
                    damageBuffBps = damageBuffCooldownBps,
                    defenceBuffBps = defenceBuffCooldownBps,
                ),
        )
    }

    private fun Player.isWearingEliteMageVoid(): Boolean =
        EquipmentChecks.isVoidMageHelm(hat) &&
            EquipmentChecks.isEliteVoidTop(torso) &&
            EquipmentChecks.isEliteVoidRobe(legs) &&
            EquipmentChecks.isVoidGloves(hands)

    public data class Bonuses(
        val offStab: Int,
        val offSlash: Int,
        val offCrush: Int,
        val offMagic: Int,
        val offRange: Int,
        val defStab: Int,
        val defSlash: Int,
        val defCrush: Int,
        val defRange: Int,
        val defMagic: Int,
        val meleeStr: Int,
        val rangedStr: Int,
        val magicDmg: Int,
        val prayer: Int,
        val undead: Int,
        val slayer: Int,
        val magicDmgAdditive: Int,
        val magicDmgMultiplier: Int,
        val undeadMeleeOnly: Boolean,
        val slayerMeleeOnly: Boolean,
        val maximumHealth: Int = 0,
        val healingPower: Int = 0,
        val threatBonusBps: Int = 0,
        val damageReductionBps: Int = 0,
        val attackSpeedModifierBps: Int = 0,
        val cooldownReductions: CooldownReductions = CooldownReductions.EMPTY,
    ) {
        val multipliedMagicDmg: Int
            get() = magicDmg * (magicDmgMultiplier / 10)
    }

    /**
     * Summed support-book cooldown reductions of every worn item, in basis points.
     *
     * Each field maps to exactly one `SupportCooldownCategory`, so a healing affix can never
     * shorten a spellbook-switch cooldown (or attack speed).
     */
    public data class CooldownReductions(
        val supportSpellBps: Int = 0,
        val healingBps: Int = 0,
        val spellbookSwitchBps: Int = 0,
        val damageBuffBps: Int = 0,
        val defenceBuffBps: Int = 0,
    ) {
        public companion object {
            public val EMPTY: CooldownReductions = CooldownReductions()
        }
    }
}
