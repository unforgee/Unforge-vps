package org.rsmod.content.skills.smithing.configs

/**
 * What an anvil pays **per bar consumed**, keyed by bar obj.
 *
 * Authored rather than read out of the cache, for the same reason [SmeltingRecipes] is: nothing in
 * the cache describes smithing XP. The client never displays it, so enums 844/845/846 carry the
 * output count, the bar cost and the level requirement and stop there. These values are
 * **observed** from live OSRS, not derived.
 *
 * Every product on a tier's grid pays the same rate per bar, so a five-bar platebody is worth five
 * one-bar daggers and the call site multiplies by the bar cost from enum 845.
 *
 * Note that **lovakite is deliberately off the 12.5-per-step ladder** the other six sit on:
 * Shayzien armour pays a flat 60 a bar, raised from 10 when Kourend Favour was removed in
 * January 2024.
 *
 * Keyed by the same bar ids [SmithingProducts.barByTier] holds - `SmithingConfigTest` pins the two
 * key sets equal, so every tier the anvil can open has a rate and no rate is orphaned.
 */
object SmithingXp {
    val perBar: Map<Int, Double> =
        mapOf(
            2349 to 12.5, // Bronze bar
            2351 to 25.0, // Iron bar
            2353 to 37.5, // Steel bar
            2359 to 50.0, // Mithril bar
            2361 to 62.5, // Adamantite bar
            2363 to 75.0, // Runite bar
            13354 to 60.0, // Lovakite bar
        )
}
