package org.rsmod.content.other.earlygame

import org.rsmod.api.type.builders.interf.InterfaceBuilder
import org.rsmod.game.difficulty.Difficulty

/**
 * The first-login difficulty selection window (`unforge_difficulty`), authored from scratch and
 * packed into the cache.
 *
 * Layout (512x334 centered modal, same chrome as `unforge_gamemode`/`starter_relic`): header with
 * title + subtitle and a crimson accent rule, five compact tier cards (one per [Difficulty]: name +
 * tagline, accent outline shown on the selected card via a `c{i}_sel` overlay), a detail line for
 * the selected tier, a status line for errors, and a wide Confirm button at the bottom-right. Every
 * clickable surface uses the wrap + holder pattern: alone inside `${name}_wrap` the holder's
 * childIndex is 0, so `children[0]` (`_box`) resolves and the If3Button packet is emitted.
 */
internal object DifficultyInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with unforge_gamemode / starter_relic.
    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1F1B15
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154

    /** cs2 45: recolors a text component. `(component, colour)` */
    private const val CS_RECOLOUR_TEXT: Int = 45

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    const val TIER_COUNT: Int = 5

    private const val CARD_W: Int = 484
    private const val CARD_H: Int = 38
    private const val CARD_X: Int = 14
    private const val CARD_Y0: Int = 50
    private const val CARD_PITCH: Int = 42

    init {
        build("unforge_difficulty:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("unforge_difficulty:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("unforge_difficulty:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "unforge_difficulty:title",
            14,
            9,
            280,
            15,
            "Choose your Difficulty",
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_difficulty:subtitle",
            14,
            26,
            470,
            12,
            "Your XP rate and bonuses are set by this choice - it is permanent.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("unforge_difficulty:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        for (index in 0 until TIER_COUNT) {
            card(index, CARD_X, CARD_Y0 + index * CARD_PITCH)
        }

        text(
            "unforge_difficulty:detail",
            14,
            262,
            484,
            14,
            "Select a difficulty to see its rules.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "unforge_difficulty:status",
            14,
            292,
            320,
            14,
            "",
            FAINT,
            alignH = 0,
            layer = rootRef(),
        )

        // Confirm button (wrap + holder): `children[0]` = `confirm_box` resolves the click.
        build("unforge_difficulty:confirm_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 346
            y = 288
            width = 152
            height = 30
        }
        build("unforge_difficulty:confirm") {
            type = TYPE_LAYER
            layer = DifficultyComponents.confirmWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Confirm")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), GOLD)
        }
        build("unforge_difficulty:confirm_box") {
            type = TYPE_RECT
            layer = DifficultyComponents.confirm.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = ACCENT
            events = EVENTS_OP1
        }
        text(
            "unforge_difficulty:confirm_text",
            0,
            0,
            152,
            30,
            "Select a difficulty",
            GOLD,
            layer = DifficultyComponents.confirm.packed,
        )
    }

    private fun rootRef() = DifficultyComponents.root.packed

    /**
     * One tier card: `${c}_wrap` positions on root, `${c}_hit` fills it and carries the `Select`
     * op, `${c}_box` (children[0]) resolves the click, `${c}_sel` is the accent outline the script
     * unhides on the selected card, `${c}_name`/`${c}_tag` render the tier name and its rule
     * summary.
     */
    private fun card(index: Int, cardX: Int, cardY: Int) {
        val p = "unforge_difficulty:c$index"
        val tier = Difficulty.entries[index]
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
            layer = DifficultyComponents.cardWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), GOLD)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = DifficultyComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("${p}_sel") {
            type = TYPE_RECT
            layer = DifficultyComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = ACCENT
            hide = true
        }
        text(
            "${p}_name",
            10,
            4,
            460,
            14,
            tier.displayName,
            GOLD,
            alignH = 0,
            layer = DifficultyComponents.cardHits[index].packed,
        )
        text(
            "${p}_tag",
            10,
            19,
            460,
            14,
            tier.tagline,
            DIM,
            alignH = 0,
            layer = DifficultyComponents.cardHits[index].packed,
        )
    }

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(childName: String): Int = DifficultyComponents.all.getValue(childName).packed

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
