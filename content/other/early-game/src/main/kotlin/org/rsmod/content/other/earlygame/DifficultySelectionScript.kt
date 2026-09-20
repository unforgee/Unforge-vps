@file:OptIn(InternalApi::class)

package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.events.NpcHitEvents
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
import org.rsmod.game.difficulty.Difficulty
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * First-login difficulty (XP-rate tier) selection - the step between the game-mode gate and the
 * Starter Relic choice.
 *
 * Runs on [SessionStateEvent.EngineLoginReady] once `gameModeSelected` is satisfied: accounts with
 * `difficultySelected == false` get the `unforge_difficulty` modal ([DifficultyInterfaceBuilder]) -
 * a card per [Difficulty], an explicit Confirm button, and a status line. Confirming persists
 * through [DifficultyService.selectDifficulty], which copies the tier's XP rate into
 * [Player.xpRate].
 *
 * A repeating soft timer re-opens the modal if the player escapes it, so the choice cannot be
 * skipped. The same script applies the tier's outgoing-damage boost to every player-sourced hit on
 * an npc through [NpcHitEvents.GlobalModify].
 */
public class DifficultySelectionScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val difficultyService: DifficultyService,
    private val players: PlayerList,
) : PluginScript() {
    private val selections = ConcurrentHashMap<Player, Int>()
    private val saving = ConcurrentHashMap.newKeySet<Player>()

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> { player.onLoginReady() }
        onPlayerSoftTimer(earlygame_timers.difficulty_select) {
            if (player.gameModeSelected && !player.difficultySelected) {
                openSelection(player)
                player.softTimer(earlygame_timers.difficulty_select, RECHECK_CYCLES)
            }
        }
        onIfOpen(difficulty_interfaces.unforgeDifficulty) { player.renderSelection() }
        onIfClose(difficulty_interfaces.unforgeDifficulty) { selections.remove(player) }
        for ((index, hit) in difficulty_components.cardHits.withIndex()) {
            onIfModalButton(hit) { player.pickCard(index) }
        }
        onIfModalButton(difficulty_components.confirm) { confirmSelection() }
        onEvent<NpcHitEvents.GlobalModify> { applyDamageBoost(this) }
    }

    private fun Player.onLoginReady() {
        // Test players and unfinished sessions have no persisted character id.
        if (runCatching { characterId }.getOrNull() == null) {
            return
        }
        // The game-mode modal owns the very first login gate; difficulty selection opens right
        // after that choice is persisted and must complete before the relic modal.
        if (!gameModeSelected || difficultySelected) {
            return
        }
        mes("Welcome! Choose your Difficulty to set your XP rate and bonuses.")
        openSelection(this)
        softTimer(earlygame_timers.difficulty_select, RECHECK_CYCLES)
    }

    private fun openSelection(player: Player) {
        launcher.launchLenient(player) { ifOpenMainModal(difficulty_interfaces.unforgeDifficulty) }
    }

    private fun Player.renderSelection() {
        val selected = selections[this]
        for (index in difficulty_components.cardHits.indices) {
            ifSetEvents(difficulty_components.cardHits[index], -1..-1, IfEvent.Op1)
            ifSetHide(difficulty_components.cardSels[index], hide = index != selected)
        }
        ifSetEvents(difficulty_components.confirm, -1..-1, IfEvent.Op1)
        ifSetText(difficulty_components.confirmText, confirmLabel(selected))
        ifSetText(difficulty_components.detail, detailText(selected))
        ifSetText(difficulty_components.status, "")
    }

    private fun Player.pickCard(index: Int) {
        if (difficultySelected || index !in Difficulty.entries.indices) {
            return
        }
        selections[this] = index
        for (i in difficulty_components.cardSels.indices) {
            ifSetHide(difficulty_components.cardSels[i], hide = i != index)
        }
        ifSetText(difficulty_components.confirmText, confirmLabel(index))
        ifSetText(difficulty_components.detail, detailText(index))
        ifSetText(difficulty_components.status, "")
    }

    private suspend fun ProtectedAccess.confirmSelection() {
        if (player.difficultySelected) {
            ifClose()
            return
        }
        val index = selections[player]
        if (index == null) {
            player.ifSetText(difficulty_components.status, "Select a difficulty first.")
            return
        }
        val tier = Difficulty.entries.getOrNull(index) ?: return
        if (!saving.add(player)) {
            return
        }
        try {
            val save = difficultyService.selectDifficulty(player, tier)
            if (save == null) {
                player.ifSetText(
                    difficulty_components.status,
                    "Could not save - try again or reconnect.",
                )
                return
            }
            while (!save.isDone) {
                delay(1)
            }
            if (runCatching { save.get() == true }.getOrDefault(false)) {
                player.mes("Difficulty selected: ${tier.displayName}. This choice is permanent.")
                ifClose()
            } else {
                player.difficulty = null
                player.difficultySelected = false
                player.ifSetText(
                    difficulty_components.status,
                    "Save failed - try again or reconnect.",
                )
            }
        } finally {
            saving.remove(player)
        }
    }

    /** Applies the tier's outgoing-damage boost to player-sourced hits on npcs. */
    private fun applyDamageBoost(event: NpcHitEvents.GlobalModify) {
        val hit = event.hit
        if (!hit.isFromPlayer || hit.damage <= 0) {
            return
        }
        val player = hit.sourceSlot?.let(players::get) ?: return
        val boostBps = player.difficulty?.damageBoostBps ?: 0
        if (boostBps > 0) {
            hit.damage =
                (hit.damage.toLong() * (10_000L + boostBps) / 10_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
        }
    }

    private fun confirmLabel(index: Int?): String =
        if (index == null) {
            "Select a difficulty"
        } else {
            "Confirm ${Difficulty.entries[index].displayName}"
        }

    private fun detailText(index: Int?): String =
        if (index == null) {
            "Select a difficulty to see its rules."
        } else {
            Difficulty.entries[index].tagline
        }

    private companion object {
        private const val RECHECK_CYCLES = 10
    }
}
