package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The Companion Hub inspect screen (`companion_inspect`): a read-only full stat-sheet view.
 *
 * Layout (512x334 centered modal, same chrome as `companion_equipment`): the left panel carries
 * identity (class/style/mode/gear rarity), the evolution track (stage, next stage, requirements,
 * bonuses), the five skill progression tracks (level + progress to next) and set activations
 * (active tiers and LOCKED states). The right panel lists every combat-facing stat with its
 * per-source breakdown ({lvl/skill/gear/set/tal/evo} tags), the ability loadout with live cooldowns
 * and the allocated talents.
 *
 * All rows are plain text components filled at runtime via `IfSetText` from
 * [org.rsmod.api.companion.CompanionInspectPresenter] - nothing is computed client-side, and an
 * absent companion renders the status line's empty state.
 */
internal object CompanionInspectInterfaceBuilder : InterfaceBuilder() {
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
    private const val PANEL: Int = 0x1B1712
    private const val FRAME: Int = 0x3A3226
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    private const val CS_SWAP_GRAPHIC: Int = 44

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    const val IDENTITY_ROWS: Int = 4
    const val EVOLUTION_ROWS: Int = 4
    const val SKILL_ROWS: Int = 5
    const val SET_ROWS: Int = 4
    const val STAT_ROWS: Int = 13
    const val ABILITY_ROWS: Int = 3
    const val TALENT_ROWS: Int = 3

    private const val LEFT_X: Int = 14
    private const val RIGHT_X: Int = 262
    private const val COL_W: Int = 236
    private const val ROW_H: Int = 12
    private const val HEAD_H: Int = 13

    init {
        build("companion_inspect:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("companion_inspect:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("companion_inspect:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "companion_inspect:title",
            14,
            9,
            280,
            15,
            "Companion Inspect",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text("companion_inspect:subtitle", 14, 26, 420, 12, "", DIM, alignH = 0, layer = rootRef())
        build("companion_inspect:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_inspect:close") {
            type = TYPE_LAYER
            layer = CompanionInspectComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_inspect:close_g") {
            type = TYPE_GRAPHIC
            layer = CompanionInspectComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("companion_inspect:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }
        build("companion_inspect:divider") {
            type = TYPE_RECT
            layer = rootRef()
            x = 252
            y = 50
            width = 2
            height = 272
            fill = true
            colour1 = FRAME
        }
        panel("left_panel", 6, 48, 240, 278)
        panel("right_panel", 258, 46, 248, 282)

        // Left column.
        var y = 50
        y = section("id_head", LEFT_X, y, "IDENTITY")
        y = lines("id", LEFT_X, y, IDENTITY_ROWS)
        y = section("evo_head", LEFT_X, y + 4, "EVOLUTION")
        y = lines("evo", LEFT_X, y, EVOLUTION_ROWS)
        y = section("skl_head", LEFT_X, y + 4, "SKILLS")
        y = lines("skl", LEFT_X, y, SKILL_ROWS)
        y = section("set_head", LEFT_X, y + 4, "SETS")
        y = lines("set", LEFT_X, y, SET_ROWS)
        text(
            "companion_inspect:status",
            LEFT_X,
            308,
            COL_W,
            12,
            "",
            FAINT,
            alignH = 0,
            layer = rootRef(),
        )

        // Right column.
        y = 50
        y = section("st_head", RIGHT_X, y, "STAT BREAKDOWN")
        y = lines("st", RIGHT_X, y, STAT_ROWS)
        y = section("ab_head", RIGHT_X, y + 4, "ABILITIES")
        y = lines("ab", RIGHT_X, y, ABILITY_ROWS)
        y = section("tl_head", RIGHT_X, y + 4, "TALENTS")
        y = lines("tl", RIGHT_X, y, TALENT_ROWS)
    }

    private const val SELF: Int = -2147483645

    private fun rootRef() = CompanionInspectComponents.root.packed

    private fun section(name: String, x: Int, y: Int, label: String): Int {
        text(
            "companion_inspect:$name",
            x,
            y,
            COL_W,
            HEAD_H,
            label,
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
        return y + HEAD_H
    }

    private fun lines(prefix: String, x: Int, y: Int, count: Int): Int {
        for (i in 0 until count) {
            text(
                "companion_inspect:$prefix$i",
                x + 4,
                y + i * ROW_H,
                COL_W - 4,
                11,
                "-",
                DIM,
                alignH = 0,
                layer = rootRef(),
            )
        }
        return y + count * ROW_H
    }

    private fun panel(name: String, xPos: Int, yPos: Int, w: Int, h: Int) {
        build("companion_inspect:$name") {
            type = TYPE_RECT
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
            fill = true
            colour1 = PANEL
        }
        build("companion_inspect:${name}_frame") {
            type = TYPE_RECT
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
            fill = false
            colour1 = FRAME
        }
    }

    private fun text(
        name: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        value: String,
        colour: Int,
        alignH: Int = 1,
        alignV: Int = 1,
        layer: Int,
    ) {
        build(name) {
            type = TYPE_TEXT
            this.layer = layer
            this.x = x
            this.y = y
            width = w
            height = h
            text = value
            textFont = FONT_B12
            textAlignH = alignH
            textAlignV = alignV
            colour1 = colour
            textShadow = true
        }
    }
}
