package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MysteryEnchantServiceTest {
    @Test
    fun `roll is deterministic and stays inside the configured pool`() {
        val first = MysteryEnchantService.roll(239L)
        assertEquals(first, MysteryEnchantService.roll(239L))
        assertTrue(first.id in MysteryEnchantService.pool)
        assertTrue(first.tier in MysteryEnchantTier.entries)
        assertEquals(MysteryEnchantConfig.DEFAULT_KILLS, first.kills)
    }

    @Test
    fun `apply and consume expire exactly at the last valid kill`() {
        val source = fixture()
        val enchanted =
            MysteryEnchantService.apply(
                source,
                MysteryEnchantRoll(MysteryEnchantId.EXECUTIONER, MysteryEnchantTier.RARE, kills = 2),
            )
        assertEquals(2, enchanted.mysteryEnchantKillsRemaining)

        val oneLeft = assertNotNull(MysteryEnchantService.consumeKill(enchanted))
        assertEquals(1, oneLeft.mysteryEnchantKillsRemaining)
        val expired = assertNotNull(MysteryEnchantService.consumeKill(oneLeft))
        assertNull(expired.mysteryEnchantId)
        assertNull(expired.mysteryEnchantTier)
        assertEquals(0, expired.mysteryEnchantKillsMax)
        assertNull(MysteryEnchantService.consumeKill(expired))
    }

    private fun fixture() =
        EquipmentInstance(
            instanceId = 1L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Uncommon,
            rollSeed = 239L,
            affixes = emptyList(),
            sockets = emptyList(),
            uniqueEffectIds = emptyList(),
            source = "test",
        )
}
