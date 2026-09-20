package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/**
 * Every usable pickaxe, its animation, and how often it rolls.
 *
 * **The pickaxe controls the roll interval, not the chance.** That is the one place mining differs
 * from woodcutting, where the axe picks the success rate and the cadence is fixed. Mod Ash, quoted
 * on the wiki's Mining page: "Your level affects the chance of getting ore each time the game
 * rolls; your pickaxe affects how often that happens." So the low/high pair lives on the rock
 * ([MiningRocks]) and the tick interval lives here.
 *
 * Intervals are the wiki's "ticks between rolls" column: bronze 8, iron 7, steel 6, black 5,
 * mithril 5, adamant 4, rune 3, dragon 2.83, crystal 2.75. The two fractional ones are not
 * fractional intervals - dragon "provides a chance to mine an ore every three ticks, and there is
 * an additional 1/6 chance to reduce this to two ticks", and crystal is the same shape with 1/4.
 * That is what [MiningParams.fast_roll_chance] encodes, and 3 - 1/6 = 2.83, 3 - 1/4 = 2.75.
 *
 * `levelrequire` is left alone: the cache already carries the **Mining** requirement on every
 * pickaxe (steel 6, black 11, mithril 21, adamant 31, rune 41, dragon 61, crystal 71), not the
 * Attack one. Bronze and iron read 0 rather than 1, which is harmless - the check is `>=`.
 */
internal object MiningPicks : ObjEditor() {
    init {
        pickaxe(objs.bronze_pickaxe, mining_seqs.bronze, ticks = 8)
        pickaxe(mining_objs.iron_pickaxe, mining_seqs.iron, ticks = 7)
        pickaxe(mining_objs.steel_pickaxe, mining_seqs.steel, ticks = 6)
        pickaxe(mining_objs.black_pickaxe, mining_seqs.black, ticks = 5)
        pickaxe(mining_objs.mithril_pickaxe, mining_seqs.mithril, ticks = 5)
        pickaxe(mining_objs.adamant_pickaxe, mining_seqs.adamant, ticks = 4)
        pickaxe(mining_objs.rune_pickaxe, mining_seqs.rune, ticks = 3)
        pickaxe(mining_objs.gilded_pickaxe, mining_seqs.gilded, ticks = 3)
        pickaxe(objs.dragon_pickaxe, mining_seqs.dragon, ticks = 3, fastRoll = 6)
        pickaxe(objs.dragon_pickaxe_upgraded, mining_seqs.dragon_upgraded, ticks = 3, fastRoll = 6)
        pickaxe(
            objs.dragon_pickaxe_or_zalcano,
            mining_seqs.dragon_or_zalcano,
            ticks = 3,
            fastRoll = 6,
        )
        pickaxe(
            objs.dragon_pickaxe_or_trailblazer,
            mining_seqs.dragon_or_trailblazer,
            ticks = 3,
            fastRoll = 6,
        )
        pickaxe(objs.third_age_pickaxe, mining_seqs.third_age, ticks = 3, fastRoll = 6)
        pickaxe(objs.infernal_pickaxe, mining_seqs.infernal, ticks = 3, fastRoll = 6)
        pickaxe(objs.infernal_pickaxe_uncharged, mining_seqs.infernal, ticks = 3, fastRoll = 6)
        pickaxe(objs.infernal_pickaxe_or, mining_seqs.infernal_or, ticks = 3, fastRoll = 6)
        pickaxe(
            objs.infernal_pickaxe_or_uncharged,
            mining_seqs.infernal_or,
            ticks = 3,
            fastRoll = 6,
        )
        pickaxe(objs.crystal_pickaxe, mining_seqs.crystal, ticks = 3, fastRoll = 4)
    }

    private fun pickaxe(type: ObjType, anim: SeqType, ticks: Int, fastRoll: Int = 0) {
        edit(type) {
            contentGroup = mining_content.pickaxe
            param[params.skill_anim] = anim
            param[mining_params.roll_ticks] = ticks
            param[mining_params.fast_roll_chance] = fastRoll
        }
    }
}
