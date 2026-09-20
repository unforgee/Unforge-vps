package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.type.builders.param.ParamBuilder
import org.rsmod.api.type.refs.content.ContentReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.api.type.refs.seq.SeqReferences

typealias mining_content = MiningContent

typealias rocks = RockLocs

typealias empty_rocks = EmptyRockLocs

typealias mining_objs = MiningObjs

typealias mining_seqs = MiningSeqs

typealias mining_params = MiningParams

object MiningContent : ContentReferences() {
    /** Every rock whose `Mine` op this module serves. */
    val rock = find("mining_rock")

    /** Every obj that can be used as a pickaxe. */
    val pickaxe = find("mining_pickaxe")
}

/**
 * The rock types this first cut serves.
 *
 * Read out of the cache rather than guessed: every one of these carries `Mine` on **op1** and
 * nothing else usable (op3 is the hidden Prospect slot), and every one ships with `params=null`, so
 * level, xp, ore and respawn are all authored in [RockLocs].
 *
 * Deliberately excluded: `tut2_copperrock` / `tut2_tinrock`, which Tutorial Island binds by type
 * and drives itself; gem, limestone, sandstone/granite, essence, motherlode, calcified, daeyalt,
 * salt, sulphur and barronite rocks, which all have their own mechanics.
 */
object RockLocs : LocReferences() {
    val clay_1 = find("clayrock1")
    val clay_2 = find("clayrock2")
    val soft_clay_1 = find("softclayrock1")
    val soft_clay_2 = find("softclayrock2")
    val prif_soft_clay = find("prif_mine_softclayrock1")
    val copper_1 = find("copperrock1")
    val copper_2 = find("copperrock2")
    val newbie_copper = find("newbiecopperrock")
    val tin_1 = find("tinrock1")
    val tin_2 = find("tinrock2")
    val newbie_tin = find("newbietinrock")
    val blurite_1 = find("blurite_rock_1")
    val blurite_2 = find("blurite_rock_2")
    val iron_1 = find("ironrock1")
    val iron_2 = find("ironrock2")
    val gim_iron = find("gim_ironrock")
    val prif_iron = find("prif_mine_ironrock1")
    val silver_1 = find("silverrock1")
    val silver_2 = find("silverrock2")
    val prif_silver = find("prif_mine_silverrock1")
    val coal_1 = find("coalrock1")
    val coal_2 = find("coalrock2")
    val prif_coal = find("prif_mine_coalrock1")
    val gold_1 = find("goldrock1")
    val gold_2 = find("goldrock2")
    val prif_gold = find("prif_mine_goldrock1")
    val lovakite_1 = find("lovakite_rock1")
    val lovakite_2 = find("lovakite_rock2")
    val mithril_1 = find("mithrilrock1")
    val mithril_2 = find("mithrilrock2")
    val prif_mithril = find("prif_mine_mithrilrock1")
    val adamantite_1 = find("adamantiterock1")
    val adamantite_2 = find("adamantiterock2")
    val prif_adamantite = find("prif_mine_adamantiterock1")
    val runite_1 = find("runiterock1")
    val runite_2 = find("runiterock2")
    val prif_runite = find("prif_mine_runiterock1")
    val amethyst_1 = find("amethystrock1")
    val amethyst_2 = find("amethystrock2")
}

/**
 * The depleted counterparts a rock turns into, paired by **model**, which is the only thing in the
 * cache that ties them together: every `<ore>rock1` renders model 1390 and `rocks1` renders 1390;
 * every `<ore>rock2` renders 1391 and so does `rocks2`. The Prifddinas rocks all render 37841,
 * which is also what `prif_mine_rocks1_empty` renders.
 */
object EmptyRockLocs : LocReferences() {
    val rocks_1 = find("rocks1")
    val rocks_2 = find("rocks2")
    val newbie_rocks = find("newbierocks1")
    val prif_rocks = find("prif_mine_rocks1_empty")
    val amethyst_empty = find("amethystrock_empty")
}

