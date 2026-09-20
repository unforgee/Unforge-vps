package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_dashboard_interfaces = CompanionDashboardInterfaces

typealias companion_dashboard_components = CompanionDashboardComponents

object CompanionDashboardInterfaces : InterfaceReferences() {
    val dashboard = find("companion_dashboard")
}

object CompanionDashboardComponents : ComponentReferences() {
    val root = find("companion_dashboard:root")
    val bg = find("companion_dashboard:bg")
    val border = find("companion_dashboard:border")
    val accent_rule = find("companion_dashboard:accent_rule")
    val title = find("companion_dashboard:title")
    val subtitle = find("companion_dashboard:subtitle")
    val status_badge = find("companion_dashboard:status_badge")
    val close_wrap = find("companion_dashboard:close_wrap")
    val close = find("companion_dashboard:close")
    val close_g = find("companion_dashboard:close_g")

    // Hero / Stats Card
    val hero_card = find("companion_dashboard:hero_card")
    val hero_border = find("companion_dashboard:hero_border")
    val hero_header = find("companion_dashboard:hero_header")
    val hp_track = find("companion_dashboard:hp_track")
    val hp_fill = find("companion_dashboard:hp_fill")
    val hp_text = find("companion_dashboard:hp_text")
    val stat_xp = find("companion_dashboard:stat_xp")
    val stat_class = find("companion_dashboard:stat_class")
    val stat_dmg = find("companion_dashboard:stat_dmg")
    val stat_speed = find("companion_dashboard:stat_speed")
    val stat_support = find("companion_dashboard:stat_support")
    val stat_pack = find("companion_dashboard:stat_pack")
    val stat_talents = find("companion_dashboard:stat_talents")
    val stat_gear = find("companion_dashboard:stat_gear")
    val stat_spell = find("companion_dashboard:stat_spell")
    val mode_label = find("companion_dashboard:mode_label")

    // Combat mode quick-switch (inside the hero card)
    val mode_def = find("companion_dashboard:mode_def")
    val mode_def_text = find("companion_dashboard:mode_def_text")
    val mode_agg = find("companion_dashboard:mode_agg")
    val mode_agg_text = find("companion_dashboard:mode_agg_text")
    val mode_pas = find("companion_dashboard:mode_pas")
    val mode_pas_text = find("companion_dashboard:mode_pas_text")

    // Right Action Buttons
    val btn_equip_wrap = find("companion_dashboard:btn_equip_wrap")
    val btn_equip = find("companion_dashboard:btn_equip")
    val btn_equip_box = find("companion_dashboard:btn_equip_box")
    val btn_equip_title = find("companion_dashboard:btn_equip_title")
    val btn_equip_desc = find("companion_dashboard:btn_equip_desc")

    val btn_talents_wrap = find("companion_dashboard:btn_talents_wrap")
    val btn_talents = find("companion_dashboard:btn_talents")
    val btn_talents_box = find("companion_dashboard:btn_talents_box")
    val btn_talents_title = find("companion_dashboard:btn_talents_title")
    val btn_talents_desc = find("companion_dashboard:btn_talents_desc")

    val btn_storage_wrap = find("companion_dashboard:btn_storage_wrap")
    val btn_storage = find("companion_dashboard:btn_storage")
    val btn_storage_box = find("companion_dashboard:btn_storage_box")
    val btn_storage_title = find("companion_dashboard:btn_storage_title")
    val btn_storage_desc = find("companion_dashboard:btn_storage_desc")

    val btn_upgrade_wrap = find("companion_dashboard:btn_upgrade_wrap")
    val btn_upgrade = find("companion_dashboard:btn_upgrade")
    val btn_upgrade_box = find("companion_dashboard:btn_upgrade_box")
    val btn_upgrade_title = find("companion_dashboard:btn_upgrade_title")
    val btn_upgrade_desc = find("companion_dashboard:btn_upgrade_desc")

    val btn_style_wrap = find("companion_dashboard:btn_style_wrap")
    val btn_style = find("companion_dashboard:btn_style")
    val btn_style_box = find("companion_dashboard:btn_style_box")
    val btn_style_title = find("companion_dashboard:btn_style_title")
    val btn_style_desc = find("companion_dashboard:btn_style_desc")

    val btn_morph_wrap = find("companion_dashboard:btn_morph_wrap")
    val btn_morph = find("companion_dashboard:btn_morph")
    val btn_morph_box = find("companion_dashboard:btn_morph_box")
    val btn_morph_title = find("companion_dashboard:btn_morph_title")
    val btn_morph_desc = find("companion_dashboard:btn_morph_desc")

