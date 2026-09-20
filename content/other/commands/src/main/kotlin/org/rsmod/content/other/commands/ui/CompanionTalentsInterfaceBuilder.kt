package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The companion talent window (`companion_talents`), authored from scratch and packed into the
 * cache. Replaces the legacy chatbox `menu()` dialog for allocating companion talent points.
 *
 * Layout (512x334 centered modal, same chrome as `unforge_talenttree`): header with a dynamic
 * title, a class/points subtitle, close X and a crimson accent rule. Below it a slim role strip
 * (TANK / SUPPORT / DPS - the current class is outlined). The body is one scrollable list that
 * holds every talent card of the companion's class (2 columns x 12 rows inside a `scrollHeight`
 * layer) - no pagination. Each card shows the talent name, tier badge, `N / M` rank and its
 * effect/ability key; an accent outline (`_sel`) marks talents with ranks trained. Clicking a card
 * spends one point (`Train`) or toggles the talent's ability in the loadout. The bottom bar carries
 * the three ability loadout slots and a Reset button, with a status line underneath.
 *
 * Every clickable surface uses the wrap + holder pattern: alone inside `${name}_wrap` the holder's
 * childIndex is 0, so `children[0]` (`_box`) resolves and the If3Button packet is emitted.
 */
internal object CompanionTalentsInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with unforge_skillshop / unforge_talenttree.
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

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    /** cs2 44: swaps a graphic component's sprite. `(component, graphic)` */
    private const val CS_SWAP_GRAPHIC: Int = 44

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    private const val SELF: Int = -2147483645
    private val ABILITY_ICONS = intArrayOf(30000, 30001, 30002)

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    /** Every talent row of a class lives in the scroll layer - no pagination. */
    const val TALENT_ROWS: Int = 24

    const val ROLE_COUNT: Int = 3

    private const val CARD_W: Int = 236
    private const val CARD_H: Int = 52
    private const val CARD_PITCH: Int = 56

    private const val SCROLL_X: Int = 14
    private const val SCROLL_Y: Int = 80
    private const val SCROLL_W: Int = 484
    private const val SCROLL_H: Int = 190
    private const val SCROLL_CONTENT_H: Int = (TALENT_ROWS / 2) * CARD_PITCH

    /** Scroll step applied by the ▲/▼ buttons, in pixels (two card rows). */
    const val SCROLL_STEP: Int = CARD_PITCH * 2

    /** Maximum valid scroll position for the talent list. */
    val MAX_SCROLL: Int = (SCROLL_CONTENT_H - SCROLL_H).coerceAtLeast(0)

    private const val ROLE_W: Int = 156
    private const val ROLE_H: Int = 24
    private const val ROLE_Y: Int = 50
    private const val ROLE_PITCH: Int = 164

    private const val BAR_Y: Int = 276
    private const val BAR_H: Int = 22
    private const val ABILITY_W: Int = 120
    private const val ABILITY_PITCH: Int = 124
    private const val RESET_X: Int = 390
    private const val RESET_W: Int = 108

    private const val STATUS_Y: Int = 302

    init {
        build("companion_talents:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("companion_talents:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("companion_talents:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "companion_talents:title",
            14,
            9,
            280,
            15,
            "Companion Talents",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text("companion_talents:subtitle", 14, 26, 360, 12, "", DIM, alignH = 0, layer = rootRef())
        // Wrap + holder pattern: the client resolves a click through
        // `widget.children[widget.childIndex]`; alone inside `close_wrap` the holder's childIndex
        // is 0, so `children[0]` = `close_g` resolves and the If3Button packet is emitted.
        build("companion_talents:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_talents:close") {
            type = TYPE_LAYER
            layer = CompanionTalentsComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_talents:close_g") {
            type = TYPE_GRAPHIC
            layer = CompanionTalentsComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("companion_talents:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        val roleLabels = arrayOf("TANK", "SUPPORT", "DPS")
        for (index in 0 until ROLE_COUNT) {
            roleButton(index, 14 + index * ROLE_PITCH, roleLabels[index])
        }

        // The whole class tree lives inside one scrollable layer: 2 columns x 12 rows of
        // talent cards. The client clips children to the view rect; if3 components have no
        // wheel-scroll hook in this client, so explicit ▲/▼ buttons step the position
        // server-side via `ifSetScrollPos` (same pattern as unforge_perks).
        build("companion_talents:talent_scroll") {
            type = TYPE_LAYER
            layer = rootRef()
            x = SCROLL_X
            y = SCROLL_Y
            width = SCROLL_W
            height = SCROLL_H
            scrollHeight = SCROLL_CONTENT_H
        }
        for (index in 0 until TALENT_ROWS) {
            val cardX = if (index % 2 == 0) 0 else 248
            val cardY = (index / 2) * CARD_PITCH
            card(index, cardX, cardY)
        }

        // Decorative scrollbar track on the right edge of the list viewport.
        build("companion_talents:scroll_bar") {
            type = TYPE_RECT
            layer = rootRef()
            x = SCROLL_X + SCROLL_W + 2
            y = SCROLL_Y
            width = 4
            height = SCROLL_H
            fill = true
            colour1 = PANEL
        }
        scrollButton("companion_talents:scroll_up", SCROLL_Y, "^")
        scrollButton("companion_talents:scroll_down", SCROLL_Y + SCROLL_H - 18, "v")

        for (index in 0 until 3) {
            abilitySlot(index, 14 + index * ABILITY_PITCH, BAR_Y)
        }

        // Reset button (wrap + holder): `children[0]` = `reset_box` resolves the click.
        build("companion_talents:reset_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = RESET_X
            y = BAR_Y
            width = RESET_W
            height = BAR_H
        }
        build("companion_talents:reset") {
            type = TYPE_LAYER
            layer = CompanionTalentsComponents.resetWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Reset")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("reset_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("reset_text"), ORANGE)
        }
        build("companion_talents:reset_box") {
            type = TYPE_RECT
            layer = CompanionTalentsComponents.reset.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        text(
            "companion_talents:reset_text",
            0,
            0,
            RESET_W,
            BAR_H,
            "Reset Talents",
            ORANGE,
            layer = CompanionTalentsComponents.reset.packed,
        )

        text(
            "companion_talents:status",
            14,
            STATUS_Y,
            484,
            14,
            "",
            FAINT,
            alignH = 0,
            layer = rootRef(),
        )
    }

    private fun rootRef() = CompanionTalentsComponents.root.packed

    private fun scrollRef() = CompanionTalentsComponents.all.getValue("talent_scroll").packed

    private fun abilitySlot(index: Int, xPos: Int, yPos: Int) {
        val id = "ability$index"
        build("companion_talents:${id}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = xPos
            y = yPos
            width = ABILITY_W
            height = BAR_H
        }
        build("companion_talents:$id") {
            type = TYPE_LAYER
            layer = packed("${id}_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Equip")
        }
        build("companion_talents:${id}_box") {
            type = TYPE_RECT
            layer = packed(id)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        build("companion_talents:${id}_icon") {
            type = TYPE_GRAPHIC
            layer = packed(id)
            x = 2
            y = 2
            width = 18
            height = 18
            graphic = ABILITY_ICONS[index]
        }
        text(
            "companion_talents:${id}_text",
            24,
            0,
            ABILITY_W - 28,
            BAR_H,
            "Ability ${index + 1}: Empty",
            DIM,
            alignH = 0,
            layer = packed(id),
        )
    }

    /**
     * One talent card: `${t}_wrap` positions on root, `${t}_hit` fills it and carries the `Train`
     * op, `${t}_box` (children[0]) resolves the click, `${t}_sel` is the accent outline the script
     * unhides on trained talents, and the text lines render name / tier / rank / effect.
     */
    private fun card(index: Int, cardX: Int, cardY: Int) {
        val p = "companion_talents:t$index"
        build("${p}_wrap") {
            type = TYPE_LAYER
            layer = scrollRef()
            x = cardX
            y = cardY
            width = CARD_W
            height = CARD_H
        }
        build("${p}_hit") {
            type = TYPE_LAYER
            layer = CompanionTalentsComponents.cardWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Train")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("t${index}_name"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("t${index}_name"), GOLD)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = CompanionTalentsComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("${p}_sel") {
            type = TYPE_RECT
            layer = CompanionTalentsComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = ACCENT
            hide = true
        }
        val hitRef = CompanionTalentsComponents.cardHits[index].packed
        text("${p}_name", 8, 4, 160, 13, "", GOLD, alignH = 0, layer = hitRef)
        text("${p}_tier", 168, 4, 60, 12, "", DIM, alignH = 2, layer = hitRef)
        text("${p}_rank", 8, 19, 220, 12, "", GOLD, alignH = 0, layer = hitRef)
        text("${p}_fx", 8, 33, 220, 14, "", DIM, alignH = 0, layer = hitRef)
    }

    /**
     * One ▲/▼ scroll stepper: `${name}_wrap` positions on root, `${name}` fills it and carries the
     * `Scroll` op, `${name}_box` (children[0]) resolves the click, `${name}_text` is the arrow
     * glyph. Clicks step `talent_scroll`'s position server-side via `ifSetScrollPos`.
     */
    private fun scrollButton(name: String, yPos: Int, label: String) {
        val holder = name.removePrefix("companion_talents:")
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = SCROLL_X + SCROLL_W - 14
            y = yPos
            width = 14
            height = 18
        }
        build(name) {
            type = TYPE_LAYER
            layer = packed("${holder}_wrap")
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Scroll")
        }
        build("${name}_box") {
            type = TYPE_RECT
            layer = packed(holder)
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        text(name + "_text", 0, 0, 14, 18, label, GOLD, layer = packed(holder))
    }

    /** One role button: holder + box (children[0], click resolution) + `_sel` outline + label. */
    private fun roleButton(index: Int, xPos: Int, label: String) {
        val p = "companion_talents:r$index"
        build("${p}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = xPos
            y = ROLE_Y
            width = ROLE_W
            height = ROLE_H
        }
        build(p) {
            type = TYPE_LAYER
            layer = CompanionTalentsComponents.roleWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("r${index}_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("r${index}_text"), DIM)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = CompanionTalentsComponents.roleHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1
        }
        build("${p}_sel") {
            type = TYPE_RECT
            layer = CompanionTalentsComponents.roleHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = ACCENT
            hide = true
        }
        text(
            "${p}_text",
            0,
            0,
            ROLE_W,
            ROLE_H,
            label,
            DIM,
            layer = CompanionTalentsComponents.roleHits[index].packed,
        )
    }

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(childName: String): Int =
        CompanionTalentsComponents.all.getValue(childName).packed

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
