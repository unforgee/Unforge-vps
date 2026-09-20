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

class PrayerTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.prayer_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.PrayerTab.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.PrayerExit.varValue -> afterPrayer(npc)
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatPlayer(happy, "Good day, brother.")
        chatNpc(happy, "Hello, I'm Brother Brace. I'm here to tell you all about Prayer.")
        progression.setStep(player, TutorialStep.PrayerTab, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_prayer)
    }

    private suspend fun Dialogue.afterPrayer(npc: Npc) {
        chatNpc(
            happy,
            "This is your Prayer list. Prayers can help a lot in combat. Click on the prayer you wish to use to activate it, and click it again to deactivate it.",
        )
        chatNpc(
            happy,
            "Active prayers will drain your Prayer Points, which you can recharge by finding an altar or other holy spot and praying there.",
        )
        chatNpc(
            happy,
            "As you noticed, most enemies will drop bones when defeated. Burying bones, by clicking them in your inventory, will gain you Prayer experience.",
        )
        chatNpc(
            happy,
            "That's all I have to teach you for now. If you need a reminder about your prayers, just speak to me again.",
        )
        progression.setStep(player, TutorialStep.PrayerExit, npc)
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Peace be with you, my child. Can I help you?")
        val choice = choice2("Tell me about Prayers again.", 1, "Nope, I'm ready to move on!", 2)
        if (choice == 1) {
            chatNpc(
                happy,
                "Ah, yes, your prayer list. Prayers can help a lot in combat. Click on the prayer you wish to use to activate it, and click it again to deactivate it.",
            )
            chatNpc(
                happy,
                "Active prayers will drain your Prayer Points, which you can recharge by finding an altar or other holy spot and praying there.",
            )
            chatNpc(
                happy,
                "As you noticed, most enemies will drop bones when defeated. Burying bones, by clicking them in your inventory, will gain you Prayer experience.",
            )
            chatNpc(happy, "Do you need anything else?")
        } else {
            chatNpc(happy, "Okay then.")
        }
        progression.setStep(player, TutorialStep.PrayerExit, npc)
    }
}
