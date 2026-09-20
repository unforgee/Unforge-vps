@file:Suppress("PropertyName")

package org.rsmod.api.shops.config

import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.aliases.ParamStr
import org.rsmod.api.type.builders.param.ParamBuilder
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.InterfaceType

public object ShopInterfaces : InterfaceReferences() {
    public val shop_main: InterfaceType = find("shopmain", 9223372036374986874)
    public val shop_side: InterfaceType = find("shopside", 9223372034793400280)
}

public object ShopComponents : ComponentReferences() {
    // shopmain:desktop_instructions (child 19) was removed in later shop UI revisions.
    // Instructions are baked into the interface / shopMainInit clientscript now.
    public val shop_side_inv: ComponentType = find("shopside:items", 5117171527864918016)
    public val shop_inv: ComponentType = find("shopmain:items", 7875443253800243706)
}

public object ShopParams : ParamReferences() {
    public val shop_sell_percentage: ParamInt = find("shop_sell_percentage")
    public val shop_buy_percentage: ParamInt = find("shop_buy_percentage")
    public val shop_change_percentage: ParamInt = find("shop_change_percentage")

    /**
     * Symbolic name of the shop inventory (`InvBuilder`/`InvEditor` name) this npc opens on its
     * "Trade" op. Setting it turns the npc type into a shopkeeper: every spawn of that npc opens
     * the same shop regardless of where it stands.
     */
    public val npc_shop: ParamStr = find("npc_shop")

    /** Optional display title for [npc_shop]; defaults to the npc's name when unset. */
    public val npc_shop_title: ParamStr = find("npc_shop_title")

    /**
     * Optional `currency.sym` name of the currency this npc's shop charges (e.g. `"pvm_points"`).
     * Defaults to standard gp when unset. The name is resolved to a [CurrencyType] at startup; an
     * unknown name fails the binding loudly instead of silently charging gp.
     */
    public val npc_shop_currency: ParamStr = find("npc_shop_currency")
}

public object ShopParamBuilder : ParamBuilder() {
    init {
        // Values are multiplied by 10 for "decimal precision".
        build<Int>("shop_sell_percentage") { default = 1300 }
        build<Int>("shop_buy_percentage") { default = 400 }
        build<Int>("shop_change_percentage") { default = 30 }
        build<String>("npc_shop")
        build<String>("npc_shop_title")
        build<String>("npc_shop_currency")
    }
}
