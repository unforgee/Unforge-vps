package org.rsmod.content.skills.crafting.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.loc.UnpackedLocType

typealias crafting_objs = CraftingObjs

typealias crafting_seqs = CraftingSeqs

object CraftingObjs : ObjReferences() {
    val chisel = find("chisel")
    val needle = find("needle")
    val thread = find("thread")
    val wool = find("wool")
    val ball_of_wool = find("ball_of_wool")
    val flax = find("flax")
    val bow_string = find("bow_string")
    val leather = find("leather")
    val hard_leather = find("hard_leather")
    val leather_gloves = find("leather_gloves")
    val leather_boots = find("leather_boots")
    val leather_cowl = find("leather_cowl")
    val leather_vambraces = find("leather_vambraces")
    val leather_armour = find("leather_armour")
    val leather_chaps = find("leather_chaps")
    val hardleather_body = find("hardleather_body")
    val studs = find("studs")
    val studded_body = find("studded_body")
    val studded_chaps = find("studded_chaps")
    val uncut_opal = find("uncut_opal")
    val uncut_jade = find("uncut_jade")
    val uncut_red_topaz = find("uncut_red_topaz")
    val uncut_sapphire = find("uncut_sapphire")
    val uncut_emerald = find("uncut_emerald")
    val uncut_ruby = find("uncut_ruby")
    val uncut_diamond = find("uncut_diamond")
    val uncut_dragonstone = find("uncut_dragonstone")
    val uncut_onyx = find("uncut_onyx")
    val uncut_zenyte = find("uncut_zenyte")
    val opal = find("opal")
    val jade = find("jade")
    val red_topaz = find("red_topaz")
    val sapphire = find("sapphire")
    val emerald = find("emerald")
    val ruby = find("ruby")
    val diamond = find("diamond")
    val dragonstone = find("dragonstone")
    val onyx = find("onyx")
    val zenyte = find("zenyte")
}

object CraftingSeqs : SeqReferences() {
    /** Shared by every gem: the cache names it after the dragonstone cut, the longest of them. */
    val cut_gem = find("human_dragonstonecutting")

    val spin = find("human_spinningwheel")

    /** Needle and thread: the same animation for gloves and for a studded body. */
    val sew = find("human_leather_crafting")
}

/**
 * Which locs are spinning wheels, decided by name.
 *
 * Content groups are cache-defined and this revision has no Crafting group, so the name is the
 * contract - the same approach Cooking takes for ranges. Every spinning wheel in the cache is named
 * `spinningwheel`, `spinningwheel_2` or `spinningwheel_quetzacali`.
 */
object CraftingLocs {
    fun isSpinningWheel(loc: UnpackedLocType): Boolean =
        loc.name.lowercase().startsWith("spinningwheel")
}
