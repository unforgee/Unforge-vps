package org.rsmod.content.areas.tutorialisland.design

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.content.areas.tutorialisland.configs.design_columns
import org.rsmod.content.areas.tutorialisland.configs.design_tables
import org.rsmod.game.dbtable.DbTableResolver
import org.rsmod.game.dbtable.DbValueColumn
import org.rsmod.game.type.dbtable.DbTableType

/**
 * Ordered identkit lists for `player_design`, loaded from cache style DB tables.
 *
 * Appearance kit slot layout (matches [org.rsmod.game.entity.player.Appearance]): `0=hair, 1=jaw,
 * 2=torso, 3=arms, 4=hands, 5=legs, 6=feet`.
 */
@Singleton
class PlayerDesignKits @Inject constructor(private val dbTables: DbTableResolver) {
    private lateinit var hairA: IntArray
    private lateinit var hairB: IntArray
    private lateinit var jawA: IntArray
    private lateinit var jawB: IntArray
    private lateinit var torsoA: IntArray
    private lateinit var torsoB: IntArray
    private lateinit var armsA: IntArray
    private lateinit var armsB: IntArray
    private lateinit var handsA: IntArray
    private lateinit var handsB: IntArray
    private lateinit var legsA: IntArray
    private lateinit var legsB: IntArray
    private lateinit var feetA: IntArray
    private lateinit var feetB: IntArray

    fun ensureLoaded() {
        if (this::hairA.isInitialized) {
            return
        }
        hairA = loadKits(design_tables.hair_styles, design_columns.hair_kit_a)
        hairB = loadKits(design_tables.hair_styles, design_columns.hair_kit_b)
        jawA = loadKits(design_tables.facial_hair_styles, design_columns.facial_kit_a)
        jawB = loadKits(design_tables.facial_hair_styles, design_columns.facial_kit_b)
        torsoA = loadKits(design_tables.torso_styles, design_columns.torso_kit_a)
        torsoB = loadKits(design_tables.torso_styles, design_columns.torso_kit_b)
        armsA = loadKits(design_tables.sleeve_styles, design_columns.sleeve_kit_a)
        armsB = loadKits(design_tables.sleeve_styles, design_columns.sleeve_kit_b)
        handsA = loadKits(design_tables.hand_styles, design_columns.hand_kit_a)
        handsB = loadKits(design_tables.hand_styles, design_columns.hand_kit_b)
        legsA = loadKits(design_tables.legging_styles, design_columns.legging_kit_a)
        legsB = loadKits(design_tables.legging_styles, design_columns.legging_kit_b)
        feetA = loadKits(design_tables.shoe_styles, design_columns.shoe_kit_a)
        feetB = loadKits(design_tables.shoe_styles, design_columns.shoe_kit_b)
    }

    fun kitsFor(slot: DesignKitSlot, bodyType: Int): IntArray {
        ensureLoaded()
        val bodyA = bodyType == constants.bodytype_a
        return when (slot) {
            DesignKitSlot.Hair -> if (bodyA) hairA else hairB
            DesignKitSlot.Jaw -> if (bodyA) jawA else jawB
            DesignKitSlot.Torso -> if (bodyA) torsoA else torsoB
            DesignKitSlot.Arms -> if (bodyA) armsA else armsB
            DesignKitSlot.Hands -> if (bodyA) handsA else handsB
            DesignKitSlot.Legs -> if (bodyA) legsA else legsB
            DesignKitSlot.Feet -> if (bodyA) feetA else feetB
        }
    }

    fun defaultKits(bodyType: Int): IntArray {
        ensureLoaded()
        return IntArray(DesignKitSlot.entries.size) { i ->
            val kits = kitsFor(DesignKitSlot.entries[i], bodyType)
            kits.firstOrNull() ?: FALLBACK_DEFAULTS[bodyType.coerceIn(0, 1)][i]
        }
    }

    private fun loadKits(table: DbTableType, column: DbValueColumn<Int, Int>): IntArray {
        val seen = LinkedHashSet<Int>()
        for (row in dbTables[table]) {
            val kit = row.getOrNull(column) ?: continue
            if (kit >= 0) {
                seen += kit
            }
        }
        return seen.toIntArray()
    }

    private companion object {
        /** Live OSRS Tutorial Island male / female starting kits (rev 239 capture). */
        private val FALLBACK_DEFAULTS =
            arrayOf(intArrayOf(0, 10, 18, 26, 33, 36, 42), intArrayOf(45, 296, 56, 61, 67, 70, 79))
    }
}

enum class DesignKitSlot(val appearanceIndex: Int) {
    Hair(0),
    Jaw(1),
    Torso(2),
    Arms(3),
    Hands(4),
    Legs(5),
    Feet(6),
}

enum class DesignColourSlot(val appearanceIndex: Int, val maxInclusive: Int) {
    Hair(0, 29),
    Torso(1, 27),
    Legs(2, 27),
    Feet(3, 5),
    Skin(4, 7),
}
