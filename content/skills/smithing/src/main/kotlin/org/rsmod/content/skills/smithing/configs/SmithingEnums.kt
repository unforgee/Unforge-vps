package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.type.refs.enums.EnumReferences
import org.rsmod.game.type.enums.EnumType
import org.rsmod.game.type.obj.ObjType

/**
 * The anvil's cost/yield/requirement tables, keyed by product obj - 188 entries each.
 *
 * Read at runtime rather than copied into Kotlin: these are the same enums `smithing_item` (cs 431)
 * reads to draw the grid, so the menu and the server cannot disagree about levels, bar costs or
 * output counts.
 *
 * [product_to_requirement] defaults to `Int.MAX_VALUE`, which is the client's way of saying a slot
 * is never craftable - a free validity check for products that do not exist at a given tier.
 */
object SmithingEnums : EnumReferences() {
    val product_to_quantity: EnumType<ObjType, Int> = find("smithing_product_to_quantity")
    val product_to_bars_required: EnumType<ObjType, Int> = find("smithing_product_to_bars_required")
    val product_to_requirement: EnumType<ObjType, Int> = find("smithing_product_to_requirement")
}
