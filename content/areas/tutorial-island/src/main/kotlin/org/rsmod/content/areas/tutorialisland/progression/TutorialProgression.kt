package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@Singleton
class TutorialProgression @Inject constructor(private val focus: TutorialFocusGuidance) {
    private var Player.step by enumVarp<TutorialStep>(tutorial_varps.tutorial_2)
    private var Player.completed by boolVarp(tutorial_varps.tut2_completed)
    private var Player.newAccount by boolVarBit(varbits.new_player_account)

    fun isActive(player: Player): Boolean = !player.completed

    fun current(player: Player): TutorialStep = player.step

    fun setStep(player: Player, step: TutorialStep, hintNpc: Npc? = null) {
        player.step = step
        if (step == TutorialStep.Completed) {
            player.completed = true
            focus.end(player)
            return
        }
        focus.apply(player, step, hintNpc)
    }

    fun advance(player: Player, to: TutorialStep, hintNpc: Npc? = null) {
        if (player.completed) return
        if (player.step.varValue >= to.varValue) {
            // Allow re-applying focus on reconnect / same-step refresh.
            focus.apply(player, player.step, hintNpc)
            return
        }
        setStep(player, to, hintNpc)
    }

    fun requireAtLeast(player: Player, min: TutorialStep): Boolean =
        player.completed || player.step.varValue >= min.varValue

    fun deny(player: Player, message: String) {
        player.mes(message)
    }

    fun resume(player: Player) {
        if (player.completed) return
        if (player.step == TutorialStep.CharCreate && !player.newAccount) {
            // Mid-tutorial reconnect of an existing incomplete account.
        }
        focus.apply(player, player.step)
    }

    fun completeQuest(player: Player) {
        // Quest complete happens at Wind Strike; leave kit applied on teleport.
        if (player.step.varValue < TutorialStep.LeaveTalk.varValue) {
            setStep(player, TutorialStep.LeaveTalk)
        }
    }

    fun finishAndLeave(player: Player) {
        setStep(player, TutorialStep.Completed)
    }
}
