package org.rsmod.content.skills.crafting.configs

import org.rsmod.content.skills.core.MakeIngredient
import org.rsmod.content.skills.core.MakeRecipe
import org.rsmod.game.type.obj.ObjType

/**
 * A gem to cut, with the level and xp of the cut itself.
 *
 * @param uncut what the chisel is used on; one is consumed per cut.
 */
data class GemCut(val uncut: ObjType, val cut: ObjType, val level: Int, val xp: Double)

/**
 * Cutting uncut gems with a chisel.
 *
 * Ordered by level, which is the order the menu lists them in.
 */
object GemCuts {
    val all: List<GemCut> =
        listOf(
            GemCut(crafting_objs.uncut_opal, crafting_objs.opal, 1, 15.0),
            GemCut(crafting_objs.uncut_jade, crafting_objs.jade, 13, 20.0),
            GemCut(crafting_objs.uncut_red_topaz, crafting_objs.red_topaz, 16, 25.0),
            GemCut(crafting_objs.uncut_sapphire, crafting_objs.sapphire, 20, 50.0),
            GemCut(crafting_objs.uncut_emerald, crafting_objs.emerald, 27, 67.5),
            GemCut(crafting_objs.uncut_ruby, crafting_objs.ruby, 34, 85.0),
            GemCut(crafting_objs.uncut_diamond, crafting_objs.diamond, 43, 107.5),
            GemCut(crafting_objs.uncut_dragonstone, crafting_objs.dragonstone, 55, 137.5),
            GemCut(crafting_objs.uncut_onyx, crafting_objs.onyx, 67, 167.5),
            GemCut(crafting_objs.uncut_zenyte, crafting_objs.zenyte, 89, 200.0),
        )

    /** Keyed by uncut obj id, which is how the chisel finds the gem it was used on. */
    val byUncut: Map<Int, GemCut>
        get() = all.associateBy { it.uncut.id }

    /** Keyed by cut obj id, which is how a menu selection finds its level requirement. */
    val byCut: Map<Int, GemCut>
        get() = all.associateBy { it.cut.id }
}

/**
 * Spinning: wool into a ball of wool, flax into a bow string.
 *
 * Both are one-ingredient recipes, so they reuse [MakeRecipe] directly rather than growing a table
 * of their own. Flax is the one worth noting: it needs level 10 Crafting, which is why spinning is
 * the Crafting method Fletching depends on.
 */
object SpinningRecipes {
    val all: List<MakeRecipe> =
        listOf(
            MakeRecipe(
                product = crafting_objs.ball_of_wool,
                label = "Ball of wool",
                level = 1,
                xp = 2.5,
                ingredients = listOf(MakeIngredient(crafting_objs.wool, 1)),
            ),
            MakeRecipe(
                product = crafting_objs.bow_string,
                label = "Bow string",
                level = 10,
                xp = 15.0,
                ingredients = listOf(MakeIngredient(crafting_objs.flax, 1)),
            ),
        )

    val byProduct: Map<Int, MakeRecipe>
        get() = all.associateBy { it.product.id }

    /**
     * Keyed by the obj being spun, which is how an item used on a spinning wheel finds its recipe.
     * Every spinning recipe has exactly one ingredient, so the mapping is unambiguous.
     */
    val byIngredient: Map<Int, MakeRecipe>
        get() = all.associateBy { it.ingredients.first().obj.id }
}

/**
 * Needle-and-thread recipes.
 *
 * The needle is a tool and is never consumed, so it is not in any ingredient list - the script that
 * opens the menu is where the needle is checked. Thread **is** consumed, one per item, and every
 * studded recipe additionally eats a steel stud.
 *
 * Ordered by level.
 */
object LeatherRecipes {
    val all: List<MakeRecipe> =
        listOf(
            leather(crafting_objs.leather_gloves, "Leather gloves", 1, 13.8, 1),
            leather(crafting_objs.leather_boots, "Leather boots", 7, 16.25, 1),
            leather(crafting_objs.leather_cowl, "Leather cowl", 9, 18.5, 1),
            leather(crafting_objs.leather_vambraces, "Leather vambraces", 11, 22.0, 1),
            leather(crafting_objs.leather_armour, "Leather body", 14, 25.0, 1),
            leather(crafting_objs.leather_chaps, "Leather chaps", 18, 27.0, 1),
            MakeRecipe(
                product = crafting_objs.hardleather_body,
                label = "Hard leather body",
                level = 28,
                xp = 35.0,
                ingredients =
                    listOf(
                        MakeIngredient(crafting_objs.hard_leather, 1),
                        MakeIngredient(crafting_objs.thread, 1),
                    ),
            ),
            MakeRecipe(
                product = crafting_objs.studded_body,
                label = "Studded body",
                level = 41,
                xp = 40.0,
                ingredients =
                    listOf(
                        MakeIngredient(crafting_objs.leather_armour, 1),
                        MakeIngredient(crafting_objs.studs, 1),
                        MakeIngredient(crafting_objs.thread, 1),
                    ),
            ),
            MakeRecipe(
                product = crafting_objs.studded_chaps,
                label = "Studded chaps",
                level = 44,
                xp = 47.0,
                ingredients =
                    listOf(
                        MakeIngredient(crafting_objs.leather_chaps, 1),
                        MakeIngredient(crafting_objs.studs, 1),
                        MakeIngredient(crafting_objs.thread, 1),
                    ),
            ),
        )

    /** Keyed by the obj the needle can be used on, which is what opens the menu. */
    val openers: Map<Int, ObjType>
        get() =
            mapOf(
                crafting_objs.leather.id to crafting_objs.leather,
                crafting_objs.hard_leather.id to crafting_objs.hard_leather,
            )

    private fun leather(
        product: ObjType,
        label: String,
        level: Int,
        xp: Double,
        count: Int,
    ): MakeRecipe =
        MakeRecipe(
            product = product,
            label = label,
            level = level,
            xp = xp,
            ingredients =
                listOf(
                    MakeIngredient(crafting_objs.leather, count),
                    MakeIngredient(crafting_objs.thread, 1),
                ),
        )
}

/** Ticks per cut gem, matching the length of `human_dragonstonecutting`. */
const val GEM_CUT_CYCLES: Int = 2

/** Ticks per spun item. */
const val SPIN_CYCLES: Int = 3

/** Ticks per sewn item. */
const val SEW_CYCLES: Int = 3
