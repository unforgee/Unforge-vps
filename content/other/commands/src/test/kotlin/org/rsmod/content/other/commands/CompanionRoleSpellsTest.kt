package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class CompanionRoleSpellsTest {
    /**
     * Baseline attack roll matching the canonical pipeline: effective magic `108` (level 99, normal
     * style `+9`) with no equipment bonus -> `108 * (0 + 64)`.
     */
    private val baseRoll = 108 * 64

    @Test
    public fun `baseline magic stats preserve previous heal behaviour`() {
        // Zero magic damage bonus and baseline accuracy => 1.0 multiplier.
        val heal = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        assertEquals((99 * 35) / 100, heal)
    }

    @Test
    public fun `equipment healing power increases heal`() {
        val baseline = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        // 50 effective healing power -> 500 per-mille -> 1.5 multiplier on the base heal.
        val boosted =
            computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll, healingPowerPm = 500)
        assertTrue(boosted > baseline)
        assertEquals((99 * 35 / 100) * 3 / 2, boosted)
    }

    @Test
    public fun `zero healing power keeps baseline heal`() {
        val baseline = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        assertEquals(
            baseline,
            computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll, healingPowerPm = 0),
        )
    }

    @Test
    public fun `increased magic damage bonus increases heal`() {
        val baseline = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        // +15% magic damage bonus (150 per-mille) => 1.15 multiplier.
        val boosted = computeSupportHealAmount(99, 10, 35, 150, baseRoll, baseRoll)
        assertTrue(boosted > baseline)
        assertEquals(39, boosted) // floor(34 * 1.15)
    }

    @Test
    public fun `increased magic accuracy increases heal`() {
        val baseline = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        // Doubled attack roll => (2.0 - 1.0) * 0.5 = +0.5 => 1.5 multiplier.
        val boosted = computeSupportHealAmount(99, 10, 35, 0, baseRoll * 2, baseRoll)
        assertTrue(boosted > baseline)
        assertEquals(51, boosted) // floor(34 * 1.5)
    }

    @Test
    public fun `magic damage and accuracy bonuses stack`() {
        val dmgOnly = computeSupportHealAmount(99, 10, 35, 150, baseRoll, baseRoll)
        val accOnly = computeSupportHealAmount(99, 10, 35, 0, baseRoll * 2, baseRoll)
        // +15% damage and doubled accuracy => 1 + 0.15 + 0.5 = 1.65 multiplier.
        val both = computeSupportHealAmount(99, 10, 35, 150, baseRoll * 2, baseRoll)
        assertTrue(both > dmgOnly)
        assertTrue(both > accOnly)
        assertEquals(56, both) // floor(34 * 1.65)
    }

    @Test
    public fun `minimum heal is preserved`() {
        // 10 maxHp * 1% = 0 base => still heals the 5 hp minimum.
        assertEquals(MIN_SUPPORT_HEAL, computeSupportHealAmount(10, 2, 1, 0, baseRoll, baseRoll))
    }

    @Test
    public fun `heal is capped by missing hitpoints`() {
        // Massive bonuses => capped multiplier, but only 2 hp may be restored.
        assertEquals(2, computeSupportHealAmount(100, 98, 35, 1000, baseRoll * 10, baseRoll))
    }

    @Test
    public fun `combined scaling is clamped at the maximum multiplier`() {
        // Raw multiplier would be 1 + 2.0 + 4.5 = 7.5 => clamped to 1.75.
        val heal = computeSupportHealAmount(99, 1, 35, 2000, baseRoll * 10, baseRoll)
        assertEquals(59, heal) // floor(34 * 1.75)
    }

    @Test
    public fun `below baseline stats never reduce the heal`() {
        // Negative damage bonus and half the baseline accuracy floor the multiplier at 1.0.
        val heal = computeSupportHealAmount(99, 10, 35, -50, baseRoll / 2, baseRoll)
        assertEquals((99 * 35) / 100, heal)
    }

    @Test
    public fun `heal scales monotonically with damage bonus and accuracy`() {
        var previous = 0
        for (pm in listOf(0, 10, 150, 300, 1000)) {
            val heal = computeSupportHealAmount(99, 10, 35, pm, baseRoll, baseRoll)
            assertTrue(heal >= previous)
            previous = heal
        }
        previous = 0
        for (ratio in listOf(1, 2, 3, 4, 8)) {
            val heal = computeSupportHealAmount(99, 10, 35, 0, baseRoll * ratio, baseRoll)
            assertTrue(heal >= previous)
            previous = heal
        }
    }

    @Test
    public fun `spell power percent still drives the base heal`() {
        // Companion-level/spell scaling is preserved before the magic-stat multiplier.
        val weak = computeSupportHealAmount(99, 10, 10, 0, baseRoll, baseRoll)
        val strong = computeSupportHealAmount(99, 10, 35, 0, baseRoll, baseRoll)
        assertEquals(9, weak)
        assertEquals(34, strong)
    }

    // --- Support-heal target selection (multi-companion healing) ---

    @Test
    public fun `support heal prefers the most wounded candidate`() {
        val owner = SupportHealCandidate(OWNER_HEAL_KEY, currentHp = 70, maxHp = 100)
        val ally = SupportHealCandidate(42_000L, currentHp = 30, maxHp = 100)
        val target = selectSupportHealTarget(listOf(owner, ally), claimed = emptySet())
        assertEquals(42_000L, target!!.key)
    }

    @Test
    public fun `support heal heals a wounded companion instead of a healthy owner`() {
        // Owner at 90% is above the threshold and not eligible; the companion at 40% is.
        val owner = SupportHealCandidate(OWNER_HEAL_KEY, currentHp = 90, maxHp = 100)
        val ally = SupportHealCandidate(7L, currentHp = 40, maxHp = 100)
        val target = selectSupportHealTarget(listOf(owner, ally), claimed = emptySet())
        assertEquals(7L, target!!.key)
    }

    @Test
    public fun `support heal skips full health candidates entirely`() {
        val owner = SupportHealCandidate(OWNER_HEAL_KEY, currentHp = 100, maxHp = 100)
        val ally = SupportHealCandidate(7L, currentHp = 99, maxHp = 100)
        // 99/100 = 990 permille > 800 threshold: nobody is wounded enough.
        assertNull(selectSupportHealTarget(listOf(owner, ally), claimed = emptySet()))
    }

    @Test
    public fun `support heal tie breaks on the lowest target key`() {
        // Equal hp ratios: the owner key (0) precedes every companion id; between two
        // companions the lower stable id wins.
        val owner = SupportHealCandidate(OWNER_HEAL_KEY, currentHp = 50, maxHp = 100)
        val ally = SupportHealCandidate(9L, currentHp = 50, maxHp = 100)
        assertEquals(
            OWNER_HEAL_KEY,
            selectSupportHealTarget(listOf(ally, owner), claimed = emptySet())!!.key,
        )
        val low = SupportHealCandidate(9L, currentHp = 25, maxHp = 50)
        val high = SupportHealCandidate(3L, currentHp = 50, maxHp = 100)
        assertEquals(3L, selectSupportHealTarget(listOf(low, high), claimed = emptySet())!!.key)
    }

    @Test
    public fun `claimed targets are skipped so a second support never double heals`() {
        val owner = SupportHealCandidate(OWNER_HEAL_KEY, currentHp = 40, maxHp = 100)
        val allyA = SupportHealCandidate(7L, currentHp = 30, maxHp = 100)
        val allyB = SupportHealCandidate(9L, currentHp = 50, maxHp = 100)
        val candidates = listOf(owner, allyA, allyB)
        // First support picks the most wounded ally and claims it.
        val first = selectSupportHealTarget(candidates, claimed = emptySet())!!
        assertEquals(7L, first.key)
        // Second support ticking the same cycle heals a different target.
        val second = selectSupportHealTarget(candidates, claimed = setOf(first.key))!!
        assertEquals(OWNER_HEAL_KEY, second.key)
        // A third support still finds the last wounded member instead of repeating a heal.
        val third = selectSupportHealTarget(candidates, claimed = setOf(first.key, second.key))!!
        assertEquals(9L, third.key)
        // Once every wounded member is claimed there is nothing left to heal.
        assertNull(
            selectSupportHealTarget(candidates, claimed = setOf(first.key, second.key, third.key))
        )
    }

    @Test
    public fun `dead candidates with zero hitpoints are never selected`() {
        val dead = SupportHealCandidate(7L, currentHp = 0, maxHp = 100)
        assertNull(selectSupportHealTarget(listOf(dead), claimed = emptySet()))
    }
}
