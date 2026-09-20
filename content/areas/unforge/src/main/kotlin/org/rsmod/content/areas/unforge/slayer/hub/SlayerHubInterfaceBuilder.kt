package org.rsmod.content.areas.unforge.slayer.hub

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.content.areas.unforge.slayer.CwSlayerUnlock

/**
 * The slayer hub window, authored from scratch and packed into the cache (interface `slayer_hub`).
 *
 * Layout: a 512x334 centered modal with a dark header (title + slayer-point badge + close X), a
 * crimson accent rule, and two text tabs - "Assignment" and "Rewards". The assignment page is a
 * card layout: a hero card with the task monster's head model, name, difficulty tag, remaining
 * count and a twenty-segment progress bar; three stat cards stacked on the right; and an action bar
 * with Teleport / Cancel / Block buttons. When no task is active the card swaps to a difficulty
 * picker (Easy/Medium/Hard/Boss + wilderness preference toggle).
 *
 * The rewards page is a natively scrollable list of every slayer unlock/extension (the client clips
 * children to the layer's view rect and wheel-scrolls it; the rail + arrow buttons step the
 * position server-side via `IfSetScrollPos`). Each row shows name, price and a Buy button which
 * swaps to an "Owned" tag once purchased.
 *
 * Component children draw in component-id order, so every button keeps its rect children on lower
 * ids than its label, and progress fill pips sit above their track pips.
 */
