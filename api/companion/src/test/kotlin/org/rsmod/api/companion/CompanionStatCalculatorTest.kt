package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategory
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentSocket
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit

private const val OWNER: Long = 7L

public class CompanionStatCalculatorTest {
    private fun companion(
        level: Int = 1,
        talents: List<CompanionTalent> = emptyList(),
        gearInstanceIds: List<Long> = emptyList(),
        evolutionStage: Int = 0,
    ): Companion =
        Companion(
            id = 1L,
            ownerCharacterId = OWNER,
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
        )

    private fun item(
        id: Long,
        affixes: List<EquipmentAffixRoll> = emptyList(),
        uniqueEffectIds: List<String> = emptyList(),
        rarity: EquipmentRarity = EquipmentRarity.Rare,
        sockets: List<EquipmentSocket> = emptyList(),
    ): EquipmentInstance =
        EquipmentInstance(
            instanceId = id,
            templateObj = 100,
            category = EquipmentCategory.Torso,
            rarity = rarity,
            rollSeed = 0L,
            affixes = affixes,
            sockets = sockets,
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

    @Test
    public fun `base companion keeps legacy combat projection`() {
        val sheet =
            CompanionStatCalculator.calculate(companion(level = 10), EquipmentInstanceRegistry())
        // level 10: hp 100 + 9*5 = 145, dmg = 1 + 9%, support = 1 + 4.5%
        assertEquals(145, sheet.combat.maximumHitpoints)
        assertEquals(1.09, sheet.combat.damageMultiplier, 0.001)
        assertEquals(1.045, sheet.combat.supportPower, 0.001)
        assertEquals(4, sheet.combat.attackDelay)
    }

    @Test
    public fun `equipment affixes feed power, healing power, cdr and dr`() {
        val registry = EquipmentInstanceRegistry()
        registry.put(
            item(
                1L,
                rarity = EquipmentRarity.Legendary,
                affixes =
                    listOf(
                        affix(EquipmentStat.Strength, 10),
                        affix(EquipmentStat.HealingPower, 40, slot = 1),
                        affix(EquipmentStat.Cooldown, 5, slot = 2),
                        affix(EquipmentStat.DamageReduction, 8, slot = 3),
                    ),
            )
        )
        val sheet =
            CompanionStatCalculator.calculate(companion(gearInstanceIds = listOf(1L)), registry)
        assertEquals(10, sheet.value(CompanionStat.MELEE_POWER))
        assertEquals(40, sheet.healingPower)
        assertEquals(500, sheet.cooldownReductionBps)
        assertEquals(800, sheet.damageReductionBps)
        // 8% DR applied once at the canonical boundary.
        assertEquals(92, sheet.reducedDamage(100))
    }

    @Test
    public fun `caps clamp multiplicative stats`() {
        val registry = EquipmentInstanceRegistry()
        registry.put(
            item(
                1L,
                rarity = EquipmentRarity.Jackpot,
                affixes =
                    listOf(
                        affix(EquipmentStat.DamageReduction, 200),
                        affix(EquipmentStat.Cooldown, 200, slot = 1),
                        affix(EquipmentStat.CriticalRate, 200, slot = 2),
                        affix(EquipmentStat.LifeSteal, 200, slot = 3),
                    ),
                sockets = listOf(EquipmentSocket(0, "gem"), EquipmentSocket(1, "gem")),
            )
        )
        val sheet =
            CompanionStatCalculator.calculate(companion(gearInstanceIds = listOf(1L)), registry)
        assertEquals(CompanionStatCaps.MAX_DAMAGE_REDUCTION_BPS, sheet.damageReductionBps)
        assertEquals(CompanionStatCaps.MAX_COOLDOWN_REDUCTION_BPS, sheet.cooldownReductionBps)
        assertEquals(
            CompanionStatCaps.MAX_CRITICAL_CHANCE_BPS,
            sheet.value(CompanionStat.CRITICAL_CHANCE_BPS),
        )
        assertEquals(CompanionStatCaps.MAX_LIFESTEAL_BPS, sheet.value(CompanionStat.LIFESTEAL_BPS))
    }

    @Test
    public fun `progression tracks feed their stats`() {
        val sheet =
            CompanionStatCalculator.calculate(
                companion(),
                EquipmentInstanceRegistry(),
                skillLevels =
                    mapOf(
                        CompanionSkill.MELEE to 60,
                        CompanionSkill.SUPPORT to 40,
                        CompanionSkill.TANK to 30,
                    ),
            )
        assertEquals(60, sheet.value(CompanionStat.MELEE_POWER))
        assertEquals(80, sheet.healingPower)
        assertEquals(100 + 30, sheet.value(CompanionStat.MAX_HEALTH))
        assertEquals(600, sheet.threatGenerationBps)
    }

    @Test
    public fun `set bonuses activate by piece count and report locked tiers`() {
        val registry = EquipmentInstanceRegistry()
        // Two infernal-pact pieces -> only the 2-piece bonus is live.
        registry.put(
            item(1L, listOf(affix(EquipmentStat.HealingPower, 1)), listOf("set:infernal-pact"))
        )
        registry.put(
            item(2L, listOf(affix(EquipmentStat.MaximumHealth, 1)), listOf("set:infernal-pact"))
        )
        val sheet =
            CompanionStatCalculator.calculate(companion(gearInstanceIds = listOf(1L, 2L)), registry)
        val activation = sheet.activeSets.single()
        assertEquals("infernal-pact", activation.definition.id)
        assertEquals(2, activation.equippedPieces)
        assertTrue(activation.isActive(2))
        assertTrue(!activation.isActive(3))
        // +5% damage from the 2-piece tier on top of the 100% base.
        assertEquals(10_500, sheet.value(CompanionStat.DAMAGE_BPS))
    }

    @Test
    public fun `evolution stage contributes its percent to power and health`() {
        val sheet =
            CompanionStatCalculator.calculate(
                companion(level = 50, evolutionStage = 2),
                EquipmentInstanceRegistry(),
            )
        // Stage 2 = +10%: damage 100% + 49% level + 10% evolution.
        assertEquals(15_900, sheet.value(CompanionStat.DAMAGE_BPS))
        assertTrue(
            sheet.breakdown(CompanionStat.DAMAGE_BPS).any {
                it.source == CompanionStatSource.EVOLUTION && it.value == 1_000
            }
        )
    }

    @Test
    public fun `breakdown reports every contributing source`() {
        val registry = EquipmentInstanceRegistry()
        registry.put(item(1L, listOf(affix(EquipmentStat.MaximumHealth, 50))))
        val sheet =
            CompanionStatCalculator.calculate(
                companion(gearInstanceIds = listOf(1L)),
                registry,
                skillLevels = mapOf(CompanionSkill.TANK to 10),
            )
        val sources = sheet.breakdown(CompanionStat.MAX_HEALTH).map { it.source }
        assertTrue(CompanionStatSource.BASE in sources)
        assertTrue(CompanionStatSource.EQUIPMENT in sources)
        assertTrue(CompanionStatSource.PROGRESSION in sources)
    }

    @Test
    public fun `effective cooldown uses the central rule`() {
        assertEquals(80, CompanionStatCaps.effectiveCooldownTicks(100, 2_000))
        // Over-cap cdr is clamped, and the minimum keeps zero-cooldown exploits impossible.
        assertEquals(60, CompanionStatCaps.effectiveCooldownTicks(100, 9_999))
        assertEquals(1, CompanionStatCaps.effectiveCooldownTicks(1, 4_000))
    }

    @Test
    public fun `stat cache invalidates on record change`() {
        val registry = EquipmentInstanceRegistry()
        val cache = CompanionStatCache()
        val sheetA = cache.sheet(companion(level = 1), registry, emptyMap())
        val grown = companion(level = 50)
        val sheetB = cache.sheet(grown, registry, emptyMap())
        assertTrue(sheetB.combat.maximumHitpoints > sheetA.combat.maximumHitpoints)
        // Same inputs reuse the cached sheet instance.
        assertTrue(cache.sheet(grown, registry, emptyMap()) === sheetB)
    }
}
