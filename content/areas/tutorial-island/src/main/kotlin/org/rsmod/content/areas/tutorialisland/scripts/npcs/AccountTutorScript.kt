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

class AccountTutorScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.bank_tutor) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val step = progression.current(player)
        startDialogue(npc) {
            when {
                step.varValue < TutorialStep.AccountMgmt.varValue -> firstTalk(npc)
                step.varValue < TutorialStep.AccountExit.varValue -> lecture(npc)
                else -> recap(npc)
            }
        }
    }

    private suspend fun Dialogue.firstTalk(npc: Npc) {
        chatPlayer(quiz, "Hello. Who are you?")
        chatNpc(happy, "I'm the Account Guide. I'm here to tell you about your account.")
        chatPlayer(quiz, "What's an account?")
        chatNpc(
            happy,
            "Your character IS your account! It's what you are using to play the game right now. Everyone here has one and some people even have multiple!",
        )
        chatNpc(
            happy,
            "There's various things you can do with your account which is where the Account Management menu comes into play. Let's take a look at it now.",
        )
        progression.setStep(player, TutorialStep.AccountMgmt, npc)
        toplevelSidebuttonSwitch(player, constants.toplevel_account)
    }

    private suspend fun Dialogue.lecture(npc: Npc) {
        chatNpc(happy, "As you can see, there's a few things you can do with your account.")
        chatNpc(
            happy,
            "First, this standalone world gives every account full access to all skills, areas, quests, and items.",
        )
        chatPlayer(quiz, "What's a world?")
        chatNpc(
            happy,
            "A world is what you play the game on. Most worlds can hold up to 2000 players. You can swap worlds with the World Switcher in the Logout menu.",
        )
        chatNpc(
            happy,
            "You are currently on the standalone world, where all game content is available without an account restriction.",
        )
        chatNpc(
            happy,
            "Bonds are another thing that you should know about. You can purchase Bonds from the store in the Account Management menu.",
        )
        chatPlayer(quiz, "Can Bonds be traded with other players?")
        chatNpc(
            happy,
            "Yes. You could purchase a Bond and then sell it to another player for some gold, or use your gold to buy one from another player.",
        )
        chatPlayer(
            quiz,
            "So Bonds can be exchanged for gold between players?",
        )
        chatNpc(happy, "Exactly!")
        chatNpc(
            happy,
            "Next up, you have your inbox and name changer. The inbox is where you'll receive important information related to your account and the game. Be sure to keep an eye on it.",
        )
        chatNpc(
            happy,
            "The name changer allows you to change your display name if you don't like your current one.",
        )
        chatPlayer(quiz, "Awesome. Is there anything else I should know about accounts?")
        chatNpc(
            happy,
            "Your account is very valuable and it's important you look after it. Various tools can help with this, such as Bank PINs and two-factor authentication options.",
        )
        chatNpc(
            happy,
            "You'll find more information on account security on the Customer Support webpage. You can use the Useful Links section of the Account Management menu to get there.",
        )
        chatNpc(
            happy,
            "Anyway, that's everything I have to tell you about accounts. Let me know if you need a recap, otherwise head on to your next instructor.",
        )
        progression.setStep(player, TutorialStep.AccountExit, npc)
    }

    private suspend fun Dialogue.recap(npc: Npc) {
        chatNpc(happy, "Hello. Would you like to hear about your account again?")
        val choice = choice2("Yes!", 1, "No thanks.", 2)
        if (choice == 1) {
            chatNpc(happy, "Excellent. Well there's a few things you can do with your account.")
            chatNpc(
                happy,
                "This standalone world gives every account full access to all skills, areas, quests, and items.",
            )
            chatNpc(
                happy,
                "Bonds are another thing that you should know about. You can purchase Bonds from the store in the Account Management menu.",
            )
            chatNpc(
                happy,
                "Your account is very valuable and it's important you look after it. Various tools can help with this, such as Bank PINs and two-factor authentication options.",
            )
        }
        progression.setStep(player, TutorialStep.AccountExit, npc)
    }
}
