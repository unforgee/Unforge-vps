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

class CombatTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.combat_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.CombatWorn.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.CombatDagger.varValue ->
                    chatNpc(happy, "Let's get started by teaching you to wield a weapon.")
                step.varValue < TutorialStep.CombatGear.varValue -> afterDagger(npc)
                step.varValue < TutorialStep.CombatStyles.varValue -> equipGear(npc)
                step.varValue < TutorialStep.CombatMelee.varValue -> openCombat(npc)
                step.varValue < TutorialStep.CombatRange.varValue ->
                    chatNpc(
                        happy,
                        "Time for some actual combat! Head into the pen through the nearby gate and kill one of those rats.",
                    )
                step.varValue < TutorialStep.CombatLadder.varValue -> afterMelee(npc)
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatPlayer(happy, "Hi! My name's ${player.displayName}.")
        chatNpc(
            quiz,
            "Do I look like I care? To me you're just another newcomer who thinks they're ready to fight.",
        )
        chatNpc(
            happy,
            "I am Vannaka, the greatest swordsman alive, and I'm here to teach you the basics of combat.",
        )
        chatNpc(happy, "Let's get started by teaching you to wield a weapon.")
        progression.setStep(player, TutorialStep.CombatWorn, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_worn)
    }

    private suspend fun Dialogue.afterDagger(npc: Npc) {
        // Advance past worn/stats once they return — dagger equip is detected by presence in worn.
        if (access.invTotal(access.worn, tutorial_objs.bronze_dagger) > 0) {
            chatNpc(
                happy,
                "Very good, but that little butter knife isn't going to protect you much. Let's get you something a bit better. Once you're properly equipped we can move on to some actual combat.",
            )
            giveSwordAndShield()
            progression.setStep(player, TutorialStep.CombatGear, npc)
        } else {
            chatNpc(happy, "Let's get started by teaching you to wield a weapon.")
            progression.setStep(player, TutorialStep.CombatDagger, npc)
            toplevelSidebuttonSwitch(player, constants.toplevel_worn)
        }
    }

    private suspend fun Dialogue.equipGear(npc: Npc) {
        val hasSword =
            access.invTotal(access.inv, tutorial_objs.bronze_sword) > 0 ||
                access.invTotal(access.worn, tutorial_objs.bronze_sword) > 0
        val hasShield =
            access.invTotal(access.inv, tutorial_objs.wooden_shield) > 0 ||
                access.invTotal(access.worn, tutorial_objs.wooden_shield) > 0
        if (!hasSword || !hasShield) {
            chatNpc(happy, "Let's get you equipped.")
            giveSwordAndShield()
            return
        }
        if (
            access.invTotal(access.worn, tutorial_objs.bronze_sword) > 0 &&
                access.invTotal(access.worn, tutorial_objs.wooden_shield) > 0
        ) {
            chatNpc(happy, "You should check out the combat interface.")
            progression.setStep(player, TutorialStep.CombatStyles, npc)
            toplevelSidebuttonSwitch(player, constants.toplevel_combat)
        } else {
            chatNpc(happy, "Let's get you equipped.")
            giveSwordAndShield()
        }
    }

    private suspend fun Dialogue.openCombat(npc: Npc) {
        chatNpc(
            happy,
            "Time for some actual combat! Head into the pen through the nearby gate and kill one of those rats.",
        )
        progression.setStep(player, TutorialStep.CombatMelee, npc)
    }

    private suspend fun Dialogue.afterMelee(npc: Npc) {
        chatPlayer(happy, "I did it! I killed a giant rat!")
        chatNpc(
            happy,
            "I saw. You seem better at this than I thought. Now that you have grasped basic swordplay, let's move on.",
        )
        chatNpc(
            happy,
            "Next up we have ranged combat. With this you can kill foes from a distance. With ranged, you'll be able to attack the rats without entering the pit meaning they won't be able to fight back.",
        )
        giveBowAndArrows()
        progression.setStep(player, TutorialStep.CombatRange, npc)
    }

    private fun Dialogue.giveSwordAndShield() {
        val needSword =
            access.invTotal(access.inv, tutorial_objs.bronze_sword) <= 0 &&
                access.invTotal(access.worn, tutorial_objs.bronze_sword) <= 0
        val needShield =
            access.invTotal(access.inv, tutorial_objs.wooden_shield) <= 0 &&
                access.invTotal(access.worn, tutorial_objs.wooden_shield) <= 0
        val needed = (if (needSword) 1 else 0) + (if (needShield) 1 else 0)
        if (needed == 0) return
        if (access.inv.freeSpace() <= 0) {
            access.mes(
                "You need some new weapons for this section of the tutorial but you don't have enough inventory room. You'll need to drop something. To do so, right-click on an item you don't want and select drop. Once you've done that, speak to the combat instructor again."
            )
            return
        }
        if (needSword && access.inv.freeSpace() > 0) {
            access.invAdd(access.inv, tutorial_objs.bronze_sword, 1)
        }
        if (needShield && access.inv.freeSpace() > 0) {
            access.invAdd(access.inv, tutorial_objs.wooden_shield, 1)
        } else if (needShield) {
            access.mes(
                "Vannaka gives you a bronze sword. He would have given you a wooden shield as well but you didn't have room for it. To get one, drop something and talk to the combat instructor again."
            )
        }
    }

    private fun Dialogue.giveBowAndArrows() {
        val needBow =
            access.invTotal(access.inv, tutorial_objs.shortbow) <= 0 &&
                access.invTotal(access.worn, tutorial_objs.shortbow) <= 0
        val arrowCount = access.invTotal(access.inv, tutorial_objs.bronze_arrow)
        val needArrows = arrowCount < 50
        if (!needBow && !needArrows) return
        if (access.inv.freeSpace() <= 0 && needBow) {
            access.mes(
                "You need some new weapons for this section of the tutorial but you don't have enough inventory room. You'll need to drop something. To do so, right-click on an item you don't want and select drop. Once you've done that, speak to the combat instructor again."
            )
            return
        }
        if (needBow && access.inv.freeSpace() > 0) {
            access.invAdd(access.inv, tutorial_objs.shortbow, 1)
        }
        if (needArrows) {
            val toGive = 50 - arrowCount
            if (toGive > 0 && (access.inv.freeSpace() > 0 || arrowCount > 0)) {
                access.invAdd(access.inv, tutorial_objs.bronze_arrow, toGive)
            } else if (needBow) {
                access.mes(
                    "Vannaka gives you a shortbow. He would have given you some bronze arrows as well but you didn't have room for them. To get some, drop something and talk to the combat instructor again."
                )
            }
        }
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Do you need something?")
        val choice =
            choice5(
                "Tell me about combat stats.",
                1,
                "Tell me about melee combat again.",
                2,
                "Tell me about ranged combat again.",
                3,
                "Tell me about the Wilderness.",
                4,
                "Nope, I'm ready to move on!",
                5,
            )
        when (choice) {
            1 -> {
                chatNpc(
                    happy,
                    "Certainly. You have three main combat stats: Strength, Defence and Attack.",
                )
                chatNpc(
                    happy,
                    "Strength determines the maximum hit you will be able to deal with your blows, Defence determines the amount of damage you will be able to defend and Attack determines the accuracy of your blows.",
                )
                chatNpc(
                    happy,
                    "Other stats are used in combat such as Prayer, Hitpoints, Magic and Ranged. All of these stats can go towards determining your combat level, which is shown near the top of your combat interface screen.",
                )
                chatNpc(
                    happy,
                    "You will find out on the mainland that certain items can also affect your stats. There are potions that can be drunk that can alter your stats temporarily, such as raising Strength.",
                )
                chatNpc(
                    happy,
                    "You will also raise your Defence and Attack values by using different weapons and armours.",
                )
                chatNpc(
                    happy,
                    "Before going into combat with an opponent it is wise to put the mouse over them and see what combat level they are.",
                )
                chatNpc(
                    happy,
                    "Green coloured writing usually means it will be an easy fight for you, red means you will probably lose, yellow means they are around your level and orange means they are slightly stronger.",
                )
                chatNpc(
                    happy,
                    "Sometimes things will go your way, sometimes they won't. There is no such thing as a guaranteed win, but if the odds are on your side, you stand the best chance of walking away victorious.",
                )
                chatNpc(happy, "Now, was there something else you wanted to hear about again?")
            }
            2 -> {
                chatNpc(
                    happy,
                    "Ah, my speciality. Close combat fighting, which is also known as melee fighting, covers all combat done at close range to your opponent.",
                )
                chatNpc(
                    happy,
                    "Melee fighting depends entirely upon your three basic combat stats: Attack, Defence, and Strength.",
                )
                chatNpc(
                    happy,
                    "Also, of course, it depends on the quality of your armour and weaponry. A high-levelled fighter with good armour and weapons will be deadly up close.",
                )
                chatNpc(
                    happy,
                    "If this is the path you wish to take, remember your success depends on getting as close to your enemy as quickly as possible.",
                )
                chatNpc(
                    happy,
                    "Personally, I consider the fine art of melee combat to be the ONLY combat method worth using.",
                )
                chatNpc(happy, "Now, did you want to hear anything else?")
            }
            3 -> {
                chatNpc(
                    happy,
                    "Thinking of being a ranger, eh? Well, okay. I don't enjoy it myself, but I can see the appeal.",
                )
                chatNpc(
                    happy,
                    "Ranging employs a lot of different weapons as a skill, not just the shortbow you have there. Spears, throwing knives, and crossbows are all used best at a distance from your enemy.",
                )
                chatNpc(
                    happy,
                    "Now, those rats were easy pickings, but on the mainland you will be very lucky if you can find a spot where you can shoot at your enemies without them being able to retaliate.",
                )
                chatNpc(
                    happy,
                    "At close range, rangers often do badly in combat. Your best tactic as a ranger is to hit and run, keeping your foe at a distance.",
                )
                chatNpc(
                    happy,
                    "Your effectiveness as a ranger is almost entirely dependent on your Ranged stat. As with all skills, the more you train it, the more powerful it will become.",
                )
                chatNpc(happy, "Anything else you wanted to know?")
            }
            4 -> {
                chatNpc(
                    happy,
                    "Ah yes, the Wilderness. It is a place of evil, mark my words. Many is the colleague I have lost in that foul place.",
                )
                chatNpc(
                    happy,
                    "It is also a place of both adventure and wealth, so if you are brave enough and strong enough to survive it, you will make a killing. Literally!",
                )
                chatNpc(
                    happy,
                    "It is also the only place in the land of Gielinor where players are able to attack each other at will, and as such is the haunt of many Player Killers, or PKers if you will.",
                )
                chatNpc(
                    happy,
                    "There are a few things different in the Wilderness in comparison to the rest of the lands of Gielinor. Firstly, as I just mentioned, you can and will be attacked by other players.",
                )
                chatNpc(
                    happy,
                    "For this reason, you will be given a warning when you approach the Wilderness, as it is not a place you would wish to enter by accident.",
                )
                chatNpc(
                    happy,
                    "Secondly, there are a number of 'levels' to it. The further into it you travel, the greater the range of people you can attack.",
                )
                chatNpc(
                    happy,
                    "In level 1 wilderness you will only be able to attack, or be attacked by, those players within one combat level of yourself.",
                )
                chatNpc(
                    happy,
                    "In level 50, any player within fifty levels of you will be able to attack, or be attacked by you. Always keep an eye on what level of the Wilderness you are currently in.",
                )
                chatNpc(
                    happy,
                    "Your current Wilderness level is shown at the bottom right of the screen. The final thing you should know about the Wilderness is being 'skulled'.",
                )
                chatNpc(
                    happy,
                    "If you attack another player without them having attacked first, you will gain a skull above your character's head.",
                )
                chatNpc(
                    happy,
                    "What this means is that if you die while skulled, you will lose EVERYTHING that your character was carrying.",
                )
                chatNpc(
                    happy,
                    "When skulled, you should try to avoid dying for the twenty minutes or so it will take for the skull to wear off.",
                )
                chatNpc(
                    happy,
                    "If you wish to find the Wilderness, head north from where you start on the mainland. It is rather large and hard to miss.",
                )
                chatNpc(
                    happy,
                    "If you don't wish to end up there, take notice of the warning you will receive when getting near to it.",
                )
                chatNpc(happy, "Now, was there anything more?")
            }
            5 -> chatNpc(happy, "Okay then.")
        }
        if (progression.current(player).varValue >= TutorialStep.CombatLadder.varValue) {
            progression.setStep(player, TutorialStep.CombatLadder, npc)
        }
    }
}
