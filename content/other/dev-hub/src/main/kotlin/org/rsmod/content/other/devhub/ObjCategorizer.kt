package org.rsmod.content.other.devhub

import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.WeaponCategory
import org.rsmod.game.type.obj.Wearpos

/**
 * Assigns every obtainable obj in the cache to exactly one [DevHubCategory].
 *
 * Rule chain, first match wins: equipment by wearpos (weapons split by [WeaponCategory]), then
 * name/iop heuristics for consumables and skilling stock, then the [DevHubCategory.EverythingElse]
 * fallback - nothing is dropped, so the hub stays literally complete.
 *
 * Filtered out entirely: cert (noted) forms, bank placeholders, transformation templates, dummy
 * items, and unnamed ("null") objs - none of them are real, obtainable items.
 */
class ObjCategorizer(private val objTypes: ObjTypeList) {
    private val categories: Map<DevHubCategory, List<Int>> by lazy { build() }

    /** Every eligible obj id across all categories - the corpus the hub's search filters. */
    val allIds: List<Int> by lazy { categories.values.flatten() }

    operator fun get(category: DevHubCategory): List<Int> = categories.getValue(category)

    private fun build(): Map<DevHubCategory, List<Int>> {
        val assigned = DevHubCategory.entries.associateWith { mutableListOf<UnpackedObjType>() }
        for (type in objTypes.values) {
            if (type.isDevHubFiltered()) {
                continue
            }
            assigned.getValue(categorize(type)) += type
        }
        return assigned.mapValues { (_, objs) -> objs.sortedBy { it.lowercaseName }.map { it.id } }
    }

    private fun categorize(type: UnpackedObjType): DevHubCategory {
        val wearpos = Wearpos[type.wearpos1]
        if (wearpos != null) {
            return equipment(type, wearpos)
        }
        val name = type.lowercaseName
        val iops = type.iop.filterNotNull()
        return when {
            iops.any { it.equals("Drink", ignoreCase = true) } -> DevHubCategory.Potions
            iops.any { it.equals("Eat", ignoreCase = true) } -> DevHubCategory.FoodAndDrink
            name.endsWith(" rune") -> DevHubCategory.Runes
            name.startsWith("grimy ") || name in CLEAN_HERBS -> DevHubCategory.Herbs
            name.endsWith(" seed") || name.endsWith(" sapling") || name.endsWith(" seedling") ->
                DevHubCategory.Seeds
            name.endsWith(" ore") || name.endsWith(" bar") || name == "coal" ->
                DevHubCategory.OresAndBars
            name.endsWith(" logs") ||
                name == "logs" ||
                name.endsWith(" plank") ||
                name == "plank" -> DevHubCategory.LogsAndPlanks
            name.startsWith("raw ") -> DevHubCategory.FishAndMeat
            name.startsWith("uncut ") || name in GEMS -> DevHubCategory.Gems
            name in TOOLS -> DevHubCategory.Tools
            name.contains("teleport") -> DevHubCategory.TeleportItems
            name.endsWith("bones") ||
                name.endsWith(" hide") ||
                name.endsWith(" leather") ||
                name.endsWith("dragonhide") ||
                name.endsWith(" feather") ||
                name == "feather" ||
                name == "flax" ||
                name.endsWith(" wool") ||
                name == "wool" -> DevHubCategory.Materials
            else -> DevHubCategory.EverythingElse
        }
    }

    private fun equipment(type: UnpackedObjType, wearpos: Wearpos): DevHubCategory =
        when (wearpos) {
            Wearpos.Hat -> DevHubCategory.Head
            Wearpos.Back -> DevHubCategory.Cape
            Wearpos.Front -> DevHubCategory.Neck
            Wearpos.RightHand -> weapon(type)
            Wearpos.Torso -> DevHubCategory.Body
            Wearpos.LeftHand -> DevHubCategory.Shield
            Wearpos.Legs -> DevHubCategory.Legs
            Wearpos.Hands,
            Wearpos.Feet -> DevHubCategory.HandsAndFeet
            Wearpos.Ring -> DevHubCategory.Rings
            Wearpos.Quiver -> DevHubCategory.Ammo
            Wearpos.Arms,
            Wearpos.Head,
            Wearpos.Jaw -> DevHubCategory.EverythingElse
        }

    private fun weapon(type: UnpackedObjType): DevHubCategory =
        when (WeaponCategory.getOrUnarmed(type.weaponCategory)) {
            WeaponCategory.Bow,
            WeaponCategory.Crossbow,
            WeaponCategory.Chinchompas,
            WeaponCategory.Thrown,
            WeaponCategory.Gun -> DevHubCategory.RangedWeapons
            WeaponCategory.Staff,
            WeaponCategory.BladedStaff,
            WeaponCategory.PoweredStaff,
            WeaponCategory.Polestaff,
            WeaponCategory.Salamander -> DevHubCategory.MagicWeapons
            else -> DevHubCategory.MeleeWeapons
        }

    private companion object {
        private val CLEAN_HERBS =
            setOf(
                "guam leaf",
                "marrentill",
                "tarromin",
                "harralander",
                "ranarr weed",
                "toadflax",
                "irit leaf",
                "avantoe",
                "kwuarm",
                "huasca",
                "snapdragon",
                "cadantine",
                "lantadyme",
                "dwarf weed",
                "torstol",
            )

        private val GEMS =
            setOf(
                "opal",
                "jade",
                "red topaz",
                "sapphire",
                "emerald",
                "ruby",
                "diamond",
                "dragonstone",
                "onyx",
                "zenyte",
                "zenyte shard",
            )

        private val TOOLS =
            setOf(
                "hammer",
                "chisel",
                "tinderbox",
                "needle",
                "thread",
                "knife",
                "spade",
                "rake",
                "seed dibber",
                "gardening trowel",
                "secateurs",
                "glassblowing pipe",
                "pestle and mortar",
                "shears",
                "saw",
                "bucket",
                "fishing rod",
                "fly fishing rod",
                "barbarian rod",
                "harpoon",
                "lobster pot",
                "small fishing net",
                "big fishing net",
            )
    }
}

/**
 * The hub-wide eligibility filter: cert (noted) forms, bank placeholders, transformation templates,
 * dummy items, and unnamed ("null") objs are not real, obtainable items and never appear in any hub
 * view - categorized, curated, or searched.
 */
internal fun UnpackedObjType.isDevHubFiltered(): Boolean =
    isCert ||
        isPlaceholder ||
        isTransformation ||
        isDummyItem ||
        name.isBlank() ||
        name.equals("null", ignoreCase = true)
