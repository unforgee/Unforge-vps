package org.rsmod.content.pvmsupport.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Server-authored HUD panel for the PvM support book (interface 1004).
 *
 * The vanilla buff bar (`interfaces.buff_bar`) is a cache/CS2 interface with no server-side API for
 * adding entries, so the support cooldowns are rendered into a panel this server owns instead. It
 * is opened as a floater and refreshed from a soft timer while it is on screen, which keeps the
 * numbers truthful to the server's own cooldown state.
 */
internal object UnforgeSupportInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER = 0
    private const val TYPE_RECT = 3
    private const val TYPE_TEXT = 4
    private const val SIZE_FILL = 1

    const val WIDTH: Int = 260
    const val HEIGHT: Int = 380

    private const val HEADER_Y = 22
    private const val LINE_HEIGHT = 12
    private const val FONT_B12 = 495

    private const val ORANGE = 0xFF981F
    private const val WHITE = 0xFFFFFF
    private const val BORDER = 0x0E0E0C
    private const val BACKGROUND = 0x332E25
    private const val DIVIDER = 0x5A5245

    init {
        build("unforge_support:root") {
            type = TYPE_LAYER
            x = 10
            y = 30
            width = WIDTH
            height = HEIGHT
            noClickThrough = true
        }
        build("unforge_support:bg") {
            type = TYPE_RECT
            layer = UnforgeSupportComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BACKGROUND
        }
        build("unforge_support:border") {
            type = TYPE_RECT
            layer = UnforgeSupportComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        build("unforge_support:title") {
            type = TYPE_TEXT
            layer = UnforgeSupportComponents.root.packed
            x = 6
            y = 4
            width = WIDTH - 12
            height = 14
            textFont = FONT_B12
            textAlignH = 0
            textAlignV = 1
            textShadow = true
            colour1 = ORANGE
            text = "PvM Support Spellbook"
        }
        build("unforge_support:divider") {
            type = TYPE_RECT
            layer = UnforgeSupportComponents.root.packed
            x = 4
            y = 19
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }
        build("unforge_support:body") {
            type = TYPE_TEXT
            layer = UnforgeSupportComponents.root.packed
            x = 6
            y = HEADER_Y
            width = WIDTH - 12
            height = HEIGHT - HEADER_Y - 4
            textFont = FONT_B12
            textAlignH = 0
            textAlignV = 0
            textShadow = true
            textLineHeight = LINE_HEIGHT
            colour1 = WHITE
            text = " "
        }
    }
}
