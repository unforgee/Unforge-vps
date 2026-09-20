package org.rsmod.content.other.commands.ui

import org.rsmod.api.type.builders.interf.InterfaceBuilder

/**
 * The companion Equipment & Stats window (`companion_equipment`), authored from scratch and packed
 * into the cache. A dedicated interactive view that replaces reusing the *player* equipment-stats
 * screen for companions.
 *
 * Layout (512x334 centered modal, same chrome as `companion_talents`): header with dynamic title,
 * class/HP subtitle, close X and a crimson accent rule. The left panel has the owner's inventory
 * grid at the top so companion gear can be equipped without leaving this page, followed by an
 * 11-cell worn-slot grid. Each worn cell shows the equipped item's obj model (`IfSetObject`) plus
 * its name, or a dimmed slot name when empty. Slots carry `Details`/`Unequip`/`Forge` ops. The
 * right panel lists offence, defence, other and vitals stat lines plus a flattened affix list, all
 * pushed via `IfSetText`.
 *
 * Clickable surfaces use the wrap + holder pattern: alone inside `${name}_wrap` the holder's
 * childIndex is 0, so `children[0]` (`_box`) resolves and the If3Button packet is emitted.
 */
internal object CompanionEquipmentInterfaceBuilder : InterfaceBuilder() {
    private const val TYPE_LAYER: Int = 0
    private const val TYPE_RECT: Int = 3
    private const val TYPE_TEXT: Int = 4
    private const val TYPE_GRAPHIC: Int = 5
    private const val TYPE_MODEL: Int = 6

    private const val EVENTS_OP1: Int = 2
    private const val EVENTS_OP1_OP2: Int = 6
    private const val EVENTS_OP1_OP2_OP3: Int = 14
    private const val MODE_CENTER: Int = 1
    private const val MODE_FAR: Int = 2
    private const val SIZE_FILL: Int = 1

    private const val FONT_B12: Int = 495

    private const val BG: Int = 0x14110D
    private const val BORDER: Int = 0x0E0E0C
    private const val PANEL: Int = 0x1B1712
    private const val FRAME: Int = 0x3A3226
    private const val PANEL_ALT: Int = 0x2A241C
    private const val ACCENT: Int = 0xC8283C
    private const val GOLD: Int = 0xFFB84D
    private const val ORANGE: Int = 0xFF981F
    private const val WHITE: Int = 0xFFFFFF
    private const val DIM: Int = 0x9A8B76
    private const val FAINT: Int = 0x6B6154
    private const val GREEN: Int = 0x33B532

    private const val SPRITE_CLOSE: Int = 539
    private const val SPRITE_CLOSE_HOVER: Int = 540

    private const val CS_SWAP_GRAPHIC: Int = 44
    private const val CS_RECOLOUR_TEXT: Int = 45
    private const val SELF: Int = -2147483645

    const val WIDTH: Int = 512
    const val HEIGHT: Int = 334

    /**
     * Eleven visible wearpos cells: hat/cape/neck/weapon/chest/shield/legs/hands/feet/ring/quiver.
     */
    const val SLOT_COUNT: Int = 11

    const val AFFIX_ROWS: Int = 5

    private const val SLOT_W: Int = 112
    private const val SLOT_H: Int = 24
    private const val SLOT_COL_A: Int = 14
    private const val SLOT_COL_B: Int = 134
    private const val SLOT_Y0: Int = 173
    private const val SLOT_PITCH: Int = 24
    const val INVENTORY_COLUMNS: Int = 7
    const val INVENTORY_ROWS: Int = 4
    const val INVENTORY_VISIBLE_HEIGHT: Int = 96
    const val INVENTORY_CONTENT_HEIGHT: Int = INVENTORY_ROWS * 32
    private const val RIGHT_X: Int = 258
    private const val RIGHT_W: Int = 240

