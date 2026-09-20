package org.rsmod.content.other.devhub.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.game.type.comp.ComponentTypeBuilder

/**
 * The dev-hub window, authored from scratch and packed into the cache (interface 1000).
 *
 * Layout: a 512x334 centered modal with a title bar (search button + close X on the right), a row
 * of six boxed tabs, a left-hand category list (12 rows), a scrollable item grid on the right
 * (populated at runtime through clientscript 149 - see `DevHubScript`), and a native scroll list
 * for the Teleports tab.
 *
 * Each tab is a button layer holding three children whose ids are deliberately ascending - box
 * rect, selected-state rect (`hide` baked on), label text - because children draw in component id
 * order, so the rects must take lower ids than the text to sit under it.
 *
 * Every constant below was harvested from decoded rev-239 vanilla components rather than guessed
 * (project method: decode the cache, never guess):
 * - Root sizing/centering copies `skill_guide_v2` 860:1 (512x334, both modes centered,
 *   `noClickThrough`).
 * - The close button copies the common vanilla close X: sprite 539 (hover 540), 26x23, baked op1,
 *   with clientscript 44 swapping the sprite on hover - e.g. 346:48, 362:84, 290:1.
 * - Buttons are type-0 layers with baked `events = 2` (op1) plus an `op` string, and text lives in
 *   a separate text component recolored on hover via clientscript 45 - the shape shopmain 300:8/9
 *   uses for its quantity buttons.
 * - Fonts/colors: b12 font 495, standard orange 0xFF981F, white hover 0xFFFFFF, border shade
 *   0x0E0E0C (harvested from 860:12).
 */
