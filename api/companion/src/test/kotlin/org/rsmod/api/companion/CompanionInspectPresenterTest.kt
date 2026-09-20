package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategory
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit

public class CompanionInspectPresenterTest {
    private fun companion(
        level: Int = 50,
        gearInstanceIds: List<Long> = emptyList(),
        evolutionStage: Int = 0,
        abilityLoadout: List<String> = emptyList(),
        talents: List<CompanionTalent> = emptyList(),
    ): Companion =
        Companion(
            id = 1L,
            ownerCharacterId = 7L,
            slot = 1,
            name = "Test",
            companionClass = CompanionClass.DPS,
            npcId = 10,
            level = level,
            hitpoints = 100,
            maximumHitpoints = 100,
            talentPoints = 99,
            talents = talents,
            gearInstanceIds = gearInstanceIds,
            evolutionStage = evolutionStage,
            abilityLoadout = abilityLoadout,
        )

    private fun item(
        id: Long,
        affixes: List<EquipmentAffixRoll> = emptyList(),
        uniqueEffectIds: List<String> = emptyList(),
    ): EquipmentInstance =
        EquipmentInstance(
            instanceId = id,
            templateObj = 100,
            category = EquipmentCategory.Torso,
            rarity = EquipmentRarity.Rare,
            rollSeed = 0L,
            affixes = affixes,
            sockets = emptyList(),
            uniqueEffectIds = uniqueEffectIds,
            source = "test",
        )

    private fun affix(stat: EquipmentStat, magnitude: Int, slot: Int = 0): EquipmentAffixRoll =
        EquipmentAffixRoll(
            slot = slot,
            definitionId = "test-${stat.name.lowercase()}",
            family = "test-${stat.name.lowercase()}",
            stat = stat,
            unit = ModifierUnit.Flat,
            polarity = ModifierPolarity.Boon,
            magnitude = magnitude,
        )

    private fun present(
        companion: Companion,
        registry: EquipmentInstanceRegistry = EquipmentInstanceRegistry(),
        skillExperience: Map<CompanionSkill, Long> = emptyMap(),
        cooldowns: Map<String, Long> = emptyMap(),
    ) =
        CompanionInspectPresenter.present(
            companion = companion,
            sheet = CompanionStatCalculator.calculate(companion, registry),
            skillExperience = skillExperience,
            abilityCooldownsMillis = cooldowns,
            equippedCount = companion.gearInstanceIds.size,
            bestGearRarity = null,
        )

    @Test
    public fun `identity lines report class style mode and gear count`() {
        val view = present(companion(gearInstanceIds = listOf(1L)))
        assertTrue(view.identityLines.any { it.startsWith("Class: DPS") })
        assertTrue(view.identityLines.any { it.startsWith("Style:") })
        assertTrue(view.identityLines.any { it.startsWith("Mode:") })
        assertTrue(view.identityLines.any { it.startsWith("Gear: 1/") })
    }

    @Test
    public fun `stat lines expose per-source breakdown from the canonical sheet`() {
        val registry = EquipmentInstanceRegistry()
        registry.put(item(1L, listOf(affix(EquipmentStat.Strength, 10))))
        val c = companion(level = 50, gearInstanceIds = listOf(1L), evolutionStage = 1)
        val view = present(c, registry)
        val damage = view.statLines.first { it.startsWith("Damage:") }
        // 100% base + 49% level + 5% evolution - breakdown grouped by source tag.
        assertTrue(damage.contains("lvl"), damage)
        assertTrue(damage.contains("evo"), damage)
        assertTrue(view.statLines.any { it.startsWith("Resist M/R/M:") })
    }

    @Test
    public fun `evolution lines show current stage next requirement and gain`() {
        val c = companion(level = 50, evolutionStage = 1)
        val view = present(c)
        assertTrue(view.evolutionLines.any { it.contains("(1/3)") }, view.evolutionLines.toString())
        // Stage 2 unlocks at level 50 - a level-50 companion is READY.
        assertTrue(view.evolutionLines.any { it.contains("@ Lv50") })
        assertTrue(view.evolutionLines.any { it.contains("READY") })
    }

    @Test
    public fun `maxed evolution reports MAX STAGE`() {
        val view = present(companion(level = 99, evolutionStage = 3))
        assertTrue(view.evolutionLines.any { it.contains("MAX STAGE") })
    }

    @Test
    public fun `all five skill tracks render with progress toward next level`() {
        val view =
            present(
                companion(),
                skillExperience = mapOf(CompanionSkill.MELEE to 150L, CompanionSkill.TANK to 0L),
            )
        assertEquals(5, view.skillLines.size)
        assertTrue(view.skillLines.any { it.startsWith("MELEE") && it.contains("Lv2") })
        assertTrue(view.skillLines.any { it.startsWith("TANK") && it.contains("Lv1") })
    }

    @Test
    public fun `locked and active set tiers render distinctly`() {
        val registry = EquipmentInstanceRegistry()
        registry.put(
            item(1L, listOf(affix(EquipmentStat.Strength, 1)), listOf("set:infernal-pact"))
        )
        registry.put(
            item(2L, listOf(affix(EquipmentStat.Strength, 1)), listOf("set:infernal-pact"))
        )
        registry.put(
            item(3L, listOf(affix(EquipmentStat.Strength, 1)), listOf("set:infernal-pact"))
        )
        // Three infernal-pact pieces activate the tier - rendered as "Infernal Pact 3p T<tier>".
        val view = present(companion(gearInstanceIds = listOf(1L, 2L, 3L)), registry)
        assertTrue(
            view.setLines.any { it.contains("Infernal Pact") && it.contains("3p T") },
            view.setLines.toString(),
        )
        // With fewer than the minimum pieces the same set renders as LOCKED.
        val locked = present(companion(gearInstanceIds = listOf(1L)), registry)
        assertTrue(
            locked.setLines.any { it.contains("Infernal Pact") && it.contains("LOCKED") },
            locked.setLines.toString(),
        )
    }

    @Test
    public fun `ability lines show ready state and remaining cooldown seconds`() {
        val c = companion(abilityLoadout = listOf("focused-volley", "crippling-strike"))
        val view = present(c, cooldowns = mapOf("focused-volley" to 5_500L))
        // (5500 + 999) / 1000 -> "6s"; the other loadout slot stays READY.
        assertTrue(view.abilityLines.any { it.endsWith(" 6s") }, view.abilityLines.toString())
        assertTrue(view.abilityLines.any { it.endsWith("READY") })
    }

    @Test
    public fun `empty talents abilities and sets render explicit empty rows`() {
        val view = present(companion())
        assertEquals(listOf("No abilities equipped"), view.abilityLines)
        assertEquals(listOf("No talents allocated"), view.talentLines)
        assertEquals(listOf("No set pieces equipped"), view.setLines)
    }
}
