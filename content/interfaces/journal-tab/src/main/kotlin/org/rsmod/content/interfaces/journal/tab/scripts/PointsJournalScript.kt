package org.rsmod.content.interfaces.journal.tab.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.journal.tab.PlayerPointCategory
import org.rsmod.content.interfaces.journal.tab.PlayerPoints
import org.rsmod.content.interfaces.journal.tab.SideJournalTab
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePointsInterfaceBuilder.MAX_SCROLL
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePointsInterfaceBuilder.ROW_SLOTS
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePointsInterfaceBuilder.SCROLL_STEP
import org.rsmod.content.interfaces.journal.tab.configs.points_components
import org.rsmod.content.interfaces.journal.tab.configs.points_interfaces
import org.rsmod.content.interfaces.journal.tab.switchJournalTab
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The `::points` command and the `Points & Progress` journal page.
 *
 * `::points` is a plain player command - no mod level, no arguments, read-only. It selects the
 * Points sub-tab and focuses the journal side tab so the player lands directly on the page; the
 * same page is reachable by clicking the fifth journal tab row (the repurposed `league_list` slot).
 *
 * All balances come from [PlayerPoints], which reads each system's server-side source of truth
 * - the command never grants, spends or otherwise mutates points. The page renders into a fixed
 *   pool of hidden row slots, so any number of registry entries is handled without interface
 *   changes.
 */
class PointsJournalScript
@Inject
constructor(private val eventBus: EventBus, private val points: PlayerPoints) : PluginScript() {
    private val scrollPositions = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onCommand("points") {
            desc = "Show your Points"
            cheat(::showPoints)
        }

        onIfOpen(points_interfaces.unforge_points) {
            scrollPositions[player] = 0
            player.ifSetScrollPos(points_components.content, 0)
            // The client reports these clicks with `comsub >= 0`, which is only honoured when a
            // runtime event range covers the slot - the baked `events` mask alone is ignored.
            player.ifSetEvents(points_components.scrollUp, -1..-1, IfEvent.Op1)
            player.ifSetEvents(points_components.scrollDown, -1..-1, IfEvent.Op1)
            player.renderPoints()
        }
        onIfClose(points_interfaces.unforge_points) { scrollPositions.remove(player) }
        onIfOverlayButton(points_components.scrollUp) { player.scrollBy(-SCROLL_STEP) }
        onIfOverlayButton(points_components.scrollDown) { player.scrollBy(SCROLL_STEP) }
    }

    private fun showPoints(cheat: Cheat) {
        with(cheat) {
            // Select the Points sub-tab first so the journal lands on it, then focus the
            // journal side tab client-side. If the journal is already open this swaps the
            // visible page immediately; if not, `onIfOpen(side_journal)` picks the varbit up.
            player.switchJournalTab(SideJournalTab.Points, eventBus)
            ClientScripts.toplevelSidebuttonSwitch(player, constants.toplevel_details)
        }
    }

    private fun Player.scrollBy(delta: Int) {
        val pos = (scrollPositions[this] ?: 0) + delta
        val clamped = pos.coerceIn(0, MAX_SCROLL)
        scrollPositions[this] = clamped
        ifSetScrollPos(points_components.content, clamped)
    }

    private fun Player.renderPoints() {
        val entries = points.entries(this)
        var row = 0
        var category: PlayerPointCategory? = null
        for (entry in entries) {
            if (entry.category != category) {
                category = entry.category
                if (row < ROW_SLOTS) {
                    ifSetText(points_components.heads[row], category.label)
                    ifSetHide(points_components.heads[row], hide = false)
                }
                row++
            }
            if (row < ROW_SLOTS) {
                ifSetText(points_components.labels[row], entry.displayName)
                ifSetText(
                    points_components.values[row],
                    entry.valueLabel ?: if (entry.active) "${entry.value}" else "Inactive",
                )
                ifSetHide(points_components.labels[row], hide = false)
                ifSetHide(points_components.values[row], hide = false)
            }
            row++
        }
        for (unused in row until ROW_SLOTS) {
            ifSetHide(points_components.heads[unused], hide = true)
            ifSetHide(points_components.labels[unused], hide = true)
            ifSetHide(points_components.values[unused], hide = true)
        }
        ifSetText(points_components.totalValue, "${points.total(entries)}")
    }
}
