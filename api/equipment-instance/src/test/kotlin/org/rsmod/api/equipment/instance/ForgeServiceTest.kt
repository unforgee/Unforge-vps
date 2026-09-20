package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Forge 2.0 domain coverage: upgrades scale magnitudes without rerolling, reforge operations never
 * change rarity or item identity, targeted rerolls leave unchecked affixes byte-for-byte identical,
 * and every invalid request fails closed before any cost is produced.
 */
class ForgeServiceTest {
    private val rollable =
        EquipmentAffixDefinition(
            id = "dmg-reduction",
            family = "dmg-reduction",
            stat = EquipmentStat.DamageReduction,
            unit = ModifierUnit.BasisPoints,
            polarity = ModifierPolarity.Boon,
            minMagnitude = 400,
            maxMagnitude = 900,
            categories = setOf(EquipmentCategory.Torso),
        )

    private val second =
        EquipmentAffixDefinition(
            id = "healing-power",
            family = "healing-power",
            stat = EquipmentStat.HealingPower,
            unit = ModifierUnit.BasisPoints,
            polarity = ModifierPolarity.Boon,
            minMagnitude = 800,
            maxMagnitude = 1400,
            categories = setOf(EquipmentCategory.Torso),
        )

    private val third =
        EquipmentAffixDefinition(
            id = "cooldown",
            family = "cooldown",
            stat = EquipmentStat.Cooldown,
            unit = ModifierUnit.BasisPoints,
            polarity = ModifierPolarity.Boon,
            minMagnitude = 200,
            maxMagnitude = 500,
            categories = setOf(EquipmentCategory.Torso),
        )

    private val exotic =
        EquipmentAffixDefinition(
            id = "legacy-mark",
            family = "legacy-mark",
            stat = EquipmentStat.ThreatGeneration,
            unit = ModifierUnit.BasisPoints,
            polarity = ModifierPolarity.Boon,
            minMagnitude = 100,
            maxMagnitude = 200,
            categories = setOf(EquipmentCategory.Torso),
            rerollable = false,
        )

    private val service = ForgeService(listOf(rollable, second, third, exotic))

    private fun roll(definition: EquipmentAffixDefinition, slot: Int, magnitude: Int) =
        EquipmentAffixRoll(
            slot = slot,
            definitionId = definition.id,
            family = definition.family,
            stat = definition.stat,
            unit = definition.unit,
            polarity = definition.polarity,
            magnitude = magnitude,
        )

    private fun instance(
        rarity: EquipmentRarity = EquipmentRarity.Epic,
        affixes: List<EquipmentAffixRoll> = listOf(roll(rollable, 0, 500), roll(second, 1, 1000)),
        upgradeLevel: Int = 0,
        lockedAffixSlots: Set<Int> = emptySet(),
        binding: ItemBinding = ItemBinding.UNBOUND,
        state: EquipmentInstanceState = EquipmentInstanceState.ACTIVE,
    ): EquipmentInstance =
        EquipmentInstance(
            instanceId = 42L,
            templateObj = 4151,
            category = EquipmentCategory.Torso,
            rarity = rarity,
            rollSeed = 239L,
            affixes = affixes,
            sockets = emptyList(),
            uniqueEffectIds = emptyList(),
            source = "test",
            upgradeLevel = upgradeLevel,
            lockedAffixSlots = lockedAffixSlots,
            binding = binding,
            state = state,
        )

    // --- UPGRADE ---

    @Test
    fun `upgrade bumps level and scales affixes without rerolling or touching rarity`() {
        val before = instance()
        val result = service.previewUpgrade(before)
        val preview = assertIs<ForgeResult.Ok>(result).preview

        assertEquals(ForgeOperation.UPGRADE, preview.operation)
        assertEquals(1, preview.after.upgradeLevel)
        assertEquals(before.rarity, preview.after.rarity)
        assertEquals(before.instanceId, preview.after.instanceId)
        assertEquals(before.templateObj, preview.after.templateObj)
        // +10% (UPGRADE_AFFIX_SCALE_BPS) on 500 -> 550 and 1000 -> 1100.
        assertEquals(550, preview.after.affixes[0].magnitude)
        assertEquals(1100, preview.after.affixes[1].magnitude)
        // Identity of the affixes is preserved - only the magnitude changed.
        assertEquals(before.affixes[0].copy(magnitude = 550), preview.after.affixes[0])
        assertEquals(ForgeConfig.upgradeCost(0), preview.cost)
    }

