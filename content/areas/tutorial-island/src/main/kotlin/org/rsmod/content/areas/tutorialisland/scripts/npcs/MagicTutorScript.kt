package org.rsmod.content.areas.tutorialisland.scripts.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts.toplevelSidebuttonSwitch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.progression.TutorialLeave
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MagicTutorScript
@Inject
constructor(private val progression: TutorialProgression, private val leave: TutorialLeave) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.magic_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.MagicTab.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.MagicRunes.varValue -> giveRunes(npc)
                step.varValue < TutorialStep.LeaveTalk.varValue -> waitingForCast(npc)
                else -> leaveTalk(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatPlayer(happy, "Hello.")
        chatNpc(
            happy,
            "Good day, newcomer. My name is Terrova. I'm here to tell you about Magic. Let's start by opening your magic interface.",
        )
        progression.setStep(player, TutorialStep.MagicTab, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_magic)
    }

    private suspend fun Dialogue.giveRunes(npc: Npc) {
        chatNpc(
            happy,
            "Currently you can only cast one offensive spell called Wind Strike. Let's try it out on one of those chickens.",
        )
        replenishRunes()
        progression.setStep(player, TutorialStep.MagicRunes, npc)
    }

    private suspend fun Dialogue.waitingForCast(npc: Npc) {
        val air = access.invTotal(access.inv, tutorial_objs.air_rune)
        val mind = access.invTotal(access.inv, tutorial_objs.mind_rune)
        if (air >= 5 && mind >= 5) {
            chatNpc(
                happy,
                "Sorry, I don't have any more runes just now. But I see you have plenty.",
            )
        } else {
            replenishRunes()
        }
    }

    private fun Dialogue.replenishRunes() {
        val air = access.invTotal(access.inv, tutorial_objs.air_rune)
        val mind = access.invTotal(access.inv, tutorial_objs.mind_rune)
        if (air < 5) {
            access.invAdd(access.inv, tutorial_objs.air_rune, 5 - air)
        }
        if (mind < 5) {
            access.invAdd(access.inv, tutorial_objs.mind_rune, 5 - mind)
        }
    }

    private suspend fun Dialogue.leaveTalk(npc: Npc) {
        chatNpc(
            happy,
            "Well, you're all finished here now. I'll give you a reasonable number of runes when you leave.",
        )
        val go = choice2("Yes.", 1, "No.", 2, title = "Do you want to go to the mainland?")
        if (go != 1) return

        // Brand-new Adventurer Jon path (default for new tutorial accounts).
        val path =
            choice3(
                "I'm brand new to this.",
                1,
                "Tell me about Ironman first.",
                2,
                "I'm ready — home teleport me.",
                3,
                title = "Before you leave...",
            )
        when (path) {
            1 -> brandNewPath(npc)
            2 -> ironmanRedirect(npc)
            3 -> homeTeleportInstruction(npc)
        }
    }

    private suspend fun Dialogue.brandNewPath(npc: Npc) {
        chatNpc(
            happy,
            "When you get to the mainland you will find yourself in the town of Lumbridge. If you want some ideas on where to go next, talk to my friend Adventurer Jon. You can't miss him; he's wearing light blue armour with a huge sword. There are also many tutors willing to teach you about the many skills you could learn.",
        )
        // Transcript gap: Adventurer Jon intro → Home Teleport line.
        homeTeleportInstruction(npc)
    }

    private suspend fun Dialogue.ironmanRedirect(npc: Npc) {
        chatNpc(
            happy,
            "If you're interested in that, you should go and ask the Ironman tutor before you leave this island. He's standing outside my house.",
        )
        chatPlayer(happy, "Thanks, I'd better go and find him.")
    }

    private suspend fun Dialogue.homeTeleportInstruction(npc: Npc) {
        chatNpc(
            happy,
            "Excellent, I think that covers everything. When you're ready to go, use your home teleport spell to teleport to Lumbridge!",
        )
        val confirm = choice2("Teleport me now.", 1, "I'll cast it myself.", 2)
        if (confirm == 1) {
            leave.giveLeaveKit(access)
        } else {
            progression.setStep(player, TutorialStep.HomeTele, npc)
            toplevelSidebuttonSwitch(player, constants.toplevel_magic)
        }
    }
}