    init {
        build("companion_equipment:root") {
            type = TYPE_LAYER
            width = WIDTH
            height = HEIGHT
            xMode = MODE_CENTER
            yMode = MODE_CENTER
            noClickThrough = true
        }
        build("companion_equipment:bg") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = BG
        }
        build("companion_equipment:border") {
            type = TYPE_RECT
            layer = rootRef()
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = BORDER
        }
        text(
            "companion_equipment:title",
            14,
            9,
            280,
            15,
            "Equipment & Stats",
            ORANGE,
            alignH = 0,
            layer = rootRef(),
        )
        text(
            "companion_equipment:subtitle",
            14,
            26,
            360,
            12,
            "",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
        build("companion_equipment:close_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 8
            y = 8
            width = 26
            height = 23
            xMode = MODE_FAR
        }
        build("companion_equipment:close") {
            type = TYPE_LAYER
            layer = CompanionEquipmentComponents.closeWrap.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1
            op = arrayOf("Close")
        }
        build("companion_equipment:close_g") {
            type = TYPE_GRAPHIC
            layer = CompanionEquipmentComponents.close.packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            graphic = SPRITE_CLOSE
            events = EVENTS_OP1
            onMouseOver = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE_HOVER)
            onMouseLeave = arrayOf(CS_SWAP_GRAPHIC, SELF, SPRITE_CLOSE)
        }
        build("companion_equipment:accent_rule") {
            type = TYPE_RECT
            layer = rootRef()
            x = 0
            y = 44
            width = WIDTH
            height = 2
            fill = true
            colour1 = ACCENT
        }
        build("companion_equipment:divider") {
            type = TYPE_RECT
            layer = rootRef()
            x = 252
            y = 50
            width = 2
            height = 272
            fill = true
            colour1 = FRAME
        }
        panel("left_panel", 6, 48, 240, 278)
        panel("right_panel", 258, 46, 248, 282)

        // The companion head preview is intentionally omitted: this space is now used for direct
        // inventory-to-companion equipment actions.
        text(
            "companion_equipment:inventory_head",
            14,
            50,
            230,
            11,
            "INVENTORY - CLICK TO EQUIP",
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
        build("companion_equipment:inventory") {
            type = TYPE_LAYER
            layer = rootRef()
            x = 10
            y = 62
            width = 232
            height = INVENTORY_VISIBLE_HEIGHT
            scrollHeight = INVENTORY_CONTENT_HEIGHT
        }
        text(
            "companion_equipment:worn_head",
            SLOT_COL_A,
            160,
            230,
            12,
            "WORN EQUIPMENT",
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )

        for (index in 0 until SLOT_COUNT) {
            val colX = if (index % 2 == 0) SLOT_COL_A else SLOT_COL_B
            val rowY = SLOT_Y0 + (index / 2) * SLOT_PITCH
            slot(index, colX, rowY)
        }

        // Right panel: stat groups.
        var y = 48
        sectionHeader("off_head", y, "OFFENCE")
        y += 12
        statLine("off_a", y)
        y += 12
        statLine("off_b", y)
        y += 16
        sectionHeader("def_head", y, "DEFENCE")
        y += 12
        statLine("def_a", y)
        y += 12
        statLine("def_b", y)
        y += 16
        sectionHeader("oth_head", y, "OTHER")
        y += 12
        statLine("oth_a", y)
        y += 12
        statLine("oth_b", y)
        y += 16
        sectionHeader("vit_head", y, "VITALS")
        y += 12
        statLine("vit_a", y)
        y += 12
        statLine("vit_b", y)
        y += 12
        statLine("vit_c", y)
        y += 16
        sectionHeader("aff_head", y, "AFFIXES")
        y += 12
        for (i in 0 until AFFIX_ROWS) {
            statLine("affix$i", y)
            y += 13
        }

        // Bottom band: status line. Equipment changes are handled directly by the inventory grid
        // and worn-slot buttons; there is no secondary equipment page anymore.
        text(
            "companion_equipment:status",
            RIGHT_X,
            306,
            166,
            22,
            "",
            FAINT,
            alignH = 0,
            layer = rootRef(),
        )
    }

