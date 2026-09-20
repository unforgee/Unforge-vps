package org.rsmod.content.interfaces.skillshop.configs

import org.rsmod.api.config.refs.stats
import org.rsmod.game.type.stat.StatType

/**
 * The Skill Quartermaster's stock - one enum entry per shop row, matching the `SkillShopEntry`
 * model sketched in `docs/pvm-skilling-architecture.md` (id, category, obj, point cost, optional
 * skill-level gate, optional unlock bit).
 *
 * This slice deliberately uses real, already-existing items as stand-ins for the PvM consumables
 * the full design calls for (the actual resist stews, campfire kits and upgrade kits land with
 * `PvMConsumable`/recipe work). What the slice proves is the *view*: price, description,
 * level-gated rows and one-time unlock rows that flip to `Owned`.
 *
 * Descriptions follow the spec's wording rule - effect first, acquisition path second - and stay
 * under ~70 visible characters so they fit one desc line at `FONT_B12`.
 */
enum class SkillShopEntry(
    val label: String,
    val description: String,
    val category: SkillShopCategory,
    val cost: Int,
    /** Item granted on purchase; `-1` for non-item purchases (unlocks). */
    val obj: Int,
    val count: Int = 1,
    /** Skill gate for the better tiers; `null` = always purchasable. */
    val requiredStat: StatType? = null,
    val requiredLevel: Int = 0,
    /** Bit in `skill_recipe_unlocks` this purchase sets; `-1` = repeatable item purchase. */
    val unlockBit: Int = -1,
) {
    Shark(
        "Shark x5",
        "PvM food: heals a chunk of hp. Caught via Fishing.",
        SkillShopCategory.Consumables,
        cost = 25,
        obj = 385,
        count = 5,
    ),
    PrayerPotion(
        "Prayer potion x2",
        "Restores prayer between kills.",
        SkillShopCategory.Consumables,
        cost = 30,
        obj = 2434,
        count = 2,
    ),
    RuneArrows(
        "Rune arrows x100",
        "Ammo for ranged builds. Fletchable for less.",
        SkillShopCategory.Consumables,
        cost = 40,
        obj = 892,
        count = 100,
    ),
    SuperRestore(
        "Super restore x2",
        "Restores drained stats and prayer.",
        SkillShopCategory.Consumables,
        cost = 45,
        obj = 3024,
        count = 2,
    ),
    CampfireKit(
        "Campfire kit",
        "Deployable campfire: fire-resist aura at the fire.",
        SkillShopCategory.Consumables,
        cost = 150,
        obj = 590, // tinderbox stand-in until the campfire deployable lands
    ),
    ArmourKit(
        "Melee armour kit <col=6b6154>- Kit</col>",
        "Upgrades melee armour one tier. Requires Smithing 40.",
        SkillShopCategory.KitsAndRecipes,
        cost = 250,
        obj = 1163, // rune full helm stand-in for the instance-roll product
        requiredStat = stats.smithing,
        requiredLevel = 40,
    ),
    FeastScroll(
        "Feast recipe scroll <col=6b6154>- Permanent</col>",
        "Teaches the group-feast recipe. Cooking 60 to use.",
        SkillShopCategory.KitsAndRecipes,
        cost = 500,
        obj = -1,
        requiredStat = stats.cooking,
        requiredLevel = 60,
        unlockBit = 0,
    ),
}

enum class SkillShopCategory(val label: String) {
    Consumables("Consumables"),
    KitsAndRecipes("Kits & Recipes"),
}