internal object SlayerHubInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5
    private const val TYPE_MODEL: Int = 6

    private const val EVENTS_OP1: Int = 2
    private const val EVENTS_OP1_OP2: Int = 6
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
    private const val PIP_TRACK: Int = 0x332C22

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    private const val SELF: Int = -2147483645

    const val INTERFACE_ID: Int = 1005

    const val PROGRESS_SEGMENTS: Int = 20

    const val CONTENT_VIEW_HEIGHT: Int = 220

    private const val REWARD_ROW_PITCH: Int = 30
    private const val SECTION_PITCH: Int = 18

    /** Scroll step applied by the rail buttons, in pixels (two reward rows). */
    const val SCROLL_STEP: Int = REWARD_ROW_PITCH * 2

    /** Total pixel height of the rewards scroll content (header, all rows, footer). */
    val CONTENT_HEIGHT: Int =
        SECTION_PITCH + CwSlayerUnlock.entries.size * REWARD_ROW_PITCH + SECTION_PITCH

    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    init {
        build("slayer_hub:root") {
            type = TYPE_LAYER
            width = 512
            height = 334
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("slayer_hub:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("slayer_hub:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text("slayer_hub:title", 14, 10, 220, 16, "Slayer", ACCENT, alignH = 0, layer = rootRef())
        text(
            "slayer_hub:points_label",
            300,
            8,
            170,
            12,
            "Slayer Points",
            DIM,
            alignH = 2,
            layer = rootRef(),
        )
        text("slayer_hub:points_value", 300, 22, 170, 16, "0", GOLD, alignH = 2, layer = rootRef())
        // Wrap + holder pattern (see `button`): the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("slayer_hub:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("slayer_hub:close") {
            type = TYPE_LAYER
            layer = packed("slayer_hub:close_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("slayer_hub:close_g") {
            type = TYPE_GRAPHIC
            layer = packed("slayer_hub:close")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("slayer_hub:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 42
            width = 512
            height = 2
            fill = true
            colour1 = ACCENT
        }

        tab("slayer_hub:tab_task", 14, "Assignment")
        tab("slayer_hub:tab_rewards", 130, "Rewards")

        assignmentPage()
        rewardsPage()
    }

    // ---------- shared pieces ----------

    private fun rootRef() = packed("slayer_hub:root")

    private fun tab(name: String, xPos: Int, label: String) {
        // Wrap + holder pattern (see `button`): `${name}_text` is built first so it sits at
        // `children[0]` - the always-visible component the client's click resolution reads
        // its op flags from (the underline starts hidden, so it must not be index 0).
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = xPos
            y = 50
            width = 108
            height = 22
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed(name, "_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), DIM)
        }
        build("${name}_text") {
            type = TYPE_TEXT
            layer = packed(name)
            x = 0
            y = 4
            width = 108
            height = 14
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = DIM
            text = label
            events = EVENTS_OP1
        }
        build("${name}_underline") {
            type = TYPE_RECT
            layer = packed(name)
            x = 0
            y = 19
            widthMode = SIZE_FILL
            height = 2
            fill = true
            colour1 = ACCENT
            hide = true
        }
    }

    /**
     * Computes a sibling component's packed id arithmetically - `(1005 shl 16) or childIndex` -
     * using the same build-order index as the `slayer_hub:N` symbols, so hover-script args never
     * depend on reference resolution having run first.
     */
    private fun packed(name: String, suffix: String = ""): Int {
        val key = name.substringAfter(':') + suffix
        return SlayerHubComponents.all.getValue(key).packed
    }

    private fun text(
        name: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: String,
        colour: Int,
        alignH: Int = 1,
        layer: Int,
        hidden: Boolean = false,
    ) {
        build(name) {
            type = TYPE_TEXT
            this.layer = layer
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            textFont = FONT_B12
            textAlignH = alignH
            textAlignV = 1
            textShadow = true
            colour1 = colour
            text = value
            if (hidden) {
                hide = true
            }
        }
    }

    private fun button(
        name: String,
        parent: Int,
        xPos: Int,
        yPos: Int,
        w: Int,
        h: Int,
        label: String,
        boxColour: Int = PANEL_ALT,
        textColour: Int = ORANGE,
        hidden: Boolean = false,
    ) {
        // Wrap + holder pattern: the client resolves a click through
        // `widget.children[widget.childIndex]` - alone inside `${name}_wrap` the button's
        // childIndex is 0, so `children[0]` (= `_box`) resolves and the If3Button packet is
        // emitted. `hidden` is applied only to the wrap: hiding the parent hides the whole
        // subtree, and unhiding it must not leave stale hide flags on the children.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = parent
            x = xPos
            y = yPos
            width = w
            height = h
            if (hidden) {
                hide = true
            }
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed(name, "_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = if (name == "slayer_hub:teleport_btn") EVENTS_OP1_OP2 else EVENTS_OP1
            op =
                if (name == "slayer_hub:teleport_btn") {
                    arrayOf(label, label)
                } else {
                    arrayOf(label)
                }
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed(name, "_text"), textColour)
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = boxColour
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, w, h, label, textColour, layer = packed(name))
    }

    // ---------- assignment page ----------

    private fun assignmentPage() {
        // Hero card.
        build("slayer_hub:task_card") {
            type = TYPE_RECT
            layer = rootRef()
            x = 12
            y = 84
            width = 368
            height = 146
            fill = true
            colour1 = PANEL
        }
        build("slayer_hub:task_card_border") {
            type = TYPE_RECT
            layer = rootRef()
            x = 12
            y = 84
            width = 368
            height = 146
            fill = false
            colour1 = CARD_BORDER
        }
        text(
            "slayer_hub:card_label",
            22,
            90,
            200,
            12,
            "CURRENT ASSIGNMENT",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("slayer_hub:npc_head") {
            type = TYPE_MODEL
            layer = rootRef()
            x = 26
            y = 106
            width = 68
            height = 68
            // Round-trip constraints: the v3 decoder hardcodes modelKind=1, and -1 sentinel
            // values for model/modelAnim decode back as null rather than -1, so neutral
            // zeroes are the only values that pack deterministically. The real npc head
            // model is always pushed at runtime via IfSetNpcHead.
            model = 0
            modelAnim = 0
            modelZoom = 900
        }
        text("slayer_hub:task_name", 104, 108, 260, 16, "-", WHITE, alignH = 0, layer = rootRef())
        text(
            "slayer_hub:task_difficulty",
            104,
            128,
            260,
            14,
            "-",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text("slayer_hub:task_hint", 104, 146, 260, 12, "", DIM, alignH = 0, layer = rootRef())
        text(
            "slayer_hub:task_remaining",
            104,
            164,
            260,
            14,
            "",
            WHITE,
            alignH = 0,
            layer = rootRef(),
        )

        // Twenty-segment progress bar along the card bottom: dim track pips are always drawn,
        // accent fill pips are hidden/shown by the script.
        for (seg in 0 until PROGRESS_SEGMENTS) {
            build("slayer_hub:pip_track$seg") {
                type = TYPE_RECT
                layer = rootRef()
                x = 22 + seg * 17
                y = 198
                width = 15
                height = 8
                fill = true
                colour1 = PIP_TRACK
            }
        }
        for (seg in 0 until PROGRESS_SEGMENTS) {
            build("slayer_hub:pip_fill$seg") {
                type = TYPE_RECT
                layer = rootRef()
                x = 22 + seg * 17
                y = 198
                width = 15
                height = 8
                fill = true
                colour1 = ACCENT
                hide = true
            }
        }

        // Stat cards on the right column.
        statCard("slayer_hub:stat_points", 84, "SLAYER POINTS", GOLD)
        statCard("slayer_hub:stat_done", 142, "TASKS DONE", WHITE)
        statCard("slayer_hub:stat_wild", 200, "WILD TASKS", WHITE)

        // Action bar.
        button(
            "slayer_hub:teleport_btn",
            rootRef(),
            12,
            240,
            112,
            26,
            "Teleport",
            boxColour = ACCENT,
            textColour = WHITE,
        )
        button("slayer_hub:cancel_btn", rootRef(), 134, 240, 112, 26, "Cancel (30p)")
        button("slayer_hub:block_btn", rootRef(), 256, 240, 112, 26, "Block (100p)")

        text(
            "slayer_hub:wild_note",
            12,
            274,
            368,
            14,
            "Dangerous assignment - targets are in the Wilderness.",
            ACCENT,
            alignH = 0,
            layer = rootRef(),
            hidden = true,
        )
        text("slayer_hub:milestone", 12, 292, 368, 12, "", DIM, alignH = 0, layer = rootRef())

        // Empty state (no active assignment).
        text(
            "slayer_hub:empty_title",
            12,
            96,
            368,
            16,
            "NO ACTIVE ASSIGNMENT",
            ACCENT,
            layer = rootRef(),
            hidden = true,
        )
        text(
            "slayer_hub:empty_hint",
            12,
            116,
            368,
            12,
            "Choose a difficulty to begin:",
            DIM,
            layer = rootRef(),
            hidden = true,
        )
        val difficulties =
            listOf(
                "easy" to ("Easy" to GREEN),
                "medium" to ("Medium" to ORANGE),
                "hard" to ("Hard" to ACCENT),
                "boss" to ("Boss" to 0xB86BFF),
            )
        for ((index, entry) in difficulties.withIndex()) {
            val (slug, style) = entry
            val (label, colour) = style
            button(
                "slayer_hub:get_$slug",
                rootRef(),
                22 + (index % 2) * 178,
                136 + (index / 2) * 34,
                170,
                28,
                label,
                textColour = colour,
                hidden = true,
            )
        }
        text(
            "slayer_hub:wild_label",
            22,
            208,
            170,
            14,
            "Prefer wilderness tasks",
            DIM,
            alignH = 0,
            layer = rootRef(),
            hidden = true,
        )
        button(
            "slayer_hub:wild_toggle",
            rootRef(),
            196,
            204,
            60,
            18,
            "Off",
            textColour = DIM,
            hidden = true,
        )
    }

    private fun statCard(name: String, yPos: Int, label: String, valueColour: Int) {
        build("${name}_card") {
            type = TYPE_RECT
            layer = rootRef()
            x = 392
            y = yPos
            width = 108
            height = 52
            fill = true
            colour1 = PANEL
        }
        build("${name}_card_border") {
            type = TYPE_RECT
            layer = rootRef()
            x = 392
            y = yPos
            width = 108
            height = 52
            fill = false
            colour1 = CARD_BORDER
        }
        text("${name}_label", 392, yPos + 7, 108, 12, label, DIM, layer = rootRef())
        text("${name}_value", 392, yPos + 23, 108, 18, "0", valueColour, layer = rootRef())
    }

    // ---------- rewards page ----------

    private fun rewardsPage() {
        build("slayer_hub:rewards_scroll") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 12
            y = 84
            width = 460
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
            hide = true
        }
        val scrollRef = SlayerHubComponents.rewardsScroll.packed

        text(
            "slayer_hub:rewards_header",
            4,
            0,
            440,
            14,
            "Unlocks & Extensions",
            ORANGE,
            alignH = 0,
            layer = scrollRef,
            hidden = true,
        )
        var cursorY = SECTION_PITCH
        for ((index, unlock) in CwSlayerUnlock.entries.withIndex()) {
            rewardRow(index, unlock, cursorY, scrollRef)
            cursorY += REWARD_ROW_PITCH
        }
        text(
            "slayer_hub:rewards_footer",
            4,
            cursorY,
            440,
            14,
            "Points are earned by completing slayer tasks.",
            FAINT,
            alignH = 0,
            layer = scrollRef,
            hidden = true,
        )

        build("slayer_hub:rewards_scrollbar") {
            type = TYPE_RECT
            layer = rootRef()
            x = 476
            y = 84
            width = 8
            height = CONTENT_VIEW_HEIGHT
            fill = true
            colour1 = PANEL
            hide = true
        }
        scrollButton("slayer_hub:scroll_up", 84, "^")
        scrollButton("slayer_hub:scroll_down", 84 + CONTENT_VIEW_HEIGHT - 18, "v")
    }

    private fun rewardRow(index: Int, unlock: CwSlayerUnlock, rowY: Int, scrollRef: Int) {
        build("slayer_hub:reward${index}_bg") {
            type = TYPE_RECT
            layer = scrollRef
            x = 0
            y = rowY
            width = 452
            height = REWARD_ROW_PITCH - 2
            fill = true
            colour1 = PANEL
            hide = true
        }
        val kind = if (unlock.extension) "Extension" else "Unlock"
        text(
            "slayer_hub:reward${index}_name",
            8,
            rowY + 8,
            280,
            14,
            "${unlock.label} <col=6b6154>- $kind</col>",
            ORANGE,
            alignH = 0,
            layer = scrollRef,
            hidden = true,
        )
        text(
            "slayer_hub:reward${index}_price",
            300,
            rowY + 8,
            76,
            14,
            "${unlock.price}p",
            GOLD,
            alignH = 2,
            layer = scrollRef,
            hidden = true,
        )
        button(
            "slayer_hub:reward${index}_buy",
            scrollRef,
            382,
            rowY + 4,
            62,
            20,
            "Buy",
            boxColour = PANEL_ALT,
            textColour = GREEN,
            hidden = true,
        )
        text(
            "slayer_hub:reward${index}_owned",
            382,
            rowY + 8,
            62,
            14,
            "Owned",
            GREEN,
            alignH = 1,
            layer = scrollRef,
            hidden = true,
        )
    }

    private fun scrollButton(name: String, yPos: Int, label: String) {
        // Wrap + holder pattern (see `button`); hidden on the wrap only, like `button`.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 474
            y = yPos
            width = 12
            height = 18
            hide = true
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed(name, "_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Scroll")
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = CARD_BORDER
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, 12, 18, label, GOLD, layer = packed(name))
    }
}
