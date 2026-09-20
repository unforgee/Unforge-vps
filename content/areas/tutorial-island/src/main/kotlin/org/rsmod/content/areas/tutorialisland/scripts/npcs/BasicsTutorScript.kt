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

class BasicsTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.basics_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.GielinorSettings.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.GielinorDoor.varValue -> afterSettings(npc)
                else -> welcomeBack(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatNpc(
            happy,
            "Greetings! I see you are a new arrival to the world of Gielinor. My job is to welcome all new visitors. So welcome!",
        )
        chatNpc(
            happy,
            "You have already learned the first thing needed to succeed in this world: talking to other people!",
        )
        chatNpc(
            happy,
            "You will find many inhabitants of this world have useful things to say to you. By clicking on them you can talk to them.",
        )
        chatNpc(happy, "Now then, let's start by looking at your settings menu.")
        progression.setStep(player, TutorialStep.GielinorSettings, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_settings)
    }

    private suspend fun Dialogue.afterSettings(npc: Npc) {
        chatNpc(
            happy,
            "Looks like you're making good progress! The menu you've just opened is one of many. You'll learn about the rest as you progress through the tutorial.",
        )
        chatNpc(happy, "Anyway, I'd say it's time for you to go and meet your first instructor!")
        progression.setStep(player, TutorialStep.GielinorDoor, npc)
    }

    private suspend fun Dialogue.welcomeBack(npc: Npc) {
        chatNpc(
            happy,
            "Welcome back. You have already learnt the first thing needed to succeed in this world: talking to other people!",
        )
        chatNpc(
            happy,
            "You will find many inhabitants of this world have useful things to say to you. By clicking on them, you can talk to them.",
        )
        chatNpc(
            happy,
            "To continue the tutorial go through that door over there and speak to your first instructor!",
        )
        progression.setStep(player, TutorialStep.GielinorDoor, npc)
    }
}
