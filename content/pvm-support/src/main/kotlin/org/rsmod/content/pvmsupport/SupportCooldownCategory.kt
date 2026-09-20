package org.rsmod.content.pvmsupport

import org.rsmod.api.equipment.instance.EquipmentStat

/**
 * The cooldown bucket a support ability belongs to.
 *
 * A bucket is selected by exactly one [EquipmentStat] cooldown-reduction affix, which is what keeps
 * the affix families independent: a `Sanctified` roll can only ever shorten healing cooldowns,
 * never a spellbook switch - and never attack speed.
 */
public enum class SupportCooldownCategory(public val affixStat: EquipmentStat) {
    /** Generic support spells (prayer restoration). */
    Support(EquipmentStat.SupportSpellCooldownReduction),

    /** Every healing spell. */
    Healing(EquipmentStat.HealingCooldownReduction),

    /** The Spellbook Codex switch. */
    SpellbookSwitch(EquipmentStat.SpellbookSwitchCooldownReduction),

    /** Offensive support buffs (`Battle Hymn`). */
    DamageBuff(EquipmentStat.DamageBuffCooldownReduction),

    /** Defensive support buffs and shields (`Iron Sanctuary`). */
    DefenceBuff(EquipmentStat.DefenceBuffCooldownReduction),
}
