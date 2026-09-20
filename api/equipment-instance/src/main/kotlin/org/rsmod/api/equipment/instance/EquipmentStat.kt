package org.rsmod.api.equipment.instance

public enum class EquipmentStat {
    AttackStab,
    AttackSlash,
    AttackCrush,
    AttackMagic,
    AttackRanged,
    DefenceStab,
    DefenceSlash,
    DefenceCrush,
    DefenceMagic,
    DefenceRanged,
    DefenceSummoning,
    AbsorbMelee,
    AbsorbMagic,
    AbsorbRanged,
    Strength,
    RangedStrength,
    Prayer,
    MagicDamage,
    AttackSpeedTicks,
    AttackSpeedPercent,
    DamageMin,
    DamageMax,
    AverageHit,
    DamagePerSecond,
    AttacksPerSecond,
    SelectedStyleAccuracy,
    CriticalRate,
    CriticalDamage,
    MeleePower,
    RangedPower,
    MagicPower,
    MaximumHealth,
    EffectiveHealth,
    LifeSteal,
    SoulSplitEffectiveness,
    SpecialEnergyCost,
    SpecialEnergyRegeneration,
    Cooldown,

    /**
     * PvM support-book cooldown reductions, in basis points, summed across every worn item.
     *
     * These are deliberately separate from [AttackSpeedPercent] and from the companion-only
     * [Cooldown]: the semantics are "shorten a support ability's own cooldown", not "attack
     * faster". Each value only ever affects its own category - see `SupportCooldownCategory` in
     * `content/pvm-support`.
     */
    SupportSpellCooldownReduction,
    HealingCooldownReduction,
    SpellbookSwitchCooldownReduction,
    DamageBuffCooldownReduction,
    DefenceBuffCooldownReduction,
    BossDamage,
    SlayerDamage,
    RunEnergy,
    DropRate,

    /** Flat healing power - scales support heals; see `EquipmentCombatStats`. */
    HealingPower,

    /** Extra threat generation in basis points (10_000 = +100%). */
    ThreatGeneration,

    /**
     * Incoming damage reduction in basis points, capped by `ThreatConfig.MAX_DAMAGE_REDUCTION_BPS`.
     */
    DamageReduction,
}

public enum class ModifierUnit {
    Flat,
    BasisPoints,
    Ticks,
    ProcBasisPoints,
}

public enum class ModifierPolarity {
    Boon,
    Curse,
    Mixed,
}
