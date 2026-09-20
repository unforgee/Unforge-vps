package org.rsmod.content.interfaces.talenttree.configs

import org.rsmod.api.talents.TalentDefinition
import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The shared talent-tree window (`unforge_talenttree`), authored from scratch and packed into the
 * cache. One interface serves both trees - `TalentTreeScript` populates it with the player catalog
 * for `::talents` or the company catalog for `::companytree`.
 *
 * Layout (512x334 centered modal, same chrome as `unforge_skillshop`): header with a dynamic title,
 * "Points Spent: X / Y" and an available-points line, a Reset button, close X and a crimson accent
 * rule. The body is a natively scrollable content layer holding one section per tier: a "Tier N"
 * header plus a "N points required per talent rank." subtitle followed by a 5-column card grid.
 * Each card is a tall framed box modelled on the reference talent tree: a bordered icon socket on
 * top holding the obj model, the talent name under it (up to two lines), an `N / M` rank counter
 * and a text state tag (`Train` / `Locked` / `Max` / `Leader` / `Cost N`). Trained cards swap to a
 * green fill via a second background layer the script toggles - colour is a bonus cue on top of the
 * always-present text tag, never the only indicator. The whole card surface is one clickable layer
 * with `Train` (op1) and `Info` (op2) actions.
 */
internal object TalentTreeInterfaceBuilder : InterfaceBuilder() {
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

    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1F1B15
    private const val PANEL_ALT: Int = 0x2A241C
    private const val PANEL_GREEN: Int = 0x2E4A2B
    private const val CARD_BORDER: Int = 0x3D3428
    private const val SOCKET: Int = 0x4A4033
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    private const val SELF: Int = -2147483645

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    private const val CONTENT_Y: Int = 50
    const val CONTENT_VIEW_HEIGHT: Int = 252

    private const val CARD_W: Int = 86
    private const val CARD_H: Int = 104
    private const val CARD_PITCH: Int = 89
    private const val TIER_HDR_PITCH: Int = 16
    private const val TIER_SUB_PITCH: Int = 12
    private const val TIER_PITCH: Int = TIER_HDR_PITCH + TIER_SUB_PITCH + CARD_H + 6

    const val CARDS_PER_TIER: Int = TalentDefinition.SLOTS_PER_TIER
    const val TIER_COUNT: Int = TalentDefinition.MAX_TIERS
    const val CARD_COUNT: Int = CARDS_PER_TIER * TIER_COUNT

    /** Scroll step applied by the rail buttons, in pixels (one tier block). */
    const val SCROLL_STEP: Int = TIER_PITCH

    private const val FOOTER_PITCH: Int = 16

    /** Total pixel height of the scrollable content. */
    val CONTENT_HEIGHT: Int = TIER_PITCH * TIER_COUNT + FOOTER_PITCH

    /** Maximum valid scroll position for the content layer. */
    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    /** Flat card index for a (1-based tier, 0-based slot) grid position. */
    fun cardIndex(tier: Int, slot: Int): Int = (tier - 1) * CARDS_PER_TIER + slot

