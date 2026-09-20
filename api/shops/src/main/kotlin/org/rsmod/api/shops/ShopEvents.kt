package org.rsmod.api.shops

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.shop.Shop

public class ShopEvents {
    /**
     * Published inside [Shops.open] after [Player.openedShop] is assigned but before the shop
     * inventory transmit begins, so subscribers can mutate the stock before the client sees it.
     */
    public data class Open(val player: Player, val shop: Shop) : UnboundEvent
}
