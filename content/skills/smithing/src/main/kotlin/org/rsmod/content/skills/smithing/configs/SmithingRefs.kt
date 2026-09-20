package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.content.ContentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

typealias smithing_content = SmithingContent

typealias smithing_locs = SmithingLocs

typealias smithing_objs = SmithingObjs

typealias smithing_interfaces = SmithingInterfaces

typealias smithing_components = SmithingComponents

typealias smithing_queues = SmithingQueues

typealias smithing_seqs = SmithingSeqs

typealias smithing_varbits = SmithingVarBits

typealias smithing_varps = SmithingVarps

object SmithingContent : ContentReferences() {
    /** Locs whose `Smelt` op opens the bar list. */
    val furnace = find("smithing_furnace")

    /** Locs whose `Smith` op opens interface 312. */
    val anvil = find("smithing_anvil")

    /** Every bar the anvil grid knows about, so a bar can be used on an anvil directly. */
    val smithable_bar = find("smithing_bar")

    /** Every ore [SmeltingRecipes] consumes, so an ore can be used on a furnace directly. */
    val smeltable_ore = find("smithing_ore")
}

/**
 * Furnaces and anvils that are plain skilling locs.
 *
 * Deliberately excludes the ones that carry their own state or belong to content this server does
 * not have: the charcoal makers, Zalcano, Tekton's anvil, the unlit/broken variants, the Elemental
 * Workshop pair, the experimental anvil, and `tut2_furnace` (which Tutorial Island drives itself).
 *
 * Furnaces put `Smelt` on **op2**, except `lovakengj_furnace_large_01` which puts it on op1. Anvils
 * put `Smith` on op1. [org.rsmod.content.skills.smithing.scripts.FurnaceScript] binds both furnace
 * ops rather than trying to keep two content groups apart.
 */
object SmithingLocs : LocReferences() {
    val furnace = find("furnace")
    val furnace_upass = find("furnace_upass")
    val viking_furnace = find("viking_furnace")
    val dwarf_keldagrim_furnace = find("dwarf_keldagrim_furnace")
    val enakh_new_furnace_lit = find("enakh_new_furnace_lit")
    val fairy_furnace = find("fairy_furnace")
    val swan_furnace = find("swan_furnace")
    val burgh_furnace_fired = find("burgh_furnace_fired")
    val varrock_diary_furnace = find("varrock_diary_furnace")
    val ahoy_new_furnace = find("ahoy_new_furnace")
    val fai_falador_furnace = find("fai_falador_furnace")
    val wilderness_resource_furnace = find("wilderness_resource_furnace")
    val lovakengj_furnace_large = find("lovakengj_furnace_large_01")
    val lovakengj_furnace = find("lovakengj_furnace_01")
    val zqfurnace_lit = find("zqfurnace_lit")
    val lovaquest_tower_furnace_lit = find("lovaquest_tower_furnace_lit")
    val brimstone_furnace = find("brimstone_furnace")
    val prif_furnace = find("prif_furnace")
    val darkm_furnace = find("darkm_furnace")

    val anvil = find("anvil")
    val dorics_anvil = find("dorics_anvil")
    val viking_anvil = find("viking_anvil")
    val dwarf_keldagrim_anvil = find("dwarf_keldagrim_anvil")
    val dorgesh_blacksmith_anvil = find("dorgesh_blacksmith_anvil")
    val brut_anvil = find("brut_anvil")
    val lovakengj_anvil = find("lovakengj_anvil")
    val ds2_guild_blacksmith_anvil = find("ds2_guild_blacksmith_anvil")
    val ds2_ac_forge_anvil = find("ds2_ac_forge_anvil")
    val darkm_anvil = find("darkm_anvil")
    val lumbridge_anvil = find("lumbridge_anvil")
}

object SmithingObjs : ObjReferences() {
    val hammer = find("hammer")

    val copper_ore = find("copper_ore")
    val tin_ore = find("tin_ore")
    val iron_ore = find("iron_ore")
    val silver_ore = find("silver_ore")
    val gold_ore = find("gold_ore")
    val mithril_ore = find("mithril_ore")
    val adamantite_ore = find("adamantite_ore")
    val runite_ore = find("runite_ore")
    val blurite_ore = find("blurite_ore")
    val lovakite_ore = find("lovakite_ore")
    val coal = find("coal")

    val bronze_bar = find("bronze_bar")
    val blurite_bar = find("blurite_bar")
    val iron_bar = find("iron_bar")
    val silver_bar = find("silver_bar")
    val steel_bar = find("steel_bar")
    val gold_bar = find("gold_bar")
    val mithril_bar = find("mithril_bar")
    val adamantite_bar = find("adamantite_bar")
    val runite_bar = find("runite_bar")
    val lovakite_bar = find("lovakite_bar")
}

object SmithingQueues : QueueReferences() {
    /**
     * Drives the make loop, one iteration per firing.
     *
     * **Weak** on purpose: `Player.clearPendingAction` - which every op and move handler calls -
     * clears weak queues, so any click cancels the rest of the run. See [MakeJob].
     */
    val make = find("smithing_make")
}

object SmithingSeqs : SeqReferences() {
    /** The furnace smelting animation. Unrelated to Falador despite the name. */
    val smelt = find("human_fai_furnace")

    val smith = find("human_smithing")
}

object SmithingInterfaces : InterfaceReferences() {
    val smithing = find("smithing")
}

/**
 * Interface 312's buttons.
 *
 * Every one of these already carries Op1 in the cache (`events=0x2`, which the v1 mask reads as
 * `DeprecatedOp1`), so no `ifSetEvents` is needed - clicks arrive as soon as the interface is open.
 * That is the opposite of interface 270, where nothing is baked at all.
 */
object SmithingComponents : ComponentReferences() {
    val make_1 = find("smithing:make_1")
    val make_5 = find("smithing:make_5")
    val make_10 = find("smithing:make_10")
    val make_x = find("smithing:make_x")
    val make_all = find("smithing:make_all")
}

object SmithingVarBits : VarBitReferences() {
    /**
     * Which bar's grid interface 312 draws. `smithing_setup` (cs 430) switches on `enum 1253` keyed
     * by this, and `smithing_init` (cs 415) re-runs the whole setup whenever varp 210 changes - so
     * writing this varbit is the only thing needed to repopulate the grid.
     */
    val bar_type = find("smithing_bar_type")
}

object SmithingVarps : VarpReferences() {
    /**
     * The shared make-quantity, capped at 28.
     *
     * `skillmain_setquantity` (cs 2930) writes this **client-side** when a quantity button is
     * clicked, and the same buttons also report to the server because their Op1 is baked. The
     * server mirrors the click into the same varp so both ends agree, which is also what keeps the
     * button highlight correct after the server changes it (the `make_x` count dialog).
     */
    val make_quantity = find("makexcrafting")
}
