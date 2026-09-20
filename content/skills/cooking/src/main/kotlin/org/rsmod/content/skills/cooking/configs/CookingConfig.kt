package org.rsmod.content.skills.cooking.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType

typealias cooking_objs = CookingObjs

typealias cooking_seqs = CookingSeqs

/** The two cooking animations: a range and an open fire have different ones. */
object CookingSeqs : SeqReferences() {
    val range = find("human_cooking")
    val fire = find("human_firecooking")
}

/**
 * Every obj the cooking table names.
 *
 * Read from the cache's own symbol names rather than authored as ids, so a mismatch between this
 * module and the cache is a startup error rather than a silently dead recipe. Note that the cache
 * calls several burnt fish `burntfish1..5`, spells shrimps `shrimp`, and calls a marrentill a
 * `marentill` - which is exactly why this list is worth checking against the table below.
 */
object CookingObjs : ObjReferences() {
    val raw_beef = find("raw_beef")
    val cooked_meat = find("cooked_meat")
    val burnt_meat = find("burnt_meat")
    val raw_chicken = find("raw_chicken")
    val cooked_chicken = find("cooked_chicken")
    val burnt_chicken = find("burnt_chicken")
    val raw_shrimp = find("raw_shrimp")
    val shrimp = find("shrimp")
    val burnt_shrimp = find("burnt_shrimp")
    val raw_sardine = find("raw_sardine")
    val sardine = find("sardine")
    val burntfish5 = find("burntfish5")
    val raw_anchovies = find("raw_anchovies")
    val anchovies = find("anchovies")
    val burntfish1 = find("burntfish1")
    val raw_herring = find("raw_herring")
    val herring = find("herring")
    val burntfish3 = find("burntfish3")
    val raw_trout = find("raw_trout")
    val trout = find("trout")
    val burntfish2 = find("burntfish2")
    val raw_pike = find("raw_pike")
    val pike = find("pike")
    val raw_salmon = find("raw_salmon")
    val salmon = find("salmon")
    val raw_tuna = find("raw_tuna")
    val tuna = find("tuna")
    val burntfish4 = find("burntfish4")
    val raw_lobster = find("raw_lobster")
    val lobster = find("lobster")
    val burnt_lobster = find("burnt_lobster")
    val raw_swordfish = find("raw_swordfish")
    val swordfish = find("swordfish")
    val burnt_swordfish = find("burnt_swordfish")
    val raw_monkfish = find("raw_monkfish")
    val monkfish = find("monkfish")
    val burnt_monkfish = find("burnt_monkfish")
    val raw_shark = find("raw_shark")
    val shark = find("shark")
    val burnt_shark = find("burnt_shark")
}

/**
 * Which locs can cook, decided by name.
 *
 * Content groups would be the tidier answer, but they are cache-defined and this cache's group list
 * has no cooking entry - only Smithing and Mining got groups in this revision. Matching the loc's
 * own name is what this module did before; it is extended here to the per-area range variants
 * (`range_lassar01_default01`) and the decorative ones (`range_icon`, `..._shadow`, `range_noop`)
 * are filtered out, so a picture of a range does not offer Cook.
 */
object CookingLocs {
    private val cookingNames = setOf("fire", "range", "fire_cook")

    private val skippedRangeParts = listOf("icon", "shadow", "noop", "graphic", "ruin")

    fun matches(loc: UnpackedLocType): Boolean {
        val name = loc.name.lowercase()
        if (name in cookingNames) {
            return true
        }
        if (!name.startsWith("range_")) {
            return false
        }
        return skippedRangeParts.none { name.contains(it) } && !name.endsWith("_noop")
    }

    /**
     * An open fire burns food more often than a range, so the two need telling apart: everything
     * that is not a campfire or the campfire cooking spot counts as a range.
     */
    fun isFire(loc: UnpackedLocType): Boolean {
        val name = loc.name.lowercase()
        return name == "fire" || name == "fire_cook"
    }
}

/**
 * @param raw what the player cooks. One is consumed per iteration.
 * @param stopBurn the level at which the food stops burning **on a fire**; a range is treated as
 *   [RANGE_BURN_OFFSET] levels more forgiving.
 * @param level the Cooking level required, which is what the burn curve measures from.
 */
data class Cookable(
    val raw: ObjType,
    val cooked: ObjType,
    val burnt: ObjType,
    val level: Int,
    val xp: Double,
    val stopBurn: Int,
)

/** A range is modelled as cooking [RANGE_BURN_OFFSET] levels above the food's fire stop-burn. */
const val RANGE_BURN_OFFSET: Int = 4

/** Ticks per cooked item. */
const val COOK_CYCLES: Int = 4
