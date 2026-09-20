package org.rsmod.content.pvmsupport.configs

import org.rsmod.api.type.builders.inv.InvBuilder
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.game.type.inv.InvType

typealias support_shop_invs = SupportScrollShopInvs

object SupportScrollShopInvs : InvReferences() {
    val spell_scrolls: InvType = find("unforge_spell_scrolls")
}

/**
 * The PvM spell scroll shop: one scroll per support spell, restocking forever.
 *
 * Opened with `::spellshop`; a shopkeeper npc can point at the same inv later (see
 * `ShopkeeperScript`'s `npc_shop` params) without touching this definition.
 */
internal object SupportScrollShopInvBuilder : InvBuilder() {
    init {
        build("unforge_spell_scrolls") {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            for ((_, scroll) in SupportScrolls.all) {
                stock += stock(scroll, count = 25, restockCycles = 100)
            }
        }
    }
}
