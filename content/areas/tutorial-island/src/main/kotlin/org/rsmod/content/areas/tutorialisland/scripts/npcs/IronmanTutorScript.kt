package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_interfaces
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class IronmanTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.ironman_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) { dialogue(npc) }
    }

    private suspend fun Dialogue.dialogue(npc: Npc) {
        chatNpc(
            happy,
            "Hello, ${player.displayName}. I'm Paul, the Ironman tutor. What can I do for you?",
        )
        val choice =
            choice3(
                "Tell me about Ironmen.",
                1,
                "I'd like to change my Ironman mode.",
                2,
                "I'm fine, thanks.",
                3,
            )
        when (choice) {
            1 -> explainIronmen(npc)
            2 -> changeMode(npc)
            3 -> chatPlayer(neutral, "I'm fine, thanks.")
        }
        // Paul is optional; talking still nudges progress toward the Magic Instructor.
        if (progression.current(player).varValue < TutorialStep.MagicTalk.varValue) {
            progression.advance(player, TutorialStep.MagicTalk, npc)
        }
    }

    private suspend fun Dialogue.explainIronmen(npc: Npc) {
        chatPlayer(quiz, "Tell me about Ironmen.")
        chatNpc(
            happy,
            "When you play as an Ironman, you do everything for yourself. You don't trade with other players, or take their items, or accept their help.",
        )
        chatNpc(
            happy,
            "As an Ironman, you choose to have these restrictions imposed on you, so everyone knows you're doing it properly.",
        )
        chatNpc(
            happy,
            "If you think you have what it takes, you can choose to become a Hardcore Ironman.",
        )
        chatNpc(
            happy,
            "In addition to the standard restrictions, Hardcore Ironmen only have one life. In the event of a dangerous death, your Hardcore Ironman status will be downgraded to that of a standard Ironman, and your stats will be frozen on the Hardcore Ironman hiscores.",
        )
        chatNpc(
            happy,
            "If you have some friends, you could stand alone... Together, as Group Ironmen. Group Ironmen have the same restrictions as regular Ironmen except you can trade your items with each other!",
        )
        chatNpc(
            happy,
            "While you're on Tutorial Island, you can switch freely between being an Ironman, a Hardcore Ironman, a Group Ironman, or a regular character.",
        )
        chatNpc(
            happy,
            "Once you've left this island, you'll be able to find my colleague Adam in Lumbridge, but he'll only let you switch your restrictions downwards, not upwards.",
        )
        chatNpc(
            happy,
            "So he will let Hardcore Ironmen or Ultimate Ironmen downgrade to standard Ironmen, and he'll let all types of Ironman become normal players.",
        )
        chatNpc(
            happy,
            "I must warn you that Ironman mode is not recommended for new players. You can still give it a go if you think you're up for the challenge though.",
        )
        val followUp = choice2("I'd like to change my Ironman mode.", 1, "I'm fine, thanks.", 2)
        if (followUp == 1) {
            changeMode(npc)
        } else {
            chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    private suspend fun Dialogue.changeMode(npc: Npc) {
        chatPlayer(quiz, "I'd like to change my Ironman mode.")
        chatNpc(
            happy,
            "Make sure you've got your Ironman settings all sorted before you leave Tutorial Island. Some things can't be changed later.",
        )
        access.ifOpenOverlay(tutorial_interfaces.ironman_setup)
    }
}
