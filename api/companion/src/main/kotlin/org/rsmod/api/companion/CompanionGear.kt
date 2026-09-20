package org.rsmod.api.companion

import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.game.type.obj.WeaponCategory

public data class CompanionCombatStats(
    public val maximumHitpoints: Int,
    public val damageMultiplier: Double,
    public val supportPower: Double,
    public val attackDelay: Int,
)

/**
 * Legacy entry point kept for existing call sites; all computation lives in the canonical
 * [CompanionStatCalculator]. Prefer `CompanionStatCalculator.calculate` (or `CompanionStatCache`)
 * when the caller also needs derived stats, breakdowns or set information.
 */
public object CompanionGearCalculator {
    public fun calculate(
        companion: Companion,
        registry: EquipmentInstanceRegistry,
        skillLevels: Map<CompanionSkill, Int> = emptyMap(),
    ): CompanionCombatStats =
        CompanionStatCalculator.calculate(companion, registry, skillLevels).combat
}

/**
 * Maps an equipped weapon's [WeaponCategory] to the [CompanionAttackStyle] the companion's attack
 * pipeline uses. Powered staves carry a built-in magic attack, while regular and bladed staves are
 * autocast-capable - both therefore drive the spell pipeline. Bows, crossbows, guns, thrown
 * weapons, chinchompas and salamanders keep the ranged pipeline; everything else is melee.
 */
public fun companionWeaponStyle(weaponCategoryId: Int): CompanionAttackStyle =
    when (weaponCategoryId) {
        WeaponCategory.PoweredStaff.id,
        WeaponCategory.Staff.id,
        WeaponCategory.BladedStaff.id -> CompanionAttackStyle.MAGIC
        WeaponCategory.Bow.id,
        WeaponCategory.Crossbow.id,
        WeaponCategory.Thrown.id,
        WeaponCategory.Chinchompas.id,
        WeaponCategory.Gun.id,
        WeaponCategory.Salamander.id -> CompanionAttackStyle.RANGED
        else -> CompanionAttackStyle.MELEE
    }

/** Whether a weapon of [weaponCategoryId] can drive the companion's magic/spell pipeline. */
public fun isMagicWeapon(weaponCategoryId: Int): Boolean =
    companionWeaponStyle(weaponCategoryId) == CompanionAttackStyle.MAGIC
