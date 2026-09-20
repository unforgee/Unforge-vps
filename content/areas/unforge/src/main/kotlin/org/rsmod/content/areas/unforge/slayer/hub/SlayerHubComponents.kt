package org.rsmod.content.areas.unforge.slayer.hub

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.content.areas.unforge.slayer.CwSlayerUnlock
import org.rsmod.game.type.comp.ComponentType

typealias slayer_interfaces = SlayerHubInterfaces

typealias slayer_components = SlayerHubComponents

object SlayerHubInterfaces : InterfaceReferences() {
    val slayer_hub = find("slayer_hub")
}

object SlayerHubComponents : ComponentReferences() {
    val root = find("slayer_hub:root")
    val title = find("slayer_hub:title")
    val pointsLabel = find("slayer_hub:points_label")
    val pointsValue = find("slayer_hub:points_value")
    val close = find("slayer_hub:close")
    val accentRule = find("slayer_hub:accent_rule")

    val tabTask = find("slayer_hub:tab_task")
    val tabTaskText = find("slayer_hub:tab_task_text")
    val tabTaskUnderline = find("slayer_hub:tab_task_underline")
    val tabRewards = find("slayer_hub:tab_rewards")
    val tabRewardsText = find("slayer_hub:tab_rewards_text")
    val tabRewardsUnderline = find("slayer_hub:tab_rewards_underline")

    // Assignment page.
    val taskCard = find("slayer_hub:task_card")
    val taskCardBorder = find("slayer_hub:task_card_border")
    val cardLabel = find("slayer_hub:card_label")
    val npcHead = find("slayer_hub:npc_head")
    val taskName = find("slayer_hub:task_name")
    val taskDifficulty = find("slayer_hub:task_difficulty")
    val taskHint = find("slayer_hub:task_hint")
    val taskRemaining = find("slayer_hub:task_remaining")
    val pipTracks =
        List(SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS) { find("slayer_hub:pip_track$it") }
    val pipFills =
        List(SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS) { find("slayer_hub:pip_fill$it") }

    val statPointsCard = find("slayer_hub:stat_points_card")
    val statPointsCardBorder = find("slayer_hub:stat_points_card_border")
    val statPointsLabel = find("slayer_hub:stat_points_label")
    val statPointsValue = find("slayer_hub:stat_points_value")
    val statDoneCard = find("slayer_hub:stat_done_card")
    val statDoneCardBorder = find("slayer_hub:stat_done_card_border")
    val statDoneLabel = find("slayer_hub:stat_done_label")
    val statDoneValue = find("slayer_hub:stat_done_value")
    val statWildCard = find("slayer_hub:stat_wild_card")
    val statWildCardBorder = find("slayer_hub:stat_wild_card_border")
    val statWildLabel = find("slayer_hub:stat_wild_label")
    val statWildValue = find("slayer_hub:stat_wild_value")

    val teleportButtonWrap = find("slayer_hub:teleport_btn_wrap")
    val teleportButton = find("slayer_hub:teleport_btn")
    val teleportBox = find("slayer_hub:teleport_btn_box")
    val teleportText = find("slayer_hub:teleport_btn_text")
    val cancelButtonWrap = find("slayer_hub:cancel_btn_wrap")
    val cancelButton = find("slayer_hub:cancel_btn")
    val cancelBox = find("slayer_hub:cancel_btn_box")
    val cancelText = find("slayer_hub:cancel_btn_text")
    val blockButtonWrap = find("slayer_hub:block_btn_wrap")
    val blockButton = find("slayer_hub:block_btn")
    val blockBox = find("slayer_hub:block_btn_box")
    val blockText = find("slayer_hub:block_btn_text")

    val wildNote = find("slayer_hub:wild_note")
    val milestone = find("slayer_hub:milestone")

    val emptyTitle = find("slayer_hub:empty_title")
    val emptyHint = find("slayer_hub:empty_hint")
    val getEasyWrap = find("slayer_hub:get_easy_wrap")
    val getEasy = find("slayer_hub:get_easy")
    val getEasyBox = find("slayer_hub:get_easy_box")
    val getEasyText = find("slayer_hub:get_easy_text")
    val getMediumWrap = find("slayer_hub:get_medium_wrap")
    val getMedium = find("slayer_hub:get_medium")
    val getMediumBox = find("slayer_hub:get_medium_box")
    val getMediumText = find("slayer_hub:get_medium_text")
    val getHardWrap = find("slayer_hub:get_hard_wrap")
    val getHard = find("slayer_hub:get_hard")
    val getHardBox = find("slayer_hub:get_hard_box")
    val getHardText = find("slayer_hub:get_hard_text")
    val getBossWrap = find("slayer_hub:get_boss_wrap")
    val getBoss = find("slayer_hub:get_boss")
    val getBossBox = find("slayer_hub:get_boss_box")
    val getBossText = find("slayer_hub:get_boss_text")
    val wildLabel = find("slayer_hub:wild_label")
    val wildToggleWrap = find("slayer_hub:wild_toggle_wrap")
    val wildToggle = find("slayer_hub:wild_toggle")
    val wildToggleBox = find("slayer_hub:wild_toggle_box")
    val wildToggleText = find("slayer_hub:wild_toggle_text")

