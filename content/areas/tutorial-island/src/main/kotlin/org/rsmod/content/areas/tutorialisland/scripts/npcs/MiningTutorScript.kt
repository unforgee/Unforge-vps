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

class MiningTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.mining_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.MineOres.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.MineSmelt.varValue ->
                    chatNpc(
                        happy,
                        "The rocks around here contain tin and copper. You should try mining some.",
                    )
                step.varValue < TutorialStep.MineHammer.varValue -> afterOres(npc)
                step.varValue < TutorialStep.MineDagger.varValue -> giveHammer(npc)
                step.varValue < TutorialStep.MineGate.varValue ->
                    chatNpc(happy, "See if you can use the anvil here to make a bronze dagger.")
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatNpc(
            happy,
            "Hi there. You must be new around here. So what do I call you? 'Newcomer' seems so impersonal, and if we're going to be working together, I'd rather call you by name.",
        )
        chatPlayer(happy, "You can call me ${player.displayName}.")
        chatNpc(
            happy,
            "Ok then, ${player.displayName}. My name is Dezzick and I'm a miner by trade. Let's teach you how to mine.",
        )
        chatNpc(
            happy,
            "Mining is very simple, all you need is a pickaxe. The rocks around here contain tin and copper. Why don't you get started by mining some?",
        )
        if (access.invTotal(access.inv, tutorial_objs.bronze_pickaxe) <= 0) {
            access.invAdd(access.inv, tutorial_objs.bronze_pickaxe, 1)
        }
        progression.setStep(player, TutorialStep.MineOres, npc)
    }

    private suspend fun Dialogue.afterOres(npc: Npc) {
        chatNpc(
            happy,
            "Now that you have some ore, you should smelt it into a bronze bar. You can use the furnace over there to do so.",
        )
        progression.setStep(player, TutorialStep.MineSmelt, npc)
    }

    private suspend fun Dialogue.giveHammer(npc: Npc) {
        chatPlayer(quiz, "I have a bronze bar. What now?")
        chatNpc(
            happy,
            "Now that you've got a bar, you can smith it into a weapon. To smith something, you need a hammer and an anvil. There's some anvils just here that you can use. See if you can make a bronze dagger.",
        )
        if (access.invTotal(access.inv, tutorial_objs.hammer) <= 0) {
            if (access.inv.freeSpace() <= 0) {
                access.mes(
                    "You need some tools for this section of the tutorial but you don't have enough inventory room. You'll need to drop something. To do so, right-click on an item you don't want and select drop. Once you've done that, speak to the mining instructor again."
                )
                return
            }
            access.invAdd(access.inv, tutorial_objs.hammer, 1)
        }
        progression.setStep(player, TutorialStep.MineDagger, npc)
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        if (access.invTotal(access.inv, tutorial_objs.hammer) <= 0 && access.inv.freeSpace() > 0) {
            access.invAdd(access.inv, tutorial_objs.hammer, 1)
        }
        chatNpc(happy, "Would you like me to recap anything?")
        val choice =
            choice4(
                "Tell me about Mining again.",
                1,
                "Tell me about Smelting again.",
                2,
                "Tell me about Smithing again.",
                3,
                "Nope, I'm ready to move on!",
                4,
            )
        when (choice) {
            1 -> {
                chatNpc(
                    happy,
                    "Certainly. To mine you need a pickaxe. Different pickaxes let you mine more efficiently.",
                )
                chatNpc(
                    happy,
                    "Earlier I gave you a bronze pickaxe. It's the most inefficient pickaxe available, but it's perfect for a beginner.",
                )
                chatNpc(
                    happy,
                    "To mine, simply click on a rock that contains ore while you have a pickaxe with you, and you will keep mining the rock until you manage to get some ore, or until it is empty.",
                )
                chatNpc(
                    happy,
                    "The better the pickaxe you use, the faster you will get ore from the rock you're mining.",
                )
                chatNpc(
                    happy,
                    "You will be able to buy better pickaxes from the Dwarven Mine when you reach the mainland, but they can be expensive.",
                )
                chatNpc(
                    happy,
                    "Also, the better the pickaxe the higher the Mining level required to use it will be. Was there anything else you wanted to hear?",
                )
            }
            2 -> {
                chatNpc(
                    happy,
                    "Smelting is very easy. Simply take the ores required to make a metal to a furnace, then use the ores on the furnace to smelt them into a bar of metal.",
                )
                chatNpc(
                    happy,
                    "Furnaces are expensive to build and maintain, so there are not that many scattered around the world. I suggest when you find one you remember its location for future use.",
                )
                chatNpc(
                    happy,
                    "An alternative to using a furnace to smelt your ore is to use high-level magic to do it.",
                )
                chatNpc(
                    happy,
                    "As well as letting you smelt ore anywhere, it has a guaranteed success rate in smelting all ores.",
                )
                chatNpc(
                    happy,
                    "Some metals, such as iron, contain impurities and can be destroyed during the smelting process in a traditional furnace, but magical heat does not destroy them.",
                )
                chatNpc(happy, "Anything else?")
            }
            3 -> {
                chatNpc(
                    happy,
                    "When you have acquired enough bars of the metal you wish to work with, you are ready to begin smithing.",
                )
                chatNpc(
                    happy,
                    "Take the hammer I gave you, or buy a new one from a general store, and proceed to a nearby anvil.",
                )
                chatNpc(
                    happy,
                    "By using a metal bar on an anvil you will be presented with a screen showing the objects you are able to smith at your current level.",
                )
                chatNpc(
                    happy,
                    "It's a pretty straightforward skill as I'm sure you discovered while making me that lovely bronze dagger.",
                )
                chatNpc(
                    happy,
                    "The higher Smithing level you are, the better quality the metal you can work with. You start off on bronze and work your way up as your smithing skills increase.",
                )
                chatNpc(happy, "Anything else?")
            }
            4 -> chatNpc(happy, "Okay then.")
        }
        if (progression.current(player).varValue >= TutorialStep.MineGate.varValue) {
            progression.setStep(player, TutorialStep.MineGate, npc)
        }
    }
}
