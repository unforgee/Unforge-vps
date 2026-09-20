package org.rsmod.api.ironman

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.game.ironman.GameMode

/**
 * The first-login game mode selection window (`unforge_gamemode`), authored from scratch and packed
 * into the cache. Replaces the vanilla CS2-driven `ironman_setup` screen, whose buttons never reach
 * the server in this flow.
 *
 * Layout (512x334 centered modal, same chrome as `unforge_skillshop`/`unforge_talenttree`): header
 * with title + subtitle and a crimson accent rule, a 2x3 grid of mode cards (one per [GameMode]
 * entry: name + difficulty tagline, accent outline shown on the selected card via a `c{i}_sel`
 * overlay), a detail line for the hovered/selected mode, a status line for errors, and a wide
 * Confirm button at the bottom-right. Every clickable surface uses the wrap + holder pattern: alone
 * inside `${name}_wrap` the holder's childIndex is 0, so `children[0]` (`_box`) resolves and the
 * If3Button packet is emitted.
 */
internal object GameModeInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with unforge_skillshop / unforge_talenttree.
    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1F1B15
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    const val MODE_COUNT: Int = 6

    private const val CARD_W: Int = 236
    private const val CARD_H: Int = 66
    private const val CARD_X0: Int = 14
    private const val CARD_X1: Int = 262
    private const val CARD_Y0: Int = 54
    private const val CARD_PITCH: Int = 72

    /** Short per-mode tagline shown under the name on each card. */
    private val TAGLINES =
        listOf(
            "Standard - trade, bank and play with no restrictions.",
            "Self-sufficient: no trading or player drops.",
            "One life: a dangerous death demotes to Ironman.",
            "No bank - carry everything you own.",
            "Ultimate rules with one life. Extreme.",
            "Ironman rules shared with a permanent group.",
        )

    init {
        build("unforge_gamemode:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("unforge_gamemode:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("unforge_gamemode:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_gamemode:title",
            14,
            9,
            280,
            15,
            "Choose your game mode",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_gamemode:subtitle",
            14,
            26,
            400,
            12,
            "This choice is permanent - pick the account type you want to play.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("unforge_gamemode:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        for (index in 0 until MODE_COUNT) {
            val cardX = if (index % 2 == 0) CARD_X0 else CARD_X1
            val cardY = CARD_Y0 + (index / 2) * CARD_PITCH
            card(index, cardX, cardY)
        }

        text(
            "unforge_gamemode:detail",
            14,
            272,
            484,
            14,
            "Select a game mode to see its rules.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        text("unforge_gamemode:status", 14, 292, 236, 30, "", FAINT, alignH = 0, layer = rootRef())

        // Confirm button (wrap + holder): `children[0]` = `confirm_box` resolves the click.
        build("unforge_gamemode:confirm_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 262
            y = 290
            width = 236
            height = 32
        }
        build("unforge_gamemode:confirm") {
            type = TYPE_LAYER
            layer = GameModeComponents.confirmWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Confirm")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), GOLD)
        }
        build("unforge_gamemode:confirm_box") {
            type = TYPE_RECT
            layer = GameModeComponents.confirm.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = ACCENT
            events = EVENTS_OP1
        }
        text(
            "unforge_gamemode:confirm_text",
            0,
            0,
            236,
            32,
            "Select a mode",
            GOLD,
            layer = GameModeComponents.confirm.packed,
        )
    }

    private fun rootRef() = GameModeComponents.root.packed

    /**
     * One mode card: `${c}_wrap` positions on root, `${c}_hit` fills it and carries the `Select`
     * op, `${c}_box` (children[0]) resolves the click, `${c}_sel` is the accent outline the script
     * unhides on the selected card, `${c}_name`/`${c}_tag` render the mode name and tagline.
     */
    private fun card(index: Int, cardX: Int, cardY: Int) {
        val p = "unforge_gamemode:c$index"
        val mode = GameMode.entries[index]
        build("${p}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = cardX
            y = cardY
            width = CARD_W
            height = CARD_H
        }
        build("${p}_hit") {
            type = TYPE_LAYER
            layer = GameModeComponents.cardWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), GOLD)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = GameModeComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("${p}_sel") {
            type = TYPE_RECT
            layer = GameModeComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = ACCENT
            hide = true
        }
        text(
            "${p}_name",
            10,
            8,
            216,
            14,
            mode.displayName,
            GOLD,
            alignH = 0,
            layer = GameModeComponents.cardHits[index].packed,
        )
        text(
            "${p}_tag",
            10,
            28,
            216,
            30,
            "${mode.difficulty} - ${TAGLINES[index]}",
            DIM,
            alignH = 0,
            layer = GameModeComponents.cardHits[index].packed,
        )
    }

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(childName: String): Int = GameModeComponents.all.getValue(childName).packed

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
