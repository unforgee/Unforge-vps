package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias companion_talents_interfaces = CompanionTalentsInterfaces

typealias companion_talents_components = CompanionTalentsComponents

object CompanionTalentsInterfaces : InterfaceReferences() {
    val talents = find("companion_talents")
}

object CompanionTalentsComponents : ComponentReferences() {
    val root = find("companion_talents:root")
    val title = find("companion_talents:title")
    val subtitle = find("companion_talents:subtitle")
    val closeWrap = find("companion_talents:close_wrap")
    val close = find("companion_talents:close")
    val closeG = find("companion_talents:close_g")
    val accentRule = find("companion_talents:accent_rule")
    val status = find("companion_talents:status")

    val cardWraps = cardParts("wrap")
    val cardHits = cardParts("hit")
    val cardBoxes = cardParts("box")
    val cardSels = cardParts("sel")
    val cardNames = cardParts("name")
    val cardTiers = cardParts("tier")
    val cardRanks = cardParts("rank")
    val cardEffects = cardParts("fx")

    val roleWraps = roleParts("wrap")
    val roleHits = roleParts("")
    val roleBoxes = roleParts("box")
    val roleSels = roleParts("sel")
    val roleTexts = roleParts("text")

    val resetWrap = find("companion_talents:reset_wrap")
    val reset = find("companion_talents:reset")
    val resetBox = find("companion_talents:reset_box")
    val resetText = find("companion_talents:reset_text")
    val talentScroll = find("companion_talents:talent_scroll")
    val scrollUp = find("companion_talents:scroll_up")
    val scrollDown = find("companion_talents:scroll_down")
    val abilitySlots = List(3) { find("companion_talents:ability$it") }
    val abilityTexts = List(3) { find("companion_talents:ability${it}_text") }

    private fun cardParts(part: String): List<ComponentType> =
        List(CompanionTalentsInterfaceBuilder.TALENT_ROWS) {
            find("companion_talents:t${it}_$part")
        }

    private fun roleParts(part: String): List<ComponentType> =
        List(CompanionTalentsInterfaceBuilder.ROLE_COUNT) { index ->
            find("companion_talents:r$index" + if (part.isEmpty()) "" else "_$part")
        }

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("companion_talents:$name")) }
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
            )
        )
        for (i in 0 until CompanionTalentsInterfaceBuilder.TALENT_ROWS) {
            add("t${i}_wrap")
            add("t${i}_hit")
            add("t${i}_box")
            add("t${i}_sel")
            add("t${i}_name")
            add("t${i}_tier")
            add("t${i}_rank")
            add("t${i}_fx")
        }
        add("status")
        addAll(listOf("talent_scroll", "scroll_bar"))
        addAll(
            listOf(
                "scroll_up_wrap",
                "scroll_up",
                "scroll_up_box",
                "scroll_up_text",
                "scroll_down_wrap",
                "scroll_down",
                "scroll_down_box",
                "scroll_down_text",
            )
        )
        for (i in 0 until CompanionTalentsInterfaceBuilder.ROLE_COUNT) {
            add("r${i}_wrap")
            add("r$i")
            add("r${i}_box")
            add("r${i}_sel")
            add("r${i}_text")
        }
        addAll(listOf("reset_wrap", "reset", "reset_box", "reset_text"))
        for (i in 0 until 3) addAll(
            listOf(
                "ability${i}_wrap",
                "ability$i",
                "ability${i}_box",
                "ability${i}_icon",
                "ability${i}_text",
            )
        )
    }
}
