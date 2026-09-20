package org.rsmod.content.skills.core

import org.rsmod.api.player.protect.ProtectedAccess

/**
 * What of [all] the player can actually make right now.
 *
 * Level and materials are checked separately so the message names the real obstacle: too low a
 * level is worth saying out loud, while a missing ingredient is visible in the inventory and needs
 * no explanation. Returning `null` means the caller should not open a menu at all - an empty
 * `skillmulti` dialog reads as a bug rather than as a refusal.
 *
 * @param skillName the skill as the player knows it, for the level message (`"Fletching"`).
 * @param requirementText what the player was trying to do, for the same message (`"carve that"`).
 */
fun ProtectedAccess.selectRecipes(
    all: List<MakeRecipe>,
    level: Int,
    requirementText: String,
    skillName: String = "Crafting",
): List<MakeRecipe>? {
    if (all.isEmpty()) {
        return null
    }
    val allowed = all.filter { level >= it.level }
    if (allowed.isEmpty()) {
        mes("You need a $skillName level of ${all.minOf { it.level }} to $requirementText.")
        return null
    }
    val affordable =
        allowed.filter { recipe -> recipe.ingredients.all { invTotal(inv, it.obj) >= it.count } }
    if (affordable.isEmpty()) {
        mes("You don't have the materials for that.")
        return null
    }
    return affordable
}
