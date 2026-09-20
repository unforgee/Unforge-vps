package org.rsmod.api.equipment.instance

/** Stable effect ids used by skilling cosmetic item instances and the client tooltip. */
public object SkillingCosmeticCatalog {
    public const val VERSION: Int = 1

    public val effects: Set<String> =
        setOf(
            "NOTED_CHANCE",
            "DIRECT_BANK_CHANCE",
            "DOUBLE_OUTPUT_CHANCE",
            "TRIPLE_OUTPUT_CHANCE",
            "EXTRA_OUTPUT_CHANCE",
            "COOK_SUCCESS_CHANCE",
            "SMITH_SAVE_CHANCE",
            "FLETCHING_DOUBLE_OUTPUT_CHANCE",
            "MINING_GEM_CHANCE",
            "WOODCUTTING_NEST_CHANCE",
            "FISHING_EXTRA_CATCH_CHANCE",
            "THIEVING_EXTRA_LOOT_CHANCE",
            "HERBLORE_SECONDARY_SAVE_CHANCE",
            "RUNECRAFTING_EXTRA_RUNE_CHANCE",
            "FIREMAKING_EXTRA_REWARD_CHANCE",
            "PRAYER_EXTRA_XP_CHANCE",
            "CRAFTING_DOUBLE_OUTPUT_CHANCE",
            "MAGIC_RUNE_SAVE_CHANCE",
            "COSMETIC_DROP_CHANCE",
            "RARE_COSMETIC_CHANCE",
        )

    public fun validate(affixes: List<SkillAffixRoll>) {
        require(affixes.map { it.slot }.distinct().size == affixes.size) {
            "Skilling affix slots must be unique."
        }
        require(affixes.all { it.effect in effects }) { "Unknown skilling cosmetic effect." }
        require(
            affixes.all { it.unit == ModifierUnit.BasisPoints || it.unit == ModifierUnit.Flat }
        ) {
            "Skilling cosmetic effects only support Flat or BasisPoints units."
        }
    }
}
