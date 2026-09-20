package org.rsmod.content.interfaces.skillshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.skillshop.configs.SkillShopEntry
import org.rsmod.content.interfaces.skillshop.configs.skillshop_components
import org.rsmod.content.interfaces.skillshop.configs.skillshop_interfaces
import org.rsmod.content.interfaces.skillshop.configs.skillshop_varps
import org.rsmod.game.type.obj.ObjType

/**
 * The purchase half of the skill-point chain: server-authoritative buys against the `skill_points`
 * varp.
 *
 * Each test opens the modal and clicks a row's Buy button the same way a client would, so the level
 * gate, the unlock bit, the funds check and the item grant are all exercised through the real
 * handler rather than a simulated purchase.
 */
class SkillShopScriptTest {
    private fun GameTestScope.openShop() {
        player.ifOpenMain(skillshop_interfaces.unforge_skillshop)
        advance()
        assertModalOpen(skillshop_interfaces.unforge_skillshop)
    }

    private fun GameTestScope.buy(index: Int) {
        player.ifButton(skillshop_components.entryBuys[index])
        advance()
    }

    private fun GameTestScope.objType(id: Int): ObjType =
        objTypes[id] ?: error("Missing obj type: $id")

    @Test
    fun GameTestState.`buy grants the item and deducts the exact cost`() =
        runGameTest(SkillShopScript::class) {
            player.setVarp(skillshop_varps.points, 100)
            openShop()
            buy(SkillShopEntry.Shark.ordinal)

            assertEquals(75, player.vars[skillshop_varps.points])
            assertEquals(5, player.count(objType(SkillShopEntry.Shark.obj)))
            assertMessageSent("<col=ffb84d>Bought Shark x5 for 25 skill points.</col>")
        }

    @Test
    fun GameTestState.`insufficient funds block the purchase`() =
        runGameTest(SkillShopScript::class) {
            player.setVarp(skillshop_varps.points, 10)
            openShop()
            buy(SkillShopEntry.Shark.ordinal)

            // Nothing changed: no points moved and no item was granted.
            assertEquals(10, player.vars[skillshop_varps.points])
            assertEquals(0, player.count(objType(SkillShopEntry.Shark.obj)))
            assertMessageSent("<col=ffb84d>You need 25 skill points for that - you have 10.</col>")
        }

    @Test
    fun GameTestState.`unlock purchase sets the bit and cannot repeat`() =
        runGameTest(SkillShopScript::class) {
            player.setVarp(skillshop_varps.points, 600)
            player.stats.setBaseLevel(stats.cooking, SkillShopEntry.FeastScroll.requiredLevel)
            openShop()
            buy(SkillShopEntry.FeastScroll.ordinal)

            assertEquals(100, player.vars[skillshop_varps.points])
            assertEquals(1, player.vars[skillshop_varps.recipeUnlocks] and 1)

            // Owned unlocks short-circuit before the funds check: a second click spends nothing.
            buy(SkillShopEntry.FeastScroll.ordinal)
            assertEquals(100, player.vars[skillshop_varps.points])
        }

    @Test
    fun GameTestState.`level gate blocks the row until the requirement is met`() =
        runGameTest(SkillShopScript::class) {
            player.setVarp(skillshop_varps.points, 300)
            player.stats.setBaseLevel(stats.smithing, SkillShopEntry.ArmourKit.requiredLevel - 1)
            openShop()
            buy(SkillShopEntry.ArmourKit.ordinal)

            assertEquals(300, player.vars[skillshop_varps.points])
            assertEquals(0, player.count(objType(SkillShopEntry.ArmourKit.obj)))
            assertMessageSent("<col=ffb84d>Requires Smithing 40 - yours: 39.</col>")

            player.stats.setBaseLevel(stats.smithing, SkillShopEntry.ArmourKit.requiredLevel)
            buy(SkillShopEntry.ArmourKit.ordinal)
            assertEquals(50, player.vars[skillshop_varps.points])
            assertEquals(1, player.count(objType(SkillShopEntry.ArmourKit.obj)))
        }
}
