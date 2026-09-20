package org.rsmod.content.other.devhub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.devhub.configs.devhub_components
import org.rsmod.content.other.devhub.configs.devhub_invs
import org.rsmod.game.type.inv.InvStackType

class DevHubConfigTest {
    @Test
    fun GameTestState.`authored components exist in the cache`() = runBasicGameTest {
        val components = cacheTypes.components
        val required =
            listOf(
                devhub_components.root,
                devhub_components.close,
                devhub_components.grid,
                devhub_components.search_button,
                devhub_components.search_button_text,
            ) +
                devhub_components.tabButtons +
                devhub_components.tabBoxes +
                devhub_components.tabSels +
                devhub_components.tabTexts +
                devhub_components.catButtons +
                devhub_components.catBoxes +
                devhub_components.catSels +
                devhub_components.catTexts
        for (component in required) {
            assertNotNull(components[component.packed]) {
                "Authored component missing from cache: $component"
            }
        }
    }

    @Test
    fun GameTestState.`hub inv resolves with grid size and always-stack`() = runBasicGameTest {
        val inv = cacheTypes.invs[devhub_invs.dev_hub_shop]
        assertEquals(48, inv.size)
        assertEquals(InvStackType.Always, inv.stack)
    }

    @Test
    fun GameTestState.`categorizer assigns every non-filtered obj exactly once`() =
        runBasicGameTest {
            val objTypes = cacheTypes.objs
            val categorizer = ObjCategorizer(objTypes)
            val categorized = DevHubCategory.entries.flatMap { categorizer[it] }
            // Exactly once: no obj may appear in two categories.
            assertEquals(categorized.size, categorized.toSet().size)

            val eligible =
                objTypes.values.count {
                    !it.isCert &&
                        !it.isPlaceholder &&
                        !it.isTransformation &&
                        !it.isDummyItem &&
                        it.name.isNotBlank() &&
                        !it.name.equals("null", ignoreCase = true)
                }
            // Literally everything: every eligible obj lands in some category.
            assertEquals(eligible, categorized.size)

            // The fallback bucket is what guarantees completeness - it must be in use.
            assertTrue(categorizer[DevHubCategory.EverythingElse].isNotEmpty())

            // No template objs may leak through into any visible category.
            for (id in categorized) {
                val type = checkNotNull(objTypes[id])
                assertTrue(!type.isCert && !type.isPlaceholder && !type.isDummyItem) {
                    "Filtered obj leaked into a category: $type"
                }
            }
        }

    @Test
    fun GameTestState.`every curated gear name resolves and every category has items`() =
        runBasicGameTest {
            val curated = CuratedGear(cacheTypes.objs)
            assertEquals(emptyList<String>(), curated.unresolvedNames)
            for (category in DevHubCategory.forTab(DevHubTab.Gear)) {
                assertTrue(curated[category].isNotEmpty()) {
                    "Curated gear category has no resolved items: $category"
                }
            }
            // The DT2 rings and Soulreaper axe are pinned by internal name - their display names
            // are ambiguous in this cache and lowest-id-wins used to pick the wrong objs
            // (verified in-game).
            val jewellery = curated[DevHubCategory.SwitchesAndJewellery]
            for (ring in listOf(28307, 28310, 28313, 28316)) {
                assertTrue(ring in jewellery) { "Pinned ring id $ring missing from jewellery." }
            }
            assertTrue(28338 in curated[DevHubCategory.MeleeGear]) {
                "Pinned Soulreaper axe id 28338 missing from melee gear."
            }
        }

    @Test
    fun GameTestState.`weapon sweep fills gear lists by level requirement`() = runBasicGameTest {
        val objTypes = cacheTypes.objs
        val curated = CuratedGear(objTypes)
        fun names(category: DevHubCategory) =
            curated[category].map { checkNotNull(objTypes[it]).lowercaseName }

        val melee = names(DevHubCategory.MeleeGear)
        val ranged = names(DevHubCategory.RangedGear)
        val magic = names(DevHubCategory.MagicGear)

        // Swept in, not hand-authored: the whip needs 70 Attack, the ACB 70 Ranged.
        assertTrue("abyssal whip" in melee) { "70-Attack sweep should include the whip." }
        assertTrue("armadyl crossbow" in ranged) { "70-Ranged sweep should include the ACB." }

        // The staff redirect: a 70-Attack staff belongs to magic, never melee.
        assertTrue("ahrim's staff" in magic)
        assertTrue("ahrim's staff" !in melee) { "70-Attack staffs must redirect to magic." }

        // Hand placement wins over the sweep: no category may hold duplicate names or ids.
        for (category in DevHubCategory.forTab(DevHubTab.Gear)) {
            val ids = curated[category]
            assertEquals(ids.size, ids.toSet().size) { "Duplicate ids in $category." }
            val categoryNames = names(category)
            assertEquals(categoryNames.size, categoryNames.toSet().size) {
                "Duplicate names in $category."
            }
        }
    }

    @Test
    fun GameTestState.`every tab fits its categories into the authored rows`() = runBasicGameTest {
        for (tab in DevHubTab.entries) {
            val categories = DevHubCategory.forTab(tab)
            assertTrue(categories.size <= devhub_components.catTexts.size) {
                "Tab $tab has ${categories.size} categories; interface has 12 rows."
            }
        }
    }
}
