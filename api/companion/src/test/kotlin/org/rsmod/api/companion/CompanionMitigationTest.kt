package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategory
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit

public class CompanionMitigationTest {
    private fun item(id: Long, vararg affixes: Pair<EquipmentStat, Int>): EquipmentInstance =
        EquipmentInstance(
            instanceId = id,
            templateObj = 100,
            category = EquipmentCategory.Torso,
            rarity = EquipmentRarity.Rare,
            rollSeed = 0L,
            affixes =
                affixes.mapIndexed { i, (stat, magnitude) ->
                    EquipmentAffixRoll(
                        slot = i,
                        definitionId = "test-${stat.name.lowercase()}",
                        family = "test-${stat.name.lowercase()}",
                        stat = stat,
                        unit = ModifierUnit.Flat,
                        polarity = ModifierPolarity.Boon,
                        magnitude = magnitude,
                    )
                },
            sockets = emptyList(),
            uniqueEffectIds = emptyList(),
            source = "test",
        )

    private fun sheet(vararg affixes: Pair<EquipmentStat, Int>): CompanionStatSheet {
        val registry = EquipmentInstanceRegistry()
        if (affixes.isNotEmpty()) {
            registry.put(item(1L, *affixes))
        }
        return CompanionStatCalculator.calculate(
            companion(gear = if (affixes.isEmpty()) emptyList() else listOf(1L)),
            registry,
        )
    }

    private fun companion(
        talents: List<CompanionTalent> = emptyList(),
        gear: List<Long> = emptyList(),
    ) =
        Companion(
            id = 1L,
            ownerCharacterId = 7L,
            slot = 1,
            name = "Test",
            companionClass = CompanionClass.TANK,
            npcId = 10,
            level = 1,
            hitpoints = 100,
            maximumHitpoints = 100,
            talents = talents,
            gearInstanceIds = gear,
        )

    @Test
    public fun `melee resistance only applies against melee attack style`() {
        val sheet = sheet(EquipmentStat.AbsorbMelee to 20) // 20% melee absorb
        assertEquals(
            80,
            CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = 9_999),
        )
        assertEquals(
            100,
            CompanionMitigation.resolve(100, sheet, attackStyle = 1, blockRoll = 9_999),
        )
        assertEquals(
            100,
            CompanionMitigation.resolve(100, sheet, attackStyle = 2, blockRoll = 9_999),
        )
        // Unknown/no attacker (poison-style loss): no style resistance at all.
        assertEquals(
            100,
            CompanionMitigation.resolve(100, sheet, attackStyle = -1, blockRoll = 9_999),
        )
    }

    @Test
    public fun `resistance applies before flat damage reduction`() {
        val sheet =
            sheet(
                EquipmentStat.AbsorbRanged to 50, // 50% ranged
                EquipmentStat.DamageReduction to 5, // 5% DR
            )
        // 100 -> 50 after resistance -> 47 after DR (50*0.95 = 47.5 floored).
        assertEquals(
            47,
            CompanionMitigation.resolve(100, sheet, attackStyle = 1, blockRoll = 9_999),
        )
    }

    @Test
    public fun `successful block halves post mitigation damage`() {
        // unyielding-wall r10 -> 2000 bps (20%) block chance on top of 50% melee absorb.
        val registry = EquipmentInstanceRegistry()
        registry.put(item(1L, EquipmentStat.AbsorbMelee to 50))
        val sheet =
            CompanionStatCalculator.calculate(
                companion(
                    talents = listOf(CompanionTalent("unyielding-wall", ranks = 10)),
                    gear = listOf(1L),
                ),
                registry,
            )
        assertEquals(2_000, sheet.blockChanceBps)
        // Blocked: 100 -> 50 (absorb) -> 25 (block).
        assertEquals(
            25,
            CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = 1_999),
        )
        // Unblocked at the strict boundary.
        assertEquals(
            50,
            CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = 2_000),
        )
    }

    @Test
    public fun `block roll boundary is strict below`() {
        val sheet = sheet()
        // A zero-block sheet never blocks, regardless of roll.
        assertEquals(100, CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = 0))
    }

    @Test
    public fun `resolve rejects out of range rolls`() {
        val sheet = sheet()
        assertThrows(IllegalArgumentException::class.java) {
            CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = 10_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompanionMitigation.resolve(100, sheet, attackStyle = 0, blockRoll = -1)
        }
    }
}
