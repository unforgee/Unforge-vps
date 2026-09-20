package org.rsmod.content.areas.unforge.wilderness

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias solo_boss_interfaces = SoloBossInterfaces

typealias solo_boss_components = SoloBossComponents

object SoloBossInterfaces : InterfaceReferences() {
    val menu = find("solo_boss_menu")
}

object SoloBossComponents : ComponentReferences() {
    val close = find("solo_boss_menu:close")
    val easy = find("solo_boss_menu:easy")
    val hard = find("solo_boss_menu:hard")
    val boss = find("solo_boss_menu:boss")
    val damage = find("solo_boss_menu:damage")
    val attackSpeed = find("solo_boss_menu:attack_speed")
    val maxHealth = find("solo_boss_menu:max_health")
    val bank = find("solo_boss_menu:bank")
    val perkStatus = find("solo_boss_menu:perk_status")

    private val generatedNames =
        listOf(
            "root",
            "bg",
            "border",
            "title",
            "subtitle",
            "perk_title",
            "perk_status",
            "easy_wrap",
            "easy",
            "easy_box",
            "easy_title",
            "easy_desc",
            "hard_wrap",
            "hard",
            "hard_box",
            "hard_title",
            "hard_desc",
            "boss_wrap",
            "boss",
            "boss_box",
            "boss_title",
            "boss_desc",
            "damage_wrap",
            "damage",
            "damage_box",
            "damage_title",
            "damage_desc",
            "attack_speed_wrap",
            "attack_speed",
            "attack_speed_box",
            "attack_speed_title",
            "attack_speed_desc",
            "max_health_wrap",
            "max_health",
            "max_health_box",
            "max_health_title",
            "max_health_desc",
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

    val all: Map<String, ComponentType> =
        generatedNames.associateWith { find("solo_boss_menu:$it") }
}

/** Shared Solo Boss / Nightmare Zone mode picker. */
internal object SoloBossInterfaceBuilder : InterfaceBuilder() {
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

    init {
        build("solo_boss_menu:root") {
            type = LAYER
            width = 512
            height = 370
            xMode = CENTER
            yMode = CENTER
            noClickThrough = true
        }
        build("solo_boss_menu:bg") {
            type = RECT
            layer = packed("solo_boss_menu:root")
            widthMode = FILL
            heightMode = FILL
            fill = true
            colour1 = BG
        }
        build("solo_boss_menu:border") {
            type = RECT
            layer = packed("solo_boss_menu:root")
            widthMode = FILL
            heightMode = FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "solo_boss_menu:title",
            20,
            16,
            470,
            22,
            "Solo Boss Arena",
            GOLD,
            alignH = 1,
            layer = packed("solo_boss_menu:root"),
        )
        text(
            "solo_boss_menu:subtitle",
            20,
            42,
            470,
            16,
            "Choose your challenge",
            DIM,
            alignH = 1,
            layer = packed("solo_boss_menu:root"),
        )
        button(
            "solo_boss_menu:easy",
            28,
            82,
            140,
            130,
            "EASY",
            "Rock crabs, sand crabs\nand other starter enemies",
            GOLD,
        )
        button(
            "solo_boss_menu:hard",
            186,
            82,
            140,
            130,
            "HARD",
            "Hellhounds, orks\nand high-tier enemies",
            RED,
        )
        button(
            "solo_boss_menu:boss",
            344,
            82,
            140,
            130,
            "BOSS",
            "Round-scaling bosses\nand bonus drops",
            WHITE,
        )
        text(
            "solo_boss_menu:perk_title",
            20,
            225,
            470,
            16,
            "Upgrade PvM perks",
            GOLD,
            alignH = 1,
            layer = packed("solo_boss_menu:root"),
        )
        text(
            "solo_boss_menu:perk_status",
            20,
            242,
            470,
            14,
            "PvM points: 0",
            DIM,
            alignH = 1,
            layer = packed("solo_boss_menu:root"),
        )
        button("solo_boss_menu:damage", 48, 250, 125, 28, "DAMAGE", "", PANEL)
        button("solo_boss_menu:attack_speed", 193, 250, 125, 28, "SPEED", "", PANEL)
        button("solo_boss_menu:max_health", 338, 250, 125, 28, "HEALTH", "", PANEL)
        button("solo_boss_menu:bank", 48, 288, 125, 28, "BANK", "", PANEL)
        button("solo_boss_menu:close", 206, 330, 100, 28, "CLOSE", "", DIM)
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
            layer = packed("solo_boss_menu:root")
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
        text("${id}_title", 8, 18, widthPx - 16, 22, title, colour, alignH = 1, layer = packed(id))
        text(
            "${id}_desc",
            8,
            58,
            widthPx - 16,
            55,
            description,
            DIM,
            alignH = 1,
            layer = packed(id),
        )
    }

    private fun packed(name: String): Int =
        SoloBossComponents.all.getValue(name.removePrefix("solo_boss_menu:")).packed

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
