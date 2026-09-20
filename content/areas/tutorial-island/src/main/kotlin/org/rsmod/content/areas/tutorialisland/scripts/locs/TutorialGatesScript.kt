package org.rsmod.content.areas.tutorialisland.scripts.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Progression gates for Tutorial Island doors, gates, and ladders. Denied interactions use
 * transcript messages; allowed ones advance the step and teleport where needed.
 */
class TutorialGatesScript @Inject constructor(private val progression: TutorialProgression) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(tutorial_locs.survival_entry) { survivalEntry(it.loc) }
        onOpLoc1(tutorial_locs.survival_exit_l) { survivalExit(it.loc) }
        onOpLoc1(tutorial_locs.survival_exit_r) { survivalExit(it.loc) }
        onOpLoc1(tutorial_locs.nav_entry) { chefEntry(it.loc) }
        onOpLoc1(tutorial_locs.nav_exit) { chefExit(it.loc) }
        onOpLoc1(tutorial_locs.quest_entry) { questEntry(it.loc) }
        onOpLoc1(tutorial_locs.quest_ladder) { questLadder() }
        onOpLoc1(tutorial_locs.mining_ladder) { miningLadderUp() }
        onOpLoc1(tutorial_locs.mining_exit_l) { miningExit(it.loc) }
        onOpLoc1(tutorial_locs.mining_exit_r) { miningExit(it.loc) }
        onOpLoc1(tutorial_locs.combat_entry_l) { combatCage(it.loc) }
        onOpLoc1(tutorial_locs.combat_entry_r) { combatCage(it.loc) }
        onOpLoc1(tutorial_locs.combat_ladder) { combatLadder() }
        onOpLoc1(tutorial_locs.bank_ladder) { bankLadder() }
        onOpLoc1(tutorial_locs.bank_exit) { bankExit(it.loc) }
        onOpLoc1(tutorial_locs.prayer_door) { prayerDoor(it.loc) }
        onOpLoc1(tutorial_locs.prayer_exit_l) { prayerExit(it.loc) }
        onOpLoc1(tutorial_locs.prayer_exit_r) { prayerExit(it.loc) }
    }

    /**
     * Puts the player on the other side of the wall the door sits in.
     *
     * A wall-shaped door occupies one edge of its own tile - the edge named by its angle - so the
     * two sides of the doorway are the door's tile and the tile just past that edge. Clicking the
     * door routes the player onto the door tile when they approach from the open side, which is why
     * this cannot key off "which side is the player on" alone: that case has no offset to mirror,
     * and the player would be left standing still, walking back to the door and clicking again.
     *
     * The tutorial doors carry no `next_loc_stage` and no door content group, so there is nothing
     * to swing open; this is a step through the doorway.
     */
    private fun ProtectedAccess.stepThrough(door: BoundLocInfo) {
        val beyond =
            when (door.angle) {
                LocAngle.West -> door.coords.translateX(-1)
                LocAngle.North -> door.coords.translateZ(1)
                LocAngle.East -> door.coords.translateX(1)
                LocAngle.South -> door.coords.translateZ(-1)
            }
        teleport(if (coords == door.coords) beyond else door.coords)
    }

    /**
     * Whether the player is about to pass *into* the space this door encloses, rather than out of
     * it.
     *
     * Each of the doors this is used for - the rat cage gates, the bank exit, the chapel's far
     * doors - is a wall on the edge of its own tile facing the enclosed side. Because that wall
     * blocks movement, the only way to end up standing on the door's tile is to have walked onto it
     * from the open side, so "player is on the door tile" is exactly "player is outside". Someone
     * inside cannot reach that tile and is instead routed adjacent to it.
     *
     * This matters because gating both directions traps the player: the rat pen locked them in at
     * the ranging step, and the bank exit is now the way both in and out of the bank.
     */
    private fun ProtectedAccess.enteringThrough(door: BoundLocInfo): Boolean = coords == door.coords

    private fun ProtectedAccess.survivalEntry(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.GielinorDoor)) {
            progression.deny(
                player,
                "You need to talk to the Gielinor Guide before you are allowed to proceed through this door.",
            )
            return
        }
        progression.advance(player, TutorialStep.SurvivalTalk)
        // Door open animation handled by map/loc params when present; nudge into survival area.
        stepThrough(door)
    }

    private fun ProtectedAccess.survivalExit(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.SurvivalGate)) {
            progression.deny(
                player,
                "You need to talk to the Survival Guide and complete her tasks before you are allowed to proceed through this gate.",
            )
            return
        }
        progression.advance(player, TutorialStep.ChefDoor)
        stepThrough(door)
    }

    private fun ProtectedAccess.chefEntry(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.ChefDoor)) {
            progression.deny(player, "You need to finish the Survival Expert's tasks first.")
            return
        }
        progression.advance(player, TutorialStep.ChefTalk)
        stepThrough(door)
    }

    private fun ProtectedAccess.chefExit(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.ChefExit)) {
            progression.deny(player, "You need to finish the Master Chef's tasks first.")
            return
        }
        progression.advance(player, TutorialStep.QuestDoor)
        stepThrough(door)
    }

    private fun ProtectedAccess.questEntry(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.QuestDoor)) {
            progression.deny(player, "You need to finish the Master Chef's tasks first.")
            return
        }
        progression.advance(player, TutorialStep.QuestTalk)
        stepThrough(door)
    }

    private fun ProtectedAccess.questLadder() {
        if (!progression.requireAtLeast(player, TutorialStep.QuestLadder)) {
            progression.deny(player, "I don't think you're ready to go down there yet.")
            return
        }
        progression.advance(player, TutorialStep.MineTalk)
        telejump(CoordGrid(0, 26, 195, 16, 47))
    }

    private fun ProtectedAccess.miningLadderUp() {
        // Allow climbing back up anytime once in the caves.
        if (!progression.requireAtLeast(player, TutorialStep.MineTalk)) {
            progression.deny(player, "You need to finish with Mining and Smithing first.")
            return
        }
        telejump(CoordGrid(0, 26, 95, 16, 47))
    }

    private fun ProtectedAccess.miningExit(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.MineGate)) {
            progression.deny(player, "You need to finish with Mining and Smithing first.")
            return
        }
        progression.advance(player, TutorialStep.CombatTalk)
        stepThrough(door)
    }

    /**
     * The rat pen gates, which are the only opening in the fence around the pen.
     *
     * Both gates are wall-shaped and sit at local (39, 46) and (39, 47) on mapsquare 26_195 facing
     * [LocAngle.West], so the barrier runs the 38|39 boundary: tile 39 is outside the pen and tile
     * 38 is inside it, alongside the rat spawns at local x 34..37. Since the gate blocks movement,
     * the only way to be standing *on* tile 39 is to have approached from outside - which makes
     * "player is on the gate tile" the test for entering rather than leaving.
     *
     * The progression checks gate entry only. Leaving is always allowed: the ranging task requires
     * the player to get back out of the pen, and gating both directions locked them inside.
     */
    private fun ProtectedAccess.combatCage(gate: BoundLocInfo) {
        val entering = enteringThrough(gate)
        val step = progression.current(player)
        when {
            !entering -> stepThrough(gate)
            step.varValue < TutorialStep.CombatMelee.varValue ->
                progression.deny(
                    player,
                    "Oi! Get away from there. Only enter the rat cage when I say so.",
                )
            step.varValue >= TutorialStep.CombatRange.varValue &&
                step.varValue < TutorialStep.CombatLadder.varValue ->
                progression.deny(
                    player,
                    "No, don't enter the pit. Range the rats from outside the cage.",
                )
            // No step advance: the gate is used in both directions and the melee task ends on the
            // kill, not on entry.
            else -> stepThrough(gate)
        }
    }

    private fun ProtectedAccess.combatLadder() {
        if (!progression.requireAtLeast(player, TutorialStep.CombatLadder)) {
            progression.deny(
                player,
                "You're not ready to continue yet. You need to know about combat before you go on.",
            )
            return
        }
        progression.advance(player, TutorialStep.PrayerTalk)
        telejump(COMBAT_LADDER_TOP)
    }

    private fun ProtectedAccess.bankLadder() {
        telejump(COMBAT_LADDER_BOTTOM)
    }

    /**
     * The bank's east door. Since the chapel now comes first, the player arrives at the bank from
     * this side, so this is the way both in and out: entry needs only to have been sent here by
     * Brother Brace, while leaving needs the bank, poll booth and Account Guide all done.
     */
    private fun ProtectedAccess.bankExit(door: BoundLocInfo) {
        val step = progression.current(player)
        if (enteringThrough(door)) {
            if (!progression.requireAtLeast(player, TutorialStep.BankOpen)) {
                progression.deny(
                    player,
                    "You need to finish Brother Brace's tasks before you are allowed to proceed through this door.",
                )
                return
            }
            stepThrough(door)
            return
        }
        when {
            step.varValue < TutorialStep.BankOpen.varValue ->
                progression.deny(player, "You need to open your bank first.")
            step.varValue < TutorialStep.PollView.varValue ->
                progression.deny(
                    player,
                    "You need to visit the poll booth before you can proceed through this door.",
                )
            step.varValue < TutorialStep.AccountExit.varValue ->
                progression.deny(
                    player,
                    "You need to talk to the Account Guide before you are allowed to proceed through this door.",
                )
            else -> {
                progression.advance(player, TutorialStep.Ironman)
                stepThrough(door)
            }
        }
    }

    /** Chapel entrance, reached from the ladder up out of the combat cave. */
    private fun ProtectedAccess.prayerDoor(door: BoundLocInfo) {
        if (!progression.requireAtLeast(player, TutorialStep.CombatLadder)) {
            progression.deny(
                player,
                "You need to finish with the Combat Instructor before you are allowed to proceed through this door.",
            )
            return
        }
        progression.advance(player, TutorialStep.PrayerTalk)
        stepThrough(door)
    }

    /**
     * Chapel exit on the far side, which now leads on to the bank rather than to the magic area.
     *
     * Also accepted as a way *in*: coming down from the ladder the player can round the chapel and
     * reach these doors before the entrance on the north wall, and refusing them there is what made
     * the chapel look broken.
     */
    private fun ProtectedAccess.prayerExit(door: BoundLocInfo) {
        if (enteringThrough(door)) {
            if (!progression.requireAtLeast(player, TutorialStep.PrayerTalk)) {
                progression.deny(
                    player,
                    "You need to finish with the Combat Instructor before you are allowed to proceed through this door.",
                )
                return
            }
            stepThrough(door)
            return
        }
        if (!progression.requireAtLeast(player, TutorialStep.PrayerExit)) {
            progression.deny(
                player,
                "You need to finish Brother Brace's tasks before you are allowed to proceed through this door.",
            )
            return
        }
        progression.advance(player, TutorialStep.BankOpen)
        stepThrough(door)
    }

    private companion object {
        /**
         * `tut2_combat_ladder` sits at local (39, 54) on the dungeon square and `tut2_bank_ladder`
         * at local (39, 54) on the surface square - the map already pairs them directly above one
         * another, so each ladder lands on the tile its counterpart occupies.
         *
         * Both destinations used to be hand-entered and neither matched: climbing up put the player
         * at (52, 50), which is inside the bank building between the booths, and climbing back down
         * put them at (50, 20), nowhere near the combat area.
         */
        private val COMBAT_LADDER_TOP = CoordGrid(0, 26, 95, 39, 54)
        private val COMBAT_LADDER_BOTTOM = CoordGrid(0, 26, 195, 39, 54)
    }
}
