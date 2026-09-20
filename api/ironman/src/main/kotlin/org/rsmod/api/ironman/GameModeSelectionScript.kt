package org.rsmod.api.ironman

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * First-login game mode selection.
 *
 * Runs on [SessionStateEvent.EngineLoginReady] (after the gameframe is open). Accounts with
 * `gameModeSelected == false` are held in a dialogue-only selection flow and gated out of every
 * gameplay path by [org.rsmod.game.ironman.IronmanPolicy] until the choice is persisted by
 * [GameModeService.selectGameMode].
 *
 * The flow uses the custom `unforge_gamemode` modal ([GameModeInterfaceBuilder]): a card per
 * [GameMode], an explicit Confirm button, and a status line for errors. The vanilla `ironman_setup`
 * interface is not usable here - its buttons are CS2-driven and never reach the server in this
 * flow.
 *
 * A repeating soft timer re-opens the selection if the player escapes the modal (walking cancels
 * the coroutine, the modal is closed, etc.) and re-arms itself on login, so a disconnect
 * mid-selection resumes the flow on the next login. Legacy accounts are migrated to
 * REGULAR+selected by the V19 migration and never see this flow.
 */
public class GameModeSelectionScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val gameModeService: GameModeService,
) : PluginScript() {
    private val selections = ConcurrentHashMap<Player, Int>()
    private val saving = ConcurrentHashMap.newKeySet<Player>()

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> { player.onLoginReady() }
        onPlayerSoftTimer(ironman_timers.game_mode_select) {
            if (!player.gameModeSelected) {
                openSelection(player)
                player.softTimer(ironman_timers.game_mode_select, RECHECK_CYCLES)
            }
        }
        onIfOpen(gamemode_interfaces.unforgeGamemode) { player.renderSelection() }
        onIfClose(gamemode_interfaces.unforgeGamemode) { selections.remove(player) }
        for ((index, hit) in gamemode_components.cardHits.withIndex()) {
            onIfModalButton(hit) { player.pickCard(index) }
        }
        onIfModalButton(gamemode_components.confirm) { confirmSelection() }
    }

    private fun Player.onLoginReady() {
        if (gameModeSelected) {
            return
        }
        val charId = runCatching { characterId }.getOrNull()
        if (charId == null) {
            gameModeSelected = true
            return
        }
        mes("Welcome! Choose your game mode to begin.")
        openSelection(this)
        softTimer(ironman_timers.game_mode_select, RECHECK_CYCLES)
    }

    private fun openSelection(player: Player) {
        launcher.launchLenient(player) { ifOpenMainModal(gamemode_interfaces.unforgeGamemode) }
    }

    /**
     * Fills the modal on open: card click registration (the wrap + holder pattern emits `comsub ==
     * 0`, so `-1..-1` covers every sub-slot the server may see), the current highlight and the
     * confirm/detail/status lines.
     */
    private fun Player.renderSelection() {
        val selected = selections[this]
        for (index in gamemode_components.cardHits.indices) {
            ifSetEvents(gamemode_components.cardHits[index], -1..-1, IfEvent.Op1)
            ifSetHide(gamemode_components.cardSels[index], hide = index != selected)
        }
        ifSetEvents(gamemode_components.confirm, -1..-1, IfEvent.Op1)
        ifSetText(gamemode_components.confirmText, confirmLabel(selected))
        ifSetText(gamemode_components.detail, detailText(selected))
        ifSetText(gamemode_components.status, "")
    }

    private fun Player.pickCard(index: Int) {
        if (gameModeSelected || index !in GameMode.entries.indices) {
            return
        }
        selections[this] = index
        for (i in gamemode_components.cardSels.indices) {
            ifSetHide(gamemode_components.cardSels[i], hide = i != index)
        }
        ifSetText(gamemode_components.confirmText, confirmLabel(index))
        ifSetText(gamemode_components.detail, detailText(index))
        ifSetText(gamemode_components.status, consequenceText(GameMode.entries[index]))
    }

    private suspend fun ProtectedAccess.confirmSelection() {
        if (player.gameModeSelected) {
            ifClose()
            return
        }
        val index = selections[player]
        if (index == null) {
            player.ifSetText(gamemode_components.status, "Select a game mode first.")
            return
        }
        val mode = GameMode.entries.getOrNull(index) ?: return
        if (!saving.add(player)) {
            return
        }
        try {
            val save = gameModeService.selectGameMode(player, mode)
            if (save == null) {
                player.ifSetText(
                    gamemode_components.status,
                    "Could not save - try again or reconnect.",
                )
                return
            }
            while (!save.isDone) {
                delay(1)
            }
            if (runCatching { save.get() == true }.getOrDefault(false)) {
                player.mes("Game mode selected: ${mode.displayName}. This choice is permanent.")
                ifClose()
            } else {
                player.gameMode = null
                player.gameModeSelected = false
                player.ifSetText(
                    gamemode_components.status,
                    "Save failed - try again or reconnect.",
                )
            }
        } finally {
            saving.remove(player)
        }
    }

    private fun confirmLabel(index: Int?): String =
        if (index == null) {
            "Select a mode"
        } else {
            "Confirm ${GameMode.entries[index].displayName}"
        }

    private fun detailText(index: Int?): String =
        if (index == null) {
            "Select a game mode to see its rules."
        } else {
            val mode = GameMode.entries[index]
            "${mode.description} ${mode.restrictions}"
        }

    private fun consequenceText(mode: GameMode): String =
        if (mode.hardcoreConsequence == "None.") "" else mode.hardcoreConsequence

    private companion object {
        private const val RECHECK_CYCLES = 10
    }
}
