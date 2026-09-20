package org.rsmod.content.other.devhub

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.max
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder.GRID_COLUMNS
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder.ITEM_GRID_ROWS
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder.ITEM_VIEW_HEIGHT
import org.rsmod.content.other.devhub.configs.devhub_components
import org.rsmod.content.other.devhub.configs.devhub_interfaces
import org.rsmod.content.other.devhub.configs.devhub_invs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.isAssociatedWith
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Per-player hub view state. Nothing here persists, and nothing client-side reads it. */
private class DevHubSession(
    var tab: DevHubTab,
    var category: DevHubCategory,
    val teleportsOnly: Boolean = false,
    var page: Int = 0,
    var teleportScroll: Int = 0,
    var itemScroll: Int = 0,
    var searchQuery: String? = null,
    var searchResults: List<Int>? = null,
)

class DevHubScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val objTypes: ObjTypeList,
    private val invTypes: InvTypeList,
    private val statTypes: StatTypeList,
) : PluginScript() {
    private val categorizer by lazy { ObjCategorizer(objTypes) }
    private val curatedGear by lazy { CuratedGear(objTypes) }
    private val teleports by lazy { DevHubTeleports(objTypes) }

    /** Weak keys so a player who logs out mid-session cannot be leaked by this map. */
    private val sessions = WeakHashMap<Player, DevHubSession>()

    override fun ScriptContext.startup() {
        onCommand("hub") {
            desc = "Open the developer hub"
            modLevel = modlevels.admin
            cheat { protectedAccess.launch(player) { openHub(DevHubTab.Items) } }
        }
        onCommand("teleui") {
            desc = "Open the in-game teleport interface"
            cheat {
                protectedAccess.launch(player) {
                    openHub(DevHubTab.Teleports, teleportsOnly = true)
                }
            }
        }
        onCommand("edge") {
            desc = "Teleport to Edgeville"
            cheat {
                protectedAccess.launch(player) {
                    telejump(CoordGrid(x = 3087, z = 3496))
                    mes("Teleported to Edgeville.")
                }
            }
        }
        onIfClose(devhub_interfaces.dev_hub) { player.cleanup() }
        onIfModalButton(devhub_components.close) { ifClose() }
        onIfModalButton(devhub_components.search_button) { searchClick() }
        onIfModalButton(devhub_components.grid) { gridClick(it) }
        onIfModalButton(devhub_components.page_prev) { pageClick(-1) }
        onIfModalButton(devhub_components.page_next) { pageClick(1) }
        onIfModalButton(devhub_components.itemScrollUp) { player.itemScrollBy(-ITEM_SCROLL_STEP) }
        onIfModalButton(devhub_components.itemScrollDown) { player.itemScrollBy(ITEM_SCROLL_STEP) }
        onIfModalButton(devhub_components.teleportScrollUp) {
            player.teleportScrollBy(-TELEPORT_SCROLL_STEP)
        }
        onIfModalButton(devhub_components.teleportScrollDown) {
            player.teleportScrollBy(TELEPORT_SCROLL_STEP)
        }
        for ((index, row) in devhub_components.teleportRows.withIndex()) {
            onIfModalButton(row) { teleportRowClick(index) }
        }
        for ((index, button) in devhub_components.tabButtons.withIndex()) {
            onIfModalButton(button) { tabClick(index) }
        }
        for ((index, button) in devhub_components.catButtons.withIndex()) {
            onIfModalButton(button) { catClick(index) }
        }
    }

    private suspend fun ProtectedAccess.openHub(
        tab: DevHubTab,
        teleportsOnly: Boolean = false,
    ) {
        val session =
            DevHubSession(
                tab = tab,
                category = DevHubCategory.forTab(tab).first(),
                teleportsOnly = teleportsOnly,
            )
        sessions[player] = session
        val hubInv = hubInv()
        invTransmit(hubInv)
        ifOpenMainModal(devhub_interfaces.dev_hub)
        ifSetEvents(
            devhub_components.grid,
            0 until hubInv.size,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )
        // Every clickable component sits alone inside its `_wrap` layer, so clicks arrive with
        // `comsub == 0`; `-1..-1` registers Op1 for every slot the server may see.
        val op1Components =
            listOf(
                devhub_components.close,
                devhub_components.search_button,
                devhub_components.page_prev,
                devhub_components.page_next,
                devhub_components.itemScrollUp,
                devhub_components.itemScrollDown,
                devhub_components.teleportScrollUp,
                devhub_components.teleportScrollDown,
            ) +
                devhub_components.tabButtons +
                devhub_components.catButtons +
                devhub_components.teleportRows
        for (component in op1Components) {
            ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        refresh(session, hubInv)
    }

    private fun Player.cleanup() {
        sessions.remove(this)
        stopInvTransmit(invMap.getOrPut(invTypes[devhub_invs.dev_hub_shop]))
    }

    private fun ProtectedAccess.hubInv(): Inventory = inv(devhub_invs.dev_hub_shop)

    private fun ProtectedAccess.refresh(session: DevHubSession, hubInv: Inventory) {
        val tab = session.tab
        ifSetText(
            devhub_components.title,
            if (session.teleportsOnly) "Teleporter" else "Developer Hub - ${tab.label}",
        )
        for ((index, text) in devhub_components.tabTexts.withIndex()) {
            val label = DevHubTab.entries[index].label
            ifSetText(text, if (index == tab.ordinal) label.selected() else label)
        }
        for ((index, wrap) in devhub_components.tabWraps.withIndex()) {
            ifSetHide(wrap, hide = session.teleportsOnly && index != DevHubTab.Teleports.ordinal)
        }
        for ((index, sel) in devhub_components.tabSels.withIndex()) {
            ifSetHide(sel, hide = index != tab.ordinal)
        }
        ifSetHide(devhub_components.searchButtonWrap, hide = session.teleportsOnly)
        val search = session.searchResults
        val categories = DevHubCategory.forTab(tab)
        for ((index, text) in devhub_components.catTexts.withIndex()) {
            val category = categories.getOrNull(index)
            val label = category?.label ?: ""
            val highlight = category == session.category && search == null
            ifSetText(text, if (highlight) label.selected() else label)
            // Unused rows hide the whole wrap; the sel rect only shows on the selection.
            ifSetHide(devhub_components.catWraps[index], hide = category == null)
            ifSetHide(devhub_components.catSels[index], hide = !highlight)
        }
        val objs =
            when {
                search != null -> search
                session.category.tab == DevHubTab.Gear -> curatedGear[session.category]
                else -> categorizer[session.category]
            }
        if (tab == DevHubTab.Teleports) {
            refreshTeleportList(session, hubInv)
            return
        }
        ifSetHide(devhub_components.grid, hide = false)
        ifSetHide(devhub_components.teleportScroll, hide = true)
        ifSetHide(devhub_components.teleportScrollUpWrap, hide = true)
        ifSetHide(devhub_components.teleportScrollDownWrap, hide = true)
        ifSetHide(devhub_components.itemScrollUpWrap, hide = objs.isEmpty())
        ifSetHide(devhub_components.itemScrollDownWrap, hide = objs.isEmpty())
        val pageCount = itemPageCount(objs.size)
        session.page = session.page.coerceIn(0, pageCount - 1)
        val pageStart = session.page * ITEM_SLOT_COUNT
        val visibleCount = minOf(ITEM_SLOT_COUNT, (objs.size - pageStart).coerceAtLeast(0))
        val maxScroll = itemMaxScroll(visibleCount)
        session.itemScroll = session.itemScroll.coerceIn(0, maxScroll)
        ifSetScrollPos(devhub_components.grid, session.itemScroll)
        for (slot in 0 until hubInv.size) {
            val objId = objs.getOrNull(pageStart + slot)
            val objType = objId?.let { objTypes[it] }
            hubInv[slot] = objType?.let { InvObj(it, count = STOCK_COUNT) }
        }
        val pageLabel =
            if (search != null) {
                "Search: \"${session.searchQuery.orEmpty().escapeTags()}\" - " +
                    "Page ${session.page + 1}/$pageCount - " +
                    "${visibleCount}/${objs.size} items - scroll to browse"
            } else {
                "${session.category.label}: Page ${session.page + 1}/$pageCount - " +
                    "${visibleCount}/${objs.size} items - scroll to browse"
            }
        ifSetText(devhub_components.page_label, pageLabel)
        ifSetHide(devhub_components.pagePrevWrap, hide = pageCount <= 1)
        ifSetHide(devhub_components.pageNextWrap, hide = pageCount <= 1)

        // The cs-149 grid is a snapshot: dynamic children do not repaint reliably off the regular
        // end-of-tick partial transmits alone (verified in-client - switching category left the old
        // page rendered). Push the inv state now, then re-run the init script so it rebuilds from
        // the data that just arrived; the immediate writes keep the two in order.
        UpdateInventory.updateInvPartial(player, hubInv)
        hubInv.clearModifiedSlots()
        // Note the arg naming: cs 153 (which cs 149 defers to) treats the FIRST count as columns -
        // the shop side inv passes (4, 7) and renders 4 wide by 7 tall. rsmod's parameter names
        // have them backwards. Decoded pitch is 36x32 per cell.
        interfaceInvInit(
            inv = hubInv,
            target = devhub_components.grid,
            objRowCount = GRID_COLUMNS,
            objColCount = ITEM_GRID_ROWS,
            op1 = "Take-1",
            op2 = "Take-5",
            op3 = "Take-50",
            op4 = "Take-X",
            op5 = "Examine",
        )
        ifSetScrollPos(devhub_components.grid, session.itemScroll)
    }

    private fun ProtectedAccess.refreshTeleportList(session: DevHubSession, hubInv: Inventory) {
        val destinations = teleports.forCategory(session.category).orEmpty()
        ifSetHide(devhub_components.grid, hide = true)
        ifSetHide(devhub_components.teleportScroll, hide = false)
        ifSetHide(devhub_components.teleportScrollUpWrap, hide = destinations.isEmpty())
        ifSetHide(devhub_components.teleportScrollDownWrap, hide = destinations.isEmpty())
        ifSetHide(devhub_components.itemScrollUpWrap, hide = true)
        ifSetHide(devhub_components.itemScrollDownWrap, hide = true)
        ifSetHide(devhub_components.pagePrevWrap, hide = true)
        ifSetHide(devhub_components.pageNextWrap, hide = true)
        ifSetText(
            devhub_components.page_label,
            "${session.category.label}: ${destinations.size} teleports - scroll to browse",
        )

        for (slot in 0 until hubInv.size) {
            hubInv[slot] = null
        }
        UpdateInventory.updateInvPartial(player, hubInv)
        hubInv.clearModifiedSlots()

        val maxScroll =
            max(
                0,
                destinations.size * DevHubInterfaceBuilder.TELEPORT_ROW_PITCH -
                    DevHubInterfaceBuilder.TELEPORT_VIEW_HEIGHT,
            )
        session.teleportScroll = session.teleportScroll.coerceIn(0, maxScroll)
        ifSetScrollPos(devhub_components.teleportScroll, session.teleportScroll)
        for (index in 0 until DevHubInterfaceBuilder.TELEPORT_ROW_COUNT) {
            val destination = destinations.getOrNull(index)
            val hidden = destination == null
            ifSetHide(devhub_components.teleportRowWraps[index], hide = hidden)
            if (!hidden) {
                ifSetText(devhub_components.teleportRowTexts[index], destination.label)
            }
        }
    }

    private fun ProtectedAccess.tabClick(index: Int) {
        val session = sessions[player] ?: return
        if (session.teleportsOnly && index != DevHubTab.Teleports.ordinal) {
            return
        }
        val tab = DevHubTab[index] ?: return
        session.tab = tab
        session.category = DevHubCategory.forTab(tab).first()
        session.page = 0
        session.teleportScroll = 0
        session.itemScroll = 0
        session.clearSearch()
        refresh(session, hubInv())
    }

    private suspend fun ProtectedAccess.catClick(index: Int) {
        val session = sessions[player] ?: return
        val category = DevHubCategory.forTab(session.tab).getOrNull(index) ?: return
        session.category = category
        session.page = 0
        session.teleportScroll = 0
        session.itemScroll = 0
        session.clearSearch()
        refresh(session, hubInv())
        // Skills keep their dialog-based editor; teleport categories are shown in the central
        // scroll list and therefore do not open a second menu anymore.
        when (session.tab) {
            DevHubTab.Skills -> skillEditor(category)
            else -> Unit
        }
    }

    private fun DevHubSession.clearSearch() {
        searchQuery = null
        searchResults = null
    }

    private suspend fun ProtectedAccess.searchClick() {
        val session = sessions[player] ?: return
        if (session.teleportsOnly) {
            return
        }
        val query = stringDialog("Search for:").trim().lowercase()
        if (query.length < 2) {
            mes("Search with at least two characters.")
            return
        }
        val results =
            categorizer.allIds
                .mapNotNull { objTypes[it] }
                .filter { it.lowercaseName.contains(query) }
                .sortedBy { it.lowercaseName }
                .map { it.id }
        if (results.isEmpty()) {
            mes("Nothing in the store matches \"$query\".")
            return
        }
        session.searchQuery = query
        session.searchResults = results
        session.page = 0
        session.teleportScroll = 0
        session.itemScroll = 0
        refresh(session, hubInv())
    }

    private fun ProtectedAccess.pageClick(delta: Int) {
        val session = sessions[player] ?: return
        if (session.teleportsOnly) {
            return
        }
        val pageCount = itemPageCount(itemObjectCount(session))
        session.page = (session.page + delta).coerceIn(0, pageCount - 1)
        session.itemScroll = 0
        refresh(session, hubInv())
    }

    private fun itemObjectCount(session: DevHubSession): Int =
        when {
            session.searchResults != null -> session.searchResults!!.size
            session.category.tab == DevHubTab.Gear -> curatedGear[session.category].size
            else -> categorizer[session.category].size
        }

    private fun itemPageCount(itemCount: Int): Int =
        max(1, (itemCount + ITEM_SLOT_COUNT - 1) / ITEM_SLOT_COUNT)

    private fun Player.itemScrollBy(delta: Int) {
        val session = sessions[this] ?: return
        val count = itemObjectCount(session)
        val pageStart = session.page * ITEM_SLOT_COUNT
        val visibleCount = minOf(ITEM_SLOT_COUNT, (count - pageStart).coerceAtLeast(0))
        session.itemScroll = (session.itemScroll + delta).coerceIn(0, itemMaxScroll(visibleCount))
        ifSetScrollPos(devhub_components.grid, session.itemScroll)
    }

    private fun itemMaxScroll(itemCount: Int): Int {
        val rows = (itemCount + GRID_COLUMNS - 1) / GRID_COLUMNS
        return max(0, rows * ITEM_ROW_PITCH - ITEM_VIEW_HEIGHT)
    }

    private fun Player.teleportScrollBy(delta: Int) {
        val session = sessions[this] ?: return
        val count = teleports.forCategory(session.category).orEmpty().size
        val maxScroll =
            max(
                0,
                count * DevHubInterfaceBuilder.TELEPORT_ROW_PITCH -
                    DevHubInterfaceBuilder.TELEPORT_VIEW_HEIGHT,
            )
        session.teleportScroll = (session.teleportScroll + delta).coerceIn(0, maxScroll)
        ifSetScrollPos(devhub_components.teleportScroll, session.teleportScroll)
    }

    private fun ProtectedAccess.teleportRowClick(index: Int) {
        val session = sessions[player] ?: return
        val destination = teleports.forCategory(session.category)?.getOrNull(index) ?: return
        telejump(destination.coords)
        mes("Teleported to ${destination.label}.")
    }

    private suspend fun ProtectedAccess.gridClick(button: IfModalButton) {
        val session = sessions[player] ?: return
        if (session.teleportsOnly) {
            return
        }
        val invObj = hubInv()[button.comsub] ?: return
        val clientObj = button.obj
        if (clientObj == null || !clientObj.isAssociatedWith(invObj)) {
            return
        }
        val type = objTypes[invObj.id] ?: return
        when (button.op) {
            IfButtonOp.Op1 -> take(type, 1)
            IfButtonOp.Op2 -> take(type, 5)
            IfButtonOp.Op3 -> take(type, 50)
            IfButtonOp.Op4 -> {
                val count = countDialog("Take how many?")
                if (count > 0) {
                    take(type, count)
                }
            }
            else -> mes(type.desc.ifBlank { type.name })
        }
    }

    private fun ProtectedAccess.take(type: UnpackedObjType, count: Int) {
        val result = player.invAdd(player.inv, type, count, strict = false)
        if (result.err is TransactionResult.RestrictedDummyitem) {
            mes("That item can't be spawned.")
            return
        }
        val added = result.completed()
        if (added <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("Took $added x ${type.name}.")
    }

    /**
     * Settles the client's own menu close before this coroutine opens anything else.
     *
     * The client closes the menu itself after a selection and reports it with a `CloseModal`
     * packet, which the main process consumes _after_ the input phase - i.e. after the selection
     * already resumed this coroutine. That deferred close runs `Player.ifClose`, whose
     * `cancelActiveDialog` **aborts any coroutine suspended on a menu or input dialog** - so a
     * [countDialog] (or second [menu]) opened in the same cycle as the selection is killed the
     * moment the close processes, with no exception logged. (Verified in-client: the skill editor's
     * count dialog did nothing on submit, while the same flow passed in integration tests, which
     * never send `CloseModal`.)
     *
     * Two steps defuse it: close the menu server-side now so the mainmodal state already matches
     * the client, then park on a [delay] - a delay suspension is condition-based, not a dialog, so
     * the deferred `ifClose` cannot abort it, and the pending close is consumed against an
     * already-empty modal stack. The delay must span **two** cycles: coroutines resume at the
     * _start_ of a cycle, before the deferred close is processed, so a one-cycle delay would open
     * the next dialog in the very cycle whose main phase still aborts it.
     */
    private suspend fun ProtectedAccess.settleMenuClose() {
        ifClose()
        delay(2)
    }

    private suspend fun ProtectedAccess.skillEditor(category: DevHubCategory) {
        val stats = DevHubSkillGroups.forCategory(category, statTypes.values.toList()).orEmpty()
        if (stats.isEmpty()) {
            return
        }
        val statIndex =
            menu("Set which skill?", hotkeys = false, choices = stats.map { it.displayName })
        val stat = stats.getOrNull(statIndex) ?: return
        settleMenuClose()
        val minLevel = max(1, stat.minLevel)
        val level = countDialog("Set ${stat.displayName} to level ($minLevel-99):")
        if (level <= 0) {
            return
        }
        val target = level.coerceIn(minLevel, 99)
        player.setDevHubStatLevel(stat, target)
        mes("Set ${stat.displayName} to level $target.")
    }

    private fun String.selected(): String = "<col=ffffff>$this</col>"

    /**
     * Escapes `<`/`>` for the client's text renderer, which treats a literal `<` as an unterminated
     * tag and draws nothing. Single-pass on purpose: chained `replace` calls corrupt themselves
     * because the escape tokens contain the characters being escaped.
     */
    private fun String.escapeTags(): String = buildString {
        for (ch in this@escapeTags) {
            when (ch) {
                '<' -> append("<lt>")
                '>' -> append("<gt>")
                else -> append(ch)
            }
        }
    }

    private companion object {
        private const val STOCK_COUNT: Int = 100_000_000
        private const val TELEPORT_SCROLL_STEP: Int = 80
        private const val ITEM_SCROLL_STEP: Int = 80
        private const val ITEM_ROW_PITCH: Int = 32
        private const val ITEM_SLOT_COUNT: Int = GRID_COLUMNS * ITEM_GRID_ROWS
    }
}
