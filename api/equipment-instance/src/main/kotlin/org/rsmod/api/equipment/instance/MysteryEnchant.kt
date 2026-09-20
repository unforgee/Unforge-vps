package org.rsmod.api.equipment.instance

import java.util.SplittableRandom

/** Balance knobs for the temporary combat enchantment system. */
public object MysteryEnchantConfig {
    public const val DEFAULT_KILLS: Int = 1_000
    public const val TICKET_COST: Int = 1
    public const val COMMON_WEIGHT: Int = 6_000
    public const val RARE_WEIGHT: Int = 2_700
    public const val EPIC_WEIGHT: Int = 1_000
    public const val LEGENDARY_WEIGHT: Int = 300
}

/** One authoritative server-side enchant roll. */
public data class MysteryEnchantRoll(
    public val id: MysteryEnchantId,
    public val tier: MysteryEnchantTier,
    public val kills: Int = MysteryEnchantConfig.DEFAULT_KILLS,
)

/** Data-driven pool and tier scaling for Mystery Enchant. */
public object MysteryEnchantService {
    public val pool: List<MysteryEnchantId> = MysteryEnchantId.entries

    public fun roll(seed: Long): MysteryEnchantRoll {
        val random = SplittableRandom(seed)
        val tierRoll = random.nextInt(10_000)
        val tier =
            when {
                tierRoll < MysteryEnchantConfig.COMMON_WEIGHT -> MysteryEnchantTier.COMMON
                tierRoll < MysteryEnchantConfig.COMMON_WEIGHT + MysteryEnchantConfig.RARE_WEIGHT ->
                    MysteryEnchantTier.RARE
                tierRoll <
                    MysteryEnchantConfig.COMMON_WEIGHT +
                        MysteryEnchantConfig.RARE_WEIGHT +
                        MysteryEnchantConfig.EPIC_WEIGHT -> MysteryEnchantTier.EPIC
                else -> MysteryEnchantTier.LEGENDARY
            }
        return MysteryEnchantRoll(pool[random.nextInt(pool.size)], tier)
    }

    /** Percentage strength multiplier for the selected tier, in basis points. */
    public fun tierMultiplierBps(tier: MysteryEnchantTier): Int =
        when (tier) {
            MysteryEnchantTier.COMMON -> 3_000
            MysteryEnchantTier.RARE -> 5_000
            MysteryEnchantTier.EPIC -> 7_000
            MysteryEnchantTier.LEGENDARY -> 10_000
        }

    /** Scales a common-tier basis-point value to the selected rarity. */
    public fun scaleBps(commonBps: Int, tier: MysteryEnchantTier): Int =
        commonBps * tierMultiplierBps(tier) / tierMultiplierBps(MysteryEnchantTier.COMMON)

    public fun displayName(id: MysteryEnchantId): String =
        id.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

    public fun clear(instance: EquipmentInstance): EquipmentInstance =
        instance.copy(
            mysteryEnchantId = null,
            mysteryEnchantTier = null,
            mysteryEnchantKillsRemaining = 0,
            mysteryEnchantKillsMax = 0,
        )

    public fun apply(instance: EquipmentInstance, roll: MysteryEnchantRoll): EquipmentInstance =
        instance.copy(
            mysteryEnchantId = roll.id,
            mysteryEnchantTier = roll.tier,
            mysteryEnchantKillsRemaining = roll.kills,
            mysteryEnchantKillsMax = roll.kills,
        )

    /** Decrements exactly one valid kill and removes the enchant at zero. */
    public fun consumeKill(instance: EquipmentInstance): EquipmentInstance? {
        if (instance.mysteryEnchantId == null || instance.mysteryEnchantKillsRemaining <= 0) {
            return null
        }
        val remaining = instance.mysteryEnchantKillsRemaining - 1
        return if (remaining == 0) clear(instance)
        else instance.copy(mysteryEnchantKillsRemaining = remaining)
    }
}
