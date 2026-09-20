package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts.toplevelSidebuttonSwitch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SurvivalTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.survival_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.SurvivalInv.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.SurvivalFish.varValue ->
                    chatNpc(
                        happy,
                        "Hello again. Open your inventory — the backpack icon — so you can see the net I gave you.",
                    )
                step.varValue < TutorialStep.SurvivalSkills.varValue ->
                    chatNpc(
                        happy,
                        "Hello again. First up, we're going to do some fishing. There's some shrimp in this pond here. Let's try and catch some.",
                    )
                step.varValue < TutorialStep.SurvivalTools.varValue ->
                    chatNpc(
                        happy,
                        "Hello again. You should take a look at that menu before we continue.",
                    )
                step.varValue < TutorialStep.SurvivalWc.varValue -> afterSkills(npc)
                step.varValue < TutorialStep.SurvivalFm.varValue ->
                    chatNpc(
                        happy,
                        "Next up you need to make a fire. First, you'll need to cut down a tree to get some logs.",
                    )
                step.varValue < TutorialStep.SurvivalCook.varValue ->
                    chatNpc(
                        happy,
                        "Now that you have some shrimp, you're going to want to cook them. To do that, you'll need a fire.",
                    )
                step.varValue < TutorialStep.SurvivalGate.varValue ->
                    chatNpc(
                        happy,
                        "Now that you have some shrimp, you're going to want to cook them. All you need to do is use them on a fire.",
                    )
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatNpc(
            happy,
            "Hello there, newcomer. My name is Brynna. My job is to teach you about the skills you can use to survive in this world.",
        )
        chatNpc(
            happy,
            "The first skill we're going to look at is Fishing. There's some shrimp in this pond here. Let's try and catch some.",
        )
        access.invAdd(access.inv, tutorial_objs.net, 1)
        progression.setStep(player, TutorialStep.SurvivalInv, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_inventory)
    }

    private suspend fun Dialogue.afterSkills(npc: Npc) {
        chatPlayer(happy, "I've managed to catch some shrimp.")
        chatNpc(
            happy,
            "Excellent work. Next you'll want to cook those shrimp. To do that, you'll need a fire. This brings us on to the Woodcutting and Firemaking skills.",
        )
        access.invAdd(access.inv, tutorial_objs.bronze_axe, 1)
        access.invAdd(access.inv, tutorial_objs.tinderbox, 1)
        progression.setStep(player, TutorialStep.SurvivalWc, npc)
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Hello again. Is there something you'd like to hear more about?")
        val choice =
            choice5(
                "Tell me about my skills again.",
                1,
                "Tell me about Woodcutting again.",
                2,
                "Tell me about Firemaking again.",
                3,
                "Tell me about Fishing again.",
                4,
                "Tell me about Cooking again.",
                5,
            )
        when (choice) {
            1 -> {
                chatNpc(
                    happy,
                    "Every skill is listed in the skills menu. Here you can see what your current levels are and how much experience you have.",
                )
                chatNpc(
                    happy,
                    "As you move your mouse over the various skills the small tooltip will show you the exact amount of experience you have and how much is needed to get to the next level.",
                )
                chatNpc(
                    happy,
                    "You can also click on a skill to open up the relevant skillguide. In the skillguide, you can see all of the unlocks available in that skill.",
                )
            }
            2 -> {
                chatNpc(
                    happy,
                    "Woodcutting, eh? Don't worry, newcomer, it's really very easy. Simply equip your axe and click on a nearby tree to chop away.",
                )
                chatNpc(
                    happy,
                    "As you explore the mainland you will discover many different kinds of trees that will require different Woodcutting levels to chop down.",
                )
                chatNpc(
                    happy,
                    "Logs are not only useful for making fires. Many archers use the skill known as Fletching to craft their own bows and arrows from trees.",
                )
            }
            3 -> {
                chatNpc(
                    happy,
                    "Certainly, newcomer. When you have logs simply use your tinderbox on them. If successful, you will start a fire.",
                )
                chatNpc(
                    happy,
                    "You can also set fire to logs you find lying on the floor already, and some other things can also be set alight...",
                )
                chatNpc(happy, "A tinderbox is always a useful item to keep around!")
            }
            4 -> {
                chatNpc(
                    happy,
                    "Ah, yes. Fishing! Fishing is undoubtedly one of the more popular hobbies here in Gielinor!",
                )
                chatNpc(
                    happy,
                    "Whenever you see sparkling waters, you can be sure there's probably some good fishing to be had there!",
                )
                chatNpc(
                    happy,
                    "Not only are fish absolutely delicious when cooked, they will also heal lost health.",
                )
                chatNpc(
                    happy,
                    "I would recommend everybody has a go at Fishing at least once in their lives!",
                )
            }
            5 -> {
                chatNpc(
                    happy,
                    "Yes, the most basic of survival techniques. Most simple foods can be used on a fire to cook them. If you're feeling a bit fancier, you can also use a range to cook the food instead.",
                )
                chatNpc(
                    happy,
                    "Eating cooked food will restore lost health. The harder something is to cook, the more it will heal you.",
                )
            }
        }
        if (progression.current(player).varValue >= TutorialStep.SurvivalGate.varValue) {
            progression.setStep(player, TutorialStep.SurvivalGate, npc)
        }
    }
}
