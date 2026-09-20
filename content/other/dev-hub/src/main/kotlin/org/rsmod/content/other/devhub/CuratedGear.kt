package org.rsmod.content.other.devhub

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.config.refs.params
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.WeaponCategory
import org.rsmod.game.type.obj.Wearpos

/**
 * Hand-authored end-game loadout lists behind the hub's Gear tab, topped up by a level-req sweep.
 *
 * Entries are authored by cache *name* rather than id and resolved once, lazily, against
 * [ObjTypeList] through a lowercase-name index. On duplicate names the lowest id wins - rev 239
 * carries leagues/deadman copies of e.g. "Shark", and the vanilla obj always precedes them. A name
 * that fails to resolve is logged and skipped rather than crashing the hub; [unresolvedNames] backs
 * the config test that keeps these lists typo-free.
 *
 * After the hand lists, each weapon category is topped up by a generated sweep of the cache: any
 * right-hand weapon requiring [SWEEP_LEVEL]+ Attack joins the melee list - unless it is a staff,
 * which joins magic - and [SWEEP_LEVEL]+ Ranged joins the ranged list. The sweep skips degraded
 * (trailing charge number) and poisoned variants, dedupes duplicate display names by lowest id, and
 * never re-adds a name the hand lists already place - so a pinned or deliberately-tabbed obj
 * (Soulreaper axe, Blue moon spear) cannot resurface as its wrong-id twin or in the wrong tab.
 *
 * Authored order is preserved (no sorting) - each list reads as a loadout: armour head to feet,
 * then jewellery, then weapons, then spares, with swept extras name-sorted at the end. Duplication
 * with the Equipment tab's categorized view is intended; this tab exists so a full PvM kit is one
 * screen instead of eleven categories.
 */
class CuratedGear(private val objTypes: ObjTypeList) {
    private val resolution: Resolution by lazy { resolve() }

    /** Curated names that failed to resolve against the cache - must stay empty (config test). */
    val unresolvedNames: List<String>
        get() = resolution.unresolved

    operator fun get(category: DevHubCategory): List<Int> =
        resolution.categories[category].orEmpty()

    private fun resolve(): Resolution {
        val byName = HashMap<String, Int>()
        val byInternal = HashMap<String, Int>()
        for (type in objTypes.values) {
            if (type.isDevHubFiltered()) {
                continue
            }
            val key = type.lowercaseName
            val existing = byName[key]
            if (existing == null || type.id < existing) {
                byName[key] = type.id
            }
            type.internalName?.let { byInternal.putIfAbsent(it, type.id) }
        }
        val unresolved = mutableListOf<String>()
        val categories =
            CURATED.mapValues { (_, names) ->
                names.mapNotNull { name ->
                    val id = PINNED[name]?.let(byInternal::get) ?: byName[name.lowercase()]
                    if (id == null) {
                        logger.warn { "Curated gear name does not resolve in this cache: $name" }
                        unresolved += name
                    }
                    id
                }
            }
        val swept = sweepWeapons()
        val merged =
            categories.mapValues { (category, handIds) ->
                val extra =
                    swept[category]
                        .orEmpty()
                        .groupBy { it.lowercaseName }
                        .map { (_, dupes) -> dupes.minBy { it.id } }
                        .sortedBy { it.lowercaseName }
                        .map { it.id }
                        .filter { it !in handIds }
                handIds + extra
            }
        return Resolution(merged, unresolved)
    }

