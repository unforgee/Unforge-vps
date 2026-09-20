package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialFocusGuidance
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.content.interfaces.gameframe.config.gameframe_components
import org.rsmod.content.interfaces.settings.configs.setting_components
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Advances tutorial steps when side-panel stones / equipment stats are opened.
 *
 * Uses stone overlay buttons instead of [onIfOpen] — many tab interfaces already have exclusive
 * IfOpen handlers elsewhere in the codebase.
 */
class TutorialSidePanelScript
@Inject
constructor(
    private val progression: TutorialProgression,
    private val focus: TutorialFocusGuidance,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(setting_components.settings_tab) { onSettingsOpened(player) }
        onIfOverlayButton(setting_components.master_icon) { onSettingsOpened(player) }

        // Combat / Stats / Quest / Inventory / Worn / Prayer / Magic / Account stones.
        bindStones(
            gameframe_components.toplevel_stone0,
            tutorial_components.fixed_stone0,
            tutorial_components.pre_eoc_stone0,
        ) {
            onCombatOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone1,
            tutorial_components.fixed_stone1,
            tutorial_components.pre_eoc_stone1,
        ) {
            onStatsOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone2,
            tutorial_components.fixed_stone2,
            tutorial_components.pre_eoc_stone2,
        ) {
            onQuestListOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone3,
            tutorial_components.fixed_stone3,
            tutorial_components.pre_eoc_stone3,
        ) {
            onInventoryOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone4,
            tutorial_components.fixed_stone4,
            tutorial_components.pre_eoc_stone4,
        ) {
            onWornOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone5,
            tutorial_components.fixed_stone5,
            tutorial_components.pre_eoc_stone5,
        ) {
            onPrayerOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone6,
            tutorial_components.fixed_stone6,
            tutorial_components.pre_eoc_stone6,
        ) {
            onMagicOpened(it)
        }
        bindStones(
            gameframe_components.toplevel_stone8,
            tutorial_components.fixed_stone8,
            tutorial_components.pre_eoc_stone8,
        ) {
            onAccountOpened(it)
        }
    }

    private fun ScriptContext.bindStones(
        stretch: ComponentType,
        fixed: ComponentType,
        preEoc: ComponentType,
        action: (Player) -> Unit,
    ) {
        onIfOverlayButton(stretch) { action(player) }
        onIfOverlayButton(fixed) { action(player) }
        onIfOverlayButton(preEoc) { action(player) }
    }

    private fun onSettingsOpened(player: Player) {
        // The step only advances once the Gielinor Guide is spoken to again, so re-point at him
        // rather than re-applying the same step (which would just repeat "click the spanner").
        if (progression.current(player) == TutorialStep.GielinorSettings) {
            focus.pointBackToBasicsTutor(player)
        }
    }

    private fun onInventoryOpened(player: Player) {
        if (progression.current(player) == TutorialStep.SurvivalInv) {
            progression.setStep(player, TutorialStep.SurvivalFish)
        }
    }

    private fun onStatsOpened(player: Player) {
        // After fishing flashes skills (SurvivalSkills); opening the tab unlocks tools talk.
        if (progression.current(player) == TutorialStep.SurvivalSkills) {
            progression.setStep(player, TutorialStep.SurvivalTools)
        }
    }

    private fun onQuestListOpened(player: Player) {
        val step = progression.current(player)
        if (step == TutorialStep.QuestTalk || step == TutorialStep.QuestList) {
            progression.setStep(player, TutorialStep.QuestList)
        }
    }

    private fun onWornOpened(player: Player) {
        when (progression.current(player)) {
            TutorialStep.CombatWorn -> progression.setStep(player, TutorialStep.CombatStats)
            // Equipment-stats button is owned by the equipment plugin (exclusive IfOverlay);
            // opening worn again or talking to Vannaka advances dagger equip.
            TutorialStep.CombatStats -> progression.setStep(player, TutorialStep.CombatDagger)
            else -> Unit
        }
    }

    private fun onCombatOpened(player: Player) {
        if (progression.current(player) == TutorialStep.CombatStyles) {
            progression.setStep(player, TutorialStep.CombatMelee)
        }
    }

    private fun onAccountOpened(player: Player) {
        if (progression.current(player) == TutorialStep.AccountMgmt) {
            progression.setStep(player, TutorialStep.AccountMgmt)
        }
    }

    private fun onPrayerOpened(player: Player) {
        if (progression.current(player) == TutorialStep.PrayerTab) {
            progression.setStep(player, TutorialStep.PrayerTab)
        }
    }

    private fun onMagicOpened(player: Player) {
        if (progression.current(player) == TutorialStep.MagicTab) {
            progression.setStep(player, TutorialStep.MagicTab)
        }
    }
}
