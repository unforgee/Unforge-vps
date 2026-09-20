package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Skippy — rare anti-bot / skip NPC in the start house (feature-flag style).
 *
 * Not always present in spawns; when talked to, offers a full skip to the leave path.
 */
class SkippyTutorScript
@Inject
constructor(private val progression: TutorialProgression, private val leave: TutorialLeave) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Cache NPC `skippy` exists; only wire if spawned (League/Deadman / rare).
        onOpNpc1(tutorial_npcs.skippy) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (!progression.isActive(player)) {
            startDialogue(npc) { chatNpc(drunk, "Hic! Go away... I'm busy.") }
            return
        }
        startDialogue(npc) { skipOffer(npc) }
    }

    private suspend fun Dialogue.skipOffer(npc: Npc) {
        chatNpc(
            drunk,
            "Hey... you look like you're in a hurry. Want me to skip you through this place?",
        )
        val choice =
            choice2(
                "Yes, skip Tutorial Island.",
                1,
                "No thanks.",
                2,
                title = "Skip Tutorial Island?",
            )
        if (choice != 1) {
            chatPlayer(happy, "No thanks.")
            return
        }
        chatNpc(drunk, "Alright then... off you go!")
        progression.setStep(player, TutorialStep.HomeTele, npc)
        leave.giveLeaveKit(access)
    }
}