    /** Collects every eligible [SWEEP_LEVEL]+ weapon per gear category - see the class KDoc. */
    private fun sweepWeapons(): Map<DevHubCategory, List<UnpackedObjType>> {
        val handNames = CURATED.values.flatten().map { it.lowercase() }.toHashSet()
        val swept = mutableMapOf<DevHubCategory, MutableList<UnpackedObjType>>()
        for (type in objTypes.values) {
            if (type.isDevHubFiltered() || type.lowercaseName in handNames) {
                continue
            }
            if (SWEPT_VARIANT.containsMatchIn(type.name)) {
                continue
            }
            if (Wearpos[type.wearpos1] != Wearpos.RightHand) {
                continue
            }
            val category =
                when {
                    type.reqLevel("ranged") >= SWEEP_LEVEL -> DevHubCategory.RangedGear
                    type.reqLevel("attack") >= SWEEP_LEVEL ->
                        if (type.isStaff()) DevHubCategory.MagicGear else DevHubCategory.MeleeGear
                    else -> null
                }
            if (category != null) {
                swept.getOrPut(category) { mutableListOf() } += type
            }
        }
        return swept
    }

    /** The level this obj requires in [stat] (a lowercase display name), or 0 if unrequired. */
    private fun UnpackedObjType.reqLevel(stat: String): Int {
        if (paramOrNull(params.statreq1_skill)?.displayName?.lowercase() == stat) {
            return paramOrNull(params.statreq1_level) ?: 0
        }
        if (paramOrNull(params.statreq2_skill)?.displayName?.lowercase() == stat) {
            return paramOrNull(params.statreq2_level) ?: 0
        }
        return 0
    }

    private fun UnpackedObjType.isStaff(): Boolean =
        when (WeaponCategory.getOrUnarmed(weaponCategory)) {
            WeaponCategory.Staff,
            WeaponCategory.BladedStaff,
            WeaponCategory.PoweredStaff,
            WeaponCategory.Polestaff -> true
            else -> false
        }

    private class Resolution(
        val categories: Map<DevHubCategory, List<Int>>,
        val unresolved: List<String>,
    )

