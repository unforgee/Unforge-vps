package org.rsmod.content.skills.cooking.configs

/**
 * The cooking table: levels, xp, and the level each food stops burning at.
 *
 * Authored rather than cache-driven - nothing in the cache describes cooking xp - but pinned to the
 * objs [CookingObjs] names, so a recipe can never point at an obj that does not exist. Ordered by
 * level, which is the order the make menu lists them in.
 */
object CookingRecipes {
    val all: List<Cookable> =
        listOf(
            Cookable(
                cooking_objs.raw_beef,
                cooking_objs.cooked_meat,
                cooking_objs.burnt_meat,
                1,
                30.0,
                30,
            ),
            Cookable(
                cooking_objs.raw_chicken,
                cooking_objs.cooked_chicken,
                cooking_objs.burnt_chicken,
                1,
                30.0,
                30,
            ),
            Cookable(
                cooking_objs.raw_shrimp,
                cooking_objs.shrimp,
                cooking_objs.burnt_shrimp,
                1,
                30.0,
                34,
            ),
            Cookable(
                cooking_objs.raw_sardine,
                cooking_objs.sardine,
                cooking_objs.burntfish5,
                1,
                40.0,
                38,
            ),
            Cookable(
                cooking_objs.raw_anchovies,
                cooking_objs.anchovies,
                cooking_objs.burntfish1,
                1,
                30.0,
                34,
            ),
            Cookable(
                cooking_objs.raw_herring,
                cooking_objs.herring,
                cooking_objs.burntfish3,
                5,
                50.0,
                41,
            ),
            Cookable(
                cooking_objs.raw_trout,
                cooking_objs.trout,
                cooking_objs.burntfish2,
                15,
                70.0,
                50,
            ),
            Cookable(
                cooking_objs.raw_pike,
                cooking_objs.pike,
                cooking_objs.burntfish3,
                20,
                80.0,
                52,
            ),
            Cookable(
                cooking_objs.raw_salmon,
                cooking_objs.salmon,
                cooking_objs.burntfish2,
                25,
                90.0,
                58,
            ),
            Cookable(
                cooking_objs.raw_tuna,
                cooking_objs.tuna,
                cooking_objs.burntfish4,
                30,
                100.0,
                64,
            ),
            Cookable(
                cooking_objs.raw_lobster,
                cooking_objs.lobster,
                cooking_objs.burnt_lobster,
                40,
                120.0,
                74,
            ),
            Cookable(
                cooking_objs.raw_swordfish,
                cooking_objs.swordfish,
                cooking_objs.burnt_swordfish,
                50,
                140.0,
                86,
            ),
            Cookable(
                cooking_objs.raw_monkfish,
                cooking_objs.monkfish,
                cooking_objs.burnt_monkfish,
                62,
                150.0,
                92,
            ),
            Cookable(
                cooking_objs.raw_shark,
                cooking_objs.shark,
                cooking_objs.burnt_shark,
                80,
                210.0,
                99,
            ),
        )

    /** Keyed by raw obj id, which is how an item used on a range finds its recipe. */
    val byRaw: Map<Int, Cookable>
        get() = all.associateBy { it.raw.id }

    /** Keyed by cooked obj id, which is how a menu selection finds its burn curve. */
    val byCooked: Map<Int, Cookable>
        get() = all.associateBy { it.cooked.id }
}
