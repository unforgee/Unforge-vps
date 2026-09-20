package org.rsmod.content.interfaces.gameframe.config

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * Server-authored Account tab for owner-bound companions and pets.
 *
 * The interface is deliberately a fixed four-page shell because the companion domain currently
 * exposes four owned slots. Text and visibility are refreshed by the server when the overlay is
 * opened or when an action changes the selected agent.
 */
internal object CompanionPetInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER = 0
    private const val TYPE_RECT = 3
    private const val TYPE_TEXT = 4
    private const val EVENTS_OP1 = 2
    private const val SIZE_FILL = 1
    private const val MODE_FAR = 2

    private const val WIDTH = 190
    private const val HEIGHT = 360
    private const val FONT_B12 = 495
    private const val ORANGE = 0xFF981F
    private const val WHITE = 0xFFFFFF
    private const val BORDER = 0x0E0E0C
    private const val BACKGROUND = 0x332E25
    private const val PANEL = 0x3A342A
    private const val PANEL_SELECTED = 0x554C3D
    private const val DIVIDER = 0x5A5245

    // Scrollable detail body: the action row is pinned to the bottom of each agent page and the
    // full info stack (stats, gear, talents) lives in a scrollable content layer above it.
    private const val CONTENT_Y = 26
    private const val ACTION_ROW_Y = 226
    private const val CONTENT_VIEW_HEIGHT = ACTION_ROW_Y - CONTENT_Y
    private const val SCROLLBAR_X = WIDTH - 8 - 12
    private const val LINE_PITCH = 14
    private const val LINE_POOL = CompanionPetComponents.LINE_POOL
    const val CONTENT_HEIGHT = LINE_POOL * LINE_PITCH
    val MAX_SCROLL = (CONTENT_HEIGHT - CONTENT_VIEW_HEIGHT).coerceAtLeast(0)
    const val SCROLL_STEP = LINE_PITCH * 2

    init {
        build("companion_pet:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            noClickThrough = true
        }
        build("companion_pet:bg") {
            type = TYPE_RECT
            layer = CompanionPetComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BACKGROUND
        }
        build("companion_pet:border") {
            type = TYPE_RECT
            layer = CompanionPetComponents.root.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "companion_pet:title",
            CompanionPetComponents.root.packed,
            4,
            4,
            WIDTH - 8,
            20,
            "Companions & Pets",
            ORANGE,
        )
        text(
            "companion_pet:subtitle",
            CompanionPetComponents.root.packed,
            4,
            25,
            WIDTH - 8,
            16,
            "Owner-bound agent control",
            WHITE,
        )
        build("companion_pet:divider") {
            type = TYPE_RECT
            layer = CompanionPetComponents.root.packed
            x = 4
            y = 41
            width = WIDTH - 8
            height = 1
            fill = true
            colour1 = DIVIDER
        }

        build("companion_pet:list_page") {
            type = TYPE_LAYER
            layer = CompanionPetComponents.root.packed
            x = 4
            y = 44
            width = WIDTH - 8
            height = HEIGHT - 48
        }
        for (slot in 0 until 4) {
            button(
                base = "companion_pet:slot${slot + 1}",
                wrap = CompanionPetComponents.slot_wraps[slot].packed,
                button = CompanionPetComponents.slot_buttons[slot].packed,
                parent = CompanionPetComponents.list_page.packed,
                x = 0,
                y = slot * 28,
                width = WIDTH - 8,
                height = 24,
                label = "Slot ${slot + 1}",
            )
        }
        text(
            "companion_pet:list_hint",
            CompanionPetComponents.list_page.packed,
            0,
            122,
            WIDTH - 8,
            74,
            "Select an agent to open its\nmanagement page.\n\nPets share this runtime.",
            WHITE,
            alignH = 0,
        )

        for (slot in 0 until 4) {
            val page = CompanionPetComponents.detail_pages[slot].packed
            build("companion_pet:agent_page${slot + 1}") {
                type = TYPE_LAYER
                layer = CompanionPetComponents.root.packed
                x = 4
                y = 44
                width = WIDTH - 8
                height = HEIGHT - 48
                hide = true
            }
            button(
                "companion_pet:agent_back${slot + 1}",
                CompanionPetComponents.back_wraps[slot].packed,
                CompanionPetComponents.back_buttons[slot].packed,
                page,
                0,
                0,
                46,
                20,
                "Back",
            )
            text(
                "companion_pet:agent_title${slot + 1}",
                page,
                52,
                0,
                126,
                20,
                "Agent ${slot + 1}",
                ORANGE,
                alignH = 0,
            )
            build("companion_pet:agent_content${slot + 1}") {
                type = TYPE_LAYER
                layer = page
                x = 0
                y = CONTENT_Y
                width = SCROLLBAR_X
                height = CONTENT_VIEW_HEIGHT
                scrollHeight = CONTENT_HEIGHT
            }
            val contentRef = CompanionPetComponents.detail_contents[slot].packed
            for (i in 0 until LINE_POOL) {
                text(
                    "companion_pet:agent_line${slot + 1}_$i",
                    contentRef,
                    0,
                    i * LINE_PITCH,
                    SCROLLBAR_X - 2,
                    LINE_PITCH,
                    "",
                    WHITE,
                    alignH = 0,
                )
            }
            build("companion_pet:agent_scrollbar${slot + 1}") {
                type = TYPE_RECT
                layer = page
                x = SCROLLBAR_X
                y = CONTENT_Y
                width = 12
                height = CONTENT_VIEW_HEIGHT
                fill = true
                colour1 = PANEL
            }
            button(
                "companion_pet:agent_scrollup${slot + 1}",
                CompanionPetComponents.scrollup_wraps[slot].packed,
                CompanionPetComponents.scroll_up_buttons[slot].packed,
                page,
                SCROLLBAR_X,
                CONTENT_Y,
                12,
                18,
                "^",
            )
            button(
                "companion_pet:agent_scrolldown${slot + 1}",
                CompanionPetComponents.scrolldown_wraps[slot].packed,
                CompanionPetComponents.scroll_down_buttons[slot].packed,
                page,
                SCROLLBAR_X,
                ACTION_ROW_Y - 18,
                12,
                18,
                "v",
            )
            button(
                "companion_pet:agent_activate${slot + 1}",
                CompanionPetComponents.activate_wraps[slot].packed,
                CompanionPetComponents.activate_buttons[slot].packed,
                page,
                0,
                ACTION_ROW_Y,
                56,
                24,
                "Activate",
            )
            button(
                "companion_pet:agent_resummon${slot + 1}",
                CompanionPetComponents.resummon_wraps[slot].packed,
                CompanionPetComponents.resummon_buttons[slot].packed,
                page,
                62,
                ACTION_ROW_Y,
                56,
                24,
                "Summon",
            )
            button(
                "companion_pet:agent_talent${slot + 1}",
                CompanionPetComponents.talent_wraps[slot].packed,
                CompanionPetComponents.talent_buttons[slot].packed,
                page,
                124,
                ACTION_ROW_Y,
                54,
                24,
                "Talent",
            )
            listOf("Defensive", "Aggressive", "Passive").forEachIndexed { mode, label ->
                button(
                    "companion_pet:agent_mode${slot + 1}_$mode",
                    CompanionPetComponents.mode_wraps[slot][mode].packed,
                    CompanionPetComponents.mode_buttons[slot][mode].packed,
                    page,
                    mode * 62,
                    ACTION_ROW_Y + 28,
                    58,
                    22,
                    label,
                )
            }
            // Combat & Magic row: spellbook and autocast pickers for the same agent page.
            button(
                "companion_pet:agent_spellbook${slot + 1}",
                CompanionPetComponents.spellbook_wraps[slot].packed,
                CompanionPetComponents.spellbook_buttons[slot].packed,
                page,
                0,
                ACTION_ROW_Y + 56,
                90,
                22,
                "Spellbook",
            )
            button(
                "companion_pet:agent_autocast${slot + 1}",
                CompanionPetComponents.autocast_wraps[slot].packed,
                CompanionPetComponents.autocast_buttons[slot].packed,
                page,
                94,
                ACTION_ROW_Y + 56,
                88,
                22,
                "Autocast",
            )
        }
    }

    private fun button(
        base: String,
        wrap: Int,
        button: Int,
        parent: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
    ) {
        // The client reports `children[childIndex]` on click: nesting the button alone inside a
        // wrap keeps `childIndex` at 0 so the packet actually reaches the server. The resolved
        // child (`_box`) must carry the op flag for the click to be emitted at all.
        build("${base}_wrap") {
            type = TYPE_LAYER
            layer = parent
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }
        build(base) {
            type = TYPE_LAYER
            layer = wrap
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Select")
        }
        build("${base}_box") {
            type = TYPE_RECT
            layer = button
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL
            events = EVENTS_OP1
        }
        build("${base}_text") {
            type = TYPE_TEXT
            layer = button
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            textFont = FONT_B12
            textAlignH = 0
            textAlignV = 1
            textShadow = true
            colour1 = ORANGE
            text = label
        }
    }

    private fun text(
        name: String,
        parent: Int,
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
            layer = parent
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
