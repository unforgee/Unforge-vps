package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class EquipmentAbilityEffectsTest {
    @Test
    public fun `every catalog ability resolves to a proc`() {
        for (ability in EquipmentAbilityCatalog.all) {
            val proc = EquipmentAbilityProcs.procFor(ability.id)
            assertTrue(!proc.isEmpty, "Ability ${ability.id} resolved to an empty proc")
        }
    }

    @Test
    public fun `lifesteal keywords heal on hit`() {
        assertTrue(EquipmentAbilityProcs.procFor("pow-vampiric-slam").onHitHealBps > 0)
        assertTrue(EquipmentAbilityProcs.procFor("spec-healing-blade").onHitHealBps > 0)
        assertTrue(EquipmentAbilityProcs.procFor("pow-astral-drain").onHitHealBps > 0)
    }

    @Test
    public fun `restore keywords grant on-kill recovery`() {
        val proc = EquipmentAbilityProcs.procFor("pow-blessed-ward")
        assertTrue(proc.onKillHeal > 0)
        assertTrue(proc.onKillPrayer > 0)
    }

    @Test
    public fun `greed keywords grant extra drop rolls`() {
        assertTrue(EquipmentAbilityProcs.procFor("pow-eclipsed-mark").extraDropRolls > 0)
        assertTrue(EquipmentAbilityProcs.procFor("pow-astral-nova").extraDropRolls > 0)
    }

    @Test
    public fun `aggregation caps stacking`() {
        val ids = List(50) { "pow-vampiric-slam" }
        val proc = EquipmentAbilityProcs.aggregate(ids)
        assertEquals(5000, proc.onHitHealBps)
        val greedy = EquipmentAbilityProcs.aggregate(List(10) { "pow-eclipsed-mark" })
        assertEquals(3, greedy.extraDropRolls)
    }

    @Test
    public fun `boss abilities hit harder than empowered`() {
        val boss = EquipmentAbilityProcs.procFor("boss-bear-trap-callisto")
        val pow = EquipmentAbilityProcs.procFor("pow-crystal-pulse")
        assertTrue(boss.outgoingDamageBps > pow.outgoingDamageBps)
    }
}
