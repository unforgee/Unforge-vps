package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MasterChefScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.nav_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.ChefDough.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.ChefBread.varValue -> replenish(npc)
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatNpc(
            happy,
            "Ah! Welcome, newcomer. I am the Master Chef, Lev. It is here that I will teach you how to cook food truly fit for a king.",
        )
        chatPlayer(neutral, "I already know how to cook. Brynna taught me just now.")
        chatNpc(
            happy,
            "Hahahahahaha! You call THAT cooking? Some shrimp on an open log fire? Oh, no, no, no. I am going to teach you the fine art of cooking bread.",
        )
        giveIngredients()
        progression.setStep(player, TutorialStep.ChefDough, npc)
    }

    private suspend fun Dialogue.replenish(npc: Npc) {
        chatNpc(happy, "Time for you to learn the fine art of cooking bread.")
        giveIngredients()
        progression.setStep(player, TutorialStep.ChefDough, npc)
    }

    private fun Dialogue.giveIngredients() {
        if (access.invTotal(access.inv, tutorial_objs.pot_flour) <= 0) {
            access.invAdd(access.inv, tutorial_objs.pot_flour, 1)
        }
        if (access.invTotal(access.inv, tutorial_objs.bucket_water) <= 0) {
            access.invAdd(access.inv, tutorial_objs.bucket_water, 1)
        }
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Do you need something?")
        val choice =
            choice3(
                "Tell me about making dough again.",
                1,
                "Tell me about range cooking again.",
                2,
                "Nothing thanks.",
                3,
            )
        when (choice) {
            1 -> {
                chatNpc(
                    happy,
                    "It's quite simple. Just use a pot of flour on a bucket of water, or vice versa, and you'll make dough. You can fill a bucket with water at any sink.",
                )
            }
            2 -> {
                chatNpc(
                    happy,
                    "The range is the only place you can cook a lot of the more complex foods in Gielinor. To cook on a range, all you need to do is click on it. You'll need to make sure you have the required items in your inventory though.",
                )
            }
        }
        if (progression.current(player).varValue >= TutorialStep.ChefExit.varValue) {
            progression.setStep(player, TutorialStep.ChefExit, npc)
        }
    }
}
