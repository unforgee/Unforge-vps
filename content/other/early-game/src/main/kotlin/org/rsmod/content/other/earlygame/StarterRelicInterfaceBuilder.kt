package org.rsmod.content.other.earlygame

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The first-login Starter Relic selection window (`starter_relic`), authored from scratch and
 * packed into the cache.
 *
 * Layout (512x334 centered modal, same chrome as `unforge_gamemode`/`slayer_hub`): header with
 * title + subtitle and a crimson accent rule, three full-width relic cards (one per [StarterRelic]:
 * name + tagline, accent outline shown on the selected card via a `c{i}_sel` overlay), a detail
 * line for the selected relic, a status line for errors, and a wide Confirm button at the
 * bottom-right. Every clickable surface uses the wrap + holder pattern: alone inside `${name}_wrap`
 * the holder's childIndex is 0, so `children[0]` (`_box`) resolves and the If3Button packet is
 * emitted.
 */
internal object StarterRelicInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4

    private const val EVENTS_OP1: Int = 2
    private const val MODE_CENTER: Int = 1
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    // Palette shared with unforge_gamemode / slayer_hub.
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

    const val RELIC_COUNT: Int = 3

    private const val CARD_W: Int = 484
    private const val CARD_H: Int = 60
    private const val CARD_X: Int = 14
    private const val CARD_Y0: Int = 54
    private const val CARD_PITCH: Int = 68

    /** Short per-relic tagline shown under the name on each card. */
    private val TAGLINES =
        listOf(
            "Melee path - recommended Companion: Vanguard.",
            "Ranged path - recommended Companion: Hunter.",
            "Magic path - recommended Companion: Mystic.",
        )

    init {
        build("starter_relic:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("starter_relic:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("starter_relic:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "starter_relic:title",
            14,
            9,
            280,
            15,
            "Choose your Starter Relic",
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "starter_relic:subtitle",
            14,
            26,
            470,
            12,
            "Your relic shapes your early Adventure Path - pick a combat style.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("starter_relic:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }

        for (index in 0 until RELIC_COUNT) {
            card(index, CARD_X, CARD_Y0 + index * CARD_PITCH)
        }

        text(
            "starter_relic:detail",
            14,
            262,
            484,
            14,
            "Select a relic to see what it unlocks.",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        text("starter_relic:status", 14, 292, 320, 14, "", FAINT, alignH = 0, layer = rootRef())

        // Confirm button (wrap + holder): `children[0]` = `confirm_box` resolves the click.
        build("starter_relic:confirm_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 346
            y = 288
            width = 152
            height = 30
        }
        build("starter_relic:confirm") {
            type = TYPE_LAYER
            layer = StarterRelicComponents.confirmWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Confirm")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("confirm_text"), GOLD)
        }
        build("starter_relic:confirm_box") {
            type = TYPE_RECT
            layer = StarterRelicComponents.confirm.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = ACCENT
            events = EVENTS_OP1
        }
        text(
            "starter_relic:confirm_text",
            0,
            0,
            152,
            30,
            "Select a relic",
            GOLD,
            layer = StarterRelicComponents.confirm.packed,
        )
    }

    private fun rootRef() = StarterRelicComponents.root.packed

    /**
     * One relic card: `${c}_wrap` positions on root, `${c}_hit` fills it and carries the `Select`
     * op, `${c}_box` (children[0]) resolves the click, `${c}_sel` is the accent outline the script
     * unhides on the selected card, `${c}_name`/`${c}_tag` render the relic name and tagline.
     */
    private fun card(index: Int, cardX: Int, cardY: Int) {
        val p = "starter_relic:c$index"
        val relic = StarterRelic.entries[index]
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
            layer = StarterRelicComponents.cardWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), WHITE)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("c${index}_name"), GOLD)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = StarterRelicComponents.cardHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("${p}_sel") {
            type = TYPE_RECT
            layer = StarterRelicComponents.cardHits[index].packed
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
            460,
            14,
            relic.displayName,
            GOLD,
            alignH = 0,
            layer = StarterRelicComponents.cardHits[index].packed,
        )
        text(
            "${p}_tag",
            10,
            28,
            460,
            26,
            TAGLINES[index],
            DIM,
            alignH = 0,
            layer = StarterRelicComponents.cardHits[index].packed,
        )
    }

    /** Resolves a sibling component's packed id through the generated references object. */
    private fun packed(childName: String): Int =
        StarterRelicComponents.all.getValue(childName).packed

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
