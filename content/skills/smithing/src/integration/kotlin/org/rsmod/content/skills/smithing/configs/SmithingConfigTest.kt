package org.rsmod.content.skills.smithing.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.ui.SkillMulti
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.ui.Component

/**
 * Guards the two halves of the module's data: the generated grid against the cache it came from,
 * and the hand-authored smelting table against what the `skillmulti` dialog can actually show.
 */
class SmithingConfigTest {
    @Test
    fun GameTestState.`every generated product is described by the cache enums`() =
        runBasicGameTest {
            val levels = cacheTypes.enums.getValue(SMITHING_REQUIREMENT_ENUM).primitiveMap
            val barsRequired = cacheTypes.enums.getValue(SMITHING_BARS_ENUM).primitiveMap
            val quantity = cacheTypes.enums.getValue(SMITHING_QUANTITY_ENUM).primitiveMap

            for ((barId, slots) in SmithingProducts.products) {
                for ((component, productId) in slots) {
                    val where = "bar=$barId component=$component product=$productId"
                    assertNotNull(cacheTypes.objs[productId], "Product obj missing: $where")
                    // If any of these were absent the client would treat the slot as never
                    // craftable, and the server would be offering something the menu never shows.
                    assertTrue(productId in levels, "No level requirement: $where")
                    assertTrue(productId in barsRequired, "No bar cost: $where")
                    assertTrue(productId in quantity, "No output count: $where")
                }
            }
        }

    @Test
    fun GameTestState.`every generated slot is an op-bearing component on interface 312`() =
        runBasicGameTest {
            val interfaceId = smithing_interfaces.smithing.id
            val slots = SmithingProducts.products.values.flatMap { it.keys }.distinct()
            assertFalse(slots.isEmpty())

            for (slot in slots) {
                val component = cacheTypes.components[Component(interfaceId, slot).packed]
                assertNotNull(component, "Missing component $interfaceId:$slot")
                // Op1 is baked on these, which is why the anvil - unlike the furnace - needs no
                // `ifSetEvents` before its clicks are reported.
                assertTrue(component!!.hasEvent(IfEvent.Op1), "No baked Op1 on $interfaceId:$slot")
            }
        }

    @Test
    fun GameTestState.`bar tiers round-trip and match the client's tier enum`() = runBasicGameTest {
        val clientTiers =
            cacheTypes.enums.getValue(BAR_TYPE_ENUM).primitiveMap.entries.associate { (k, v) ->
                (k as Int) to (v as Int)
            }
        assertEquals(clientTiers, SmithingProducts.barByTier)

        for ((tier, barId) in SmithingProducts.barByTier) {
            assertEquals(tier, SmithingProducts.tierByBar[barId])
            assertTrue(SmithingProducts.products.containsKey(barId), "No grid for bar $barId")
        }
    }

    @Test
    fun GameTestState.`smelting recipes fit the skillmulti dialog`() = runBasicGameTest {
        val recipes = SmeltingRecipes.all
        assertTrue(recipes.size <= SkillMulti.MAX_OPTIONS)

        for (recipe in recipes) {
            // `|` is the delimiter cs 632 splits the dialog's single string argument on; a label
            // containing one would silently shift every row after it.
            assertFalse(recipe.label.contains('|'), "Label has a pipe: ${recipe.label}")
            assertTrue(recipe.level >= 1, "Bad level: ${recipe.label}")
            assertTrue(recipe.xp > 0.0, "Bad xp: ${recipe.label}")
            assertTrue(recipe.ingredients.isNotEmpty(), "No ingredients: ${recipe.label}")
            assertNotNull(cacheTypes.objs[recipe.bar.id], "Unknown bar: ${recipe.label}")
            for (ingredient in recipe.ingredients) {
                assertTrue(ingredient.count >= 1, "Bad count: ${recipe.label}")
                assertNotNull(cacheTypes.objs[ingredient.obj.id], "Unknown ore: ${recipe.label}")
            }
        }

        assertEquals(recipes.sortedBy { it.level }, recipes, "Recipes must be listed by level.")
        assertEquals(recipes.distinctBy { it.bar.id }.size, recipes.size, "Duplicate bar.")
    }

    @Test
    fun GameTestState.`every anvil tier has its own xp rate`() = runBasicGameTest {
        // Keyed exactly like `barByTier`, because that is where the call site's bar id comes from.
        // A tier added to the grid without a rate would make the anvil refuse it outright, and a
        // rate left behind for a tier that no longer exists is dead data - both fail here.
        assertEquals(
            SmithingProducts.barByTier.values.sorted(),
            SmithingXp.perBar.keys.sorted(),
            "The xp table and the anvil grid disagree about which tiers exist.",
        )

        for ((barId, xp) in SmithingXp.perBar) {
            val bar = cacheTypes.objs.getValue(barId)
            assertTrue(xp > 0.0, "Bad xp for ${bar.name}: $xp")
            // Xp is stored to a tenth; anything finer would be silently truncated when awarded.
            assertEquals(0.0, (xp * 10) % 1.0, "Xp for ${bar.name} is finer than 0.1: $xp")
        }
    }

    @Test
    fun GameTestState.`every anvil bar is grouped so it can be used on an anvil`() =
        runBasicGameTest {
            for (barId in SmithingProducts.barByTier.values) {
                val bar = cacheTypes.objs.getValue(barId)
                assertTrue(
                    bar.isContentType(smithing_content.smithable_bar),
                    "Bar ${bar.name} has a grid but is not in the smithable-bar group.",
                )
            }
        }

    @Test
    fun GameTestState.`silver and gold smelt but never reach an anvil`() = runBasicGameTest {
        // Deliberate, and easy to "fix" back into a bug: `smithing_setup` (cs 430) has no tier for
        // either, so a bar used on an anvil has to fall through to the normal "nothing happens".
        // Adding them to the smithable-bar group would open a grid with nothing in it.
        for (bar in listOf(smithing_objs.silver_bar, smithing_objs.gold_bar)) {
            val type = cacheTypes.objs.getValue(bar.id)
            assertFalse(
                type.isContentType(smithing_content.smithable_bar),
                "${type.name} has no anvil grid but is grouped as smithable.",
            )
            assertFalse(
                SmithingProducts.tierByBar.containsKey(bar.id),
                "${type.name} unexpectedly has an anvil grid.",
            )
            assertTrue(
                SmeltingRecipes.all.any { it.bar.id == bar.id },
                "${type.name} should still be smeltable at a furnace.",
            )
        }
    }

    @Test
    fun GameTestState.`every smelting ingredient is grouped so it can be used on a furnace`() =
        runBasicGameTest {
            for (recipe in SmeltingRecipes.all) {
                for (ingredient in recipe.ingredients) {
                    val ore = cacheTypes.objs.getValue(ingredient.obj.id)
                    assertTrue(
                        ore.isContentType(smithing_content.smeltable_ore),
                        "${ore.name} is smelted but is not in the smeltable-ore group.",
                    )
                }
            }
        }

    @Test
    fun GameTestState.`anvil loc is in the anvil content group`() = runBasicGameTest {
        val anvil = cacheTypes.locs.getValue(smithing_locs.anvil.id)
        assertTrue(anvil.isContentType(smithing_content.anvil))
    }

    private companion object {
        private const val SMITHING_QUANTITY_ENUM = 844
        private const val SMITHING_BARS_ENUM = 845
        private const val SMITHING_REQUIREMENT_ENUM = 846

        /** Unnamed in `enum.sym`; maps `smithing_bar_type` to the bar obj cs 430 switches on. */
        private const val BAR_TYPE_ENUM = 1253
    }
}
