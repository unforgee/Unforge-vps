package org.rsmod.content.interfaces.skillshop.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias skillshop_interfaces = SkillShopInterfaces

typealias skillshop_components = SkillShopComponents

object SkillShopInterfaces : InterfaceReferences() {
    val unforge_skillshop = find("unforge_skillshop")
}

object SkillShopComponents : ComponentReferences() {
    val root = find("unforge_skillshop:root")
    val title = find("unforge_skillshop:title")
    val pointsLabel = find("unforge_skillshop:points_label")
    val pointsValue = find("unforge_skillshop:points_value")
    val close = find("unforge_skillshop:close")
    val closeWrap = find("unforge_skillshop:close_wrap")
    val closeG = find("unforge_skillshop:close_g")
    val accentRule = find("unforge_skillshop:accent_rule")

    val content = find("unforge_skillshop:content")
    val footer = find("unforge_skillshop:footer")
    val scrollbar = find("unforge_skillshop:scrollbar")
    val scrollUp = find("unforge_skillshop:scroll_up")
    val scrollUpWrap = find("unforge_skillshop:scroll_up_wrap")
    val scrollUpBox = find("unforge_skillshop:scroll_up_box")
    val scrollUpText = find("unforge_skillshop:scroll_up_text")
    val scrollDown = find("unforge_skillshop:scroll_down")
    val scrollDownWrap = find("unforge_skillshop:scroll_down_wrap")
    val scrollDownBox = find("unforge_skillshop:scroll_down_box")
    val scrollDownText = find("unforge_skillshop:scroll_down_text")

    val sectionHeaders =
        SkillShopCategory.entries.map { find("unforge_skillshop:sec_${it.name.lowercase()}") }

    val entryBgs = List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_bg") }
    val entryBgBorders =
        List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_bg_border") }
    val entryNames = List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_name") }
    val entryDescs = List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_desc") }
    val entryPrices =
        List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_price") }
    val entryBuys = List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_buy") }
    val entryBuyWraps =
        List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_buy_wrap") }
    val entryBuyBoxes =
        List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_buy_box") }
    val entryBuyTexts =
        List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_buy_text") }
    val entryTags = List(SkillShopEntry.entries.size) { find("unforge_skillshop:entry${it}_tag") }

    /** Every component keyed by its child name - used by the builder for hover-script args. */
    val all: Map<String, ComponentType> = buildMap {
        fun reg(name: String) {
            put(name, find("unforge_skillshop:$name"))
        }
        listOf(
                "root",
                "bg",
                "border",
                "title",
                "points_label",
                "points_value",
                "close",
                "close_wrap",
                "close_g",
                "accent_rule",
                "content",
                "footer",
                "scrollbar",
                "scroll_up",
                "scroll_up_wrap",
                "scroll_up_box",
                "scroll_up_text",
                "scroll_down",
                "scroll_down_wrap",
                "scroll_down_box",
                "scroll_down_text",
            )
            .forEach { reg(it) }
        for (category in SkillShopCategory.entries) {
            reg("sec_${category.name.lowercase()}")
        }
        for (i in SkillShopEntry.entries.indices) {
            reg("entry${i}_bg")
            reg("entry${i}_bg_border")
            reg("entry${i}_name")
            reg("entry${i}_desc")
            reg("entry${i}_price")
            reg("entry${i}_buy")
            reg("entry${i}_buy_wrap")
            reg("entry${i}_buy_box")
            reg("entry${i}_buy_text")
            reg("entry${i}_tag")
        }
    }
}
