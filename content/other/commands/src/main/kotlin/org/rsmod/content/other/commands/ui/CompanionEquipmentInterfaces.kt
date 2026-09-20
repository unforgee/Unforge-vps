package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_equipment_interfaces = CompanionEquipmentInterfaces

typealias companion_equipment_components = CompanionEquipmentComponents

object CompanionEquipmentInterfaces : InterfaceReferences() {
    val equipment = find("companion_equipment")
}

object CompanionEquipmentComponents : ComponentReferences() {
    val root = find("companion_equipment:root")
    val title = find("companion_equipment:title")
    val subtitle = find("companion_equipment:subtitle")
    val closeWrap = find("companion_equipment:close_wrap")
    val close = find("companion_equipment:close")
    val inventory = find("companion_equipment:inventory")
    val status = find("companion_equipment:status")

    val slotWraps = slotParts("wrap")
    val slotHits = slotParts("hit")
    val slotBoxes = slotParts("box")
    val slotFrames = slotParts("frame")
    val slotObjs = slotParts("obj")
    val slotLabels = slotParts("label")

    val offLines = listOf(find("companion_equipment:off_a"), find("companion_equipment:off_b"))
    val defLines = listOf(find("companion_equipment:def_a"), find("companion_equipment:def_b"))
    val othLines = listOf(find("companion_equipment:oth_a"), find("companion_equipment:oth_b"))
    val vitLines =
        listOf(
            find("companion_equipment:vit_a"),
            find("companion_equipment:vit_b"),
            find("companion_equipment:vit_c"),
        )
    val affixLines =
        List(CompanionEquipmentInterfaceBuilder.AFFIX_ROWS) { find("companion_equipment:affix$it") }

    private fun slotParts(part: String): List<ComponentType> =
        List(CompanionEquipmentInterfaceBuilder.SLOT_COUNT) {
            find("companion_equipment:s${it}_$part")
        }

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("companion_equipment:$name")) }
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
                "inventory_head",
                "inventory",
                "worn_head",
                "left_panel",
                "left_panel_frame",
                "right_panel",
                "right_panel_frame",
            )
        )
        for (i in 0 until CompanionEquipmentInterfaceBuilder.SLOT_COUNT) {
            add("s${i}_wrap")
            add("s${i}_hit")
            add("s${i}_box")
            add("s${i}_frame")
            add("s${i}_obj")
            add("s${i}_label")
        }
        addAll(
            listOf(
                "off_head",
                "off_a",
                "off_b",
                "def_head",
                "def_a",
                "def_b",
                "oth_head",
                "oth_a",
                "oth_b",
                "vit_head",
                "vit_a",
                "vit_b",
                "vit_c",
                "aff_head",
            )
        )
        for (i in 0 until CompanionEquipmentInterfaceBuilder.AFFIX_ROWS) {
            add("affix$i")
        }
        add("status")
    }
}
