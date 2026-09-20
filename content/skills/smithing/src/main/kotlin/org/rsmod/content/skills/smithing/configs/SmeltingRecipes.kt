package org.rsmod.content.skills.smithing.configs

import org.rsmod.game.type.obj.ObjType

/** @param count how many of [obj] one smelt consumes. */
data class SmeltIngredient(val obj: ObjType, val count: Int)

/**
 * @param bar what the furnace produces.
 * @param label the row's text in the `skillmulti` dialog. Must not contain `|`.
 * @param level the Smithing level required.
 * @param xp awarded per bar.
 */
data class SmeltRecipe(
    val bar: ObjType,
    val label: String,
    val level: Int,
    val xp: Double,
    val ingredients: List<SmeltIngredient>,
)

/**
 * Bars are the one part of smithing the cache does **not** describe.
 *
 * Enums 844/845/846 are keyed by *anvil product* and none of the bar objs appear in them, so unlike
 * [SmithingProducts] this table cannot be generated - it is authored here and is the only smithing
 * data in this module that is not read back out of the cache at runtime.
 *
 * Ordered by level, which is the order the furnace dialog lists them in.
 */
object SmeltingRecipes {
    val all: List<SmeltRecipe> =
        listOf(
            SmeltRecipe(
                bar = smithing_objs.bronze_bar,
                label = "Bronze",
                level = 1,
                xp = 6.2,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.copper_ore, 1),
                        SmeltIngredient(smithing_objs.tin_ore, 1),
                    ),
            ),
            SmeltRecipe(
                bar = smithing_objs.blurite_bar,
                label = "Blurite",
                level = 8,
                xp = 8.0,
                ingredients = listOf(SmeltIngredient(smithing_objs.blurite_ore, 1)),
            ),
            SmeltRecipe(
                bar = smithing_objs.iron_bar,
                label = "Iron",
                level = 15,
                xp = 12.5,
                ingredients = listOf(SmeltIngredient(smithing_objs.iron_ore, 1)),
            ),
            SmeltRecipe(
                bar = smithing_objs.silver_bar,
                label = "Silver",
                level = 20,
                xp = 13.7,
                ingredients = listOf(SmeltIngredient(smithing_objs.silver_ore, 1)),
            ),
            SmeltRecipe(
                bar = smithing_objs.steel_bar,
                label = "Steel",
                level = 30,
                xp = 17.5,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.iron_ore, 1),
                        SmeltIngredient(smithing_objs.coal, 2),
                    ),
            ),
            SmeltRecipe(
                bar = smithing_objs.gold_bar,
                label = "Gold",
                level = 40,
                xp = 22.5,
                ingredients = listOf(SmeltIngredient(smithing_objs.gold_ore, 1)),
            ),
            SmeltRecipe(
                bar = smithing_objs.lovakite_bar,
                label = "Lovakite",
                level = 45,
                xp = 20.0,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.lovakite_ore, 1),
                        SmeltIngredient(smithing_objs.coal, 2),
                    ),
            ),
            SmeltRecipe(
                bar = smithing_objs.mithril_bar,
                label = "Mithril",
                level = 50,
                xp = 30.0,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.mithril_ore, 1),
                        SmeltIngredient(smithing_objs.coal, 4),
                    ),
            ),
            SmeltRecipe(
                bar = smithing_objs.adamantite_bar,
                label = "Adamantite",
                level = 70,
                xp = 37.5,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.adamantite_ore, 1),
                        SmeltIngredient(smithing_objs.coal, 6),
                    ),
            ),
            SmeltRecipe(
                bar = smithing_objs.runite_bar,
                label = "Runite",
                level = 85,
                xp = 50.0,
                ingredients =
                    listOf(
                        SmeltIngredient(smithing_objs.runite_ore, 1),
                        SmeltIngredient(smithing_objs.coal, 8),
                    ),
            ),
        )
}
