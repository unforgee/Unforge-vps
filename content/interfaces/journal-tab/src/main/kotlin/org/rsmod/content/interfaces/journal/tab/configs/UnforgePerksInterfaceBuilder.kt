package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.player.perk.Perk
import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Server-authored "Perk" page for the side journal tab (interface 1002).
 *
 * Sized 190x300 to match the side-panel tab container. The header (title + perk points) is fixed to
 * the root; every perk row lives inside the scrollable `content` layer whose `scrollHeight` covers
 * all rows. Wheel scrolling needs a client-side `onScroll` hook that if3 components lack, so the
 * explicit ▲/▼ buttons step the position via `IfSetScrollPos` in [PerkJournalScript].
 *
 * Each perk row shows its name, current rank, effect summary, a ten-segment progression bar (five
 * ranks per pip, toggled at runtime with `ifSetHide`) and a compact plus button for upgrading it.
 */
public object UnforgePerksInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER = 0
    private const val TYPE_RECT = 3
    private const val TYPE_TEXT = 4
    private const val EVENTS_OP1 = 2
    private const val SIZE_FILL = 1

    private const val WIDTH = 190
    private const val HEIGHT = 300
    private const val FONT_B12 = 495
    private const val ORANGE = 0xFF981F
    private const val WHITE = 0xFFFFFF
    private const val GOLD = 0xFFB84D
    private const val SECTION = 0x7FB8FF
    private const val BORDER = 0x0E0E0C
    private const val BACKGROUND = 0x332E25
    private const val PANEL = 0x3A342A
    private const val DIVIDER = 0x5A5245
    private const val BAR_EMPTY = 0x1E1A14
    private const val BAR_FILL = 0x00AC00

    const val SEGMENTS_PER_PERK = 10

    /** First enum index belonging to the skilling section (drives the section header). */
    private val SKILLING_START = Perk.Scholar.ordinal

    private const val ROW_PITCH = 42
    private const val SECTION_PITCH = 16

    /**
     * Visible height of the scrollable content layer. The side panel (`toplevel_osrs_stretch`,
     * `side_container`) is 190x261 and `side_journal` spends the top 24px on tab icons, so the
     * `tab_container` hosting this overlay clips at 237px. The scroll-down button sits at the
     * bottom of the scrollbar column (y = 38 + viewHeight - 18) and must end above that clip: 38 +
     * 195 = 233 leaves a 4px margin. Anything below y=237 is unreachable.
     */
    const val CONTENT_VIEW_HEIGHT = 195

    /** Scroll step applied by the ▲/▼ buttons, in pixels (two perk rows). */
    const val SCROLL_STEP = ROW_PITCH * 2

    /** Total pixel height of the scrollable content, including the final hint row. */
    val CONTENT_HEIGHT: Int =
        SECTION_PITCH +
            SKILLING_START * ROW_PITCH +
            SECTION_PITCH +
            (Perk.entries.size - SKILLING_START) * ROW_PITCH +
            SECTION_PITCH +
            16

    /** Maximum valid scroll position for the content layer. */
    val MAX_SCROLL: Int = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)

    init {
        build("unforge_perks:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            noClickThrough = true
        }
        build("unforge_perks:bg") {
            type = TYPE_RECT
            layer = UnforgePerksComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BACKGROUND
        }
        build("unforge_perks:border") {
            type = TYPE_RECT
            layer = UnforgePerksComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text("unforge_perks:title", 4, 4, WIDTH - 8, 14, "Perks", ORANGE, layer = rootRef())
        text(
            "unforge_perks:points_label",
            4,
            20,
            116,
            12,
            "Points left:",
            WHITE,
            alignH = 0,
            layer = rootRef(),
        )
        text("unforge_perks:points", 122, 20, 60, 12, "0", GOLD, alignH = 2, layer = rootRef())
        build("unforge_perks:divider") {
            type = TYPE_RECT
            layer = rootRef()
            x = 4
            y = 34
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }

        // Scrollable content layer: all perk rows + section headers live here. The client clips
        // children to the view rect; the scrollbar track is drawn on the right edge and the
        // ▲/▼ buttons move the position server-side.
        build("unforge_perks:content") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 0
            y = 38
            width = WIDTH - 16
            height = CONTENT_VIEW_HEIGHT
            scrollHeight = CONTENT_HEIGHT
        }

        val contentRef = UnforgePerksComponents.content.packed
        var cursorY = 0
        for ((index, perk) in Perk.entries.withIndex()) {
            if (index == 0) {
                sectionHeader("unforge_perks:sec_combat", cursorY, "Combat")
                cursorY += SECTION_PITCH
            }
            if (index == SKILLING_START) {
                sectionHeader("unforge_perks:sec_skilling", cursorY, "Skilling")
                cursorY += SECTION_PITCH
            }
            perkRow(index, perk, cursorY, contentRef)
            cursorY += ROW_PITCH
        }
        text(
            "unforge_perks:hint",
            4,
            cursorY,
            WIDTH - 24,
            12,
            "Points come from boss kills.",
            WHITE,
            alignH = 0,
            layer = contentRef,
        )

        // Scrollbar visuals + step buttons in the right-hand column.
        build("unforge_perks:scrollbar") {
            type = TYPE_RECT
            layer = rootRef()
            x = WIDTH - 12
            y = 38
            width = 8
            height = CONTENT_VIEW_HEIGHT
            fill = true
            colour1 = PANEL
        }
        scrollButton("unforge_perks:scroll_up", 40, "^")
        scrollButton("unforge_perks:scroll_down", 38 + CONTENT_VIEW_HEIGHT - 18, "v")
    }

    private fun rootRef() = UnforgePerksComponents.root.packed

    private fun sectionHeader(name: String, y: Int, label: String) {
        text(
            name,
            4,
            y,
            WIDTH - 24,
            14,
            label,
            SECTION,
            alignH = 0,
            layer = UnforgePerksComponents.content.packed,
        )
    }

    private fun perkRow(index: Int, perk: Perk, rowY: Int, contentRef: Int) {
        text(
            "unforge_perks:perk${index}_name",
            4,
            rowY,
            108,
            13,
            perk.displayName,
            ORANGE,
            alignH = 0,
            layer = contentRef,
        )
        text(
            "unforge_perks:perk${index}_level",
            112,
            rowY,
            40,
            13,
            "0/${Perk.MAX_PERK_RANK}",
            WHITE,
            alignH = 2,
            layer = contentRef,
        )
        text(
            "unforge_perks:perk${index}_desc",
            4,
            rowY + 13,
            WIDTH - 24,
            12,
            perk.description,
            WHITE,
            alignH = 0,
            layer = contentRef,
        )

        build("unforge_perks:perk${index}_barbg") {
            type = TYPE_RECT
            layer = contentRef
            x = 4
            y = rowY + 27
            width = 111
            height = 9
            fill = true
            colour1 = BAR_EMPTY
        }
        for (seg in 0 until SEGMENTS_PER_PERK) {
            build("unforge_perks:perk${index}_seg$seg") {
                type = TYPE_RECT
                layer = contentRef
                x = 4 + seg * 11
                y = rowY + 27
                width = 10
                height = 9
                fill = true
                colour1 = BAR_FILL
                hide = true
            }
        }

        val train = UnforgePerksComponents.trainButtons[index].packed
        val trainWrap = UnforgePerksComponents.trainWraps[index].packed
        // The client reports `children[childIndex]` on click: nesting the button alone inside a
        // wrap keeps `childIndex` at 0 so the packet actually reaches the server.
        build("unforge_perks:perk${index}_train_wrap") {
            type = TYPE_LAYER
            layer = contentRef
            x = 154
            y = rowY + 24
            width = 18
            height = 15
        }
        build("unforge_perks:perk${index}_train") {
            type = TYPE_LAYER
            layer = trainWrap
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Upgrade")
        }
        build("unforge_perks:perk${index}_train_box") {
            type = TYPE_RECT
            layer = train
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("unforge_perks:perk${index}_train_text") {
            type = TYPE_TEXT
            layer = train
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = GOLD
            text = "+"
        }
    }

    private fun scrollButton(name: String, y: Int, label: String) {
        val up = name == "unforge_perks:scroll_up"
        val btn =
            if (up) UnforgePerksComponents.scrollUp.packed
            else UnforgePerksComponents.scrollDown.packed
        val wrap =
            if (up) UnforgePerksComponents.scrollUpWrap.packed
            else UnforgePerksComponents.scrollDownWrap.packed
        // The client reports `children[childIndex]` on click: nesting the button alone inside a
        // wrap keeps `childIndex` at 0 so the packet actually reaches the server.
        build("${name}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = WIDTH - 14
            this.y = y
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
            layer = btn
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = DIVIDER
            events = EVENTS_OP1
        }
        build("${name}_text") {
            type = TYPE_TEXT
            layer = btn
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 1
            textAlignV = 1
            textShadow = true
            colour1 = GOLD
            text = label
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
