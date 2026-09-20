package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Lumbridge Home Teleport + Wind Strike spellbook clicks. */
class TutorialMagicUiScript
@Inject
constructor(
    private val realm: Realm,
    private val progression: TutorialProgression,
    private val leave: TutorialLeave,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(tutorial_components.spell_home_teleport) {
            protectedAccess.launch(player) { homeTeleport() }
        }
        onIfOverlayButton(tutorial_components.spell_wind_strike) { selectWindStrike(player) }
    }

    private fun ProtectedAccess.homeTeleport() {
        // Outside the tutorial the home teleport returns the player to their realm spawn ("home").
        if (!progression.isActive(player)) {
            teleport(realm.config.spawnCoord)
            return
        }
        val step = progression.current(player)
        when {
            step.varValue < TutorialStep.LeaveTalk.varValue ->
                progression.deny(
                    player,
                    "You can't cast Lumbridge Home Teleport until you've finished talking to the Magic Instructor.",
                )
            step == TutorialStep.HomeTele || step == TutorialStep.LeaveTalk ->
                leave.giveLeaveKit(this)
            else ->
                progression.deny(player, "Speak to the Magic Instructor before leaving the island.")
        }
    }

    private fun selectWindStrike(player: Player) {
        if (!progression.isActive(player)) return
        val step = progression.current(player)
        if (step == TutorialStep.MagicRunes || step == TutorialStep.MagicCast) {
            progression.setStep(player, TutorialStep.MagicCast)
            player.mes("Click a chicken to cast Wind Strike.")
        }
    }
}
