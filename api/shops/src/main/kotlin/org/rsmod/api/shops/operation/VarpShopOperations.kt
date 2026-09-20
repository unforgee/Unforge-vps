package org.rsmod.api.shops.operation

import kotlin.math.min
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.objExamine
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Shop operations for point-style currencies that live in a persistent player balance instead of
 * the inventory (PvM points, wilderness slayer points, ...).
 *
 * Concrete subclasses wire one currency each: [balanceOf]/[debit] read and mutate the balance, and
 * [priceOf] returns the fixed per-item price (the dynamic stock-scaling cost model used for coins
 * does not apply to point shops - their prices are tuned data, not alch-derived values).
 *
 * These shops are buy-only, matching the `canSellToStore: false` convention of their source data:
 * players cannot dump inventory items for points. The buy path is atomic with respect to the shop
 * and player inventories (`invTransaction`); the balance is debited only after the item transfer
 * has fully succeeded, so a failed purchase can never lose points.
 */
public abstract class VarpShopOperations(
    private val objTypes: ObjTypeList,
    private val restockProcess: ShopRestockProcess,
    private val marketPrices: MarketPrices,
) : StandardShopOperations {
    /** Display name used in messages, e.g. `"PvM points"`. */
    protected abstract val currencyName: String

    /** Current balance of this shop's currency for [player]. */
    protected abstract fun balanceOf(player: Player): Int

    /** Deducts [amount] from the player's balance; only called after a successful item transfer. */
    protected abstract fun debit(player: Player, amount: Int)

    /** Fixed price of [type] in this currency, or `null` when the obj is not for sale. */
    protected abstract fun priceOf(type: ObjType): Int?

    override fun examineShopValue(player: Player, shop: Shop, slot: Int) {
        val obj = shop.inv[slot] ?: return
        val objType = objTypes[obj]
        val price = priceOf(objType)
        if (price == null) {
            player.mes("${objType.name} is not for sale.")
            return
        }
        if (price == 1) {
            player.mes("${objType.name}: costs 1 ${singularCurrency()}.")
        } else {
            player.mes("${objType.name}: costs ${price.formatAmount} $currencyName.")
        }
    }

    override fun shopBuy(player: Player, sideInv: Inventory, shop: Shop, slot: Int, request: Int) {
        val shopInv = shop.inv
        val obj = shopInv[slot] ?: return
        val objType = objTypes[obj]

        val price = priceOf(objType)
        if (price == null) {
            player.mes("You can't buy this item here.")
            return
        }

        val initialPurchaseRequest =
            IronmanPolicy.shopBuyCap(
                player,
                currentStock = shopInv.count(obj, objType),
                initialStock = shopInv.initialStockCount(obj),
                request = request,
            )
        if (initialPurchaseRequest == 0) {
            if (
                shopInv.count(obj, objType) > 0 &&
                    IronmanPolicy.effectiveMode(player).let { it != null && it.isIronmanRestricted }
            ) {
                player.mes(IronmanPolicy.MSG_SHOP_OVERSTOCK)
            } else {
                player.mes("That item is currently out of stock.")
            }
            return
        }

        val spaceCappedRequest =
            if (objType.isStackable) {
                min(Int.MAX_VALUE - sideInv.count(objType), initialPurchaseRequest)
            } else {
                min(sideInv.freeSpace(), initialPurchaseRequest)
            }
        if (spaceCappedRequest <= 0) {
            player.mes(NOT_ENOUGH_INV_SPACE)
            return
        }

        val balance = balanceOf(player)
        val affordable = min(spaceCappedRequest, balance / price)
        if (affordable <= 0) {
            player.mes(notEnoughCurrencyMessage())
            return
        }

        val transaction =
            player.invTransaction(sideInv) {
                val inv = select(sideInv)
                val shopInv = select(shop.inv)
                delete {
                    this.from = shopInv
                    this.obj = obj.id
                    this.strictCount = affordable
                }
                insert {
                    this.into = inv
                    this.obj = obj.id
                    this.strictCount = affordable
                }
            }

        if (!transaction.success) {
            player.mes(NOT_ENOUGH_INV_SPACE)
            return
        }

        debit(player, price * affordable)
        restockProcess += shopInv

        if (affordable < initialPurchaseRequest) {
            if (balance < price * initialPurchaseRequest) {
                player.mes(notEnoughCurrencyMessage())
            } else {
                player.mes(NOT_ENOUGH_INV_SPACE)
            }
        }

        if (shopInv[slot] == null) {
            shopInv.resetDefaultStockItem(slot, objType)
        }
    }

    override fun examineInvValue(player: Player, sideInv: Inventory, shop: Shop, slot: Int) {
        if (sideInv[slot] == null) {
            return
        }
        player.mes(CANNOT_SELL)
    }

    override fun invSell(player: Player, sideInv: Inventory, shop: Shop, slot: Int, request: Int) {
        if (sideInv[slot] == null) {
            return
        }
        player.mes(CANNOT_SELL)
    }

    override fun examineDesc(player: Player, inv: Inventory, shop: Shop, slot: Int) {
        val obj = inv[slot] ?: return
        val type = objTypes[obj]
        val marketPrice = marketPrices[type] ?: 0
        player.objExamine(type, obj.count, marketPrice)
    }

    private fun singularCurrency(): String = currencyName.removeSuffix("s")

    private fun notEnoughCurrencyMessage(): String = "You don't have enough $currencyName."

    private fun Inventory.initialStockCount(obj: InvObj): Int {
        val startStock = type.stock ?: return 0
        for (i in startStock.indices) {
            val stock = startStock[i] ?: continue
            if (stock.obj == obj.id) {
                return stock.count
            }
        }
        return 0
    }

    private fun Inventory.resetDefaultStockItem(slot: Int, objType: ObjType) {
        val defaultStockIndices = type.stock?.indices ?: return
        if (slot in defaultStockIndices) {
            this[slot] = InvObj(objType, count = 0)
        }
    }

    public companion object {
        public const val NOT_ENOUGH_INV_SPACE: String = "You don't have enough inventory space."
        public const val CANNOT_SELL: String = "You cannot sell items to this shop."
    }
}
