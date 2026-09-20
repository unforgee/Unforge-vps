package org.rsmod.content.interfaces.iteminspect.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The item-inspect side panel (`unforge_iteminspect`), authored from scratch and packed into the
 * cache. Opened via `ifOpenSide` whenever a player examines a weapon or armour piece.
 *
 * A 243x334 panel sized for the side-modal slot: header (item name + close X), a subtitle line
 * carrying the instance summary, a 3D obj model on the right, a crimson accent rule, and a natively
 * scrollable stack of text lines. The script fills the fixed line pool with the authoritative
 * [org.rsmod.api.player.output.EquipmentInstanceDescribe] output (effective stats, affix rolls,
 * sockets, unique effects) plus per-ability mechanic explanations resolved from
 * [org.rsmod.api.equipment.instance.EquipmentAbilityProcs].
 */
internal object ItemInspectInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5
    private const val TYPE_MODEL: Int = 6

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with slayer_hub / unforge_skillshop.
    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1F1B15
    private const val CARD_BORDER: Int = 0x3D3428
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    private const val SELF: Int = -2147483645

    // Match the companion dashboard shell so equipment inspection feels like part of the same
    // modern UI rather than a legacy narrow side panel.
    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    private const val CONTENT_Y: Int = 94
    const val CONTENT_VIEW_HEIGHT: Int = 232

    const val LINE_PITCH: Int = 14
    private const val LINE_POOL: Int = ItemInspectComponents.LINE_POOL

    /** Scroll step applied by the rail buttons, in pixels (two lines). */
    const val SCROLL_STEP: Int = LINE_PITCH * 2

    const val CONTENT_HEIGHT: Int = LINE_POOL * LINE_PITCH

    /** Maximum valid scroll position for the content layer. */
    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    init {
        build("unforge_iteminspect:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("unforge_iteminspect:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("unforge_iteminspect:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_iteminspect:title",
            8,
            8,
            170,
            16,
            "Item",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text("unforge_iteminspect:subtitle", 8, 26, 170, 12, "", DIM, alignH = 0, layer = rootRef())
        // Wrap + holder pattern (see `scrollButton`): the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("unforge_iteminspect:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 16
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("unforge_iteminspect:close") {
            type = TYPE_LAYER
            layer = packed("close_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("unforge_iteminspect:close_g") {
            type = TYPE_GRAPHIC
            layer = packed("close")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("unforge_iteminspect:item_model") {
            type = TYPE_MODEL
            layer = rootRef()
            x = 16
            y = 32
            width = 52
            height = 52
            xMode = MODE_FAR
            // Same round-trip constraint as slayer_hub:npc_head - the real obj model is pushed at
            // runtime via IfSetObject.
            model = 0
            modelAnim = 0
            modelZoom = 900
        }
        build("unforge_iteminspect:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 88
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        build("unforge_iteminspect:content") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 16
            y = CONTENT_Y
            width = 456
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
        }
        val contentRef = packed("content")
        for (i in 0 until LINE_POOL) {
            text(
                "unforge_iteminspect:line$i",
                0,
                i * LINE_PITCH,
                452,
                LINE_PITCH,
                "",
                WHITE,
                alignH = 0,
                layer = contentRef,
            )
        }

        build("unforge_iteminspect:scrollbar") {
            type = TYPE_RECT
            layer = rootRef()
            x = 488
            y = CONTENT_Y
            width = 8
            height = CONTENT_VIEW_HEIGHT
            fill = true
            colour1 = PANEL
        }
        scrollButton("unforge_iteminspect:scroll_up", CONTENT_Y, "^")
        scrollButton("unforge_iteminspect:scroll_down", CONTENT_Y + CONTENT_VIEW_HEIGHT - 18, "v")
    }

    private fun rootRef() = packed("root")

    private fun scrollButton(name: String, yPos: Int, label: String) {
        // Wrap + holder pattern: the client resolves a click through
        // `widget.children[widget.childIndex]` - alone inside `${name}_wrap` the button's
        // childIndex is 0, so `children[0]` (= `_box`) resolves and the packet is emitted.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 486
            y = yPos
            width = 12
            height = 18
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed(name.substringAfter(':') + "_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Scroll")
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(name.substringAfter(':'))
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = CARD_BORDER
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, 12, 18, label, GOLD, layer = packed(name.substringAfter(':')))
    }

    /** Packed component id resolved through `component.sym` via the reference map. */
    private fun packed(childName: String): Int =
        ItemInspectComponents.all.getValue(childName).packed

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
