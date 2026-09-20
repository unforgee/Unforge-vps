package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_inspect_interfaces = CompanionInspectInterfaces

typealias companion_inspect_components = CompanionInspectComponents

object CompanionInspectInterfaces : InterfaceReferences() {
    val inspect = find("companion_inspect")
}

object CompanionInspectComponents : ComponentReferences() {
    val root = find("companion_inspect:root")
    val title = find("companion_inspect:title")
    val subtitle = find("companion_inspect:subtitle")
    val closeWrap = find("companion_inspect:close_wrap")
    val close = find("companion_inspect:close")
    val status = find("companion_inspect:status")

    val idLines = rows("id", CompanionInspectInterfaceBuilder.IDENTITY_ROWS)
    val evoLines = rows("evo", CompanionInspectInterfaceBuilder.EVOLUTION_ROWS)
    val sklLines = rows("skl", CompanionInspectInterfaceBuilder.SKILL_ROWS)
    val setLines = rows("set", CompanionInspectInterfaceBuilder.SET_ROWS)
    val stLines = rows("st", CompanionInspectInterfaceBuilder.STAT_ROWS)
    val abLines = rows("ab", CompanionInspectInterfaceBuilder.ABILITY_ROWS)
    val tlLines = rows("tl", CompanionInspectInterfaceBuilder.TALENT_ROWS)

    private fun rows(prefix: String, count: Int): List<ComponentType> =
        List(count) { find("companion_inspect:${prefix}$it") }

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("companion_inspect:$name")) }
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
                "id_head",
                "evo_head",
                "skl_head",
                "set_head",
                "status",
                "st_head",
                "ab_head",
                "tl_head",
            )
        )
        fun rows(prefix: String, count: Int) {
            for (i in 0 until count) add("${prefix}$i")
        }
        rows("id", CompanionInspectInterfaceBuilder.IDENTITY_ROWS)
        rows("evo", CompanionInspectInterfaceBuilder.EVOLUTION_ROWS)
        rows("skl", CompanionInspectInterfaceBuilder.SKILL_ROWS)
        rows("set", CompanionInspectInterfaceBuilder.SET_ROWS)
        rows("st", CompanionInspectInterfaceBuilder.STAT_ROWS)
        rows("ab", CompanionInspectInterfaceBuilder.ABILITY_ROWS)
        rows("tl", CompanionInspectInterfaceBuilder.TALENT_ROWS)
    }
}
