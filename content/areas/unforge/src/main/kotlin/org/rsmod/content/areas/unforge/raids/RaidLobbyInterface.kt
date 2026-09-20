package org.rsmod.content.areas.unforge.raids

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias raid_lobby_interfaces = RaidLobbyInterfaces

typealias raid_lobby_components = RaidLobbyComponents

object RaidLobbyInterfaces : InterfaceReferences() {
    val lobby = find("raid_lobby")
}

object RaidLobbyComponents : ComponentReferences() {
    val tob = find("raid_lobby:tob")
    val cox = find("raid_lobby:cox")
    val normal = find("raid_lobby:normal")
    val expert = find("raid_lobby:expert")
    val start = find("raid_lobby:start")
    val bank = find("raid_lobby:bank")
    val close = find("raid_lobby:close")
    val selection = find("raid_lobby:selection")
    val partyStatus = find("raid_lobby:party_status")

    private val generatedNames =
        listOf(
            "root",
            "bg",
            "border",
            "title",
            "subtitle",
            "selection",
            "party_status",
            "tob_wrap",
            "tob",
            "tob_box",
            "tob_title",
            "tob_desc",
            "cox_wrap",
            "cox",
            "cox_box",
            "cox_title",
            "cox_desc",
            "normal_wrap",
            "normal",
            "normal_box",
            "normal_title",
            "normal_desc",
            "expert_wrap",
            "expert",
            "expert_box",
            "expert_title",
            "expert_desc",
            "start_wrap",
            "start",
            "start_box",
            "start_title",
            "start_desc",
            "bank_wrap",
            "bank",
            "bank_box",
            "bank_title",
            "bank_desc",
            "close_wrap",
            "close",
            "close_box",
            "close_title",
            "close_desc",
        )

    val all: Map<String, ComponentType> = generatedNames.associateWith { find("raid_lobby:$it") }
}

/** Shared raid lobby: raid + difficulty pickers, party status, start/leave/bank. */
internal object RaidLobbyInterfaceBuilder : InterfaceBuilder() {
    private const val LAYER = 0
    private const val RECT = 3
    private const val TEXT = 4
    private const val CENTER = 1
    private const val FILL = 1
    private const val EVENTS_OP1 = 2
    private const val FONT_B12 = 495

    private const val BG = 0x14110D
    private const val PANEL = 0x241E17
    private const val BORDER = 0x5A4630
    private const val GOLD = 0xFFB84D
    private const val RED = 0xC8283C
    private const val WHITE = 0xFFFFFF
    private const val DIM = 0xB7A88F
    private const val GREEN = 0x33B532

    init {
        build("raid_lobby:root") {
            type = LAYER
            width = 512
            height = 370
            xMode = CENTER
            yMode = CENTER
            noClickThrough = true
        }
        build("raid_lobby:bg") {
            type = RECT
            layer = packed("raid_lobby:root")
            widthMode = FILL
            heightMode = FILL
            fill = true
            colour1 = BG
        }
        build("raid_lobby:border") {
            type = RECT
            layer = packed("raid_lobby:root")
            widthMode = FILL
            heightMode = FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "raid_lobby:title",
            20,
            12,
            470,
            22,
            "Raid Lobby",
            GOLD,
            alignH = 1,
            layer = packed("raid_lobby:root"),
        )
        text(
            "raid_lobby:subtitle",
            20,
            36,
            470,
            16,
            "Choose a raid and a difficulty",
            DIM,
            alignH = 1,
            layer = packed("raid_lobby:root"),
        )
        text(
            "raid_lobby:selection",
            20,
            56,
            470,
            16,
            "Theatre of Blood - Normal",
            WHITE,
            alignH = 1,
            layer = packed("raid_lobby:root"),
        )
        text(
            "raid_lobby:party_status",
            20,
            74,
            470,
            14,
            "Solo",
            DIM,
            alignH = 1,
            layer = packed("raid_lobby:root"),
        )
        button(
            "raid_lobby:tob",
            28,
            96,
            220,
            104,
            "THEATRE OF BLOOD",
            "Six linear rooms ending\nat Verzik Vitur",
            RED,
        )
        button(
            "raid_lobby:cox",
            264,
            96,
            220,
            104,
            "CHAMBERS OF XERIC",
            "Shuffled rooms ending\nat the Great Olm",
            GOLD,
        )
        button("raid_lobby:normal", 90, 214, 150, 52, "NORMAL", "Standard difficulty", GREEN)
        button("raid_lobby:expert", 272, 214, 150, 52, "EXPERT", "+50% stats, better loot", RED)
        button("raid_lobby:start", 186, 276, 140, 44, "START", "", WHITE)
        button("raid_lobby:bank", 48, 330, 125, 28, "BANK", "", PANEL)
        button("raid_lobby:close", 339, 330, 125, 28, "CLOSE", "", DIM)
    }

    private fun button(
        id: String,
        xPos: Int,
        yPos: Int,
        widthPx: Int,
        heightPx: Int,
        title: String,
        description: String,
        colour: Int,
    ) {
        build("${id}_wrap") {
            type = LAYER
            layer = packed("raid_lobby:root")
            x = xPos
            y = yPos
            width = widthPx
            height = heightPx
        }
        build(id) {
            type = LAYER
            layer = packed("${id}_wrap")
            widthMode = FILL
            heightMode = FILL
            events = EVENTS_OP1
            op = arrayOf(title)
        }
        build("${id}_box") {
            type = RECT
            layer = packed(id)
            widthMode = FILL
            heightMode = FILL
            fill = true
            colour1 = PANEL
        }
        text("${id}_title", 8, 14, widthPx - 16, 22, title, colour, alignH = 1, layer = packed(id))
        text(
            "${id}_desc",
            8,
            52,
            widthPx - 16,
            44,
            description,
            DIM,
            alignH = 1,
            layer = packed(id),
        )
    }

    private fun packed(name: String): Int =
        RaidLobbyComponents.all.getValue(name.removePrefix("raid_lobby:")).packed

    private fun text(
        name: String,
        xPos: Int,
        yPos: Int,
        widthPx: Int,
        heightPx: Int,
        value: String,
        colour: Int,
        alignH: Int,
        layer: Int,
    ) {
        build(name) {
            type = TEXT
            this.layer = layer
            x = xPos
            y = yPos
            width = widthPx
            height = heightPx
            textFont = FONT_B12
            textAlignH = alignH
            textAlignV = 1
            textShadow = true
            colour1 = colour
            text = value
        }
    }
}
