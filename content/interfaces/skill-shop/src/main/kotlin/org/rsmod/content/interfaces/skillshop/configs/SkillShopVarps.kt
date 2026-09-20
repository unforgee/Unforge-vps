package org.rsmod.content.interfaces.skillshop.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

typealias skillshop_varps = SkillShopVarps

/**
 * The skill-point economy behind the Skill Quartermaster.
 *
 * [points] mirrors `perk_points` exactly: a persistent, server-only balance that is never
 * transmitted to the client (the shop header renders it with `ifSetText` instead). Points are
 * minted only by skilling actions and spent only here - never convertible to gp or perk points. The
 * varp itself is declared in `skilling-core` (`SkillingPointVarpBuilder`), next to the
 * `SkillingPoints` service that writes it, so skill scripts resolve it on their own classpath;
 * these refs find the same type by name.
 *
 * [recipeUnlocks] is a 32-bit bitmask of one-time recipe purchases, one bit per
 * [SkillShopEntry.unlockBit] - the same pattern as `unforge_spell_unlocks`. It is shop-owned state,
 * so its builder stays here.
 */
object SkillShopVarps : VarpReferences() {
    val points: VarpType = find("skill_points")
    val recipeUnlocks: VarpType = find("skill_recipe_unlocks")
}

internal object SkillShopVarpBuilder : VarpBuilder() {
    init {
        build("skill_recipe_unlocks") {
            permanent = true
            transmitNever = true
        }
    }
}
