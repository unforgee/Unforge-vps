package org.rsmod.content.other.devhub.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias devhub_interfaces = DevHubInterfaces

typealias devhub_components = DevHubComponents

object DevHubInterfaces : InterfaceReferences() {
    val dev_hub = find("dev_hub")
}

object DevHubComponents : ComponentReferences() {
    val root = find("dev_hub:root")
    val title = find("dev_hub:title")
    val close = find("dev_hub:close")
    val closeWrap = find("dev_hub:close_wrap")
    val closeG = find("dev_hub:close_g")
    val grid = find("dev_hub:grid")
    val page_prev = find("dev_hub:page_prev")
    val pagePrevWrap = find("dev_hub:page_prev_wrap")
    val page_prev_text = find("dev_hub:page_prev_text")
    val page_next = find("dev_hub:page_next")
    val pageNextWrap = find("dev_hub:page_next_wrap")
    val page_next_text = find("dev_hub:page_next_text")
    val page_label = find("dev_hub:page_label")
    val search_button = find("dev_hub:search_button")
    val searchButtonWrap = find("dev_hub:search_button_wrap")
    val search_button_text = find("dev_hub:search_button_text")

    /** One entry per [org.rsmod.content.other.devhub.DevHubTab], in enum order. */
    val tabWraps =
        listOf(
            find("dev_hub:tab_items_wrap"),
            find("dev_hub:tab_equipment_wrap"),
            find("dev_hub:tab_gear_wrap"),
            find("dev_hub:tab_teleports_wrap"),
            find("dev_hub:tab_skills_wrap"),
            find("dev_hub:tab_misc_wrap"),
        )

    val tabButtons =
        listOf(
            find("dev_hub:tab_items"),
            find("dev_hub:tab_equipment"),
            find("dev_hub:tab_gear"),
            find("dev_hub:tab_teleports"),
            find("dev_hub:tab_skills"),
            find("dev_hub:tab_misc"),
        )

    val tabBoxes =
        listOf(
            find("dev_hub:tab_items_box"),
            find("dev_hub:tab_equipment_box"),
            find("dev_hub:tab_gear_box"),
            find("dev_hub:tab_teleports_box"),
            find("dev_hub:tab_skills_box"),
            find("dev_hub:tab_misc_box"),
        )

    val tabSels =
        listOf(
            find("dev_hub:tab_items_sel"),
            find("dev_hub:tab_equipment_sel"),
            find("dev_hub:tab_gear_sel"),
            find("dev_hub:tab_teleports_sel"),
            find("dev_hub:tab_skills_sel"),
            find("dev_hub:tab_misc_sel"),
        )

    val tabTexts =
        listOf(
            find("dev_hub:tab_items_text"),
            find("dev_hub:tab_equipment_text"),
            find("dev_hub:tab_gear_text"),
            find("dev_hub:tab_teleports_text"),
            find("dev_hub:tab_skills_text"),
            find("dev_hub:tab_misc_text"),
        )

    val catWraps = List(DevHubInterfaceBuilder.CATEGORY_ROWS) { find("dev_hub:cat${it}_wrap") }

    val catButtons = List(DevHubInterfaceBuilder.CATEGORY_ROWS) { find("dev_hub:cat$it") }

    val catBoxes = List(DevHubInterfaceBuilder.CATEGORY_ROWS) { find("dev_hub:cat${it}_box") }

    val catSels = List(DevHubInterfaceBuilder.CATEGORY_ROWS) { find("dev_hub:cat${it}_sel") }

    val catTexts = List(DevHubInterfaceBuilder.CATEGORY_ROWS) { find("dev_hub:cat${it}_text") }

    val teleportScroll = find("dev_hub:teleport_scroll")
    val teleportScrollUp = find("dev_hub:teleport_scroll_up")
    val teleportScrollUpWrap = find("dev_hub:teleport_scroll_up_wrap")
    val teleportScrollDown = find("dev_hub:teleport_scroll_down")
    val teleportScrollDownWrap = find("dev_hub:teleport_scroll_down_wrap")
    val itemScrollUp = find("dev_hub:item_scroll_up")
    val itemScrollUpWrap = find("dev_hub:item_scroll_up_wrap")
    val itemScrollDown = find("dev_hub:item_scroll_down")
    val itemScrollDownWrap = find("dev_hub:item_scroll_down_wrap")
    val teleportRowWraps =
        List(DevHubInterfaceBuilder.TELEPORT_ROW_COUNT) { find("dev_hub:teleport_row${it}_wrap") }
    val teleportRows =
        List(DevHubInterfaceBuilder.TELEPORT_ROW_COUNT) { find("dev_hub:teleport_row$it") }
    val teleportRowBoxes =
        List(DevHubInterfaceBuilder.TELEPORT_ROW_COUNT) { find("dev_hub:teleport_row${it}_box") }
    val teleportRowTexts =
        List(DevHubInterfaceBuilder.TELEPORT_ROW_COUNT) { find("dev_hub:teleport_row${it}_text") }
}
