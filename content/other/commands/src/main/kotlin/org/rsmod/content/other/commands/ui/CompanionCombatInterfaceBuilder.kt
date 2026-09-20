package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Companion "Combat & Spells" modal (`companion_combat`): the graphical replacement for the legacy
 * `menu()` spellbook dialog. One screen groups every combat-behaviour toggle:
 * - Combat Mode: Defensive / Aggressive / Passive (what the companion picks fights with)
 * - Attack Style: Melee / Ranged / Magic (the damage pipeline it resolves through)
 * - Spellbook: Standard / Ancients (the companion-owned book, gated by attack style)
 * - Autocast: current spell, a picker entry point and a clear action
 *
 * Same 512x334 chrome and wrap + holder click pattern as `companion_dashboard`.
 */
internal object CompanionCombatInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL_ALT: Int = 0x2A241C
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    private const val CS_SWAP_GRAPHIC: Int = 44
    private const val CS_RECOLOUR_TEXT: Int = 45
    private const val SELF: Int = -2147483645

    private const val WIDTH: Int = 512
    private const val HEIGHT: Int = 334

    init {
        // 0: root
        build("companion_combat:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        // 1: bg
        build("companion_combat:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        // 2: border
        build("companion_combat:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        // 3: accent_rule
        build("companion_combat:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }
        // 4: title
        text("companion_combat:title", 16, 8, 300, 18, "Combat Style & Spells", ORANGE, alignH = 0)
        // 5: subtitle
        text(
            "companion_combat:subtitle",
            16,
            26,
            380,
            14,
            "Companion combat behaviour, spellbook & autocast",
            WHITE,
            alignH = 0,
        )
        // 6-8: close
        build("companion_combat:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_combat:close") {
            type = TYPE_LAYER
            layer = packed("companion_combat:close_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_combat:close_g") {
            type = TYPE_GRAPHIC
            layer = packed("companion_combat:close")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }

        // --- Combat Mode ---
        // 9-10
        text("companion_combat:mode_lbl", 14, 54, 200, 14, "Combat Mode", ORANGE, alignH = 0)
        text(
            "companion_combat:mode_desc",
            262,
            54,
            236,
            14,
            "How eagerly the companion picks fights.",
            FAINT,
            alignH = 0,
        )
        // 11-22
        simpleButton("companion_combat:mode_def", 14, 72, 156, 30, "Defensive")
        simpleButton("companion_combat:mode_agg", 178, 72, 156, 30, "Aggressive")
        simpleButton("companion_combat:mode_pas", 342, 72, 156, 30, "Passive")

        // --- Attack Style ---
        // 23
        text("companion_combat:style_lbl", 14, 116, 200, 14, "Attack Style", ORANGE, alignH = 0)
        // 24-35
        simpleButton("companion_combat:style_melee", 14, 134, 156, 30, "Melee")
        simpleButton("companion_combat:style_ranged", 178, 134, 156, 30, "Ranged")
        simpleButton("companion_combat:style_magic", 342, 134, 156, 30, "Magic")

        // --- Spellbook ---
        // 36-37
        text("companion_combat:book_lbl", 14, 178, 200, 14, "Spellbook", ORANGE, alignH = 0)
        text(
            "companion_combat:book_note",
            262,
            178,
            236,
            14,
            "Switching books may clear autocast.",
            FAINT,
            alignH = 0,
        )
        // 38-45
        simpleButton("companion_combat:book_standard", 14, 196, 156, 30, "Standard")
        simpleButton("companion_combat:book_ancients", 178, 196, 156, 30, "Ancients")

        // --- Autocast ---
        // 46-47
        text("companion_combat:cast_lbl", 14, 240, 200, 14, "Autocast", ORANGE, alignH = 0)
        text("companion_combat:cast_current", 14, 262, 200, 30, "None", GOLD, alignH = 0)
        // 48-55
        simpleButton("companion_combat:cast_choose", 226, 254, 132, 30, "Choose Spell")
        simpleButton("companion_combat:cast_clear", 366, 254, 132, 30, "Clear")

        // 56: status
        text("companion_combat:status", 14, 296, 380, 26, "", DIM, alignH = 0)
        // 57-60: back
        simpleButton("companion_combat:back", 400, 294, 98, 28, "Back")
    }

    private fun rootRef() = packed("companion_combat:root")

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(name: String, suffix: String = ""): Int {
        val key = name.substringAfter(':') + suffix
        return CompanionCombatComponents.all.getValue(key).packed
    }

    private fun text(
        name: String,
        xPos: Int,
        yPos: Int,
        w: Int,
        h: Int,
        value: String,
        colour: Int,
        alignH: Int = 0,
        layer: Int = rootRef(),
    ) {
        build(name) {
            type = TYPE_TEXT
            this.layer = layer
            x = xPos
            y = yPos
            width = w
            height = h
            textFont = FONT_B12
            textAlignH = alignH
            textAlignV = 1
            textShadow = true
            colour1 = colour
            text = value
        }
    }

    private fun simpleButton(
        name: String,
        xPos: Int,
        yPos: Int,
        w: Int,
        h: Int,
        labelText: String,
    ) {
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed(name, "_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf(labelText)
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), ORANGE)
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, w, h, labelText, ORANGE, alignH = 1, layer = packed(name))
    }
}
