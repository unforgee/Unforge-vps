package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Modern 512x334 centered modal dashboard for owner-bound companions.
 *
 * Provides a unified, high-polish graphical control panel replacing legacy chatbox menus:
 * - Left Hero Card: Companion level, combat stats (HP bar, XP, damage multiplier, attack speed,
 *   support power, pack space)
 * - Right Action Cards: Direct one-click access to Equipment Screen, Talent Tree, Beast of Burden
 *   Storage, Combat Style & Spells, Appearance/Transmog
 * - Bottom Action Bar: One-click Quick Deposit Inv, Quick Withdraw Pack, Summon/Dismiss, and Reset
 *   Talents
 */
internal object CompanionDashboardInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette: near-black base, warm panels, crimson accent, gold points.
    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1F1B15
    private const val PANEL_ALT: Int = 0x2A241C
    private const val CARD_BORDER: Int = 0x3D3428
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154
    private const val GREEN: Int = 0x33B532
    private const val TRACK: Int = 0x332C22

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    private const val CS_SWAP_GRAPHIC: Int = 44
    private const val CS_RECOLOUR_TEXT: Int = 45
    private const val SELF: Int = -2147483645

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    init {
        // 0: root
        build("companion_dashboard:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        // 1: bg
        build("companion_dashboard:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        // 2: border
        build("companion_dashboard:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        // 3: accent_rule
        build("companion_dashboard:accent_rule") {
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
        text("companion_dashboard:title", 16, 8, 280, 18, "Companion Dashboard", ORANGE, alignH = 0)
        // 5: subtitle
        text(
            "companion_dashboard:subtitle",
            16,
            26,
            280,
            14,
            "Owner-bound combat agent & beast of burden",
            WHITE,
            alignH = 0,
        )
        // 6: status_badge
        text(
            "companion_dashboard:status_badge",
            310,
            16,
            160,
            16,
            "ACTIVE • FOLLOWING",
            GREEN,
            alignH = 2,
        )
        // Wrap + holder pattern: the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("companion_dashboard:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_dashboard:close") {
            type = TYPE_LAYER
            layer = packed("companion_dashboard:close_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_dashboard:close_g") {
            type = TYPE_GRAPHIC
            layer = packed("companion_dashboard:close")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }

        // --- Left Column: Hero Card & Stats ---
        // 8: hero_card
        build("companion_dashboard:hero_card") {
            type = TYPE_RECT
            layer = rootRef()
            x = 14
            y = 54
            width = 236
            height = 226
            fill = true
            colour1 = PANEL
        }
        // 9: hero_border
        build("companion_dashboard:hero_border") {
            type = TYPE_RECT
            layer = rootRef()
            x = 14
            y = 54
            width = 236
            height = 226
            fill = false
            colour1 = CARD_BORDER
        }
        // 10: hero_header
        text(
            "companion_dashboard:hero_header",
            24,
            60,
            216,
            16,
            "Combat & Agent Attributes",
            GOLD,
            alignH = 0,
        )
        // 11: hp_track
        build("companion_dashboard:hp_track") {
            type = TYPE_RECT
            layer = rootRef()
            x = 24
            y = 78
            width = 216
            height = 14
            fill = true
            colour1 = TRACK
        }
        // 12: hp_fill
        build("companion_dashboard:hp_fill") {
            type = TYPE_RECT
            layer = rootRef()
            x = 24
            y = 78
            width = 216
            height = 14
            fill = true
            colour1 = ACCENT
        }
        // 13: hp_text
        text(
            "companion_dashboard:hp_text",
            24,
            78,
            216,
            14,
            "Hitpoints: 85 / 85",
            WHITE,
            alignH = 1,
        )
        // 14: stat_xp
        text("companion_dashboard:stat_xp", 24, 98, 216, 14, "Combat XP: 0", DIM, alignH = 0)
        // 15: stat_class
        text(
            "companion_dashboard:stat_class",
            24,
            114,
            216,
            14,
            "Combat Role: Paladin (Melee)",
            ORANGE,
            alignH = 0,
        )
        // 16: stat_dmg
        text(
            "companion_dashboard:stat_dmg",
            24,
            130,
            216,
            14,
            "Damage Multiplier: x1.00",
            WHITE,
            alignH = 0,
        )
        // 17: stat_speed
        text(
            "companion_dashboard:stat_speed",
            24,
            146,
            216,
            14,
            "Attack Speed: 4 ticks",
            WHITE,
            alignH = 0,
        )
        // 18: stat_support
        text(
            "companion_dashboard:stat_support",
            24,
            162,
            216,
            14,
            "Support Power: x1.00",
            WHITE,
            alignH = 0,
        )
        // 19: stat_pack
        text(
            "companion_dashboard:stat_pack",
            24,
            178,
            216,
            14,
            "Pack Storage: 0 / 30 slots",
            GOLD,
            alignH = 0,
        )
        // 20: stat_talents
        text(
            "companion_dashboard:stat_talents",
            24,
            194,
            216,
            14,
            "Talent Points: 0 unspent",
            DIM,
            alignH = 0,
        )
        // 21: stat_gear
        text(
            "companion_dashboard:stat_gear",
            24,
            210,
            216,
            14,
            "Equipped Gear: 0 / 11 slots",
            DIM,
            alignH = 0,
        )
        // 22: stat_spell
        text(
            "companion_dashboard:stat_spell",
            24,
            226,
            216,
            14,
            "Autocast: None (Standard)",
            DIM,
            alignH = 0,
        )
        // 23: mode_label (runtime text shows the current combat mode)
        text(
            "companion_dashboard:mode_label",
            24,
            244,
            216,
            14,
            "Combat Mode: DEFENSIVE",
            GOLD,
            alignH = 0,
        )
        // 71-82: combat mode quick-switch buttons inside the hero card
        simpleButton("companion_dashboard:mode_def", 18, 262, 72, 20, "Defensive")
        simpleButton("companion_dashboard:mode_agg", 94, 262, 72, 20, "Aggressive")
        simpleButton("companion_dashboard:mode_pas", 170, 262, 72, 20, "Passive")

        // --- Right Column: Action Cards ---
        // 24-27: btn_equip
        actionCard(
            "companion_dashboard:btn_equip",
            262,
            54,
            236,
            36,
            "Equipment & Stats",
            "Inspect worn gear, bonuses & affixes",
            ORANGE,
        )
        // 28-31: btn_talents
        actionCard(
            "companion_dashboard:btn_talents",
            262,
            92,
            236,
            36,
            "Talents & Specialization",
            "Allocate talent points & view specs",
            GOLD,
        )
        // 32-35: btn_storage
        actionCard(
            "companion_dashboard:btn_storage",
            262,
            130,
            236,
            36,
            "Beast of Burden Pack",
            "Open companion pack",
            WHITE,
        )
        // 83-87: btn_upgrade
        actionCard(
            "companion_dashboard:btn_upgrade",
            262,
            168,
            236,
            36,
            "Upgrade Pack",
            "Increase pack capacity",
            GOLD,
        )
        // 36-39: btn_style
        actionCard(
            "companion_dashboard:btn_style",
            262,
            206,
            236,
            36,
            "Combat Style & Spells",
            "Change spell, spellbook & style",
            WHITE,
        )
        // 40-43: btn_morph
        actionCard(
            "companion_dashboard:btn_morph",
            262,
            244,
            236,
            36,
            "Appearance & Transmog",
            "Cycle adventurer or boss pet form",
            WHITE,
        )

        // --- Bottom Action Bar (7 compact buttons) ---
        // 44-46: btn_store_all
        simpleButton("companion_dashboard:btn_store_all", 14, 288, 66, 34, "Store")
        // 47-49: btn_withdraw_all
        simpleButton("companion_dashboard:btn_withdraw_all", 84, 288, 66, 34, "Take")
        // 50-52: btn_summon
        simpleButton("companion_dashboard:btn_summon", 154, 288, 66, 34, "Dismiss")
        // 53-55: btn_reset_talents
        simpleButton("companion_dashboard:btn_reset_talents", 224, 288, 66, 34, "Reset")
        simpleButton("companion_dashboard:btn_autoloot", 294, 288, 66, 34, "Loot")
        // 88-91: btn_inspect
        simpleButton("companion_dashboard:btn_inspect", 364, 288, 66, 34, "Inspect")
        // 92-95: btn_behaviour
        simpleButton("companion_dashboard:btn_behaviour", 434, 288, 66, 34, "AI")
    }

    private fun rootRef() = packed("companion_dashboard:root")

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(name: String, suffix: String = ""): Int {
        val key = name.substringAfter(':') + suffix
        return CompanionDashboardComponents.all.getValue(key).packed
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

    private fun actionCard(
        name: String,
        xPos: Int,
        yPos: Int,
        w: Int,
        h: Int,
        titleText: String,
        descText: String,
        titleColour: Int,
    ) {
        // Wrap + holder: `${name}` is alone inside `${name}_wrap` (childIndex 0), so the client
        // resolves the click through `children[0]` = `${name}_box` and emits the If3Button packet.
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
            op = arrayOf(titleText)
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_title"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_title"), titleColour)
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
        text(
            "${name}_title",
            8,
            4,
            w - 16,
            16,
            titleText,
            titleColour,
            alignH = 0,
            layer = packed(name),
        )
        text("${name}_desc", 8, 20, w - 16, 14, descText, DIM, alignH = 0, layer = packed(name))
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
            colour1 = PANEL
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, w, h, labelText, ORANGE, alignH = 1, layer = packed(name))
    }
}
