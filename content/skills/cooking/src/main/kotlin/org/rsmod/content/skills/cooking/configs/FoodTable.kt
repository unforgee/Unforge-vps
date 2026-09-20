package org.rsmod.content.skills.cooking.configs

/**
 * What eating an obj does.
 *
 * @param heal hitpoints restored per bite.
 * @param next the obj left behind by a multi-bite food (`null` when the bite finishes it), ex. a
 *   whole cake becomes a partial cake, while a half pizza becomes nothing.
 */
data class EdibleFood(val heal: Int, val next: Int? = null)

/**
 * The eating table: obj id -> hitpoints restored.
 *
 * Authored rather than cache-driven, for the same reason [CookingRecipes] is - nothing in the cache
 * describes how much health an obj restores. Values are the live OSRS ones. The ids are written out
 * with their names because [org.rsmod.content.skills.cooking.scripts.EatFoodScript] is driven by
 * the `food` content group, so an id that drifts away from its name would be silently wrong rather
 * than a startup error.
 *
 * Objs that are food but have no entry still eat - [DEFAULT_HEAL] applies - so that nothing is
 * uneatable. `EatFoodScript` logs such objs at startup; add them here instead of letting them fall
 * through.
 */
object FoodTable {
    const val DEFAULT_HEAL: Int = 1

    private const val CAKE: Int = 1891
    private const val CAKE_PARTIAL: Int = 1893
    private const val CAKE_SLICE: Int = 1895
    private const val CHOCOLATE_CAKE: Int = 1897
    private const val CHOCOLATE_CAKE_PARTIAL: Int = 1899
    private const val CHOCOLATE_SLICE: Int = 1901
    private const val MEAT_PIE: Int = 2327
    private const val MEAT_PIE_HALF: Int = 2331
    private const val REDBERRY_PIE: Int = 2325
    private const val REDBERRY_PIE_HALF: Int = 2333
    private const val APPLE_PIE: Int = 2323
    private const val APPLE_PIE_HALF: Int = 2335
    private const val PLAIN_PIZZA: Int = 2289
    private const val PLAIN_PIZZA_HALF: Int = 2291
    private const val MEAT_PIZZA: Int = 2293
    private const val MEAT_PIZZA_HALF: Int = 2295
    private const val ANCHOVIE_PIZZA: Int = 2297
    private const val ANCHOVIE_PIZZA_HALF: Int = 2299
    private const val PINEAPPLE_PIZZA: Int = 2301
    private const val PINEAPPLE_PIZZA_HALF: Int = 2303

    private val entries: Map<Int, EdibleFood> = buildMap {
        /* Cooked fish and meat - the output of this module. */
        put(315, EdibleFood(3)) // shrimp
        put(319, EdibleFood(1)) // anchovies
        put(325, EdibleFood(4)) // sardine
        put(329, EdibleFood(9)) // salmon
        put(333, EdibleFood(7)) // trout
        put(337, EdibleFood(6)) // giant_carp
        put(339, EdibleFood(7)) // cod
        put(347, EdibleFood(5)) // herring
        put(351, EdibleFood(8)) // pike
        put(355, EdibleFood(6)) // mackerel
        put(361, EdibleFood(10)) // tuna
        put(365, EdibleFood(13)) // bass
        put(373, EdibleFood(14)) // swordfish
        put(379, EdibleFood(12)) // lobster
        put(385, EdibleFood(20)) // shark
        put(2140, EdibleFood(3)) // cooked_chicken
        put(2142, EdibleFood(3)) // cooked_meat
        put(2149, EdibleFood(11)) // lava_eel
        put(3228, EdibleFood(5)) // cooked_rabbit
        put(3144, EdibleFood(18)) // tbwt_cooked_karambwan
        put(7946, EdibleFood(16)) // monkfish
        put(11936, EdibleFood(22)) // dark_crab

        /* Single-bite staples. */
        put(1885, EdibleFood(19)) // ugthanki_kebab
        put(1942, EdibleFood(1)) // potato
        put(1963, EdibleFood(2)) // banana
        put(1965, EdibleFood(1)) // cabbage
        put(1969, EdibleFood(2)) // spinach_roll
        put(1973, EdibleFood(3)) // chocolate_bar
        put(1982, EdibleFood(2)) // tomato
        put(1985, EdibleFood(2)) // cheese
        put(2003, EdibleFood(11)) // stew
        put(2011, EdibleFood(19)) // curry
        put(2108, EdibleFood(2)) // orange
        put(2126, EdibleFood(2)) // dwellberries
        put(2152, EdibleFood(3)) // toads_legs
        put(2185, EdibleFood(15)) // chocolate_bomb
        put(2187, EdibleFood(15)) // tangled_toads_legs
        put(2309, EdibleFood(5)) // bread

        /* Multi-bite food: each bite heals the same and hands back the next stage. */
        put(CAKE, EdibleFood(4, CAKE_PARTIAL))
        put(CAKE_PARTIAL, EdibleFood(4, CAKE_SLICE))
        put(CAKE_SLICE, EdibleFood(4))
        put(CHOCOLATE_CAKE, EdibleFood(5, CHOCOLATE_CAKE_PARTIAL))
        put(CHOCOLATE_CAKE_PARTIAL, EdibleFood(5, CHOCOLATE_SLICE))
        put(CHOCOLATE_SLICE, EdibleFood(5))
        put(MEAT_PIE, EdibleFood(6, MEAT_PIE_HALF))
        put(MEAT_PIE_HALF, EdibleFood(6))
        put(REDBERRY_PIE, EdibleFood(5, REDBERRY_PIE_HALF))
        put(REDBERRY_PIE_HALF, EdibleFood(5))
        put(APPLE_PIE, EdibleFood(7, APPLE_PIE_HALF))
        put(APPLE_PIE_HALF, EdibleFood(7))
        put(PLAIN_PIZZA, EdibleFood(7, PLAIN_PIZZA_HALF))
        put(PLAIN_PIZZA_HALF, EdibleFood(7))
        put(MEAT_PIZZA, EdibleFood(8, MEAT_PIZZA_HALF))
        put(MEAT_PIZZA_HALF, EdibleFood(8))
        put(ANCHOVIE_PIZZA, EdibleFood(9, ANCHOVIE_PIZZA_HALF))
        put(ANCHOVIE_PIZZA_HALF, EdibleFood(9))
        put(PINEAPPLE_PIZZA, EdibleFood(11, PINEAPPLE_PIZZA_HALF))
        put(PINEAPPLE_PIZZA_HALF, EdibleFood(11))
    }

    val ids: Set<Int> = entries.keys

    val all: Map<Int, EdibleFood> = entries

    operator fun get(obj: Int): EdibleFood? = entries[obj]
}