internal object DevHubInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5

    /** v1-style event mask with only op1 set - what every baked vanilla button carries. */
    private const val EVENTS_OP1: Int = 2

    /** Anchor modes harvested from vanilla comps: 0 = absolute, 1 = centered, 2 = far edge. */
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2

    /** Size modes: 0 = fixed, 1 = fill parent. */
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495
    private const val COLOUR_ORANGE: Int = 0xFF981F
    private const val COLOUR_WHITE: Int = 0xFFFFFF
    private const val COLOUR_BORDER: Int = 0x0E0E0C
    private const val COLOUR_BACKGROUND: Int = 0x332E25
    private const val COLOUR_DIVIDER: Int = 0x5A5245
    private const val COLOUR_TAB: Int = 0x3A342A
    private const val COLOUR_TAB_SELECTED: Int = 0x554C3D

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    /** The "this component" sentinel vanilla hover hooks pass as the component arg. */
    private const val SELF: Int = -2147483645

    private const val TAB_Y: Int = 34
    private const val TAB_WIDTH: Int = 78
    private const val TAB_HEIGHT: Int = 24
    private const val TAB_PITCH: Int = 82

    private const val CAT_X: Int = 8
    private const val CAT_Y: Int = 64
    private const val CAT_WIDTH: Int = 140
    private const val CAT_HEIGHT: Int = 20
    private const val CAT_PITCH: Int = 22

    const val CATEGORY_ROWS: Int = 12

    const val GRID_COLUMNS: Int = 8
    const val ITEM_GRID_ROWS: Int = 31
    const val ITEM_VIEW_HEIGHT: Int = 216
    private const val ITEM_CONTENT_HEIGHT: Int = ITEM_GRID_ROWS * 32
    const val TELEPORT_ROW_COUNT: Int = 128
    const val TELEPORT_ROW_PITCH: Int = 20
    const val TELEPORT_VIEW_HEIGHT: Int = 216
    private const val TELEPORT_VIEW_WIDTH: Int = 324
    private const val TELEPORT_CONTENT_HEIGHT: Int = TELEPORT_ROW_COUNT * TELEPORT_ROW_PITCH

    init {
        build("dev_hub:root") {
            type = TYPE_LAYER
            width = 512
            height = 334
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("dev_hub:bg") {
            type = TYPE_RECT
            layer = DevHubComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = COLOUR_BACKGROUND
        }
        build("dev_hub:border") {
            type = TYPE_RECT
            layer = DevHubComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = COLOUR_BORDER
        }
        build("dev_hub:title") {
            type = TYPE_TEXT
            layer = DevHubComponents.root.packed
            y = 8
            height = 24
            widthMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = COLOUR_ORANGE
            text = "Developer Hub"
        }
        // Wrap + holder pattern (see `scrollButton`): the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("dev_hub:close_wrap") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 6
            y = 6
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("dev_hub:close") {
            type = TYPE_LAYER
            layer = DevHubComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("dev_hub:close_g") {
            type = TYPE_GRAPHIC
            layer = DevHubComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }

        build("dev_hub:search_button_wrap") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 38
            y = 8
            width = 64
            height = 23
            xMode = MODE_FAR
        }
        build("dev_hub:search_button") {
            holderLayer(
                parent = DevHubComponents.searchButtonWrap.packed,
                opText = "Search",
                hoverTextPacked = DevHubComponents.search_button_text.packed,
            )
        }
        build("dev_hub:search_button_text") {
            buttonText(parent = DevHubComponents.search_button.packed, label = "Search")
            events = EVENTS_OP1
        }

        val tabs =
            listOf(
                "items" to "Items",
                "equipment" to "Equipment",
                "gear" to "Gear",
                "teleports" to "Teleports",
                "skills" to "Skills",
                "misc" to "Misc",
            )
        for ((index, tab) in tabs.withIndex()) {
            val (slug, label) = tab
            val buttonPacked = DevHubComponents.tabButtons[index].packed
            val textPacked = DevHubComponents.tabTexts[index].packed
            build("dev_hub:tab_${slug}_wrap") {
                type = TYPE_LAYER
                layer = DevHubComponents.root.packed
                x = 8 + index * TAB_PITCH
                y = TAB_Y
                width = TAB_WIDTH
                height = TAB_HEIGHT
            }
            build("dev_hub:tab_$slug") {
                holderLayer(
                    parent = DevHubComponents.tabWraps[index].packed,
                    opText = "Select",
                    hoverTextPacked = textPacked,
                )
            }
            build("dev_hub:tab_${slug}_box") {
                fillRect(parent = buttonPacked, shade = COLOUR_TAB)
                events = EVENTS_OP1
            }
            build("dev_hub:tab_${slug}_sel") {
                fillRect(parent = buttonPacked, shade = COLOUR_TAB_SELECTED, hidden = true)
            }
            build("dev_hub:tab_${slug}_text") { buttonText(parent = buttonPacked, label = label) }
        }

        build("dev_hub:divider") {
            type = TYPE_RECT
            layer = DevHubComponents.root.packed
            x = 152
            y = 62
            width = 1
            height = 264
            fill = true
            colour1 = COLOUR_DIVIDER
        }

        for (index in 0 until CATEGORY_ROWS) {
            val buttonPacked = DevHubComponents.catButtons[index].packed
            val textPacked = DevHubComponents.catTexts[index].packed
            build("dev_hub:cat${index}_wrap") {
                type = TYPE_LAYER
                layer = DevHubComponents.root.packed
                x = CAT_X
                y = CAT_Y + index * CAT_PITCH
                width = CAT_WIDTH
                height = CAT_HEIGHT
            }
            build("dev_hub:cat$index") {
                holderLayer(
                    parent = DevHubComponents.catWraps[index].packed,
                    opText = "Select",
                    hoverTextPacked = textPacked,
                )
            }
            build("dev_hub:cat${index}_box") {
                fillRect(parent = buttonPacked, shade = COLOUR_TAB)
                events = EVENTS_OP1
            }
            build("dev_hub:cat${index}_sel") {
                fillRect(parent = buttonPacked, shade = COLOUR_TAB_SELECTED, hidden = true)
            }
            build("dev_hub:cat${index}_text") {
                buttonText(parent = buttonPacked, label = "", alignLeft = true)
            }
        }

        build("dev_hub:grid") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 160
            y = 68
            width = 336
            height = ITEM_VIEW_HEIGHT
            scrollHeight = ITEM_CONTENT_HEIGHT
        }
        scrollButton("dev_hub:item_scroll_up", DevHubComponents.itemScrollUpWrap.packed, 68, "^")
        scrollButton(
            "dev_hub:item_scroll_down",
            DevHubComponents.itemScrollDownWrap.packed,
            68 + ITEM_VIEW_HEIGHT - 18,
            "v",
        )

        build("dev_hub:teleport_scroll") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 160
            y = 68
            width = TELEPORT_VIEW_WIDTH
            height = TELEPORT_VIEW_HEIGHT
            scrollHeight = TELEPORT_CONTENT_HEIGHT
            hide = true
        }
        val teleportScrollRef = DevHubComponents.teleportScroll.packed
        for (index in 0 until TELEPORT_ROW_COUNT) {
            val rowRef = DevHubComponents.teleportRows[index].packed
            // Each row gets its own wrap: the click resolves `children[childIndex]` on the row,
            // and the row's childIndex inside `teleport_scroll` equals `index` (up to 127) while
            // the row only has two children - without the wrap every row past index 1 is dead.
            build("dev_hub:teleport_row${index}_wrap") {
                type = TYPE_LAYER
                layer = teleportScrollRef
                x = 0
                y = index * TELEPORT_ROW_PITCH
                width = TELEPORT_VIEW_WIDTH
                height = TELEPORT_ROW_PITCH
                hide = true
            }
            build("dev_hub:teleport_row$index") {
                type = TYPE_LAYER
                layer = DevHubComponents.teleportRowWraps[index].packed
                widthMode = SIZE_FILL
                heightMode = SIZE_FILL
                events = EVENTS_OP1
                op = arrayOf("Teleport")
                onMouseOver =
                    arrayOf(
                        CS_RECOLOUR_TEXT,
                        DevHubComponents.teleportRowTexts[index].packed,
                        COLOUR_WHITE,
                    )
                onMouseLeave =
                    arrayOf(
                        CS_RECOLOUR_TEXT,
                        DevHubComponents.teleportRowTexts[index].packed,
                        COLOUR_ORANGE,
                    )
            }
            build("dev_hub:teleport_row${index}_box") {
                type = TYPE_RECT
                layer = rowRef
                widthMode = SIZE_FILL
                heightMode = SIZE_FILL
                fill = true
                colour1 = if (index % 2 == 0) COLOUR_TAB else COLOUR_BACKGROUND
                events = EVENTS_OP1
            }
            build("dev_hub:teleport_row${index}_text") {
                type = TYPE_TEXT
                layer = rowRef
                x = 8
                width = TELEPORT_VIEW_WIDTH - 16
                height = TELEPORT_ROW_PITCH
                textFont = FONT_B12
                textAlignH = 0
                textAlignV = 1
                textShadow = true
                colour1 = COLOUR_ORANGE
                text = ""
            }
        }
        scrollButton(
            "dev_hub:teleport_scroll_up",
            DevHubComponents.teleportScrollUpWrap.packed,
            68,
            "^",
        )
        scrollButton(
            "dev_hub:teleport_scroll_down",
            DevHubComponents.teleportScrollDownWrap.packed,
            68 + TELEPORT_VIEW_HEIGHT - 18,
            "v",
        )

        build("dev_hub:page_prev_wrap") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 160
            y = 292
            width = 40
            height = 24
        }
        build("dev_hub:page_prev") {
            holderLayer(
                parent = DevHubComponents.pagePrevWrap.packed,
                opText = "Previous page",
                hoverTextPacked = DevHubComponents.page_prev_text.packed,
            )
        }
        build("dev_hub:page_prev_text") {
            // A literal `<` starts a formatting tag in the client's text renderer and draws
            // nothing; `<lt>` is the escape for it. (Verified in-client: the button hovered and
            // clicked fine, but its label was invisible.)
            buttonText(parent = DevHubComponents.page_prev.packed, label = "<lt>")
            events = EVENTS_OP1
        }
        build("dev_hub:page_next_wrap") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 456
            y = 292
            width = 40
            height = 24
        }
        build("dev_hub:page_next") {
            holderLayer(
                parent = DevHubComponents.pageNextWrap.packed,
                opText = "Next page",
                hoverTextPacked = DevHubComponents.page_next_text.packed,
            )
        }
        build("dev_hub:page_next_text") {
            buttonText(parent = DevHubComponents.page_next.packed, label = "<gt>")
            events = EVENTS_OP1
        }
        build("dev_hub:page_label") {
            type = TYPE_TEXT
            layer = DevHubComponents.root.packed
            x = 200
            y = 292
            width = 256
            height = 24
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = COLOUR_ORANGE
        }
    }

    /**
     * The clickable holder inside a button's `_wrap` layer: fills the wrap so its childIndex is 0,
     * letting the client's `children[0]` click resolution reach the first visual child.
     */
    private fun ComponentTypeBuilder.holderLayer(
        parent: Int,
        opText: String,
        hoverTextPacked: Int,
    ) {
        type = TYPE_LAYER
        layer = parent
        widthMode = SIZE_FILL
        heightMode = SIZE_FILL
        events = EVENTS_OP1
        op = arrayOf(opText)
        onMouseOver = arrayOf(CS_RECOLOUR_TEXT, hoverTextPacked, COLOUR_WHITE)
        onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, hoverTextPacked, COLOUR_ORANGE)
    }

    private fun ComponentTypeBuilder.fillRect(parent: Int, shade: Int, hidden: Boolean = false) {
        type = TYPE_RECT
        layer = parent
        widthMode = SIZE_FILL
        heightMode = SIZE_FILL
        fill = true
        colour1 = shade
        if (hidden) {
            hide = true
        }
    }

    private fun ComponentTypeBuilder.buttonText(
        parent: Int,
        label: String,
        alignLeft: Boolean = false,
    ) {
        type = TYPE_TEXT
        layer = parent
        widthMode = SIZE_FILL
        heightMode = SIZE_FILL
        textFont = FONT_B12
        textAlignH = if (alignLeft) 0 else 1
        textAlignV = 1
        textShadow = true
        colour1 = COLOUR_ORANGE
        text = label
    }

    /** Resolves a scroll-button component's packed id through the generated references object. */
    private fun packed(name: String): Int =
        when (name) {
            "dev_hub:teleport_scroll_up" -> DevHubComponents.teleportScrollUp.packed
            "dev_hub:teleport_scroll_down" -> DevHubComponents.teleportScrollDown.packed
            "dev_hub:item_scroll_up" -> DevHubComponents.itemScrollUp.packed
            "dev_hub:item_scroll_down" -> DevHubComponents.itemScrollDown.packed
            else -> error("No component ref for $name")
        }

    private fun scrollButton(name: String, wrap: Int, yPos: Int, label: String) {
        // Wrap + holder pattern (see `holderLayer`): `hidden` lives on the wrap only so the
        // script can toggle the whole subtree with a single IfSetHide.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = DevHubComponents.root.packed
            x = 484
            y = yPos
            width = 12
            height = 18
            hide = true
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
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = COLOUR_TAB
            events = EVENTS_OP1
        }
        build("${name}_text") {
            type = TYPE_TEXT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = COLOUR_ORANGE
            text = label
        }
    }
}
