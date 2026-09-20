package org.rsmod.content.areas.tutorialisland.scripts.combat

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.progression.LearningTheRopesJournal
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Tutorial Island combat progression for giant rats and Wind Strike chickens.
 *
 * Combat itself is the ordinary combat path - these npcs are attacked, damaged and killed like any
 * other. Progression is driven purely from the death queues, so a kill counts the same whether the
 * player opened the fight or retaliated after being attacked.
 *
 * There used to be `onOpNpc2` handlers on both npc types that faked kills outright. Registering
 * those replaced the real attack path, so clicking Attack awarded the step without any combat while
 * retaliation still ran genuine combat underneath - two paths advancing the same step differently.
 * The pen gate in `TutorialGatesScript` is what actually keeps the player away from the rats until
 * the combat step, so nothing was lost by deleting them.
 */
class TutorialCombatScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val players: PlayerList,
    private val death: NpcDeath,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onNpcQueue(tutorial_npcs.giant_rat, queues.death) { onRatDeath() }
        onNpcQueue(tutorial_npcs.chicken, queues.death) { onChickenDeath() }
    }

    /**
     * Overriding a npc type's death queue replaces [NpcDeath.deathWithDrops], which the catch-all
     * `onNpcQueue(queues.death)` in `NpcDeathScript` would otherwise have run. Without calling the
     * death sequence here the rat is never despawned: it sits at 0 hitpoints and regenerates.
     */
    private suspend fun StandardNpcAccess.onRatDeath() {
        val hero = findHero(players)
        if (hero != null) {
            when (progression.current(hero)) {
                TutorialStep.CombatMelee -> {
                    hero.mes("You have defeated the giant rat!")
                    progression.advance(hero, TutorialStep.CombatRange)
                }
                TutorialStep.CombatRange -> {
                    hero.mes("You have defeated the giant rat!")
                    progression.advance(hero, TutorialStep.CombatLadder)
                }
                else -> Unit
            }
        }
        death.deathWithDrops(this)
    }

    /** See [onRatDeath] for why the death sequence has to be run explicitly. */
    private suspend fun StandardNpcAccess.onChickenDeath() {
        val hero = findHero(players)
        val step = hero?.let(progression::current)
        if (
            step != null &&
                step.varValue in
                    TutorialStep.MagicRunes.varValue until TutorialStep.LeaveTalk.varValue
        ) {
            hero.mes("The chicken is defeated by your Wind Strike!")
            LearningTheRopesJournal.notifyComplete(hero)
            progression.completeQuest(hero)
            progression.setStep(hero, TutorialStep.LeaveTalk)
        }
        death.deathWithDrops(this)
    }
}
