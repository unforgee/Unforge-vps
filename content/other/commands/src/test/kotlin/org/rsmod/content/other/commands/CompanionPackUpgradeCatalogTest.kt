package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvTypeBuilder
import org.rsmod.game.type.util.UncheckedType

@OptIn(UncheckedType::class)
public class CompanionPackUpgradeCatalogTest {
    private fun inventory(vararg objs: InvObj?): Inventory {
        val type =
            InvTypeBuilder("test_pack_inv")
                .apply {
                    scope = InvScope.Perm
                    protect = false
                    size = 28
                }
                .build(0)
        val inv = Inventory.create(type)
        objs.forEachIndexed { slot, obj -> inv[slot] = obj }
        return inv
    }

    @Test
    public fun `next tier after base capacity is the twenty slot tier`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(CompanionPackUpgradeCatalog.BASE_CAPACITY)
        assertEquals(20, tier!!.capacity)
        assertEquals(1, tier.tier)
    }

    @Test
    public fun `tiers strictly ascend and stop at the hundred slot cap`() {
        val capacities =
            listOf(CompanionPackUpgradeCatalog.BASE_CAPACITY) +
                CompanionPackUpgradeCatalog.tiers.map { it.capacity }
        assertEquals(capacities.sorted(), capacities)
        assertEquals(CompanionPackUpgradeCatalog.MAX_CAPACITY, capacities.last())
        assertEquals(100, CompanionPackUpgradeCatalog.MAX_CAPACITY)
    }

    @Test
    public fun `no tier exists past the maximum capacity`() {
        // At max capacity the upgrade flow must not start - the UI shows "Pack Capacity MAX".
        assertNull(CompanionPackUpgradeCatalog.nextTier(100))
        assertNull(CompanionPackUpgradeCatalog.nextTier(CompanionPackUpgradeCatalog.MAX_CAPACITY))
    }

    @Test
    public fun `requirements pass with exact gold and materials`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        val inv =
            inventory(
                InvObj(995, tier.gold),
                InvObj(1741, 10), // 10x Soft leather
                InvObj(1734, 1), // 1x Thread
            )
        assertTrue(CompanionPackUpgradeCatalog.hasRequirements(inv, tier))
    }

    @Test
    public fun `noted materials count toward requirements`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        // Soft leather certed (1742) must satisfy the 10x Soft leather requirement.
        val inv = inventory(InvObj(995, tier.gold), InvObj(1742, 10), InvObj(1734, 1))
        assertTrue(CompanionPackUpgradeCatalog.hasRequirements(inv, tier))
    }

    @Test
    public fun `requirements fail without materials`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        // Gold alone is never enough - every listed material must be present.
        val inv = inventory(InvObj(995, tier.gold))
        assertFalse(CompanionPackUpgradeCatalog.hasRequirements(inv, tier))
    }

    @Test
    public fun `requirements fail without enough gold`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        val inv = inventory(InvObj(995, tier.gold - 1), InvObj(1741, 10), InvObj(1734, 1))
        assertFalse(CompanionPackUpgradeCatalog.hasRequirements(inv, tier))
    }

    @Test
    public fun `requirements fail on an empty inventory`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        assertFalse(CompanionPackUpgradeCatalog.hasRequirements(inventory(), tier))
    }

    @Test
    public fun `requirements fail when only some materials are present`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        // 9 of 10 leather is not enough, and thread is missing entirely.
        val inv = inventory(InvObj(995, tier.gold), InvObj(1741, 9))
        assertFalse(CompanionPackUpgradeCatalog.hasRequirements(inv, tier))
    }

    @Test
    public fun `format cost lists gold and every material`() {
        val tier = CompanionPackUpgradeCatalog.nextTier(10)!!
        val text = CompanionPackUpgradeCatalog.formatCost(tier)
        assertTrue(text.contains("GP"))
        assertTrue(text.contains("10x Soft leather"))
        assertTrue(text.contains("1x Thread"))
    }
}
