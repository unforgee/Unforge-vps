package org.rsmod.content.interfaces.stats.configs

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Server-authored `::stats` panel (interface 1003).
 *
 * A single modal with four text columns, each filled by
 * [org.rsmod.content.interfaces.stats .UnforgeStatsScript] through `ifSetText`:
 * - `skills` - every skill's base level and xp, one per line.
 * - `bonuses` - combat level, maximum hitpoints and the summed equipment bonuses.
 * - `gear` - gear attack-speed bonus, the resulting attack rate, and each worn item.
 * - `perks` - perk points and every perk's current level, packed two per line.
 *
 * Lines inside a column are separated with `<br>`, which the client's font renderer treats as a
 * hard line break (see `AbstractFont.breakLines`); anything longer than the column width wraps on
 * its own.
 */
internal object UnforgeStatsInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER = 0
    private const val TYPE_RECT = 3
    private const val TYPE_TEXT = 4
    private const val SIZE_FILL = 1

    const val WIDTH: Int = 510
    const val HEIGHT: Int = 330

    private const val HEADER_Y = 20
    private const val FONT_B12 = 495

    /** Line pitch in pixels; the columns are sized for [HEIGHT] / [LINE_HEIGHT] rows. */
    const val LINE_HEIGHT: Int = 11

    /** Rows available per column, used by the script to budget its content. */
    const val MAX_ROWS: Int = (HEIGHT - HEADER_Y) / LINE_HEIGHT

    private const val ORANGE = 0xFF981F
    private const val WHITE = 0xFFFFFF
    private const val BORDER = 0x0E0E0C
    private const val BACKGROUND = 0x332E25
    private const val DIVIDER = 0x5A5245

    private const val COL_SKILLS_X = 4
    private const val COL_SKILLS_W = 126
    private const val COL_BONUSES_X = 132
    private const val COL_BONUSES_W = 124
    private const val COL_GEAR_X = 260
    private const val COL_GEAR_W = 132
    private const val COL_PERKS_X = 396
    private const val COL_PERKS_W = 110

    init {
        build("unforge_stats:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            noClickThrough = true
        }
        build("unforge_stats:bg") {
            type = TYPE_RECT
            layer = UnforgeStatsComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BACKGROUND
        }
        build("unforge_stats:border") {
            type = TYPE_RECT
            layer = UnforgeStatsComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text("unforge_stats:title", 4, 3, WIDTH - 8, 14, "Character Stats", ORANGE, alignH = 0)
        build("unforge_stats:divider") {
            type = TYPE_RECT
            layer = UnforgeStatsComponents.root.packed
            x = 4
            y = 16
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }

        column("unforge_stats:skills", COL_SKILLS_X, COL_SKILLS_W)
        column("unforge_stats:bonuses", COL_BONUSES_X, COL_BONUSES_W)
        column("unforge_stats:gear", COL_GEAR_X, COL_GEAR_W)
        column("unforge_stats:perks", COL_PERKS_X, COL_PERKS_W)
    }

    private fun column(name: String, x: Int, width: Int) {
        build(name) {
            type = TYPE_TEXT
            layer = UnforgeStatsComponents.root.packed
            this.x = x
            this.y = HEADER_Y
            this.width = width
            this.height = HEIGHT - HEADER_Y
            textFont = FONT_B12
            textAlignH = 0
            textAlignV = 0
            textShadow = true
            textLineHeight = LINE_HEIGHT
            colour1 = WHITE
            text = " "
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
    ) {
        build(name) {
            type = TYPE_TEXT
            layer = UnforgeStatsComponents.root.packed
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