    // Rewards page.
    val rewardsScroll = find("slayer_hub:rewards_scroll")
    val rewardsHeader = find("slayer_hub:rewards_header")
    val rewardsFooter = find("slayer_hub:rewards_footer")
    val rewardsScrollbar = find("slayer_hub:rewards_scrollbar")
    val scrollUpWrap = find("slayer_hub:scroll_up_wrap")
    val scrollUp = find("slayer_hub:scroll_up")
    val scrollUpBox = find("slayer_hub:scroll_up_box")
    val scrollUpText = find("slayer_hub:scroll_up_text")
    val scrollDownWrap = find("slayer_hub:scroll_down_wrap")
    val scrollDown = find("slayer_hub:scroll_down")
    val scrollDownBox = find("slayer_hub:scroll_down_box")
    val scrollDownText = find("slayer_hub:scroll_down_text")

    val rewardBgs = List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_bg") }
    val rewardNames = List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_name") }
    val rewardPrices = List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_price") }
    val rewardBuys = List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_buy") }
    val rewardBuyWraps =
        List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_buy_wrap") }
    val rewardBuyBoxes =
        List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_buy_box") }
    val rewardBuyTexts =
        List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_buy_text") }
    val rewardOwneds = List(CwSlayerUnlock.entries.size) { find("slayer_hub:reward${it}_owned") }

    /**
     * Every component keyed by its child name - used by the builder for `layer` parents and
     * hover-script args. Packed ids resolve through `component.sym`, so ordering does not matter.
     */
    val all: Map<String, ComponentType> = buildMap {
        allChildNames().forEach { name -> put(name, find("slayer_hub:$name")) }
    }

    private fun allChildNames(): List<String> = buildList {
        // Must contain every `slayer_hub:*` child name the builder creates or references.
        // `find` resolves each name against component.sym - a missing entry fails the pack.
        addAll(
            listOf(
                "root",
                "bg",
                "border",
                "title",
                "points_label",
                "points_value",
                "close_wrap",
                "close",
                "close_g",
                "accent_rule",
                "tab_task_wrap",
                "tab_task",
                "tab_task_text",
                "tab_task_underline",
                "tab_rewards_wrap",
                "tab_rewards",
                "tab_rewards_text",
                "tab_rewards_underline",
                "task_card",
                "task_card_border",
                "card_label",
                "npc_head",
                "task_name",
                "task_difficulty",
                "task_hint",
                "task_remaining",
            )
        )
        for (i in 0 until SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS) {
            add("pip_track$i")
        }
        for (i in 0 until SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS) {
            add("pip_fill$i")
        }
        for (stat in listOf("stat_points", "stat_done", "stat_wild")) {
            add("${stat}_card")
            add("${stat}_card_border")
            add("${stat}_label")
            add("${stat}_value")
        }
        for (btn in listOf("teleport_btn", "cancel_btn", "block_btn")) {
            add("${btn}_wrap")
            add(btn)
            add("${btn}_box")
            add("${btn}_text")
        }
        addAll(listOf("wild_note", "milestone", "empty_title", "empty_hint"))
        for (diff in listOf("easy", "medium", "hard", "boss")) {
            add("get_${diff}_wrap")
            add("get_$diff")
            add("get_${diff}_box")
            add("get_${diff}_text")
        }
        add("wild_label")
        addAll(listOf("wild_toggle_wrap", "wild_toggle", "wild_toggle_box", "wild_toggle_text"))
        addAll(listOf("rewards_scroll", "rewards_header"))
        for (i in CwSlayerUnlock.entries.indices) {
            add("reward${i}_bg")
            add("reward${i}_name")
            add("reward${i}_price")
            add("reward${i}_buy_wrap")
            add("reward${i}_buy")
            add("reward${i}_buy_box")
            add("reward${i}_buy_text")
            add("reward${i}_owned")
        }
        addAll(
            listOf(
                "rewards_footer",
                "rewards_scrollbar",
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
    }
}
