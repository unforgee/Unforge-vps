package org.rsmod.content.interfaces.journal.tab.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.Perk
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePerksInterfaceBuilder.MAX_SCROLL
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePerksInterfaceBuilder.SCROLL_STEP
import org.rsmod.content.interfaces.journal.tab.configs.UnforgePerksInterfaceBuilder.SEGMENTS_PER_PERK
import org.rsmod.content.interfaces.journal.tab.configs.perks_components
import org.rsmod.content.interfaces.journal.tab.configs.perks_interfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Populates the Perks journal page and handles the per-perk upgrade buttons. Each upgrade click
 * asks for confirmation before buying one next rank via [PerkService.train].
 *
 * The perk list lives in a scrollable content layer: the client wheel-scrolls it natively and the
 * ▲/▼ buttons step the position server-side via `IfSetScrollPos`. The tracked position is
 * ephemeral - it resets to the top whenever the page opens.
 */
class PerkJournalScript
@Inject
constructor(private val perks: PerkService, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private val scrollPositions = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onIfOpen(perks_interfaces.unforge_perks) {
            scrollPositions[player] = 0
            player.ifSetScrollPos(perks_components.content, 0)
            // These clicks arrive with `comsub >= 0`, which the server only honours when a runtime
            // event range covers the slot - the baked `events` mask alone is ignored.
            player.ifSetEvents(perks_components.scrollUp, -1..-1, IfEvent.Op1)
            player.ifSetEvents(perks_components.scrollDown, -1..-1, IfEvent.Op1)
            for (button in perks_components.trainButtons) {
                player.ifSetEvents(button, -1..-1, IfEvent.Op1)
            }
            player.refreshPerks()
        }
        onIfClose(perks_interfaces.unforge_perks) { scrollPositions.remove(player) }
        onIfOverlayButton(perks_components.scrollUp) { player.scrollBy(-SCROLL_STEP) }
        onIfOverlayButton(perks_components.scrollDown) { player.scrollBy(SCROLL_STEP) }
        for ((index, button) in perks_components.trainButtons.withIndex()) {
            onIfOverlayButton(button) { player.confirmTrainPerk(Perk.entries[index]) }
        }
    }

    private fun Player.scrollBy(delta: Int) {
        val pos = (scrollPositions[this] ?: 0) + delta
        val clamped = pos.coerceIn(0, MAX_SCROLL)
        scrollPositions[this] = clamped
        ifSetScrollPos(perks_components.content, clamped)
    }

    private fun Player.confirmTrainPerk(perk: Perk) {
        val opened = protectedAccess.launch(this) { confirmTrainPerk(perk) }
        if (!opened) {
            mes("Please finish your current action before upgrading a perk.")
        }
    }

    private suspend fun ProtectedAccess.confirmTrainPerk(perk: Perk) {
        when {
            perks.level(player, perk) >= Perk.MAX_PERK_RANK ->
                mes("<col=ffb84d>${perk.displayName} is already at max level.</col>")
            perks.pointsLong(player) < perks.nextRankCost(player, perk) ->
                mes("<col=ffb84d>You need perk points - kill bosses to earn them.</col>")
            else -> {
                val cost = perks.nextRankCost(player, perk)
                val accepted =
                    choice2(
                        "Accept",
                        true,
                        "Cancel",
                        false,
                        title = "Upgrade ${perk.displayName} for $cost Perk Points?",
                    )
                if (!accepted) {
                    player.mes("Perk upgrade cancelled.")
                    return
                }

                // Re-check after the chatbox suspension so a stale confirmation can never spend
                // a point on a perk that became unavailable while the player was deciding.
                when {
                    perks.level(player, perk) >= Perk.MAX_PERK_RANK ->
                        player.mes("<col=ffb84d>${perk.displayName} is already at max level.</col>")
                    perks.pointsLong(player) < perks.nextRankCost(player, perk) ->
                        player.mes(
                            "<col=ffb84d>You need perk points - kill bosses to earn them.</col>"
                        )
                    perks.train(player, perk) > 0L ->
                        player.mes(
                            "<col=ffb84d>Upgraded ${perk.displayName} to rank " +
                                "${perks.level(player, perk)}.</col>"
                        )
                }
            }
        }
        player.refreshPerks()
    }

    private fun Player.refreshPerks() {
        ifSetText(perks_components.points, "${perks.pointsLong(this)}")
        for ((index, perk) in Perk.entries.withIndex()) {
            val level = perks.level(this, perk)
            ifSetText(perks_components.levels[index], "$level/${Perk.MAX_PERK_RANK}")
            ifSetText(
                perks_components.descs[index],
                "${perk.description}  Next: ${perks.displayCost(perks.nextRankCost(this, perk))}",
            )
            ifSetHide(perks_components.trainWraps[index], hide = level >= Perk.MAX_PERK_RANK)
            val filledPips =
                ((level * SEGMENTS_PER_PERK) + Perk.MAX_PERK_RANK - 1) / Perk.MAX_PERK_RANK
            for (seg in 0 until SEGMENTS_PER_PERK) {
                ifSetHide(perks_components.segments[index][seg], hide = seg >= filledPips)
            }
        }
    }
}
