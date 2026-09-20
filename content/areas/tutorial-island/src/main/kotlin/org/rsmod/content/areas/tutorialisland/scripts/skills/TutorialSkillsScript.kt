package org.rsmod.content.areas.tutorialisland.scripts.skills

import jakarta.inject.Inject
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_seqs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.skills.smithing.SmithingProductMade
import org.rsmod.content.skills.smithing.scripts.SmithingActions
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Island-scoped skill interactions for Learning the Ropes.
 *
 * Level-3 caps / HP rules live in
 * [org.rsmod.content.areas.tutorialisland.scripts.rules.TutorialIslandRulesScript].
 */
class TutorialSkillsScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val locRepo: LocRepository,
    private val smithing: SmithingActions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(tutorial_npcs.fishing_spot) { fish() }
        onOpLoc1(tutorial_locs.tree) { chop() }
        onOpLoc1(tutorial_locs.tree2) { chop() }
        onOpLoc1(tutorial_locs.oak) { oakDeny() }
        onOpHeldU(tutorial_objs.tinderbox, tutorial_objs.tut2_logs) { lightFire() }
        onOpLocU(tutorial_locs.fire, tutorial_objs.tut2_raw_shrimp) { cookShrimp() }
        onOpHeldU(tutorial_objs.pot_flour, tutorial_objs.bucket_water) { mixDough() }
        onOpLoc1(tutorial_locs.range) { cookBread() }
        onOpLocU(tutorial_locs.range, tutorial_objs.bread_dough) { cookBread() }
        onOpLoc1(tutorial_locs.tin_rock) { mineTin() }
        onOpLoc1(tutorial_locs.copper_rock) { mineCopper() }
        onOpLoc1(tutorial_locs.furnace) { smelt() }
        onOpLocU(tutorial_locs.furnace, tutorial_objs.tin_ore) { smelt() }
        onOpLocU(tutorial_locs.furnace, tutorial_objs.copper_ore) { smelt() }
        // No anvil binding: the island's anvil is the shared `anvil` loc, and a type-level handler
        // wins over a content-level one everywhere - so binding it here would put the tutorial's
        // one-recipe script on every anvil in the game. The smithing module's content handler
        // serves it instead; `advanceOnSmithing` below is what keeps progression moving.
        onEvent<SmithingProductMade> { advanceTutorial() }
    }

    private fun ProtectedAccess.fish() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalInv)) {
            progression.deny(
                player,
                "You cannot fish here yet. You must progress further in the tutorial.",
            )
            return
        }
        if (inv.freeSpace() <= 0 && invTotal(inv, tutorial_objs.tut2_raw_shrimp) <= 0) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        anim(tutorial_seqs.small_net)
        invAdd(inv, tutorial_objs.tut2_raw_shrimp, 1)
        statAdvance(stats.fishing, FISHING_XP)
        mes("You manage to catch some shrimp.")
        // Catch advances to SurvivalSkills (flash skills tab). Opening skills → SurvivalTools,
        // then talking to Brynna gives the axe and tinderbox.
        if (progression.current(player).varValue <= TutorialStep.SurvivalFish.varValue) {
            progression.setStep(player, TutorialStep.SurvivalSkills)
        }
    }

    private fun ProtectedAccess.chop() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalWc)) {
            progression.deny(
                player,
                "You cannot cut down this tree yet. You must progress further in the tutorial.",
            )
            return
        }
        if (progression.current(player).varValue > TutorialStep.SurvivalFm.varValue) {
            mes("Perhaps you've done enough woodcutting now.")
            return
        }
        if (inv.freeSpace() <= 0) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        anim(seqs.human_woodcutting_bronze_axe)
        invAdd(inv, tutorial_objs.tut2_logs, 1)
        statAdvance(stats.woodcutting, WOODCUTTING_XP)
        mes("You manage to cut some logs.")
        progression.advance(player, TutorialStep.SurvivalFm)
    }

    private fun ProtectedAccess.oakDeny() {
        mes(
            "You won't be able to chop oak trees until you have a Woodcutting level of 15. You'll advance to higher Woodcutting levels by chopping down normal trees."
        )
    }

    private suspend fun ProtectedAccess.lightFire() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalFm)) {
            return
        }
        if (invTotal(inv, tutorial_objs.tut2_logs) <= 0) {
            return
        }
        val fireCoords = coords
        invDel(inv, tutorial_objs.tut2_logs, 1)
        // The light-fire seq has to finish before the player is asked to move: walking in the same
        // tick replaces the animation and nothing is drawn at all.
        anim(tutorial_seqs.light_fire)
        delay(LIGHT_FIRE_DELAY)
        statAdvance(stats.firemaking, FIREMAKING_XP)
        // Live steps the player off the log tile as the fire appears. `walk` routes around blocked
        // tiles, so the player simply stays put if west is unavailable.
        walk(fireCoords.translateX(-1))
        locRepo.add(
            fireCoords,
            tutorial_locs.fire,
            FIRE_DURATION,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        mes("The fire catches and the logs begin to burn.")
        progression.advance(player, TutorialStep.SurvivalCook)
    }

    private fun ProtectedAccess.cookShrimp() {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalCook)) {
            return
        }
        if (invTotal(inv, tutorial_objs.tut2_raw_shrimp) > 0) {
            anim(tutorial_seqs.cook_fire)
            invDel(inv, tutorial_objs.tut2_raw_shrimp, 1)
            invAdd(inv, tutorial_objs.shrimp, 1)
            statAdvance(stats.cooking, COOK_SHRIMP_XP)
            mes("You manage to cook some shrimp.")
            progression.advance(player, TutorialStep.SurvivalGate)
        }
    }

    private fun ProtectedAccess.mixDough() {
        if (!progression.requireAtLeast(player, TutorialStep.ChefDough)) {
            return
        }
        if (
            invTotal(inv, tutorial_objs.pot_flour) <= 0 ||
                invTotal(inv, tutorial_objs.bucket_water) <= 0
        ) {
            return
        }
        invDel(inv, tutorial_objs.pot_flour, 1)
        invDel(inv, tutorial_objs.bucket_water, 1)
        invAdd(inv, tutorial_objs.pot_empty, 1)
        invAdd(inv, tutorial_objs.bucket_empty, 1)
        invAdd(inv, tutorial_objs.bread_dough, 1)
        mes("You make some dough.")
        progression.advance(player, TutorialStep.ChefBread)
    }

    private fun ProtectedAccess.cookBread() {
        if (!progression.requireAtLeast(player, TutorialStep.ChefBread)) {
            if (progression.current(player).varValue < TutorialStep.ChefDough.varValue) {
                mes(
                    "You haven't got anything suitable for cooking! The master chef can help you out with that."
                )
            }
            return
        }
        if (invTotal(inv, tutorial_objs.bread_dough) <= 0) {
            mes(
                "You haven't got anything suitable for cooking! The master chef can help you out with that."
            )
            return
        }
        anim(tutorial_seqs.cook_range)
        invDel(inv, tutorial_objs.bread_dough, 1)
        invAdd(inv, tutorial_objs.bread, 1)
        statAdvance(stats.cooking, COOK_BREAD_XP)
        mes("You manage to bake some bread.")
        progression.advance(player, TutorialStep.ChefExit)
    }

    private fun ProtectedAccess.mineTin() {
        mineOre(tutorial_objs.tin_ore, "tin")
    }

    private fun ProtectedAccess.mineCopper() {
        mineOre(tutorial_objs.copper_ore, "copper")
    }

    private fun ProtectedAccess.mineOre(ore: org.rsmod.game.type.obj.ObjType, name: String) {
        if (!progression.requireAtLeast(player, TutorialStep.MineOres)) {
            progression.deny(
                player,
                "You are not ready to mine yet. Please follow the tutorial and you'll be mining in no time.",
            )
            return
        }
        if (invTotal(inv, tutorial_objs.bronze_pickaxe) <= 0) {
            progression.deny(
                player,
                "You are not ready to mine yet. Please follow the tutorial and you'll be mining in no time.",
            )
            return
        }
        if (inv.freeSpace() <= 0) {
            mes(
                "You don't have enough room in your inventory to hold the ore. You'll need to drop something. To do so, right-click on an item you don't want and select drop. Once you've done that, try mining the rock again."
            )
            return
        }
        anim(tutorial_seqs.mine_bronze_pickaxe)
        invAdd(inv, ore, 1)
        statAdvance(stats.mining, MINING_XP)
        mes("You manage to mine some $name.")
        val hasTin = invTotal(inv, tutorial_objs.tin_ore) > 0
        val hasCopper = invTotal(inv, tutorial_objs.copper_ore) > 0
        if (hasTin && hasCopper) {
            progression.advance(player, TutorialStep.MineSmelt)
        }
    }

    /**
     * The island's furnace is `tut2_furnace`, a tutorial-only loc, so gating it here is safe and
     * only the smelting itself is delegated. The real dialog offers every bar, but the player is
     * carrying one tin and one copper, so bronze is the only row that can produce anything.
     */
    private suspend fun ProtectedAccess.smelt() {
        if (!progression.requireAtLeast(player, TutorialStep.MineSmelt)) {
            progression.deny(
                player,
                "This is a furnace for smelting metal. You'll learn how to use it soon.",
            )
            return
        }
        smithing.openSmeltDialog(this)
    }

    /**
     * Advances the mining chapter off the real smithing module's output.
     *
     * The island used to hand-script both halves - a silent bronze bar at the furnace, a silent
     * dagger at the anvil - which is also why interface 312 was never opened here. The client
     * expects it to be: `smithing_item` (cs 431) attaches a highlight to the bronze dagger slot
     * whenever `tutorial_2` is low, which only makes sense if the tutorial uses the real interface.
     */
    private fun SmithingProductMade.advanceTutorial() {
        if (product.id == tutorial_objs.bronze_bar.id) {
            progression.advance(player, TutorialStep.MineHammer)
        } else if (product.id == tutorial_objs.bronze_dagger.id) {
            progression.advance(player, TutorialStep.MineGate)
        }
    }

    private companion object {
        /** Two minutes, long enough to cook the tutorial shrimp without stranding the fire. */
        private const val FIRE_DURATION = 200

        /** Long enough for `human_createfire` to play before the player steps away. */
        private const val LIGHT_FIRE_DELAY = 4

        /* Live xp rewards for the tutorial's one-off skilling actions. */
        private const val FISHING_XP = 10.0
        private const val WOODCUTTING_XP = 25.0
        private const val FIREMAKING_XP = 40.0
        private const val COOK_SHRIMP_XP = 30.0
        private const val COOK_BREAD_XP = 40.0
        private const val MINING_XP = 17.5
    }
}
