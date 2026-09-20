package org.rsmod.content.interfaces.talenttree.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias talenttree_interfaces = TalentTreeInterfaces

typealias talenttree_components = TalentTreeComponents

object TalentTreeInterfaces : InterfaceReferences() {
    val unforge_talenttree = find("unforge_talenttree")
}

object TalentTreeComponents : ComponentReferences() {
    val root = find("unforge_talenttree:root")
    val title = find("unforge_talenttree:title")
    val avail = find("unforge_talenttree:avail")
    val pointsLabel = find("unforge_talenttree:points_label")
    val pointsValue = find("unforge_talenttree:points_value")
    val reset = find("unforge_talenttree:reset")
    val resetWrap = find("unforge_talenttree:reset_wrap")
    val resetBox = find("unforge_talenttree:reset_box")
    val resetText = find("unforge_talenttree:reset_text")
    val close = find("unforge_talenttree:close")
    val closeWrap = find("unforge_talenttree:close_wrap")
    val closeG = find("unforge_talenttree:close_g")
    val accentRule = find("unforge_talenttree:accent_rule")

    val content = find("unforge_talenttree:content")
    val footer = find("unforge_talenttree:footer")
    val scrollbar = find("unforge_talenttree:scrollbar")
    val scrollUp = find("unforge_talenttree:scroll_up")
    val scrollUpWrap = find("unforge_talenttree:scroll_up_wrap")
    val scrollUpBox = find("unforge_talenttree:scroll_up_box")
    val scrollUpText = find("unforge_talenttree:scroll_up_text")
    val scrollDown = find("unforge_talenttree:scroll_down")
    val scrollDownWrap = find("unforge_talenttree:scroll_down_wrap")
    val scrollDownBox = find("unforge_talenttree:scroll_down_box")
    val scrollDownText = find("unforge_talenttree:scroll_down_text")

    val tierHeaders =
        (1..TalentTreeInterfaceBuilder.TIER_COUNT).map { find("unforge_talenttree:tier${it}_hdr") }
    val tierSubs =
        (1..TalentTreeInterfaceBuilder.TIER_COUNT).map { find("unforge_talenttree:tier${it}_sub") }

    /** Per-card part lists, indexed by `cardIndex(tier, slot)` = (tier-1)*5 + slot. */
    val cardBgs = cardParts("bg")
    val cardBgsOn = cardParts("bg_on")
    val cardIframes = cardParts("iframe")
    val cardBds = cardParts("bd")
    val cardNames = cardParts("name")
    val cardIcons = cardParts("icon")
    val cardRanks = cardParts("rank")
    val cardTags = cardParts("tag")
    val cardCosts = cardParts("cost")
    val cardHits = cardParts("hit")
    val cardHitWraps = cardParts("hit_wrap")

    private fun cardParts(part: String): List<ComponentType> =
        List(TalentTreeInterfaceBuilder.CARD_COUNT) { find("unforge_talenttree:c${it}_$part") }

    /** Every component keyed by its child name - used by the builder for hover-script args. */
    val all: Map<String, ComponentType> = buildMap {
        fun reg(name: String) {
            put(name, find("unforge_talenttree:$name"))
        }
        listOf(
                "root",
                "bg",
                "border",
                "title",
                "avail",
                "points_label",
                "points_value",
                "reset",
                "reset_wrap",
                "reset_box",
                "reset_text",
                "close_wrap",
                "close",
                "close_g",
                "accent_rule",
                "content",
                "footer",
                "scrollbar",
                "scroll_up_wrap",
                "scroll_up",
                "scroll_up_box",
                "scroll_up_text",
                "scroll_down_wrap",
                "scroll_down",
                "scroll_down_box",
                "scroll_down_text",
            )
            .forEach(::reg)
        for (tier in 1..TalentTreeInterfaceBuilder.TIER_COUNT) {
            reg("tier${tier}_hdr")
            reg("tier${tier}_sub")
        }
        for (i in 0 until TalentTreeInterfaceBuilder.CARD_COUNT) {
            reg("c${i}_bg")
            reg("c${i}_bg_on")
            reg("c${i}_iframe")
            reg("c${i}_bd")
            reg("c${i}_name")
            reg("c${i}_icon")
            reg("c${i}_rank")
            reg("c${i}_tag")
            reg("c${i}_cost")
            reg("c${i}_hit_wrap")
            reg("c${i}_hit")
        }
    }
}
