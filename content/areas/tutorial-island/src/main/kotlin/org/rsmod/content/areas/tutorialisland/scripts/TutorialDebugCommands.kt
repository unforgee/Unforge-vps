package org.rsmod.content.areas.tutorialisland.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::varp tutorial_2 <n>` writes the step value but nothing re-runs the tutorial's cues, so the
 * overlay keeps showing the previous step until the next login. This jumps the step through
 * [TutorialProgression] instead, which re-applies guide text, hint arrows and highlights.
 */
class TutorialDebugCommands @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tutstep") {
            modLevel = modlevels.admin
            desc = "Jump to a tutorial step"
            invalidArgs = "Use as ::tutstep stepNameOrValue (ex: SurvivalFm, or 11)"
            cheat { tutStep(this) }
        }
    }

    private fun tutStep(cheat: Cheat) =
        with(cheat) {
            val input = args[0]
            val step =
                TutorialStep.entries.firstOrNull { it.name.equals(input, ignoreCase = true) }
                    ?: input.toIntOrNull()?.let { value ->
                        TutorialStep.entries.firstOrNull { it.varValue == value }
                    }
            if (step == null) {
                player.mes("No tutorial step matches: '$input'")
                return
            }
            progression.setStep(player, step)
            player.mes("Tutorial step set to ${step.name} (${step.varValue}).")
        }
}
