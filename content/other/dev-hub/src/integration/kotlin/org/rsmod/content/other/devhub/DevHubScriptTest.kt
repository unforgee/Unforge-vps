package org.rsmod.content.other.devhub

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.devhub.configs.devhub_components
import org.rsmod.content.other.devhub.configs.devhub_invs
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.map.CoordGrid

class DevHubTestDeps
@Inject
constructor(
    val cheats: CheatCommandMap,
    val invTypes: InvTypeList,
    val modLevels: ModLevelTypeList,
) {
    fun hubInv(player: Player): Inventory =
        player.invMap.getOrPut(invTypes[devhub_invs.dev_hub_shop])

    /** Test players have no mod level assigned; the `::hub` admin gate needs one. */
    fun openHub(player: Player) {
        player.modLevel = modLevels[modlevels.owner]
        cheats.execute(player, "hub", emptyList())
    }
}

class DevHubScriptTest {
    @Test
    fun GameTestState.`hub command fills the grid with hundred-million stock`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            assertNotNull(hubInv[0]) { "First grid slot should hold the first category obj." }
            assertEquals(100_000_000, hubInv[0]?.count)
        }

    @Test
    fun GameTestState.`grid take-five grants the clicked obj for free`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            val shown = checkNotNull(hubInv[0])
            player.ifButton(devhub_components.grid, comsub = 0, op = IfButtonOp.Op2, obj = shown.id)
            advance()

            val granted = player.inv.filterNotNull().filter { it.id == shown.id }
            assertTrue(granted.isNotEmpty()) { "Take-5 should add the obj to the player inv." }
            assertEquals(5, granted.sumOf { it.count })
            // The store never decrements - stock still reads 100M.
            assertEquals(100_000_000, hubInv[0]?.count)
        }

    @Test
    fun GameTestState.`tab and category clicks repopulate the grid`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            val itemsTabObj = checkNotNull(hubInv[0]).id

            // Equipment tab (index 1) swaps the grid to the first equipment category.
            player.ifButton(devhub_components.tabButtons[1])
            advance()
            val equipmentObj = checkNotNull(hubInv[0]).id
            assertNotEquals(itemsTabObj, equipmentObj)

            // Second category row swaps within the tab.
            player.ifButton(devhub_components.catButtons[1])
            advance()
            assertNotEquals(equipmentObj, checkNotNull(hubInv[0]).id)
        }

    @Test
    fun GameTestState.`skills flow sets a level through the menu and count dialog`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            // The Skills tab (index 4 since Gear went in at 2) just selects the tab; its
            // "All skills" category row is what launches the menu-driven editor.
            player.ifButton(devhub_components.tabButtons[4])
            advance()
            player.ifButton(devhub_components.catButtons[0])
            advance()
            checkNotNull(player.activeCoroutine) { "Skill menu should be awaiting a selection." }

            // Select the first stat in the menu (the pause-button component is not verified by
            // `menu`, only the subcomponent index is read).
            player.resumeActiveCoroutine(
                ResumePauseButtonInput(devhub_components.root, subcomponent = 0)
            )
            // The real client closes the menu modal after a selection and reports it via a
            // `CloseModal` packet, which the main process consumes after the input phase. Replicate
            // it - without the script's one-cycle wait after the menu, this close races the count
            // dialog's modal capture and the flow dies silently (the original in-client bug).
            player.simulateClientCloseModal()
            // Three cycles: the script waits out two (its menu-close settle delay, during which
            // the deferred close is consumed) and resumes into the count dialog on the next.
            advance()
            advance()
            advance()
            checkNotNull(player.activeCoroutine) { "Count dialog should be awaiting input." }

            player.resumeActiveCoroutine(ResumePCountDialogInput(count = 60))
            advance()

            val stat = statTypes().values.first()
            assertEquals(60, player.statBase(stat))
        }

    private fun GameTestState.statTypes() = cacheTypes.stats

    @OptIn(InternalApi::class)
    private fun Player.simulateClientCloseModal() {
        ui.closeModal = true
    }

    @Test
    fun GameTestState.`teleport category shows its own scroll list`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            // The Teleports tab (index 3) only selects the tab and shows its category rows.
            player.ifButton(devhub_components.tabButtons[3])
            advance()
            assertNull(player.activeCoroutine) { "Tab click alone should open no menu." }

            // Row 4 is "Cities"; its first destination comes from the shared Eco teleport catalog.
            player.ifButton(devhub_components.catButtons[4])
            advance()
            assertNull(player.activeCoroutine) { "Teleport categories should not open a menu." }

            player.ifButton(devhub_components.teleportRows[0])
            advance()
            assertEquals(CoordGrid(x = 3299, z = 3197), player.coords)
        }

    @Test
    fun GameTestState.`teleports tab exposes the flattened list without pagination`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            player.ifButton(devhub_components.tabButtons[3])
            advance()

            // The first row is the standard spellbook home teleport. The row is a direct action;
            // it no longer requires opening a second paged/menu view first.
            player.ifButton(devhub_components.teleportRows[0])
            advance()
            assertEquals(CoordGrid(x = 3222, z = 3218), player.coords)
        }

    @Test
    fun GameTestState.`main-world teleporter cannot access item tabs`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.cheats.execute(player, "teleui", emptyList())
            advance()

            // The command is available without an admin level, but the item tab is ignored.
            player.ifButton(devhub_components.tabButtons[0])
            advance()
            player.ifButton(devhub_components.teleportRows[0])
            advance()

            assertEquals(CoordGrid(x = 3222, z = 3218), player.coords)
        }

    @Test
    fun GameTestState.`edge command teleports directly to Edgeville`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.cheats.execute(player, "edge", emptyList())
            advance()

            assertEquals(CoordGrid(x = 3087, z = 3496), player.coords)
        }

    @Test
    fun GameTestState.`gear tab serves the curated loadout lists`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            // Gear tab is index 2; its first category is the curated melee loadout, which is
            // authored (not sorted) - slot 0 is the list's first entry.
            player.ifButton(devhub_components.tabButtons[2])
            advance()
            val first = checkNotNull(hubInv[0]) { "Curated melee gear should fill slot 0." }
            assertEquals("Torva full helm", cacheTypes.objs[first.id]?.name)
        }

    @Test
    fun GameTestState.`search fills the grid with matches and a tab click clears it`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            player.ifButton(devhub_components.search_button)
            advance()
            checkNotNull(player.activeCoroutine) { "Search should be awaiting the string dialog." }

            player.resumeActiveCoroutine(ResumePStringDialogInput(text = "Dragon claws"))
            advance()
            val result = checkNotNull(hubInv[0]) { "Search results should fill slot 0." }
            val resultName = checkNotNull(cacheTypes.objs[result.id]).lowercaseName
            assertTrue(resultName.contains("dragon claws")) {
                "Slot 0 should hold a search match, was: $resultName"
            }

            // Any tab click drops the search view and returns to the tab's first category.
            player.ifButton(devhub_components.tabButtons[0])
            advance()
            assertNotEquals(result.id, checkNotNull(hubInv[0]).id)
        }

    @Test
    fun GameTestState.`item lists scroll within 248-item pages`() =
        runInjectedGameTest(DevHubTestDeps::class, null, DevHubScript::class) { deps ->
            deps.openHub(player)
            advance()

            val hubInv = deps.hubInv(player)
            val firstObj = checkNotNull(hubInv[0]).id

            player.ifButton(devhub_components.itemScrollDown)
            advance()
            assertEquals(firstObj, checkNotNull(hubInv[0]).id)

            player.ifButton(devhub_components.itemScrollUp)
            advance()
            assertEquals(firstObj, checkNotNull(hubInv[0]).id)
        }
}
