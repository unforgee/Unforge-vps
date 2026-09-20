package org.rsmod.content.areas.tutorialisland.scripts.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Bank booth + poll booth gates on Tutorial Island. */
class TutorialBankScript
@Inject
constructor(private val progression: TutorialProgression, private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(tutorial_locs.bankbooth) { openTutorialBank() }
        onOpLoc1(tutorial_locs.pollbooth) { openPollBooth() }
    }

    private fun ProtectedAccess.openTutorialBank() {
        if (!progression.requireAtLeast(player, TutorialStep.BankOpen)) {
            progression.deny(
                player,
                "You're not ready to continue yet. You need to know about combat before you go on.",
            )
            return
        }
        if (invTotal(bank, tutorial_objs.coins) <= 0) {
            invAdd(bank, tutorial_objs.coins, 25)
        }
        player.openBank(eventBus)
        mes("This is your bank. You can store things here for safekeeping.")
        if (progression.current(player).varValue < TutorialStep.PollView.varValue) {
            progression.setStep(player, TutorialStep.PollView)
        }
    }

    private fun ProtectedAccess.openPollBooth() {
        if (!progression.requireAtLeast(player, TutorialStep.PollView)) {
            progression.deny(player, "You need to open your bank first.")
            return
        }
        mes(
            "This is a poll booth. Polling booths can be found throughout Gielinor and allow you to vote on important issues."
        )
        mes("You cannot vote while on Tutorial Island.")
        if (progression.current(player).varValue < TutorialStep.AccountTalk.varValue) {
            progression.setStep(player, TutorialStep.AccountTalk)
        }
    }
}
