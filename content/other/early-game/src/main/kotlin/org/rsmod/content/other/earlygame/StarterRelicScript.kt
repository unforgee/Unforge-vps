@file:OptIn(InternalApi::class)

package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.annotations.InternalApi
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
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object earlygame_timers : TimerReferences() {
    val starter_relic_select = find("starter_relic_select")
    val difficulty_select = find("difficulty_select")
}

/**
 * First-login Starter Relic selection - the first Adventure Path step.
 *
 * Runs on [SessionStateEvent.EngineLoginReady] once the game-mode gate is satisfied: accounts with
 * `relicSelected == false` get the `starter_relic` modal ([StarterRelicInterfaceBuilder]) - a card
 * per [StarterRelic], an explicit Confirm button, and a status line. Choosing a relic completes
 * [AdventureMilestone.CHOOSE_RELIC] through [EarlyGameProgressionService.changeRelic].
 *
 * A repeating soft timer re-opens the modal if the player escapes it (walking closes the modal,
 * disconnect mid-selection, etc.), so the choice cannot be skipped. Legacy accounts keep the
 * `::relic` command which now opens the same modal instead of parsing an argument.
 */
public class StarterRelicScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val progression: EarlyGameProgressionService,
) : PluginScript() {
    private val selections = ConcurrentHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> { player.onLoginReady() }
        onPlayerSoftTimer(earlygame_timers.starter_relic_select) {
            if (
                player.gameModeSelected &&
                    player.difficultySelected &&
                    !progression.ensure(player).relicSelected
            ) {
                openSelection(player)
                player.softTimer(earlygame_timers.starter_relic_select, RECHECK_CYCLES)
            }
        }
        onIfOpen(starterrelic_interfaces.starterRelic) { player.renderSelection() }
        onIfClose(starterrelic_interfaces.starterRelic) { selections.remove(player) }
        for ((index, hit) in starterrelic_components.cardHits.withIndex()) {
            onIfModalButton(hit) { player.pickCard(index) }
        }
        onIfModalButton(starterrelic_components.confirm) { confirmSelection() }
    }

    private fun Player.onLoginReady() {
        // Test players and unfinished sessions have no persisted character id - the progression
        // state is keyed on it, so there is nothing to select against.
        if (runCatching { characterId }.getOrNull() == null) {
            return
        }
        // The game-mode and difficulty modals own the first login gates; relic selection is
        // step one of the Adventure Path and opens only after both choices are persisted.
        if (!gameModeSelected || !difficultySelected) {
            softTimer(earlygame_timers.starter_relic_select, RECHECK_CYCLES)
            return
        }
        if (progression.ensure(this).relicSelected) {
            return
        }
        mes("Welcome! Choose your Starter Relic to begin your Adventure Path.")
        openSelection(this)
        softTimer(earlygame_timers.starter_relic_select, RECHECK_CYCLES)
    }

    private fun openSelection(player: Player) {
        launcher.launchLenient(player) { ifOpenMainModal(starterrelic_interfaces.starterRelic) }
    }

    /**
     * Fills the modal on open: card click registration (the wrap + holder pattern emits `comsub ==
     * 0`, so `-1..-1` covers every sub-slot the server may see), the current highlight and the
     * confirm/detail/status lines.
     */
    private fun Player.renderSelection() {
        val selected =
            selections[this]
                ?: StarterRelic.entries.indexOf(progression.ensure(this).starterRelic).takeIf {
                    it >= 0
                }
        for (index in starterrelic_components.cardHits.indices) {
            ifSetEvents(starterrelic_components.cardHits[index], -1..-1, IfEvent.Op1)
            ifSetHide(starterrelic_components.cardSels[index], hide = index != selected)
        }
        ifSetEvents(starterrelic_components.confirm, -1..-1, IfEvent.Op1)
        ifSetText(starterrelic_components.confirmText, confirmLabel(selected))
        ifSetText(starterrelic_components.detail, detailText(selected))
        ifSetText(starterrelic_components.status, "")
    }

    private fun Player.pickCard(index: Int) {
        if (index !in StarterRelic.entries.indices) {
            return
        }
        selections[this] = index
        for (i in starterrelic_components.cardSels.indices) {
            ifSetHide(starterrelic_components.cardSels[i], hide = i != index)
        }
        ifSetText(starterrelic_components.confirmText, confirmLabel(index))
        ifSetText(starterrelic_components.detail, detailText(index))
        ifSetText(starterrelic_components.status, "")
    }

    private suspend fun ProtectedAccess.confirmSelection() {
        val index = selections[player]
        if (index == null) {
            player.ifSetText(starterrelic_components.status, "Select a relic first.")
            return
        }
        val relic = StarterRelic.entries.getOrNull(index) ?: return
        if (progression.changeRelic(player, relic)) {
            ifClose()
        } else {
            // Same relic re-picked - nothing to do, just close.
            if (progression.ensure(player).starterRelic == relic) {
                ifClose()
            }
        }
    }

    private fun confirmLabel(index: Int?): String =
        if (index == null) {
            "Select a relic"
        } else {
            "Confirm ${StarterRelic.entries[index].displayName}"
        }

    private fun detailText(index: Int?): String =
        if (index == null) {
            "Select a relic to see what it unlocks."
        } else {
            val relic = StarterRelic.entries[index]
            "${relic.displayName}: recommended Companion - ${relic.recommendedCompanion}."
        }

    private companion object {
        private const val RECHECK_CYCLES = 10
    }
}