    private companion object {
        private val logger = InlineLogger()

        /** Minimum Attack/Ranged requirement for a weapon to be swept into a gear list. */
        private const val SWEEP_LEVEL: Int = 70

        /**
         * Sweep exclusions: degraded barrows-style charge suffixes ("Ahrim's staff 100") and
         * poisoned/karambwan variants ("(p)", "(p+)", "(p++)", "(kp)").
         */
        private val SWEPT_VARIANT = Regex(""" \d+$|\((?:p|p\+|p\+\+|kp)\)$""")

        /**
         * Display names that are ambiguous in this cache, pinned to the obj's unique internal name
         * instead. Each of these has a lower-id obj sharing its display name, so lowest-id-wins
         * picked the wrong obj - the pinned internal names map to ids 28307/28310/28313/28316 (DT2
         * rings) and 28338 (Soulreaper axe), matched in-game against a working set in grove's save.
         */
        private val PINNED: Map<String, String> =
            mapOf(
                "Ultor ring" to "ultor_ring",
                "Venator ring" to "venator_ring",
                "Magus ring" to "magus_ring",
                "Bellator ring" to "bellator_ring",
                "Soulreaper axe" to "soulreaper",
            )

        private val CURATED: Map<DevHubCategory, List<String>> =
            mapOf(
                DevHubCategory.MeleeGear to
                    listOf(
                        "Torva full helm",
                        "Torva platebody",
                        "Torva platelegs",
                        "Neitiznot faceguard",
                        "Infernal cape",
                        "Amulet of torture",
                        "Ferocious gloves",
                        "Primordial boots",
                        "Avernic defender",
                        "Scythe of vitur",
                        "Osmumten's fang",
                        "Ghrazi rapier",
                        "Blade of saeldor (c)",
                        "Soulreaper axe",
                        "Dragon claws",
                        "Voidwaker",
                        "Dragon warhammer",
                        "Elder maul",
                        "Bandos godsword",
                        "Inquisitor's great helm",
                        "Inquisitor's hauberk",
                        "Inquisitor's plateskirt",
                        "Inquisitor's mace",
                        "Blood moon helm",
                        "Blood moon chestplate",
                        "Blood moon tassets",
                        "Dual macuahuitl",
                    ),
                DevHubCategory.RangedGear to
                    listOf(
                        "Masori mask (f)",
                        "Masori body (f)",
                        "Masori chaps (f)",
                        "Necklace of anguish",
                        "Ava's assembler",
                        "Zaryte vambraces",
                        "Pegasian boots",
                        "Twisted buckler",
                        "Twisted bow",
                        "Zaryte crossbow",
                        "Bow of faerdhinen (c)",
                        "Toxic blowpipe",
                        "Venator bow",
                        "Black chinchompa",
                        "Armadyl helmet",
                        "Armadyl chestplate",
                        "Armadyl chainskirt",
                        "Armadyl crossbow",
                        "Eclipse moon helm",
                        "Eclipse moon chestplate",
                        "Eclipse moon tassets",
                        "Eclipse atlatl",
                        "Atlatl dart",
                    ),
                DevHubCategory.MagicGear to
                    listOf(
                        "Ancestral hat",
                        "Ancestral robe top",
                        "Ancestral robe bottom",
                        "Occult necklace",
                        "Imbued saradomin cape",
                        "Tormented bracelet",
                        "Eternal boots",
                        "Elidinis' ward (f)",
                        "Book of the dead",
                        "Tumeken's shadow",
                        "Sanguinesti staff",
                        "Kodai wand",
                        "Harmonised nightmare staff",
                        "Saturated heart",
                        "Ancient staff",
                        "Ancient sceptre",
                        "Ahrim's hood",
                        "Ahrim's robetop",
                        "Ahrim's robeskirt",
                        "Ahrim's staff",
                        "Blue moon helm",
                        "Blue moon chestplate",
                        "Blue moon tassets",
                        "Blue moon spear",
                    ),
                DevHubCategory.SwitchesAndJewellery to
                    listOf(
                        "Lightbearer",
                        "Ultor ring",
                        "Magus ring",
                        "Venator ring",
                        "Bellator ring",
                        "Berserker ring (i)",
                        "Archers ring (i)",
                        "Seers ring (i)",
                        "Ring of suffering (i)",
                        "Amulet of blood fury",
                        "Salve amulet(ei)",
                        "Slayer helmet (i)",
                        "Dragon defender",
                        "Barrows gloves",
                    ),
                DevHubCategory.PvmPotions to
                    listOf(
                        "Super combat potion(4)",
                        "Divine super combat potion(4)",
                        "Ranging potion(4)",
                        "Divine ranging potion(4)",
                        "Divine bastion potion(4)",
                        "Saradomin brew(4)",
                        "Super restore(4)",
                        "Prayer potion(4)",
                        "Stamina potion(4)",
                        "Forgotten brew(4)",
                        "Anti-venom+(4)",
                        "Antidote++(4)",
                        "Extended super antifire(4)",
                        "Extended antifire(4)",
                    ),
                DevHubCategory.PvmFood to
                    listOf(
                        "Shark",
                        "Manta ray",
                        "Dark crab",
                        "Anglerfish",
                        "Cooked karambwan",
                        "Tuna potato",
                        "Purple sweets",
                    ),
                DevHubCategory.RunesAndAmmo to
                    listOf(
                        "Air rune",
                        "Water rune",
                        "Earth rune",
                        "Fire rune",
                        "Mind rune",
                        "Body rune",
                        "Chaos rune",
                        "Death rune",
                        "Blood rune",
                        "Soul rune",
                        "Nature rune",
                        "Law rune",
                        "Cosmic rune",
                        "Astral rune",
                        "Wrath rune",
                        "Dragon arrow",
                        "Amethyst broad bolts",
                        "Ruby dragon bolts (e)",
                        "Diamond dragon bolts (e)",
                        "Zulrah's scales",
                        // This cache's sailing-era tiers renamed vanilla "Cannonball" (id 2).
                        "Steel cannonball",
                    ),
            )
    }
}