    init {
        build("unforge_talenttree:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("unforge_talenttree:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("unforge_talenttree:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_talenttree:title",
            14,
            9,
            220,
            15,
            "Talent Tree",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_talenttree:avail",
            14,
            26,
            200,
            11,
            "Available: 0 pts",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_talenttree:points_label",
            240,
            9,
            160,
            11,
            "Points Spent",
            DIM,
            alignH = 2,
            layer = rootRef(),
        )
        text(
            "unforge_talenttree:points_value",
            240,
            22,
            160,
            15,
            "0 / 0",
            GOLD,
            alignH = 2,
            layer = rootRef(),
        )
        button(
            "unforge_talenttree:reset",
            TalentTreeComponents.resetWrap.packed,
            rootRef(),
            408,
            10,
            62,
            20,
            "Reset",
            boxColour = PANEL_ALT,
            textColour = ORANGE,
        )
        // Wrap + holder pattern (see `button`): the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("unforge_talenttree:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("unforge_talenttree:close") {
            type = TYPE_LAYER
            layer = TalentTreeComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("unforge_talenttree:close_g") {
            type = TYPE_GRAPHIC
            layer = TalentTreeComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("unforge_talenttree:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 42
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        build("unforge_talenttree:content") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 12
            y = CONTENT_Y
            width = 460
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
        }
        val contentRef = TalentTreeComponents.content.packed

        var cursorY = 0
        for (tier in 1..TIER_COUNT) {
            text(
                "unforge_talenttree:tier${tier}_hdr",
                4,
                cursorY,
                440,
                14,
                "Tier $tier",
                ORANGE,
                alignH = 0,
                layer = contentRef,
            )
            text(
                "unforge_talenttree:tier${tier}_sub",
                4,
                cursorY + TIER_HDR_PITCH - 2,
                440,
                12,
                "",
                DIM,
                alignH = 0,
                layer = contentRef,
            )
            for (slot in 0 until CARDS_PER_TIER) {
                card(
                    cardIndex(tier, slot),
                    slot * CARD_PITCH,
                    cursorY + TIER_HDR_PITCH + TIER_SUB_PITCH,
                    contentRef,
                )
            }
            cursorY += TIER_PITCH
        }
        text(
            "unforge_talenttree:footer",
            4,
            cursorY,
            440,
            14,
            "Right-click a talent for details.",
            FAINT,
            alignH = 0,
            layer = contentRef,
        )

        build("unforge_talenttree:scrollbar") {
            type = TYPE_RECT
            layer = rootRef()
            x = 476
            y = CONTENT_Y
            width = 8
            height = CONTENT_VIEW_HEIGHT
            fill = true
            colour1 = PANEL
        }
        scrollButton(
            "unforge_talenttree:scroll_up",
            TalentTreeComponents.scrollUpWrap.packed,
            CONTENT_Y,
            "^",
        )
        scrollButton(
            "unforge_talenttree:scroll_down",
            TalentTreeComponents.scrollDownWrap.packed,
            CONTENT_Y + CONTENT_VIEW_HEIGHT - 18,
            "v",
        )
    }

    private fun rootRef() = TalentTreeComponents.root.packed

    /**
     * One talent card in the reference layout: a dark fill for untrained plus a green fill the
     * script unhides once rank > 0, an outer border, a bordered icon socket up top holding the obj
     * model, the name under it (two lines), then rank / tag / cost lines. A single hit layer on top
     * carries the `Train`/`Info` actions. Every part is static chrome - the script fills
     * name/rank/tag/cost/icon, picks the background and hides whole cards for unused slots.
     */
    private fun card(index: Int, cardX: Int, cardY: Int, contentRef: Int) {
        val p = "unforge_talenttree:c${index}"
        build("${p}_bg") {
            type = TYPE_RECT
            layer = contentRef
            x = cardX
            y = cardY
            width = CARD_W
            height = CARD_H
            fill = true
            colour1 = PANEL
        }
        build("${p}_bg_on") {
            type = TYPE_RECT
            layer = contentRef
            x = cardX
            y = cardY
            width = CARD_W
            height = CARD_H
            fill = true
            colour1 = PANEL_GREEN
            hide = true
        }
        build("${p}_iframe") {
            type = TYPE_RECT
            layer = contentRef
            x = cardX + 24
            y = cardY + 5
            width = 38
            height = 38
            fill = false
            colour1 = SOCKET
        }
        build("${p}_icon") {
            type = TYPE_MODEL
            layer = contentRef
            x = cardX + 28
            y = cardY + 9
            width = 30
            height = 30
            model = 0
            modelAnim = 0
            modelZoom = 800
        }
        text("${p}_name", cardX + 2, cardY + 46, 82, 22, "", ORANGE, alignH = 1, layer = contentRef)
        text(
            "${p}_rank",
            cardX + 4,
            cardY + 69,
            78,
            12,
            "0/0",
            GOLD,
            alignH = 1,
            layer = contentRef,
        )
        text("${p}_tag", cardX + 4, cardY + 81, 78, 10, "", DIM, alignH = 1, layer = contentRef)
        text("${p}_cost", cardX + 4, cardY + 92, 78, 10, "", DIM, alignH = 1, layer = contentRef)
        // Wrap + holder pattern: alone inside `c{index}_hit_wrap` the hit layer's childIndex is
        // 0, so `hit.children[0]` (= `c{index}_bd`) resolves on click and the packet is sent.
        // The bd rect carries the op flags since it is the component the client resolves to.
        build("${p}_hit_wrap") {
            type = TYPE_LAYER
            layer = contentRef
            x = cardX
            y = cardY
            width = CARD_W
            height = CARD_H
        }
        build("${p}_hit") {
            type = TYPE_LAYER
            layer = TalentTreeComponents.cardHitWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1_OP2
            op = arrayOf("Train", "Info")
            opBase = "talent"
        }
        build("${p}_bd") {
            type = TYPE_RECT
            layer = TalentTreeComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = CARD_BORDER
            events = EVENTS_OP1_OP2
        }
    }

    private fun scrollButton(name: String, wrap: Int, yPos: Int, label: String) {
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 474
            y = yPos
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
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = CARD_BORDER
            events = EVENTS_OP1
        }
        text("${name}_text", 0, 0, 12, 18, label, GOLD, layer = packed(name))
    }

    private fun button(
        name: String,
        wrap: Int,
        parent: Int,
        xPos: Int,
        yPos: Int,
        w: Int,
        h: Int,
        label: String,
        boxColour: Int = PANEL_ALT,
        textColour: Int = ORANGE,
    ) {
        // Wrap + holder pattern: the client resolves a click through
        // `widget.children[widget.childIndex]` - alone inside `${name}_wrap` the button's
        // childIndex is 0, so `children[0]` (= `_box`) resolves and the packet is emitted.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = parent
            x = xPos
            y = yPos
            width = w
            height = h
        }
        build(name) {
            type = TYPE_LAYER
            layer = wrap
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf(label)
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

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(name: String, suffix: String = ""): Int {
        val key = name.substringAfter(':') + suffix
        return TalentTreeComponents.all.getValue(key).packed
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
        }
    }
}
