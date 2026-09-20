package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.clearHintArrow
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_interfaces
import org.rsmod.content.areas.tutorialisland.configs.tutorial_locs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_npcs
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varbits
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.hunt.HuntVis
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.interf.InterfaceType
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.midi.MidiType
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

/**
 * Single hub for Tutorial Island attention cues. Every [TutorialProgression.setStep] routes here.
 *
 * Yellow hint arrows are step-driven: NPC talk steps resolve a nearby instance (or use [hintNpc]
 * when it matches), door/loc steps use tile arrows, and UI flash steps intentionally leave the
 * world arrow cleared so the flashing side icon is the only cue.
 *
 * Overlay chrome matches live OSRS: `tutorial_overlay` on `overlay_atmosphere`, guide text via CS2
 * `mesoverlay` (1974), and side panels closed while the tutorial is active.
 */
@Singleton
class TutorialFocusGuidance
@Inject
constructor(private val eventBus: EventBus, private val npcSearch: NpcSearch) {
    private var Player.flashingSide by intVarBit(tutorial_varbits.flashside)

    fun apply(player: Player, step: TutorialStep, hintNpc: Npc? = null) {
        clear(player)
        openOverlay(player)
        applyClickBlocker(player, step)
        applySidePanels(player, step)
        setControlGuide(player, step)
        ClientScripts.tutorialProgressbarSet(player, step.varValue, TutorialStep.MAX_PROGRESS)
        applyHint(player, step, hintNpc)
        applyUiFlash(player, step)
        applyOutlines(player, step)
    }

    fun clear(player: Player) {
        player.clearHintArrow()
        ClientScripts.entityHighlightClear(player)
        ClientScripts.highlightingOff(player)
    }

    fun end(player: Player) {
        clear(player)
        // Every panel the tutorial kept locked has to come back, or the player leaves the island
        // with dead side tabs until their next login re-opens them.
        for (panel in SIDE_PANELS) {
            player.openPanel(panel)
        }
        ClientScripts.tutorialEnd(player)
    }

    /**
     * Live first-login order after the overlay is up: drop the click blocker for the Newbie Melody
     * sting, then put it back before the character creator modal opens.
     */
    fun playCharacterCreateSting(player: Player) {
        player.ifSetHide(tutorial_components.tutorial_noclick, hide = true)
        player.midiSong(NEWBIE_MELODY)
        player.ifSetHide(tutorial_components.tutorial_noclick, hide = false)
    }

    /**
     * `tutorial_overlay:noclick` (614:1) is a parent-sized `noClickThrough` layer that swallows
     * every click on the game view. The cache ships it hidden; it is only meant to be shown while a
     * tutorial modal owns the screen, so it has to be hidden again for every ordinary step -
     * otherwise the world stays unclickable for the rest of the session.
     */
    private fun applyClickBlocker(player: Player, step: TutorialStep) {
        val modalStep = step == TutorialStep.CharCreate || step == TutorialStep.ExpSelect
        player.ifSetHide(tutorial_components.tutorial_noclick, hide = !modalStep)
    }

    /**
     * The Gielinor Guide has a second line of dialogue once the settings menu has been opened, but
     * the step value does not change, so nothing in [apply] would move the player's attention back
     * to him. Re-points the arrow and swaps the guide text to say so.
     */
    fun pointBackToBasicsTutor(player: Player) {
        val npc =
            npcSearch.find(
                player.coords,
                tutorial_npcs.basics_tutor,
                HINT_NPC_DISTANCE,
                HuntVis.Off,
            )
        if (npc != null) {
            HintArrows.setNpc(player, npc)
        }
        ClientScripts.mesOverlay(
            player,
            "<col=0000ff>Settings menu</col><br>Good. Now speak to the Gielinor Guide again to " +
                "continue.",
        )
    }

    fun setControlGuide(player: Player, step: TutorialStep) {
        player.ifSetText(tutorial_components.hint_text_1, step.guideTitle)
        player.ifSetText(tutorial_components.hint_text_2, step.guideBody)
        player.ifSetText(tutorial_components.hint_text_3, "")
        player.ifSetText(tutorial_components.hint_text_4, "")
        player.ifOpenOverlay(tutorial_interfaces.mesoverlay, components.chatbox_chatmodal, eventBus)
        ClientScripts.mesOverlay(player, guideHtml(step))
    }

    /**
     * Live Past Experience modal (`tutorial_player_experience` / 929) on the main modal slot.
     *
     * The three choices are static components, not children of the content layer (929:4 decodes
     * with no children), so Op1 is enabled per button over the static `-1..-1` range. Clicks then
     * arrive as `comsub == -1` on each button.
     */
    fun openPastExperience(player: Player) {
        player.ifOpenMainModal(tutorial_interfaces.tutorial_player_experience, eventBus)
        player.ifSetEvents(tutorial_components.experience_button_new, -1..-1, IfEvent.Op1)
        player.ifSetEvents(tutorial_components.experience_button_returning, -1..-1, IfEvent.Op1)
        player.ifSetEvents(tutorial_components.experience_button_experienced, -1..-1, IfEvent.Op1)
    }

    private fun openOverlay(player: Player) {
        // Live opens `tutorial_overlay` on toplevel:overlay_atmosphere (164:1 / stretch equiv).
        // `tutorial_progressbar_init` is not sent from here: 614:0 carries a baked onLoad hook
        // that runs it with the six component ids it needs as soon as the overlay opens.
        player.ifOpenOverlay(
            tutorial_interfaces.tutorial_overlay,
            components.toplevel_target_overlay_atmosphere,
            eventBus,
        )
    }

    /**
     * Matches OSRS first-login: side panels start closed and unlock one at a time as the tutorial
     * reaches them (orbs / XP drops stay throughout).
     *
     * A side tab is a sub-interface the gameframe opens into `toplevel_target_sideN` once, at
     * login; clicking a stone is a client-side switch between whatever is already open. So closing
     * a panel here is what locks the tab - and the panel has to be re-opened for the tab to do
     * anything at all, which is why this cannot simply close everything on every step.
     */
    private fun applySidePanels(player: Player, step: TutorialStep) {
        for (panel in SIDE_PANELS) {
            if (step.varValue >= panel.unlock.varValue) {
                player.openPanel(panel)
            } else {
                player.closePanel(panel)
            }
        }
    }

    private fun Player.openPanel(panel: SidePanel) {
        if (!ui.containsOverlay(panel.interf)) {
            ifOpenOverlay(panel.interf, panel.target, eventBus)
        }
    }

    private fun Player.closePanel(panel: SidePanel) {
        if (ui.containsOverlay(panel.interf)) {
            ifCloseOverlay(panel.interf, eventBus)
        }
    }

    private fun applyHint(player: Player, step: TutorialStep, hintNpc: Npc?) {
        val tile = hintTile(step)
        if (tile != null) {
            HintArrows.setTile(player, tile)
            return
        }
        val type = hintNpcType(step) ?: return
        val npc =
            hintNpc?.takeIf { it.id == type.id }
                ?: npcSearch.find(player.coords, type, HINT_NPC_DISTANCE, HuntVis.Off)
        if (npc != null) {
            HintArrows.setNpc(player, npc)
        }
    }

    private fun applyUiFlash(player: Player, step: TutorialStep) {
        player.flashingSide = FLASH_SIDE_NONE
        val side =
            when (step) {
                TutorialStep.GielinorSettings -> constants.toplevel_settings
                TutorialStep.SurvivalInv -> constants.toplevel_inventory
                TutorialStep.SurvivalSkills -> constants.toplevel_stats
                TutorialStep.QuestList -> constants.toplevel_details
                TutorialStep.CombatWorn -> constants.toplevel_worn
                TutorialStep.CombatStyles -> constants.toplevel_combat
                TutorialStep.AccountMgmt -> constants.toplevel_account
                TutorialStep.PrayerTab -> constants.toplevel_prayer
                TutorialStep.MagicTab -> constants.toplevel_magic
                TutorialStep.CombatStats -> {
                    ClientScripts.equipmentIconFlash(player)
                    return
                }
                TutorialStep.MagicRunes,
                TutorialStep.MagicCast -> {
                    ClientScripts.magicFlashSpell(player, tutorial_components.spell_wind_strike)
                    return
                }
                TutorialStep.HomeTele -> {
                    ClientScripts.magicFlashSpell(player, tutorial_components.spell_home_teleport)
                    return
                }
                else -> return
            }
        // `flashside` is 4 bits holding the side id plus one, leaving 0 for "nothing is flashing".
        // Confirmed in-game: the settings step flashes the spanner. `toplevel_flashicon` (913) is
        // a proc taking 3 ints whose meaning has not been recovered, so it is not called here.
        player.flashingSide = side + 1
    }

    private fun applyOutlines(player: Player, step: TutorialStep) {
        ClientScripts.highlightingOn(player)
        val npcType = outlineNpcType(step)
        if (npcType != null) {
            ClientScripts.highlightNpcTypeOn(player, npcType.id)
        }

        val locType = outlineLocType(step)
        if (locType != null) {
            ClientScripts.highlightLocTypeOn(player, locType.id)
        }
    }

    /**
     * Tiles are the decoded map positions of each loc, not estimates: every one of these was read
     * out of the rev-239 mapsquare loc lists for 26_95 and 26_195.
     *
     * The chef door is the one exception. It is a wall door on the west edge of 7,12, so an arrow
     * on the loc's own tile lands on the square the player already stands on rather than on the
     * doorway; it points at 6,12, the tile through the gap.
     */
    private fun hintTile(step: TutorialStep): CoordGrid? =
        when (step) {
            TutorialStep.GielinorDoor -> CoordGrid(0, 26, 95, 26, 35)
            TutorialStep.SurvivalGate -> CoordGrid(0, 26, 95, 17, 20)
            TutorialStep.ChefDoor -> CoordGrid(0, 26, 95, 6, 12)
            TutorialStep.ChefExit -> CoordGrid(0, 26, 95, 0, 18)
            TutorialStep.QuestDoor -> CoordGrid(0, 26, 95, 14, 54)
            TutorialStep.QuestLadder -> CoordGrid(0, 26, 95, 16, 47)
            TutorialStep.MineGate -> CoordGrid(0, 26, 195, 22, 31)
            TutorialStep.CombatLadder -> CoordGrid(0, 26, 195, 39, 54)
            TutorialStep.BankOpen -> CoordGrid(0, 26, 95, 48, 52)
            TutorialStep.PollView -> CoordGrid(0, 26, 95, 47, 49)
            TutorialStep.AccountExit -> CoordGrid(0, 26, 95, 58, 52)
            TutorialStep.PrayerExit -> CoordGrid(0, 26, 95, 57, 34)
            else -> null
        }

    private fun hintNpcType(step: TutorialStep): NpcType? =
        when (step) {
            TutorialStep.GielinorTalk -> tutorial_npcs.basics_tutor
            TutorialStep.SurvivalTalk,
            TutorialStep.SurvivalTools -> tutorial_npcs.survival_tutor
            TutorialStep.SurvivalFish -> tutorial_npcs.fishing_spot
            TutorialStep.ChefTalk -> tutorial_npcs.nav_tutor
            TutorialStep.QuestTalk -> tutorial_npcs.quest_tutor
            TutorialStep.MineTalk,
            TutorialStep.MineHammer -> tutorial_npcs.mining_tutor
            TutorialStep.CombatTalk,
            TutorialStep.CombatGear,
            TutorialStep.CombatRange -> tutorial_npcs.combat_tutor
            TutorialStep.CombatMelee -> tutorial_npcs.giant_rat
            TutorialStep.AccountTalk -> tutorial_npcs.bank_tutor
            TutorialStep.PrayerTalk -> tutorial_npcs.prayer_tutor
            TutorialStep.Ironman -> tutorial_npcs.ironman_tutor
            TutorialStep.MagicTalk,
            TutorialStep.LeaveTalk -> tutorial_npcs.magic_tutor
            TutorialStep.MagicRunes,
            TutorialStep.MagicCast -> tutorial_npcs.chicken
            else -> null
        }

    private fun outlineNpcType(step: TutorialStep): NpcType? =
        when (step) {
            TutorialStep.GielinorTalk -> tutorial_npcs.basics_tutor
            TutorialStep.SurvivalTalk,
            TutorialStep.SurvivalTools -> tutorial_npcs.survival_tutor
            TutorialStep.SurvivalFish -> tutorial_npcs.fishing_spot
            TutorialStep.ChefTalk -> tutorial_npcs.nav_tutor
            TutorialStep.QuestTalk,
            TutorialStep.QuestList -> tutorial_npcs.quest_tutor
            TutorialStep.MineTalk,
            TutorialStep.MineHammer -> tutorial_npcs.mining_tutor
            TutorialStep.CombatTalk,
            TutorialStep.CombatGear,
            TutorialStep.CombatRange -> tutorial_npcs.combat_tutor
            TutorialStep.CombatMelee -> tutorial_npcs.giant_rat
            TutorialStep.AccountTalk,
            TutorialStep.AccountMgmt -> tutorial_npcs.bank_tutor
            TutorialStep.PrayerTalk,
            TutorialStep.PrayerTab -> tutorial_npcs.prayer_tutor
            TutorialStep.Ironman -> tutorial_npcs.ironman_tutor
            TutorialStep.MagicTalk,
            TutorialStep.MagicTab,
            TutorialStep.LeaveTalk -> tutorial_npcs.magic_tutor
            TutorialStep.MagicRunes,
            TutorialStep.MagicCast -> tutorial_npcs.chicken
            else -> null
        }

    private fun outlineLocType(step: TutorialStep): LocType? =
        when (step) {
            TutorialStep.GielinorDoor -> tutorial_locs.survival_entry
            TutorialStep.SurvivalWc -> tutorial_locs.tree
            TutorialStep.SurvivalGate -> tutorial_locs.survival_exit_l
            TutorialStep.ChefDoor -> tutorial_locs.nav_entry
            TutorialStep.ChefBread -> tutorial_locs.range
            TutorialStep.ChefExit -> tutorial_locs.nav_exit
            TutorialStep.QuestDoor -> tutorial_locs.quest_entry
            TutorialStep.QuestLadder -> tutorial_locs.quest_ladder
            TutorialStep.MineOres -> tutorial_locs.tin_rock
            TutorialStep.MineSmelt -> tutorial_locs.furnace
            TutorialStep.MineDagger -> tutorial_locs.anvil
            TutorialStep.MineGate -> tutorial_locs.mining_exit_l
            TutorialStep.CombatLadder -> tutorial_locs.combat_ladder
            TutorialStep.BankOpen -> tutorial_locs.bankbooth
            TutorialStep.PollView -> tutorial_locs.pollbooth
            TutorialStep.AccountExit -> tutorial_locs.bank_exit
            TutorialStep.PrayerExit -> tutorial_locs.prayer_exit_l
            else -> null
        }

    private data class SidePanel(
        val interf: InterfaceType,
        val target: ComponentType,
        val unlock: TutorialStep,
    )

    private companion object {
        /** Wide enough to cover each tutorial area from its entrance teleports. */
        private const val HINT_NPC_DISTANCE = 64

        /** `flashside` value that leaves every side icon alone. */
        private const val FLASH_SIDE_NONE = 0

        /**
         * Targets mirror the gameframe's own `StandardOverlays` mapping. Each panel unlocks on the
         * step that first asks the player to use it; the social/emote/music tabs stay locked for
         * the whole tutorial and come back in [end].
         */
        private val SIDE_PANELS =
            listOf(
                SidePanel(
                    interfaces.settings_side,
                    components.toplevel_target_side11,
                    TutorialStep.GielinorSettings,
                ),
                SidePanel(
                    interfaces.inventory,
                    components.toplevel_target_side3,
                    TutorialStep.SurvivalInv,
                ),
                SidePanel(
                    interfaces.stats,
                    components.toplevel_target_side1,
                    TutorialStep.SurvivalSkills,
                ),
                SidePanel(
                    interfaces.side_journal,
                    components.toplevel_target_side2,
                    TutorialStep.QuestList,
                ),
                SidePanel(
                    interfaces.wornitems,
                    components.toplevel_target_side4,
                    TutorialStep.CombatWorn,
                ),
                SidePanel(
                    interfaces.combat_interface,
                    components.toplevel_target_side0,
                    TutorialStep.CombatStyles,
                ),
                SidePanel(
                    interfaces.account,
                    components.toplevel_target_side8,
                    TutorialStep.AccountMgmt,
                ),
                SidePanel(
                    interfaces.prayerbook,
                    components.toplevel_target_side5,
                    TutorialStep.PrayerTab,
                ),
                SidePanel(
                    interfaces.magic_spellbook,
                    components.toplevel_target_side6,
                    TutorialStep.MagicTab,
                ),
                SidePanel(
                    interfaces.logout,
                    components.toplevel_target_side10,
                    TutorialStep.Completed,
                ),
                SidePanel(
                    interfaces.friends,
                    components.toplevel_target_side9,
                    TutorialStep.Completed,
                ),
                SidePanel(
                    interfaces.side_channels,
                    components.toplevel_target_side7,
                    TutorialStep.Completed,
                ),
                SidePanel(
                    interfaces.emote,
                    components.toplevel_target_side12,
                    TutorialStep.Completed,
                ),
                SidePanel(
                    interfaces.music,
                    components.toplevel_target_side13,
                    TutorialStep.Completed,
                ),
            )

        /** Live `midi_song_v2 id=62` (Newbie Melody) on first character-create open. */
        private val NEWBIE_MELODY = MidiType(internalId = 62, internalName = "newbie_melody")

        private fun guideHtml(step: TutorialStep): String =
            "<col=0000ff>${step.guideTitle}</col><br>${step.guideBody}"
    }
}
