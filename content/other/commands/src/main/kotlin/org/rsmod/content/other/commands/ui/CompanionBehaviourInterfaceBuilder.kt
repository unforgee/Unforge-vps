package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The Companion Hub Behaviour tab (`companion_behaviour`): the player-facing editor for the
 * persisted [org.rsmod.api.companion.CompanionBehaviour] record.
 *
 * Layout (512x334 centered modal, same chrome as `companion_equipment`): the left panel carries
 * combat mode (Passive/Defensive/Aggressive), target priority (owner target / owner attacker /
 * nearest hostile / lowest-health ally) and attack style (melee/ranged/magic) pickers. The right
 * panel carries the follow distance stepper, the catch-up teleport toggle and role actions
 * (auto-taunt, auto-defend, auto-heal with its heal-below threshold stepper, auto-buff).
 *
 * Every control applies immediately through `CompanionService` - the interface only displays the
 * persisted state, so relog/teleport/respawn always re-render the same values.
 */
internal object CompanionBehaviourInterfaceBuilder : InterfaceBuilder() {
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
    private const val PANEL_ALT: Int = 0x2A241C
    private const val FRAME: Int = 0x3A3226
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

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    private const val LEFT_X: Int = 14
    private const val RIGHT_X: Int = 262
    private const val COL_W: Int = 236
    private const val BTN_H: Int = 20
    private const val BTN_W: Int = 112
    private const val ROW_PITCH: Int = 24

    init {
        build("companion_behaviour:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("companion_behaviour:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("companion_behaviour:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "companion_behaviour:title",
            14,
            9,
            280,
            15,
            "Companion Behaviour",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "companion_behaviour:subtitle",
            14,
            26,
            420,
            12,
            "",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("companion_behaviour:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_behaviour:close") {
            type = TYPE_LAYER
            layer = CompanionBehaviourComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_behaviour:close_g") {
            type = TYPE_GRAPHIC
            layer = CompanionBehaviourComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("companion_behaviour:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }
        build("companion_behaviour:divider") {
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

        // --- Left column: combat ---
        section("mode_head", LEFT_X, 52, "COMBAT MODE")
        button("mode_pas", LEFT_X, 66, 72, "Passive")
        button("mode_def", LEFT_X + 78, 66, 72, "Defensive")
        button("mode_agg", LEFT_X + 156, 66, 72, "Aggressive")

        section("tgt_head", LEFT_X, 94, "TARGET PRIORITY")
        button("tgt_owner", LEFT_X, 108, BTN_W, "Owner target")
        button("tgt_attacker", LEFT_X + 120, 108, BTN_W, "Owner attacker")
        button("tgt_nearest", LEFT_X, 108 + ROW_PITCH, BTN_W, "Nearest hostile")
        button("tgt_ally", LEFT_X + 120, 108 + ROW_PITCH, BTN_W, "Lowest-hp ally")

        section("sty_head", LEFT_X, 164, "ATTACK STYLE")
        button("sty_melee", LEFT_X, 178, 72, "Melee")
        button("sty_ranged", LEFT_X + 78, 178, 72, "Ranged")
        button("sty_magic", LEFT_X + 156, 178, 72, "Magic")

        text(
            "companion_behaviour:status",
            LEFT_X,
            308,
            COL_W,
            12,
            "",
            FAINT,
            alignH = 0,
            layer = rootRef(),
        )

        // --- Right column: follow & role ---
        section("fol_head", RIGHT_X, 52, "FOLLOW DISTANCE")
        button("dist_minus", RIGHT_X, 66, 54, "-")
        text(
            "companion_behaviour:dist_val",
            RIGHT_X + 60,
            66,
            62,
            BTN_H,
            "2 tiles",
            WHITE,
            alignH = 1,
            layer = rootRef(),
        )
        button("dist_plus", RIGHT_X + 128, 66, 54, "+")

        section("catch_head", RIGHT_X, 96, "CATCH-UP")
        button("catch_tgl", RIGHT_X, 110, COL_W, "Catch-up teleport: ON")

        section("role_head", RIGHT_X, 140, "ROLE ACTIONS")
        button("taunt_tgl", RIGHT_X, 154, COL_W, "Auto-taunt: ON")
        button("defend_tgl", RIGHT_X, 154 + ROW_PITCH, COL_W, "Auto-defend: ON")
        button("heal_tgl", RIGHT_X, 154 + ROW_PITCH * 2, COL_W, "Auto-heal: ON")
        button("buff_tgl", RIGHT_X, 154 + ROW_PITCH * 3, COL_W, "Auto-buff: ON")

        section("hb_head", RIGHT_X, 258, "HEAL BELOW")
        button("hb_minus", RIGHT_X, 272, 54, "-")
        text(
            "companion_behaviour:hb_val",
            RIGHT_X + 60,
            272,
            62,
            BTN_H,
            "80%",
            WHITE,
            alignH = 1,
            layer = rootRef(),
        )
        button("hb_plus", RIGHT_X + 128, 272, 54, "+")
    }

    private fun rootRef() = CompanionBehaviourComponents.root.packed

    private fun packed(childName: String): Int =
        CompanionBehaviourComponents.all.getValue(childName).packed

    private fun section(name: String, x: Int, y: Int, label: String) {
        text(
            "companion_behaviour:$name",
            x,
            y,
            COL_W,
            12,
            label,
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
    }

    /**
     * Wrap + holder pattern: `${name}` is alone inside `${name}_wrap` (childIndex 0), so the click
     * resolves through `children[0]` = `${name}_box` and the If3Button packet is emitted. The
     * runtime rewrites `${name}_text` to mark the active selection.
     */
    private fun button(name: String, x: Int, y: Int, w: Int, labelText: String) {
        build("companion_behaviour:${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            this.x = x
            this.y = y
            width = w
            height = BTN_H
        }
        build("companion_behaviour:$name") {
            type = TYPE_LAYER
            layer = packed("${name}_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf(labelText)
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("${name}_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("${name}_text"), ORANGE)
        }
        build("companion_behaviour:${name}_box") {
            type = TYPE_RECT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        text(
            "companion_behaviour:${name}_text",
            0,
            0,
            w,
            BTN_H,
            labelText,
            ORANGE,
            alignH = 1,
            layer = packed(name),
        )
    }

    private fun panel(name: String, xPos: Int, yPos: Int, w: Int, h: Int) {
        build("companion_behaviour:$name") {
            type = TYPE_RECT
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
            fill = true
            colour1 = PANEL
        }
        build("companion_behaviour:${name}_frame") {
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
