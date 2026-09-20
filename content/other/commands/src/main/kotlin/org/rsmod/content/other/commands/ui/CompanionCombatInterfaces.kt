package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_combat_interfaces = CompanionCombatInterfaces

typealias companion_combat_components = CompanionCombatComponents

object CompanionCombatInterfaces : InterfaceReferences() {
    val combat = find("companion_combat")
}

object CompanionCombatComponents : ComponentReferences() {
    val root = find("companion_combat:root")
    val bg = find("companion_combat:bg")
    val border = find("companion_combat:border")
    val accent_rule = find("companion_combat:accent_rule")
    val title = find("companion_combat:title")
    val subtitle = find("companion_combat:subtitle")
    val close = find("companion_combat:close")

    val mode_lbl = find("companion_combat:mode_lbl")
    val mode_desc = find("companion_combat:mode_desc")
    val mode_def = find("companion_combat:mode_def")
    val mode_agg = find("companion_combat:mode_agg")
    val mode_pas = find("companion_combat:mode_pas")
    val mode_def_text = find("companion_combat:mode_def_text")
    val mode_agg_text = find("companion_combat:mode_agg_text")
    val mode_pas_text = find("companion_combat:mode_pas_text")

    val style_melee = find("companion_combat:style_melee")
    val style_ranged = find("companion_combat:style_ranged")
    val style_magic = find("companion_combat:style_magic")
    val style_melee_text = find("companion_combat:style_melee_text")
    val style_ranged_text = find("companion_combat:style_ranged_text")
    val style_magic_text = find("companion_combat:style_magic_text")

    val book_standard = find("companion_combat:book_standard")
    val book_ancients = find("companion_combat:book_ancients")
    val book_standard_text = find("companion_combat:book_standard_text")
    val book_ancients_text = find("companion_combat:book_ancients_text")

    val cast_current = find("companion_combat:cast_current")
    val cast_choose = find("companion_combat:cast_choose")
    val cast_clear = find("companion_combat:cast_clear")

    val status = find("companion_combat:status")
    val back = find("companion_combat:back")

    /** Every component keyed by its child name - used by the builder for hover-script args. */
    val all: Map<String, ComponentType> = buildMap {
        fun reg(name: String) {
            put(name, find("companion_combat:$name"))
        }
        listOf(
                "root",
                "bg",
                "border",
                "accent_rule",
                "title",
                "subtitle",
                "close_wrap",
                "close",
                "close_g",
                "mode_lbl",
                "mode_desc",
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
                "style_lbl",
                "style_melee_wrap",
                "style_melee",
                "style_melee_box",
                "style_melee_text",
                "style_ranged_wrap",
                "style_ranged",
                "style_ranged_box",
                "style_ranged_text",
                "style_magic_wrap",
                "style_magic",
                "style_magic_box",
                "style_magic_text",
                "book_lbl",
                "book_note",
                "book_standard_wrap",
                "book_standard",
                "book_standard_box",
                "book_standard_text",
                "book_ancients_wrap",
                "book_ancients",
                "book_ancients_box",
                "book_ancients_text",
                "cast_lbl",
                "cast_current",
                "cast_choose_wrap",
                "cast_choose",
                "cast_choose_box",
                "cast_choose_text",
                "cast_clear_wrap",
                "cast_clear",
                "cast_clear_box",
                "cast_clear_text",
                "status",
                "back_wrap",
                "back",
                "back_box",
                "back_text",
            )
            .forEach { reg(it) }
    }
}
