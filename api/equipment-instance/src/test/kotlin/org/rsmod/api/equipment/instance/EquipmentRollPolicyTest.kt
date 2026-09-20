package org.rsmod.api.equipment.instance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquipmentRollPolicyTest {
    @Test
    fun `same seed produces identical equipment roll`() {
        val policy = policy()
        val first = policy.roll(4151, EquipmentCategory.RightHand, 987654321L, "test")
        val second = policy.roll(4151, EquipmentCategory.RightHand, 987654321L, "test")
        assertEquals(first, second)
    }

    @Test
    fun `different seeds produce different equipment rolls`() {
        val policy = policy()
        val first = policy.roll(4151, EquipmentCategory.RightHand, 100L, "test")
        val second = policy.roll(4151, EquipmentCategory.RightHand, 200L, "test")
        assertNotEquals(first.rollSeed, second.rollSeed)
        assertNotEquals(first, second)
    }

    @Test
    fun `roll never repeats an affix family`() {
        val policy = policy()
        repeat(10_000) { seed ->
            val roll = policy.roll(4151, EquipmentCategory.RightHand, seed.toLong(), "test")
            assertEquals(roll.affixes.size, roll.affixes.map { it.family }.distinct().size)
        }
    }

    @Test
    fun `every equipment category can use the shared instance model`() {
        val policy = policy(categories = EquipmentCategory.entries.toSet())
        for (category in EquipmentCategory.entries) {
            val roll =
                policy.roll(1000 + category.ordinal, category, category.ordinal.toLong(), "test")
            assertEquals(category, roll.category)
            assertTrue(roll.affixes.isNotEmpty())
        }
    }

    @Test
    fun `rarity weights form exactly one hundred percent`() {
        assertEquals(
            EquipmentRarity.TOTAL_WEIGHT_BASIS_POINTS,
            EquipmentRarity.entries.sumOf { it.weightBasisPoints },
        )
    }

    private fun policy(
        categories: Set<EquipmentCategory> = setOf(EquipmentCategory.RightHand)
    ): EquipmentRollPolicy {
        val affixes =
            listOf(
                affix("strength", "power", EquipmentStat.Strength, 100, 1_500, categories),
                affix("accuracy", "accuracy", EquipmentStat.AttackSlash, 100, 1_000, categories),
                affix("critical", "critical", EquipmentStat.CriticalRate, 50, 500, categories),
                affix("speed", "speed", EquipmentStat.AttackSpeedTicks, -1, 0, categories),
                affix("prayer", "prayer", EquipmentStat.Prayer, 1, 8, categories),
                affix("lifesteal", "sustain", EquipmentStat.LifeSteal, 25, 300, categories),
                affix("boss", "target", EquipmentStat.BossDamage, 100, 800, categories),
            )
        return EquipmentRollPolicy(
            affixes = affixes,
            uniqueEffects = listOf("jackpot_chain_strike", "jackpot_two", "jackpot_three"),
        )
    }

    private fun affix(
        id: String,
        family: String,
        stat: EquipmentStat,
        min: Int,
        max: Int,
        categories: Set<EquipmentCategory>,
    ): EquipmentAffixDefinition =
        EquipmentAffixDefinition(
            id = id,
            family = family,
            stat = stat,
            unit = ModifierUnit.BasisPoints,
            polarity = if (min < 0) ModifierPolarity.Mixed else ModifierPolarity.Boon,
            minMagnitude = min,
            maxMagnitude = max,
            categories = categories,
        )
}
