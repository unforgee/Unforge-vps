package org.rsmod.content.interfaces.skillshop.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The Skill Quartermaster shop window (interface `unforge_skillshop`), authored from scratch and
 * packed into the cache.
 *
 * A 512x334 centered modal on the `slayer_hub` frame: header (title + skill-point balance + close
 * X), crimson accent rule, and one natively scrollable list of shop rows grouped under category
 * headers. Each row is a card with a name line, a one-line description (effect first, acquisition
 * path second), a gold price tag and a Buy button that the script swaps for an `Owned`/`Lvl N` tag
 * when the purchase is one-time or level-gated.
 *
 * This is the vertical slice for `docs/pvm-skilling-ux-spec.md`: it proves the row anatomy, the
 * points header and the three row states (buyable / locked / owned) end-to-end. Category tabs and
 * item models are deliberately left out - see the spec's post-MVP list.
 */
internal object SkillShopInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with slayer_hub: near-black base, warm panels, crimson accent, gold points.
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

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    private const val SELF: Int = -2147483645

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    private const val CONTENT_Y: Int = 54
    const val CONTENT_VIEW_HEIGHT: Int = 266

    const val ROW_PITCH: Int = 40
    private const val SECTION_PITCH: Int = 18

    /** Scroll step applied by the rail buttons, in pixels (two rows). */
    const val SCROLL_STEP: Int = ROW_PITCH * 2

    /**
     * Total pixel height of the scrollable content: one section header per category plus a footer
     * line, then one row per entry. Assumes entries stay grouped by category in enum order, which
     * is what the init loop below relies on.
     */
    val CONTENT_HEIGHT: Int =
        SECTION_PITCH * (SkillShopCategory.entries.size + 1) +
            SkillShopEntry.entries.size * ROW_PITCH

    /** Maximum valid scroll position for the content layer. */
    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    init {
        build("unforge_skillshop:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("unforge_skillshop:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("unforge_skillshop:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_skillshop:title",
            14,
            10,
            220,
            16,
            "Skill Quartermaster",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_skillshop:points_label",
            300,
            8,
            170,
            12,
            "Skill Points",
            DIM,
            alignH = 2,
            layer = rootRef(),
        )
        text(
            "unforge_skillshop:points_value",
            300,
            22,
            170,
            16,
            "0",
            GOLD,
            alignH = 2,
            layer = rootRef(),
        )
        // Wrap + holder pattern (see `button`): the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("unforge_skillshop:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("unforge_skillshop:close") {
            type = TYPE_LAYER
            layer = SkillShopComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("unforge_skillshop:close_g") {
            type = TYPE_GRAPHIC
            layer = SkillShopComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("unforge_skillshop:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 42
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        build("unforge_skillshop:content") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 12
            y = CONTENT_Y
            width = 460
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
        }
        val contentRef = SkillShopComponents.content.packed

        var cursorY = 0
        var category: SkillShopCategory? = null
        for ((index, entry) in SkillShopEntry.entries.withIndex()) {
            if (entry.category != category) {
                category = entry.category
                text(
                    "unforge_skillshop:sec_${category.name.lowercase()}",
                    4,
                    cursorY,
                    440,
                    14,
                    category.label,
                    ORANGE,
                    alignH = 0,
                    layer = contentRef,
                )
                cursorY += SECTION_PITCH
            }
            entryRow(index, entry, cursorY, contentRef)
            cursorY += ROW_PITCH
        }
        text(
            "unforge_skillshop:footer",
            4,
            cursorY,
            440,
            14,
            "Points come from skilling - any non-combat skill.",
            FAINT,
            alignH = 0,
            layer = contentRef,
        )

        build("unforge_skillshop:scrollbar") {
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
            "unforge_skillshop:scroll_up",
            SkillShopComponents.scrollUpWrap.packed,
            CONTENT_Y,
            "^",
        )
        scrollButton(
            "unforge_skillshop:scroll_down",
            SkillShopComponents.scrollDownWrap.packed,
            CONTENT_Y + CONTENT_VIEW_HEIGHT - 18,
            "v",
        )
    }

    private fun rootRef() = SkillShopComponents.root.packed

    private fun entryRow(index: Int, entry: SkillShopEntry, rowY: Int, contentRef: Int) {
        build("unforge_skillshop:entry${index}_bg") {
            type = TYPE_RECT
            layer = contentRef
            x = 0
            y = rowY
            width = 452
            height = ROW_PITCH - 2
            fill = true
            colour1 = PANEL
        }
        build("unforge_skillshop:entry${index}_bg_border") {
            type = TYPE_RECT
            layer = contentRef
            x = 0
            y = rowY
            width = 452
            height = ROW_PITCH - 2
            fill = false
            colour1 = CARD_BORDER
        }
        text(
            "unforge_skillshop:entry${index}_name",
            8,
            rowY + 4,
            286,
            13,
            entry.label,
            ORANGE,
            alignH = 0,
            layer = contentRef,
        )
        text(
            "unforge_skillshop:entry${index}_desc",
            8,
            rowY + 20,
            286,
            12,
            entry.description,
            DIM,
            alignH = 0,
            layer = contentRef,
        )
        text(
            "unforge_skillshop:entry${index}_price",
            300,
            rowY + 13,
            76,
            14,
            "${entry.cost}p",
            GOLD,
            alignH = 2,
            layer = contentRef,
        )
        button(
            "unforge_skillshop:entry${index}_buy",
            SkillShopComponents.entryBuyWraps[index].packed,
            contentRef,
            382,
            rowY + 9,
            62,
            20,
            "Buy",
            boxColour = PANEL_ALT,
            textColour = GREEN,
        )
        // Swaps in for the Buy button when the row can't be bought: "Owned" (unlock already
        // purchased) or "Lvl N" (skill gate unmet). Never an error colour - see the spec.
        text(
            "unforge_skillshop:entry${index}_tag",
            382,
            rowY + 13,
            62,
            14,
            "",
            DIM,
            alignH = 1,
            layer = contentRef,
            hidden = true,
        )
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
        hidden: Boolean = false,
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
            if (hidden) {
                hide = true
            }
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
            if (hidden) {
                hide = true
            }
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(name)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = boxColour
            events = EVENTS_OP1
            if (hidden) {
                hide = true
            }
        }
        text("${name}_text", 0, 0, w, h, label, textColour, layer = packed(name), hidden = hidden)
    }

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(name: String, suffix: String = ""): Int {
        val key = name.substringAfter(':') + suffix
        return SkillShopComponents.all.getValue(key).packed
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