    // Bottom Action Bar Buttons
    val btn_store_all_wrap = find("companion_dashboard:btn_store_all_wrap")
    val btn_store_all = find("companion_dashboard:btn_store_all")
    val btn_store_all_box = find("companion_dashboard:btn_store_all_box")
    val btn_store_all_text = find("companion_dashboard:btn_store_all_text")

    val btn_withdraw_all_wrap = find("companion_dashboard:btn_withdraw_all_wrap")
    val btn_withdraw_all = find("companion_dashboard:btn_withdraw_all")
    val btn_withdraw_all_box = find("companion_dashboard:btn_withdraw_all_box")
    val btn_withdraw_all_text = find("companion_dashboard:btn_withdraw_all_text")

    val btn_summon_wrap = find("companion_dashboard:btn_summon_wrap")
    val btn_summon = find("companion_dashboard:btn_summon")
    val btn_summon_box = find("companion_dashboard:btn_summon_box")
    val btn_summon_text = find("companion_dashboard:btn_summon_text")

    val btn_reset_talents_wrap = find("companion_dashboard:btn_reset_talents_wrap")
    val btn_reset_talents = find("companion_dashboard:btn_reset_talents")
    val btn_reset_talents_box = find("companion_dashboard:btn_reset_talents_box")
    val btn_reset_talents_text = find("companion_dashboard:btn_reset_talents_text")
    val btn_autoloot_wrap = find("companion_dashboard:btn_autoloot_wrap")
    val btn_autoloot = find("companion_dashboard:btn_autoloot")
    val btn_autoloot_box = find("companion_dashboard:btn_autoloot_box")
    val btn_autoloot_text = find("companion_dashboard:btn_autoloot_text")

    val btn_inspect_wrap = find("companion_dashboard:btn_inspect_wrap")
    val btn_inspect = find("companion_dashboard:btn_inspect")
    val btn_behaviour_wrap = find("companion_dashboard:btn_behaviour_wrap")
    val btn_behaviour = find("companion_dashboard:btn_behaviour")

    /** Every component keyed by its child name - used by the builder for hover-script args. */
    val all: Map<String, ComponentType> = buildMap {
        fun reg(name: String) {
            put(name, find("companion_dashboard:$name"))
        }
        listOf(
                "root",
                "bg",
                "border",
                "accent_rule",
                "title",
                "subtitle",
                "status_badge",
                "close_wrap",
                "close",
                "close_g",
                "hero_card",
                "hero_border",
                "hero_header",
                "hp_track",
                "hp_fill",
                "hp_text",
                "stat_xp",
                "stat_class",
                "stat_dmg",
                "stat_speed",
                "stat_support",
                "stat_pack",
                "stat_talents",
                "stat_gear",
                "stat_spell",
                "mode_label",
                "mode_def_wrap",
                "mode_def",
                "mode_def_box",
                "mode_def_text",
                "mode_agg_wrap",
                "mode_agg",
                "mode_agg_box",
                "mode_agg_text",
                "mode_pas_wrap",
                "mode_pas",
                "mode_pas_box",
                "mode_pas_text",
                "btn_equip_wrap",
                "btn_equip",
                "btn_equip_box",
                "btn_equip_title",
                "btn_equip_desc",
                "btn_talents_wrap",
                "btn_talents",
                "btn_talents_box",
                "btn_talents_title",
                "btn_talents_desc",
                "btn_storage_wrap",
                "btn_storage",
                "btn_storage_box",
                "btn_storage_title",
                "btn_storage_desc",
                "btn_upgrade_wrap",
                "btn_upgrade",
                "btn_upgrade_box",
                "btn_upgrade_title",
                "btn_upgrade_desc",
                "btn_style_wrap",
                "btn_style",
                "btn_style_box",
                "btn_style_title",
                "btn_style_desc",
                "btn_morph_wrap",
                "btn_morph",
                "btn_morph_box",
                "btn_morph_title",
                "btn_morph_desc",
                "btn_store_all_wrap",
                "btn_store_all",
                "btn_store_all_box",
                "btn_store_all_text",
                "btn_withdraw_all_wrap",
                "btn_withdraw_all",
                "btn_withdraw_all_box",
                "btn_withdraw_all_text",
                "btn_summon_wrap",
                "btn_summon",
                "btn_summon_box",
                "btn_summon_text",
                "btn_reset_talents_wrap",
                "btn_reset_talents",
                "btn_reset_talents_box",
                "btn_reset_talents_text",
                "btn_autoloot_wrap",
                "btn_autoloot",
                "btn_autoloot_box",
                "btn_autoloot_text",
                "btn_inspect_wrap",
                "btn_inspect",
                "btn_inspect_box",
                "btn_inspect_text",
                "btn_behaviour_wrap",
                "btn_behaviour",
                "btn_behaviour_box",
                "btn_behaviour_text",
            )
            .forEach { reg(it) }
    }
}
