package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Thin Learning the Ropes journal hooks.
 *
 * Full quest-list integration (Starter sort, journal objective strings, 1 QP varp) lands when the
 * shared quest framework exists. Completion toast fires on Wind Strike; Adventure Paths eligibility
 * is set during Past Experience selection.
 */
class LearningTheRopesJournal @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Completion is driven by TutorialCombatScript / TutorialProgression.completeQuest.
    }

    companion object {
        fun notifyComplete(player: Player) {
            player.mes("Congratulations, you've completed a quest: Learning the Ropes")
            // Award 1 quest point when the shared quest framework exposes a QP varp.
            // Adventure Paths flag is already set for brand-new players at Past Experience.
        }
    }
}
