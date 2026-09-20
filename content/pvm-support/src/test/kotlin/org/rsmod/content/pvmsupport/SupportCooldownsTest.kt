package org.rsmod.content.pvmsupport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.api.equipment.instance.EquipmentAffixCatalog
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierUnit

public class SupportCooldownsTest {
    @Test
    public fun `base cooldown is unchanged with no affixes`() {
        for (spell in SupportSpell.entries) {
            assertEquals(
                spell.baseCooldownTicks,
                SupportCooldowns.effectiveCooldownTicks(spell, reductionBps = 0, endgame = false),
                spell.name,
            )
        }
    }

    @Test
    public fun `ten percent reduction shaves a tenth off the cooldown`() {
        // Mending Light = 50 ticks -> 50 - 10 % = 45.
        assertEquals(
            45,
            SupportCooldowns.effectiveCooldownTicks(
                SupportSpell.MendingLight,
                reductionBps = 1_000,
                endgame = false,
            ),
        )
    }

    @Test
    public fun `total reduction is capped at 25 percent normally`() {
        // 9 999 bps of affixes must behave exactly like the 2 500 bps cap.
        val capped =
            SupportCooldowns.effectiveCooldownTicks(
                SupportSpell.MendingLight,
                reductionBps = 9_999,
                endgame = false,
            )
        val atCap =
            SupportCooldowns.effectiveCooldownTicks(
                SupportSpell.MendingLight,
                reductionBps = SupportCooldowns.DEFAULT_CAP_BPS,
                endgame = false,
            )
        assertEquals(atCap, capped)
        assertEquals(38, capped)
    }

    @Test
    public fun `endgame gear raises the cap to 35 percent`() {
        val endgame =
            SupportCooldowns.effectiveCooldownTicks(
                SupportSpell.MendingLight,
                reductionBps = SupportCooldowns.ENDGAME_CAP_BPS,
                endgame = true,
            )
        val capped =
            SupportCooldowns.effectiveCooldownTicks(
                SupportSpell.MendingLight,
                reductionBps = 99_999,
                endgame = true,
            )
        assertEquals(33, endgame)
        assertEquals(endgame, capped)
        assertTrue(
            endgame <
                SupportCooldowns.effectiveCooldownTicks(
                    SupportSpell.MendingLight,
                    SupportCooldowns.ENDGAME_CAP_BPS,
                    false,
                )
        )
    }

    @Test
    public fun `a cooldown can never reach zero or drop below its floor`() {
        for (spell in SupportSpell.entries) {
            for (bps in listOf(0, 2_500, 3_500, 10_000, 1_000_000, Int.MAX_VALUE)) {
                val ticks = SupportCooldowns.effectiveCooldownTicks(spell, bps, endgame = true)
                assertTrue(ticks >= spell.minCooldownTicks, "${spell.name} @ $bps -> $ticks")
                assertTrue(ticks > 0, "${spell.name} @ $bps -> $ticks")
            }
        }
    }

    @Test
    public fun `negative reduction is ignored`() {
        for (spell in SupportSpell.entries) {
            assertEquals(
                spell.baseCooldownTicks,
                SupportCooldowns.effectiveCooldownTicks(spell, -5_000, endgame = false),
                spell.name,
            )
        }
    }

    @Test
    public fun `every spell floor is below its base cooldown`() {
        for (spell in SupportSpell.entries) {
            assertTrue(
                spell.minCooldownTicks in 1 until spell.baseCooldownTicks,
                "${spell.name}: base=${spell.baseCooldownTicks} min=${spell.minCooldownTicks}",
            )
        }
    }

    @Test
    public fun `remaining ticks only reaches zero once the deadline has passed`() {
        assertEquals(
            0,
            SupportCooldowns.remainingTicksUntil(deadline = 100, now = 100, maxTicks = 50),
        )
        assertEquals(
            0,
            SupportCooldowns.remainingTicksUntil(deadline = 50, now = 100, maxTicks = 50),
        )
        assertEquals(
            1,
            SupportCooldowns.remainingTicksUntil(deadline = 151, now = 100, maxTicks = 50),
        )
        // Never reports more than the spell's own base cooldown, even for a stale deadline.
        assertEquals(
            50,
            SupportCooldowns.remainingTicksUntil(deadline = 999_999, now = 100, maxTicks = 50),
        )
    }

    @Test
    public fun `every cooldown category maps to a distinct affix stat`() {
        val stats = SupportCooldownCategory.entries.map { it.affixStat }
        assertEquals(stats.size, stats.distinct().size, "categories must not share an affix stat")
    }

    @Test
    public fun `cooldown categories never use the attack speed stats`() {
        for (category in SupportCooldownCategory.entries) {
            assertNotEquals(EquipmentStat.AttackSpeedPercent, category.affixStat)
            assertNotEquals(EquipmentStat.AttackSpeedTicks, category.affixStat)
        }
    }

    @Test
    public fun `every cooldown category has exactly one catalog affix`() {
        for (category in SupportCooldownCategory.entries) {
            val matches = EquipmentAffixCatalog.definitions.filter { it.stat == category.affixStat }
            assertEquals(1, matches.size, "missing/duplicated affix for ${category.name}")
            val definition = matches.single()
            assertEquals(ModifierUnit.BasisPoints, definition.unit, category.name)
            assertTrue(definition.family.isNotBlank(), category.name)
            // A real range means the roll uses the authored bounds instead of the rarity band.
            assertTrue(
                definition.minMagnitude in 1 until definition.maxMagnitude,
                "${category.name}: ${definition.minMagnitude}..${definition.maxMagnitude}",
            )
        }
    }

    @Test
    public fun `cooldown affixes have unique ids and families`() {
        val cooldownStats = SupportCooldownCategory.entries.map { it.affixStat }.toSet()
        val affixes = EquipmentAffixCatalog.definitions.filter { it.stat in cooldownStats }
        assertEquals(affixes.size, affixes.map { it.id }.distinct().size)
        assertEquals(affixes.size, affixes.map { it.family }.distinct().size)
    }
}
