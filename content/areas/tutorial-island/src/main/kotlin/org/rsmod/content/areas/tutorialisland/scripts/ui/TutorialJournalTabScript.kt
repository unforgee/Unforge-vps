package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Quest-list open advances are handled by [TutorialSidePanelScript] stone hooks to avoid exclusive
 * [onIfOpen] collisions with the journal-tab plugin.
 */
class TutorialJournalTabScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Intentionally empty — see TutorialSidePanelScript.
    }
}
