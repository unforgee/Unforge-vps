package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The `Points & Progress` journal page (interface `unforge_points`), a server-authored side panel
 * in the `side_journal` tab container - same frame and palette as `unforge_perks`.
 *
 * The body is a fixed pool of [ROW_SLOTS] generic rows inside one natively scrollable `content`
 * layer. Each physical row carries three hidden components: `row{i}_head` (a section header),
 * `row{i}_label` + `row{i}_value` (a point line). The script walks the registry's entries, writes
 * headers and values into consecutive slots and hides the rest - so new point systems and
 * categories never require a rebuild of this interface, and the row count is driven entirely by
 * server data.
 *
 * `Total points` is pinned outside the scroll layer so it stays visible at any scroll offset.
 */
internal object UnforgePointsInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    private const val WIDTH: Int = 190
    private const val HEIGHT: Int = 300

    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val GOLD: Int = 0xFFB84D
    private const val SECTION: Int = 0x7FB8FF
    private const val DIM: Int = 0x9A8B76
    private const val BORDER: Int = 0x0E0E0C
    private const val BACKGROUND: Int = 0x332E25
    private const val PANEL: Int = 0x3A342A
    private const val DIVIDER: Int = 0x5A5245

    private const val CONTENT_Y: Int = 24

    /** Visible height of the scrollable content layer. */
    const val CONTENT_VIEW_HEIGHT: Int = 246

    /** Pixel pitch of one generic row (label+value or section header). */
    const val ROW_PITCH: Int = 14

    /** Fixed pool of renderable rows - comfortably above the current entry count. */
    const val ROW_SLOTS: Int = 24

    /** Scroll step applied by the rail buttons, in pixels. */
    const val SCROLL_STEP: Int = ROW_PITCH * 4

    /** Total pixel height of the scrollable content. */
    val CONTENT_HEIGHT: Int = ROW_SLOTS * ROW_PITCH

    /** Maximum valid scroll position for the content layer. */
    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    init {
        build("unforge_points:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
        }
        build("unforge_points:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BACKGROUND
        }
        build("unforge_points:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_points:title",
            4,
            4,
            WIDTH - 8,
            14,
            "Points",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        build("unforge_points:divider") {
            type = TYPE_RECT
            layer = rootRef()
            x = 4
            y = 20
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }

        build("unforge_points:content") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 0
            y = CONTENT_Y
            width = WIDTH - 16
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
        }
        val contentRef = UnforgePointsComponents.content.packed
        for (row in 0 until ROW_SLOTS) {
            text(
                "unforge_points:row${row}_head",
                4,
                row * ROW_PITCH,
                WIDTH - 24,
                13,
                "",
                SECTION,
                alignH = 0,
                layer = contentRef,
                hidden = true,
            )
            text(
                "unforge_points:row${row}_label",
                8,
                row * ROW_PITCH,
                110,
                13,
                "",
                DIM,
                alignH = 0,
                layer = contentRef,
                hidden = true,
            )
            text(
                "unforge_points:row${row}_value",
                118,
                row * ROW_PITCH,
                56,
                13,
                "",
                GOLD,
                alignH = 2,
                layer = contentRef,
                hidden = true,
            )
        }

        build("unforge_points:scrollbar") {
            type = TYPE_RECT
            layer = rootRef()
            x = WIDTH - 12
            y = CONTENT_Y
            width = 8
            height = CONTENT_VIEW_HEIGHT
            fill = true
            colour1 = PANEL
        }
        scrollButton("unforge_points:scroll_up", CONTENT_Y + 2, "^")
        scrollButton("unforge_points:scroll_down", CONTENT_Y + CONTENT_VIEW_HEIGHT - 18, "v")

        build("unforge_points:footer_divider") {
            type = TYPE_RECT
            layer = rootRef()
            x = 4
            y = CONTENT_Y + CONTENT_VIEW_HEIGHT + 4
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }
        text(
            "unforge_points:total_label",
            4,
            CONTENT_Y + CONTENT_VIEW_HEIGHT + 10,
            100,
            14,
            "Total:",
            WHITE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_points:total_value",
            104,
            CONTENT_Y + CONTENT_VIEW_HEIGHT + 10,
            82,
            14,
            "0",
            GOLD,
            alignH = 2,
            layer = rootRef(),
        )
    }

    private fun rootRef() = UnforgePointsComponents.root.packed

    private fun scrollButton(name: String, y: Int, label: String) {
        val up = name == "unforge_points:scroll_up"
        val btn =
            if (up) UnforgePointsComponents.scrollUp.packed
            else UnforgePointsComponents.scrollDown.packed
        val wrap =
            if (up) UnforgePointsComponents.scrollUpWrap.packed
            else UnforgePointsComponents.scrollDownWrap.packed
        // The client reports `children[childIndex]` on click: nesting the button alone inside a
        // wrap keeps `childIndex` at 0 so the packet actually reaches the server.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = WIDTH - 14
            this.y = y
            width = 12
            height = 18
        }
        build(name) {
            type = TYPE_LAYER
            layer = wrap
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Scroll")
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = btn
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = DIVIDER
            events = EVENTS_OP1
        }
        build("${name}_text") {
            type = TYPE_TEXT
            layer = btn
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = GOLD
            text = label
        }
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
}
