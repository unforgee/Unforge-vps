@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias tutorial_interfaces = TutorialInterfaces

typealias tutorial_components = TutorialComponents

object TutorialInterfaces : InterfaceReferences() {
    /** Live OSRS Tutorial Island chrome (`614`). */
    val tutorial_overlay = find("tutorial_overlay")
    /** Live character creator modal (`679`). */
    val player_design = find("player_design")
    /** Live Past Experience / familiarity modal (`929`). */
    val tutorial_player_experience = find("tutorial_player_experience")
    /** Chat-layer text host opened with [org.rsmod.api.player.output.ClientScripts.mesOverlay]. */
    val mesoverlay = find("mesoverlay")

    val makeover = find("makeover")
    val makeover_mage = find("makeover_mage")
    val ironman_setup = find("ironman_setup")
    val cr_tutorial = find("cr_tutorial")
}

object TutorialComponents : ComponentReferences() {
    val ironman_none_button = find("ironman_setup:none_button")
    val ironman_im_button = find("ironman_setup:im_button")
    val ironman_uim_button = find("ironman_setup:uim_button")
    val ironman_hcim_button = find("ironman_setup:hcim_button")
    val ironman_gim_button = find("ironman_setup:gim_button")
    val ironman_hcgim_button = find("ironman_setup:hcgim_button")
    val ironman_ugim_button = find("ironman_setup:ugim_button")

    val tutorial_noclick = find("tutorial_overlay:noclick")
    val hint_layer = find("tutorial_overlay:hint_layer")
    val hint_text_1 = find("tutorial_overlay:hint_text_1")
    val hint_text_2 = find("tutorial_overlay:hint_text_2")
    val hint_text_3 = find("tutorial_overlay:hint_text_3")
    val hint_text_4 = find("tutorial_overlay:hint_text_4")
    val progressbar = find("tutorial_overlay:progressbar")
    val progressbar_container = find("tutorial_overlay:progressbar_container")

    val player_design_confirm = find("player_design:confirm")
    val player_design_pronouns = find("player_design:pronouns_buttons")

    val experience_content = find("tutorial_player_experience:content")
    val experience_button_new = find("tutorial_player_experience:button_new")
    val experience_button_returning = find("tutorial_player_experience:button_returning")
    val experience_button_experienced = find("tutorial_player_experience:button_experienced")

    val head_left = find("player_design:head_left")
    val head_right = find("player_design:head_right")
    val jaw_left = find("player_design:jaw_left")
    val jaw_right = find("player_design:jaw_right")
    val torso_left = find("player_design:torso_left")
    val torso_right = find("player_design:torso_right")
    val arms_left = find("player_design:arms_left")
    val arms_right = find("player_design:arms_right")
    val hands_left = find("player_design:hands_left")
    val hands_right = find("player_design:hands_right")
    val legs_left = find("player_design:legs_left")
    val legs_right = find("player_design:legs_right")
    val feet_left = find("player_design:feet_left")
    val feet_right = find("player_design:feet_right")

    val hair_left = find("player_design:hair_left")
    val hair_right = find("player_design:hair_right")
    val torso_col_left = find("player_design:torso_col_left")
    val torso_col_right = find("player_design:torso_col_right")
    val legs_col_left = find("player_design:legs_col_left")
    val legs_col_right = find("player_design:legs_col_right")
    val feet_col_left = find("player_design:feet_col_left")
    val feet_col_right = find("player_design:feet_col_right")
    val skin_left = find("player_design:skin_left")
    val skin_right = find("player_design:skin_right")

    val gender_male = find("player_design:gender_male")
    val gender_female = find("player_design:gender_female")

    val spell_wind_strike = find("magic_spellbook:wind_strike")
    val spell_home_teleport = find("magic_spellbook:teleport_home_standard")
    val worn_equipment_stats = find("wornitems:equipment")

    val fixed_stone0 = find("toplevel:stone0")
    val fixed_stone1 = find("toplevel:stone1")
    val fixed_stone2 = find("toplevel:stone2")
    val fixed_stone3 = find("toplevel:stone3")
    val fixed_stone4 = find("toplevel:stone4")
    val fixed_stone5 = find("toplevel:stone5")
    val fixed_stone6 = find("toplevel:stone6")
    val fixed_stone8 = find("toplevel:stone8")

    val pre_eoc_stone0 = find("toplevel_pre_eoc:stone0")
    val pre_eoc_stone1 = find("toplevel_pre_eoc:stone1")
    val pre_eoc_stone2 = find("toplevel_pre_eoc:stone2")
    val pre_eoc_stone3 = find("toplevel_pre_eoc:stone3")
    val pre_eoc_stone4 = find("toplevel_pre_eoc:stone4")
    val pre_eoc_stone5 = find("toplevel_pre_eoc:stone5")
    val pre_eoc_stone6 = find("toplevel_pre_eoc:stone6")
    val pre_eoc_stone8 = find("toplevel_pre_eoc:stone8")
}
