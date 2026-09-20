package org.rsmod.api.equipment.instance

import java.util.SplittableRandom
import kotlin.math.max

/**
 * Affix magnitude ranges and rolls shared by initial item generation ([EquipmentRollPolicy]) and
 * the Forge reroll operations ([ForgeService]).
 *
 * The rarity band logic mirrors creation-time rolling exactly: an affix rerolled through the Forge
 * lands in the same range it could have been created with, so a reroll can never produce a value
 * outside what the item's rarity allows.
 */
public object AffixRollService {
    /** Stats that scale the flat power affix cap (melee str / ranged str / magic dmg). */
    private val POWER_STATS =
        setOf(EquipmentStat.Strength, EquipmentStat.RangedStrength, EquipmentStat.MagicDamage)

    /**
     * The inclusive magnitude range [definition] may roll within for an item of [rarity] and
     * [tier] - the exact bounds [rollMagnitude] samples.
     */
    public fun magnitudeRange(
        definition: EquipmentAffixDefinition,
        rarity: EquipmentRarity,
        tier: EquipmentTier,
    ): IntRange =
        when {
            definition.stat == EquipmentStat.MaximumHealth -> {
                val health = EquipmentBalance.armorHealth(tier, rarity)
                health..health
            }
            // Percent-based affixes with a degenerate catalog range (`min == max`) defer to the
            // rarity band so the roll scales with the item's rarity.
            isPercentUnit(definition.unit) && definition.minMagnitude == definition.maxMagnitude ->
                EquipmentBalance.bonusPercentRange(rarity)
            // Flat power affixes roll 1..cap scaled into stored units; the cap scales with
            // rarity (Uncommon +5 up to Jackpot +20 per item).
            definition.unit == ModifierUnit.Flat &&
                definition.stat in POWER_STATS &&
                definition.minMagnitude == definition.maxMagnitude -> {
                val scale = EquipmentBalance.armorPowerUnitScale(definition.stat)
                scale..(EquipmentBalance.armorPowerCap(rarity) * scale)
            }
            else -> definition.minMagnitude..max(definition.minMagnitude, definition.maxMagnitude)
        }

    /** Uniformly samples a magnitude inside [magnitudeRange], honouring the per-unit floor. */
    public fun rollMagnitude(
        random: SplittableRandom,
        definition: EquipmentAffixDefinition,
        rarity: EquipmentRarity,
        tier: EquipmentTier,
    ): Int {
        val range = magnitudeRange(definition, rarity, tier)
        val magnitude =
            when {
                definition.stat == EquipmentStat.MaximumHealth -> range.first
                definition.unit == ModifierUnit.Flat &&
                    definition.stat in POWER_STATS &&
                    definition.minMagnitude == definition.maxMagnitude -> {
                    // Roll 1..cap, then scale into stored units so the distribution stays uniform
                    // over the cap steps rather than over the full scaled range.
                    val scale = EquipmentBalance.armorPowerUnitScale(definition.stat)
                    random.nextInt(1, EquipmentBalance.armorPowerCap(rarity) + 1) * scale
                }
                else -> random.nextInt(range.first, range.last + 1)
            }
        // A rolled affix must always grant something: flats/ticks never land below +1 and
        // percent rolls never below 1% (100 bps) - a "0.0%" affix reads as broken.
        return magnitude.coerceAtLeast(minMagnitude(definition.unit))
    }

    /**
     * Normalized roll quality of [affix] within its permitted range, in basis points (`0` = minimum
     * roll, `10_000` = perfect). Returns `-1` for fixed-range affixes where a quality rating is
     * meaningless. Display only - never feeds back into rarity or stats.
     */
    public fun qualityBps(
        affix: EquipmentAffixRoll,
        definition: EquipmentAffixDefinition?,
        rarity: EquipmentRarity,
        tier: EquipmentTier,
    ): Int {
        if (definition == null) return -1
        val range = magnitudeRange(definition, rarity, tier)
        val span = range.last - range.first
        if (span <= 0) return -1
        return ((affix.magnitude - range.first).toLong() * 10_000L / span)
            .toInt()
            .coerceIn(0, 10_000)
    }

    /**
     * `true` when [affix] may be rerolled by the Forge: its definition still exists in the catalog,
     * is marked `rerollable`, and the slot is not one of [EquipmentInstance]'s persisted
     * `lockedAffixSlots`.
     */
    public fun isRerollable(
        affix: EquipmentAffixRoll,
        definition: EquipmentAffixDefinition?,
        instance: EquipmentInstance,
    ): Boolean =
        definition != null && definition.rerollable && affix.slot !in instance.lockedAffixSlots

    private fun isPercentUnit(unit: ModifierUnit): Boolean =
        unit == ModifierUnit.BasisPoints || unit == ModifierUnit.ProcBasisPoints

    private fun minMagnitude(unit: ModifierUnit): Int =
        when (unit) {
            ModifierUnit.BasisPoints,
            ModifierUnit.ProcBasisPoints -> 100
            else -> 1
        }
}
