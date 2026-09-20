package org.rsmod.content.interfaces.settings.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias setting_components = SettingComponents

object SettingComponents : ComponentReferences() {
    // Rev 239: orbs:runbutton (27) is the non-clickable graphic; the Toggle Run target is
    // orbs:28 (packed as orbs:runenergy_text), matching settings_side:runmode.
    val runbutton_orb = find("orbs:runenergy_text")
    val runmode = find("settings_side:runmode")

    val settings_tab = find("settings_side:settings_tab")
    val audio_tab = find("settings_side:audio_tab")
    val display_tab = find("settings_side:display_tab")
    val settings_open = find("settings_side:settings_open")

    val skull_prevention = find("settings_side:skull_prevention")
    val attack_priority_player_buttons = find("settings_side:attack_priority_player_buttons")
    val attack_priority_npc_buttons = find("settings_side:attack_priority_npc_buttons")
    val acceptaid = find("settings_side:acceptaid")
    val houseoptions = find("settings_side:houseoptions")
    val bondoptions = find("settings_side:bondoptions")

    val master_icon = find("settings_side:master_icon")
    val master_bobble_container = find("settings_side:master_bobble_container")
    val music_icon = find("settings_side:music_icon")
    val music_bobble_container = find("settings_side:music_bobble_container")
    val sound_icon = find("settings_side:sound_icon")
    val sound_bobble_container = find("settings_side:sound_bobble_container")
    val areasound_icon = find("settings_side:areasound_icon")
    val areasounds_bobble_container = find("settings_side:areasounds_bobble_container")
    // Legacy name kept for AudioSettingsScript; child 127 is zoom28 in rev 239.
    // TODO(protocol): wire unlock-message toggle to the correct 239 component.
    val music_toggle = find("settings_side:zoom28")

    val brightness_bobble_container = find("settings_side:brightness_bobble_container")
    val zoom_toggle = find("settings_side:zoom_toggle")
    val zoom_slider = find("settings_side:zoom_slider")
    val client_type_buttons = find("settings_side:display_dynamic_setting_1_buttons")

    val settings_close = find("settings:close")
}