/** The pickaxes, ores and outfit pieces `BaseObjs` does not already name. */
object MiningObjs : ObjReferences() {
    val iron_pickaxe = find("iron_pickaxe")
    val steel_pickaxe = find("steel_pickaxe")
    val black_pickaxe = find("black_pickaxe")
    val mithril_pickaxe = find("mithril_pickaxe")
    val adamant_pickaxe = find("adamant_pickaxe")
    val rune_pickaxe = find("rune_pickaxe")
    val gilded_pickaxe = find("trail_gilded_pickaxe")

    val clay = find("clay")
    val soft_clay = find("softclay")
    val copper_ore = find("copper_ore")
    val tin_ore = find("tin_ore")
    val blurite_ore = find("blurite_ore")
    val iron_ore = find("iron_ore")
    val silver_ore = find("silver_ore")
    val coal = find("coal")
    val gold_ore = find("gold_ore")
    val lovakite_ore = find("lovakite_ore")
    val mithril_ore = find("mithril_ore")
    val adamantite_ore = find("adamantite_ore")
    val runite_ore = find("runite_ore")
    val amethyst = find("amethyst")

    val prospector_hat = find("motherlode_reward_hat")
    val prospector_top = find("motherlode_reward_top")
    val prospector_legs = find("motherlode_reward_legs")
    val prospector_boots = find("motherlode_reward_boots")
    val prospector_hat_gold = find("motherlode_reward_hat_gold")
    val prospector_top_gold = find("motherlode_reward_top_gold")
    val prospector_legs_gold = find("motherlode_reward_legs_gold")
    val prospector_boots_gold = find("motherlode_reward_boots_gold")
}

/**
 * The mining animation per pickaxe.
 *
 * Each pickaxe has three: the plain one, a `_wall` variant for wall-mounted rocks (veins), and a
 * `_noreachforward` variant. Rocks are free-standing locs, so the plain one is what this module
 * uses.
 */
object MiningSeqs : SeqReferences() {
    val bronze = find("human_mining_bronze_pickaxe")
    val iron = find("human_mining_iron_pickaxe")
    val steel = find("human_mining_steel_pickaxe")
    val black = find("human_mining_black_pickaxe")
    val mithril = find("human_mining_mithril_pickaxe")
    val adamant = find("human_mining_adamant_pickaxe")
    val rune = find("human_mining_rune_pickaxe")
    val gilded = find("human_mining_gilded_pickaxe")
    val dragon = find("human_mining_dragon_pickaxe")
    val dragon_upgraded = find("human_mining_dragon_pickaxe_pretty")
    val dragon_or_zalcano = find("human_mining_zalcano_pickaxe")
    val dragon_or_trailblazer = find("human_mining_trailblazer_pickaxe_no_infernal")
    val third_age = find("human_mining_3a_pickaxe")
    val infernal = find("human_mining_infernal_pickaxe")
    val infernal_or = find("human_mining_trailblazer_pickaxe")
    val crystal = find("human_mining_crystal_pickaxe")
}

object MiningParams : ParamReferences() {
    /** The rock's success rate at Mining level 1, as a numerator over 256. */
    val rate_low: ParamInt = find("mining_rate_low")

    /** The rock's success rate at Mining level 99, as a numerator over 256. */
    val rate_high: ParamInt = find("mining_rate_high")

    /** Ticks between rolls for this pickaxe. */
    val roll_ticks: ParamInt = find("mining_roll_ticks")

    /** `1` in this many rolls takes one tick less than [roll_ticks]. `0` means never. */
    val fast_roll_chance: ParamInt = find("mining_fast_roll_chance")
}

internal object MiningParamBuilder : ParamBuilder() {
    init {
        build<Int>("mining_rate_low")
        build<Int>("mining_rate_high")
        build<Int>("mining_roll_ticks")
        build<Int>("mining_fast_roll_chance") { default = 0 }
    }
}