    private fun rootRef() = CompanionEquipmentComponents.root.packed

    private fun sectionHeader(name: String, y: Int, label: String) {
        text(
            "companion_equipment:$name",
            RIGHT_X,
            y,
            RIGHT_W,
            11,
            label,
            GOLD,
            alignH = 0,
            layer = rootRef(),
        )
    }

    private fun statLine(name: String, y: Int) {
        text(
            "companion_equipment:$name",
            RIGHT_X + 4,
            y,
            RIGHT_W - 4,
            11,
            "-",
            DIM,
            alignH = 0,
            layer = rootRef(),
        )
    }

    /**
     * One worn-slot cell: `s{i}_wrap` positions on root, `s{i}_hit` fills it and carries the
     * `Details`/`Unequip` ops, `s{i}_box` (children[0]) resolves the click, `s{i}_obj` is the item
     * model (`IfSetObject`) and `s{i}_label` shows the item or dimmed slot name.
     */
    private fun slot(index: Int, cellX: Int, cellY: Int) {
        val p = "companion_equipment:s$index"
        build("${p}_wrap") {
            type = TYPE_LAYER
            layer = rootRef()
            x = cellX
            y = cellY
            width = SLOT_W
            height = SLOT_H
        }
        build("${p}_hit") {
            type = TYPE_LAYER
            layer = CompanionEquipmentComponents.slotWraps[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            events = EVENTS_OP1_OP2_OP3
            op = arrayOf("Unequip", "Details", "Forge")
            onMouseOver = arrayOf(CS_RECOLOUR_TEXT, packed("s${index}_label"), GOLD)
            onMouseLeave = arrayOf(CS_RECOLOUR_TEXT, packed("s${index}_label"), WHITE)
        }
        build("${p}_box") {
            type = TYPE_RECT
            layer = CompanionEquipmentComponents.slotHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = true
            colour1 = PANEL_ALT
            events = EVENTS_OP1_OP2_OP3
        }
        build("${p}_frame") {
            type = TYPE_RECT
            layer = CompanionEquipmentComponents.slotHits[index].packed
            widthMode = SIZE_FILL
            heightMode = SIZE_FILL
            fill = false
            colour1 = FRAME
        }
        build("${p}_obj") {
            type = TYPE_MODEL
            layer = CompanionEquipmentComponents.slotHits[index].packed
            x = 1
            y = 1
            width = SLOT_H - 2
            height = SLOT_H - 2
            model = 0
            modelAnim = 0
            modelZoom = 560
        }
        text(
            "${p}_label",
            SLOT_H + 2,
            0,
            SLOT_W - SLOT_H - 4,
            SLOT_H,
            "-",
            WHITE,
            alignH = 0,
            alignV = 1,
            layer = CompanionEquipmentComponents.slotHits[index].packed,
        )
    }

    private fun panel(name: String, xPos: Int, yPos: Int, w: Int, h: Int) {
        build("companion_equipment:$name") {
            type = TYPE_RECT
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
            fill = true
            colour1 = PANEL
        }
        build("companion_equipment:${name}_frame") {
            type = TYPE_RECT
            layer = rootRef()
            x = xPos
            y = yPos
            width = w
            height = h
            fill = false
            colour1 = FRAME
        }
    }

    private fun text(
        name: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        value: String,
        colour: Int,
        alignH: Int = 1,
        alignV: Int = 1,
        layer: Int,
    ) {
        build(name) {
            type = TYPE_TEXT
            this.layer = layer
            this.x = x
            this.y = y
            width = w
            height = h
            text = value
            textFont = FONT_B12
            textAlignH = alignH
            textAlignV = alignV
            colour1 = colour
            textShadow = true
        }
    }

    private fun packed(childName: String): Int =
        CompanionEquipmentComponents.all.getValue(childName).packed
}
