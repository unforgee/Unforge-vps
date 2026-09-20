package org.rsmod.content.areas.tutorialisland.scripts.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Optional ambient interactions: chapel organ + empty containers. */
class TutorialAmbientScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(tutorial_locs.churchorgan) { playOrgan() }
        onOpLoc1(tutorial_locs.drawers1) { emptyContainer("drawers") }
        onOpLoc1(tutorial_locs.drawers2) { emptyContainer("drawers") }
        onOpLoc1(tutorial_locs.drawers3) { emptyContainer("drawers") }
        onOpLoc1(tutorial_locs.cupboard) { emptyContainer("cupboard") }
    }

    private fun ProtectedAccess.playOrgan() {
        if (!progression.isActive(player)) {
            mes("You play the organ.")
            return
        }
        mes("You play an organ. It makes a pleasing sound.")
    }

    private fun ProtectedAccess.emptyContainer(kind: String) {
        if (!progression.isActive(player)) return
        mes("The $kind is empty.")
    }
}