    @Test
    fun `upgrade is rejected at max level`() {
        val result = service.previewUpgrade(instance(upgradeLevel = ForgeConfig.MAX_UPGRADE_LEVEL))
        assertEquals("max-upgrade", assertIs<ForgeResult.Err>(result).error.code)
    }

    @Test
    fun `upgrade does not reroll affixes and preserves locked slots`() {
        val before = instance(upgradeLevel = 3, lockedAffixSlots = setOf(1))
        val preview = assertIs<ForgeResult.Ok>(service.previewUpgrade(before)).preview
        assertEquals(4, preview.after.upgradeLevel)
        assertEquals(before.affixes.size, preview.after.affixes.size)
        assertEquals(preview.after.lockedAffixSlots, before.lockedAffixSlots)
        assertEquals(emptyList(), preview.changedSlots)
    }

    // --- REFORGE ALL ---

    @Test
    fun `reforge all rerolls within rarity range and never changes rarity`() {
        for (rarity in listOf(EquipmentRarity.Epic, EquipmentRarity.Legendary)) {
            for (seed in 0L until 50L) {
                val before = instance(rarity = rarity)
                val preview =
                    assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, seed)).preview
                assertEquals(rarity, preview.after.rarity)
                assertEquals(before.instanceId, preview.after.instanceId)
                assertEquals(before.templateObj, preview.after.templateObj)
                for (affix in preview.after.affixes) {
                    val def = listOf(rollable, second).first { it.id == affix.definitionId }
                    assertTrue(
                        affix.magnitude >= def.minMagnitude && affix.magnitude <= def.maxMagnitude,
                        "magnitude ${affix.magnitude} outside ${def.minMagnitude}..${def.maxMagnitude}",
                    )
                }
            }
        }
    }

    @Test
    fun `reforge all keeps non-rerollable affixes byte-for-byte identical`() {
        val before =
            instance(
                affixes =
                    listOf(roll(rollable, 0, 500), roll(exotic, 1, 150), roll(second, 2, 1000))
            )
        for (seed in 0L until 20L) {
            val preview = assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, seed)).preview
            assertEquals(before.affixes[1], preview.after.affixes[1])
            assertEquals(listOf(0, 2), preview.changedSlots)
        }
    }

    @Test
    fun `reforge all with no rerollable affixes fails`() {
        val before = instance(affixes = listOf(roll(exotic, 0, 150)))
        val result = service.previewReforgeAll(before, 1L)
        assertEquals("nothing-to-reforge", assertIs<ForgeResult.Err>(result).error.code)
    }

    @Test
    fun `reforge all respects locked affix slots`() {
        val before = instance(lockedAffixSlots = setOf(1))
        val preview = assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, 7L)).preview
        assertEquals(listOf(0), preview.changedSlots)
        assertEquals(before.affixes[1], preview.after.affixes[1])
    }

    // --- REFORGE SELECTED ---

    @Test
    fun `reforge selected only changes the checked slots`() {
        val before =
            instance(
                affixes = listOf(roll(rollable, 0, 500), roll(second, 1, 1000), roll(third, 2, 300))
            )
        for (seed in 0L until 20L) {
            val preview =
                assertIs<ForgeResult.Ok>(service.previewReforgeSelected(before, setOf(0, 2), seed))
                    .preview
            // Slot 1 is unchecked and must be byte-for-byte identical.
            assertEquals(before.affixes[1], preview.after.affixes[1])
            assertEquals(listOf(0, 2), preview.changedSlots)
            assertEquals(ForgeConfig.reforgeSelectedCost(2), preview.cost)
            // The item still reports the same rarity and identity.
            assertEquals(before.rarity, preview.after.rarity)
        }
    }

    @Test
    fun `reforge selected premium cost scales with selection size`() {
        val before =
            instance(
                affixes = listOf(roll(rollable, 0, 500), roll(second, 1, 1000), roll(third, 2, 300))
            )
        for (count in 1..3) {
            val selected = (0 until count).toSet()
            val preview =
                assertIs<ForgeResult.Ok>(service.previewReforgeSelected(before, selected, 5L))
                    .preview
            assertEquals(
                ForgeConfig.REFORGE_SELECTED_BASE_TICKETS +
                    count * ForgeConfig.REFORGE_SELECTED_TICKETS_PER_AFFIX,
                preview.cost.tickets,
            )
            assertTrue(preview.cost.tickets > ForgeConfig.REFORGE_ALL_TICKETS)
        }
    }

    @Test
    fun `reforge selected rejects slots that do not exist`() {
        val result = service.previewReforgeSelected(instance(), setOf(9), 1L)
        assertEquals("slot-not-on-item", assertIs<ForgeResult.Err>(result).error.code)
    }

    @Test
    fun `reforge selected rejects locked and non-rerollable slots`() {
        val locked =
            service.previewReforgeSelected(instance(lockedAffixSlots = setOf(1)), setOf(1), 1L)
        assertEquals("slot-not-rerollable", assertIs<ForgeResult.Err>(locked).error.code)

        val exoticItem = instance(affixes = listOf(roll(exotic, 0, 150), roll(second, 1, 1000)))
        val nonRerollable = service.previewReforgeSelected(exoticItem, setOf(0), 1L)
        assertEquals("slot-not-rerollable", assertIs<ForgeResult.Err>(nonRerollable).error.code)
    }

    @Test
    fun `reforge selected requires at least one checked affix`() {
        val result = service.previewReforgeSelected(instance(), emptySet(), 1L)
        assertEquals("empty-selection", assertIs<ForgeResult.Err>(result).error.code)
    }

    // --- FORGEABILITY + QUALITY ---

    @Test
    fun `trade-locked and non-active items are not forgeable`() {
        for (bad in
            listOf(
                instance(binding = ItemBinding.TRADE_LOCKED),
                instance(state = EquipmentInstanceState.IN_TRADE),
                instance(binding = ItemBinding.DESTROYED),
            )) {
            assertEquals(
                "not-forgeable",
                assertIs<ForgeResult.Err>(service.previewUpgrade(bad)).error.code,
            )
            assertEquals(
                "not-forgeable",
                assertIs<ForgeResult.Err>(service.previewReforgeAll(bad, 1L)).error.code,
            )
        }
    }

    @Test
    fun `reforge determinism - same seed reproduces the same rolls`() {
        val before = instance()
        val a = assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, 42L)).preview
        val b = assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, 42L)).preview
        assertEquals(a.after.affixes, b.after.affixes)
        val c = assertIs<ForgeResult.Ok>(service.previewReforgeAll(before, 43L)).preview
        // Overwhelmingly likely to differ; guards against the seed being ignored entirely.
        assertNotEquals(a.after.affixes, c.after.affixes)
    }

    @Test
    fun `quality grading distinguishes high rolls`() {
        val lowRoll = instance(affixes = listOf(roll(rollable, 0, 400)))
        val highRoll = instance(affixes = listOf(roll(rollable, 0, 900)))
        assertEquals(0, service.qualityBps(lowRoll, lowRoll.affixes[0]))
        assertEquals(10_000, service.qualityBps(highRoll, highRoll.affixes[0]))
        assertTrue(service.hasHighRoll(highRoll))
        assertTrue(!service.hasHighRoll(lowRoll))
    }

    @Test
    fun `upgrade preserves sockets unique effects and binding`() {
        val before =
            instance()
                .copy(
                    binding = ItemBinding.ACCOUNT_BOUND,
                    ownerAccountId = 7L,
                    sockets = listOf(EquipmentSocket(0, "Gem", socketedObj = 1603)),
                )
        val preview = assertIs<ForgeResult.Ok>(service.previewUpgrade(before)).preview
        assertEquals(before.sockets, preview.after.sockets)
        assertEquals(before.binding, preview.after.binding)
        assertEquals(before.ownerAccountId, preview.after.ownerAccountId)
    }
}
