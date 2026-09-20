package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_behaviour_interfaces = CompanionBehaviourInterfaces

typealias companion_behaviour_components = CompanionBehaviourComponents

object CompanionBehaviourInterfaces : InterfaceReferences() {
    val behaviour = find("companion_behaviour")
}

object CompanionBehaviourComponents : ComponentReferences() {
    val root = find("companion_behaviour:root")
    val title = find("companion_behaviour:title")
    val subtitle = find("companion_behaviour:subtitle")
    val closeWrap = find("companion_behaviour:close_wrap")
    val close = find("companion_behaviour:close")
    val status = find("companion_behaviour:status")

    val modeButtons =
        listOf(
            find("companion_behaviour:mode_pas"),
            find("companion_behaviour:mode_def"),
            find("companion_behaviour:mode_agg"),
        )
    val modeTexts =
        listOf(
            find("companion_behaviour:mode_pas_text"),
            find("companion_behaviour:mode_def_text"),
            find("companion_behaviour:mode_agg_text"),
        )
    val targetButtons =
        listOf(
            find("companion_behaviour:tgt_owner"),
            find("companion_behaviour:tgt_attacker"),
            find("companion_behaviour:tgt_nearest"),
            find("companion_behaviour:tgt_ally"),
        )
    val targetTexts =
        listOf(
            find("companion_behaviour:tgt_owner_text"),
            find("companion_behaviour:tgt_attacker_text"),
            find("companion_behaviour:tgt_nearest_text"),
            find("companion_behaviour:tgt_ally_text"),
        )
    val styleButtons =
        listOf(
            find("companion_behaviour:sty_melee"),
            find("companion_behaviour:sty_ranged"),
            find("companion_behaviour:sty_magic"),
        )
    val styleTexts =
        listOf(
            find("companion_behaviour:sty_melee_text"),
            find("companion_behaviour:sty_ranged_text"),
            find("companion_behaviour:sty_magic_text"),
        )

    val distValue = find("companion_behaviour:dist_val")
    val distMinus = find("companion_behaviour:dist_minus")
    val distPlus = find("companion_behaviour:dist_plus")

    val catchToggle = find("companion_behaviour:catch_tgl")
    val catchText = find("companion_behaviour:catch_tgl_text")

    val tauntToggle = find("companion_behaviour:taunt_tgl")
    val tauntText = find("companion_behaviour:taunt_tgl_text")
    val defendToggle = find("companion_behaviour:defend_tgl")
    val defendText = find("companion_behaviour:defend_tgl_text")
    val healToggle = find("companion_behaviour:heal_tgl")
    val healText = find("companion_behaviour:heal_tgl_text")
    val buffToggle = find("companion_behaviour:buff_tgl")
    val buffText = find("companion_behaviour:buff_tgl_text")

    val healBelowValue = find("companion_behaviour:hb_val")
    val healBelowMinus = find("companion_behaviour:hb_minus")
    val healBelowPlus = find("companion_behaviour:hb_plus")

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("companion_behaviour:$name")) }
    }

    private fun childNames(): List<String> = buildList {
        addAll(
            listOf(
                "root",
                "bg",
                "border",
                "title",
                "subtitle",
                "close_wrap",
                "close",
                "close_g",
                "accent_rule",
                "divider",
                "left_panel",
                "left_panel_frame",
                "right_panel",
                "right_panel_frame",
                "mode_head",
                "tgt_head",
                "sty_head",
                "fol_head",
                "dist_val",
                "catch_head",
                "role_head",
                "hb_head",
                "hb_val",
                "status",
            )
        )
        fun button(name: String) {
            add("${name}_wrap")
            add(name)
            add("${name}_box")
            add("${name}_text")
        }
        button("mode_pas")
        button("mode_def")
        button("mode_agg")
        button("tgt_owner")
        button("tgt_attacker")
        button("tgt_nearest")
        button("tgt_ally")
        button("sty_melee")
        button("sty_ranged")
        button("sty_magic")
        button("dist_minus")
        button("dist_plus")
        button("catch_tgl")
        button("taunt_tgl")
        button("defend_tgl")
        button("heal_tgl")
        button("buff_tgl")
        button("hb_minus")
        button("hb_plus")
    }
}
