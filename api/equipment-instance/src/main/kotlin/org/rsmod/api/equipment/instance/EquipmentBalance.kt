package org.rsmod.api.equipment.instance

public object EquipmentBalance {
    public const val VERSION: Int = 4
    private val percentRanges =
        mapOf(
            EquipmentRarity.Uncommon to (100..2000),
            EquipmentRarity.Rare to (1000..4000),
            EquipmentRarity.Epic to (2000..6000),
            EquipmentRarity.Legendary to (3000..9000),
            EquipmentRarity.Mythic to (4000..10000),
            EquipmentRarity.Jackpot to (5000..15000),
        )

    public fun bonusPercentRange(rarity: EquipmentRarity): IntRange = percentRanges.getValue(rarity)

    /**
     * Per-item flat power cap rolled for armour affixes: `Uncommon` caps at +5, `Jackpot` at +20.
     * There is intentionally no total damage cap in the game - these caps bound a single affix roll
     * on a single item, and stacking multiple items sums freely.
     */
    private val powerCaps =
        mapOf(
            EquipmentRarity.Uncommon to 5,
            EquipmentRarity.Rare to 7,
            EquipmentRarity.Epic to 10,
            EquipmentRarity.Legendary to 13,
            EquipmentRarity.Mythic to 16,
            EquipmentRarity.Jackpot to 20,
        )

    public fun armorPowerCap(rarity: EquipmentRarity): Int = powerCaps.getValue(rarity)

    /**
     * The stored-units multiplier for a flat power affix roll. `MagicDamage` lives in 0.1% units in
     * `WornBonuses` (divisor 1000), so a rolled +20 is stored as 200; melee/ranged strength are
     * already in flat bonus units.
     */
    public fun armorPowerUnitScale(stat: EquipmentStat): Int =
        if (stat == EquipmentStat.MagicDamage) 10 else 1

    /**
     * Flat maximum-hitpoints bonus rolled for `MaximumHealth` affixes.
     *
     * The value is in **hitpoint levels** (the same scale as `Player.baseHitpointsLvl`), so a
     * top-end roll grants roughly +50 max HP on a Torva-tier Jackpot item.
     */
    public fun armorHealth(tier: EquipmentTier, rarity: EquipmentRarity): Int =
        tier.value * rarity.ordinal.coerceAtLeast(1)

    /**
     * Quality band per rarity: `Uncommon` rolls 40-50%, `Jackpot` 90-100%. The roll itself is
     * deterministic per instance seed (see [EquipmentRollPolicy.roll]).
     */
    public fun qualityMin(rarity: EquipmentRarity): Int = 40 + rarity.ordinal * 10

    /**
     * Default item level when the caller has no natural level to offer (e.g. an npc's vislevel).
     */
    public fun defaultItemLevel(tier: EquipmentTier, rarity: EquipmentRarity): Int =
        tier.value * 10 + rarity.ordinal * 2
}
