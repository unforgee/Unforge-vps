package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts.toplevelSidebuttonSwitch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuestTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.quest_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.QuestList.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.QuestLadder.varValue -> afterJournal(npc)
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatNpc(
            happy,
            "Ah. Welcome, adventurer. I'm here to tell you all about quests. Let's start by opening your quest list.",
        )
        progression.setStep(player, TutorialStep.QuestList, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_details)
    }

    private suspend fun Dialogue.afterJournal(npc: Npc) {
        chatNpc(
            happy,
            "Now that you have the quest list open, you can see all the quests within it. Opening a quest's journal will provide you with more information on it.",
        )
        chatNpc(
            happy,
            "If you haven't started the quest, it will tell you where to begin and what requirements you need. If the quest is in progress, it will remind you what to do next.",
        )
        chatNpc(
            happy,
            "It's very easy to find quest start points. Just look out for the quest icon on your minimap. You should see one marking this house.",
        )
        chatNpc(
            happy,
            "The quests themselves can vary greatly from collecting beads to hunting down dragons. Completing quests will reward you with all sorts of things, such as new areas and better weapons!",
        )
        chatNpc(
            happy,
            "There's not a lot more I can tell you about questing. You have to experience the thrill of it yourself to fully understand. Let me know if you want a recap, otherwise you can move on.",
        )
        progression.setStep(player, TutorialStep.QuestLadder, npc)
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Would you like to hear about quests again?")
        val choice = choice2("Yes!", 1, "Nope, I'm ready to move on!", 2)
        if (choice == 1) {
            chatNpc(
                happy,
                "Within your quest list, you'll unsurprisingly find a list of quests. Opening a quest's journal will provide you with more information on it.",
            )
            chatNpc(
                happy,
                "If you haven't started the quest, it will tell you where to begin and what requirements you need. If the quest is in progress, it will remind you what to do next.",
            )
        } else {
            chatNpc(happy, "Okay then.")
        }
        progression.setStep(player, TutorialStep.QuestLadder, npc)
    }
}
