package org.rsmod.api.shops.operation

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.Constants
import org.rsmod.api.config.refs.currencies
import org.rsmod.api.config.refs.objs
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.ShopScript
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.config.ShopComponents
import org.rsmod.api.shops.config.ShopInterfaces
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.script.dsl.InvPluginBuilder
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.currency.CurrencyType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

class VarpShopOperationsBuyTest {
    @Test
    fun GameTestState.`buy single obj deducts points`() =
        runInjectedGameTest(Deps::class, childModule = null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 500)
            registerTestOps(deps, points)
            val shop = openPointShop()
            shop.inv[0] = InvObj(objs.newcomer_map, 5)

            buyStock(shop, objs.newcomer_map, OP_BUY1)

            assertEquals(1, player.count(objs.newcomer_map))
            assertEquals(4, shop.inv.count(objs.newcomer_map))
            assertEquals(225, points[player])
            assertNoMessageSent()
        }

    @Test
    fun GameTestState.`buy fails when balance cannot cover a single item`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 100)
            registerTestOps(deps, points)
            val shop = openPointShop()
            shop.inv[0] = InvObj(objs.newcomer_map, 5)

            buyStock(shop, objs.newcomer_map, OP_BUY1)

            assertEquals(0, player.count(objs.newcomer_map))
            assertEquals(5, shop.inv.count(objs.newcomer_map))
            assertEquals(100, points[player])
            assertMessageSent("You don't have enough test points.")
        }

    @Test
    fun GameTestState.`bulk buy is capped by remaining balance`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            // Price is 275, so 300 covers exactly one purchase.
            val points = mutableMapOf(player to 300)
            registerTestOps(deps, points)
            val shop = openPointShop()
            shop.inv[0] = InvObj(objs.newcomer_map, 5)

            buyStock(shop, objs.newcomer_map, OP_BUY5)

            assertEquals(1, player.count(objs.newcomer_map))
            assertEquals(4, shop.inv.count(objs.newcomer_map))
            assertEquals(25, points[player])
            assertMessageSent("You don't have enough test points.")
        }

    @Test
    fun GameTestState.`buy fails on out of stock`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 500)
            registerTestOps(deps, points)
            val shop = openPointShop()
            shop.inv[0] = InvObj(objs.newcomer_map, 0)

            buyStock(shop, objs.newcomer_map, OP_BUY1)

            assertEquals(0, player.count(objs.newcomer_map))
            assertEquals(500, points[player])
            assertMessageSent("That item is currently out of stock.")
        }

    @Test
    fun GameTestState.`buy fails with no inventory space`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 10_000)
            registerTestOps(deps, points)
            val shop = openPointShop()
            shop.inv[0] = InvObj(objs.newcomer_map, 5)

            player.fillInv()

            buyStock(shop, objs.newcomer_map, OP_BUY1)

            assertEquals(0, player.count(objs.newcomer_map))
            assertEquals(5, shop.inv.count(objs.newcomer_map))
            assertEquals(10_000, points[player])
            assertMessageSent("You don't have enough inventory space.")
        }

    @Test
    fun GameTestState.`unpriced stock is not for sale`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 10_000)
            registerTestOps(deps, points)
            val shop = openPointShop()
            // sos_security_book is not in the test price table.
            shop.inv[0] = InvObj(objs.sos_security_book, 5)

            buyStock(shop, objs.sos_security_book, OP_BUY1)

            assertEquals(0, player.count(objs.sos_security_book))
            assertEquals(5, shop.inv.count(objs.sos_security_book))
            assertEquals(10_000, points[player])
            assertMessageSent("You can't buy this item here.")
        }

    @Test
    fun GameTestState.`sell to point shop is rejected without touching balance`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            val points = mutableMapOf(player to 100)
            registerTestOps(deps, points)
            openPointShop()

            player.inv[0] = InvObj(objs.newcomer_map, 3)

            player.ifButton(
                ShopComponents.shop_side_inv,
                comsub = 0,
                obj = objs.newcomer_map.id,
                op = OP_SELL1,
            )
            advance(ticks = 1)

            assertEquals(3, player.count(objs.newcomer_map))
            assertEquals(100, points[player])
            assertMessageSent("You cannot sell items to this shop.")
        }

    @Test
    fun GameTestState.`gp shop still charges coins after point currency registration`() =
        runInjectedGameTest(Deps::class, null, ShopScript::class) { deps ->
            registerTestOps(deps, mutableMapOf())
            val shop = openShop(currencies.standard_gp)

            player.inv[0] = InvObj(objs.coins, 5)
            shop.inv[0] = InvObj(objs.newcomer_map, 5)

            buyStock(shop, objs.newcomer_map, OP_BUY1)

            assertEquals(1, player.count(objs.newcomer_map))
            assertEquals(4, player.count(objs.coins))
        }

    private fun GameTestScope.registerTestOps(deps: Deps, points: MutableMap<Player, Int>) {
        val ops =
            object : VarpShopOperations(deps.objTypes, deps.restockProcess, deps.marketPrices) {
                override val currencyName: String = "test points"

                override fun balanceOf(player: Player): Int = points[player] ?: 0

                override fun debit(player: Player, amount: Int) {
                    points[player] = balanceOf(player) - amount
                }

                override fun priceOf(type: ObjType): Int? =
                    if (type.id == objs.newcomer_map.id) 275 else null
            }
        deps.operationMap.register(TEST_CURRENCY, ops)
    }

    private fun GameTestScope.buyStock(shop: Shop, obj: ObjType, op: IfButtonOp) {
        val slot = shop.inv.indexOfFirst { it?.id == obj.id }
        check(slot != -1) { "Obj not found in stock: obj=$obj, stock=${shop.inv}" }

        val invObj = shop.inv.getValue(slot)
        player.ifButton(ShopComponents.shop_inv, comsub = slot + 1, obj = invObj.id, op = op)
        advance(ticks = 1)
    }

    private fun GameTestScope.openPointShop(): Shop = openShop(TEST_CURRENCY)

    private fun GameTestScope.openShop(currency: CurrencyType): Shop {
        player.gameMode = GameMode.REGULAR
        player.gameModeSelected = true
        val inventory = createShopInv()
        val invTypes = InvTypeList(mutableMapOf(inventory.type.id to inventory.type))
        val shops = Shops(invTypes, eventBus)

        shops.open(
            player = player,
            title = "",
            shopInv = inventory,
            sideInv = player.inv,
            currency = currency,
            buyPercentage = 40.0,
            sellPercentage = 130.0,
            changePercentage = 3.0,
            subtext = "",
        )

        client.clearOutgoing()

        check(player.ui.containsModal(ShopInterfaces.shop_main))
        check(player.ui.containsModal(ShopInterfaces.shop_side))
        return checkNotNull(player.openedShop)
    }

    private fun createShopInv(): Inventory {
        val builder =
            InvPluginBuilder().apply {
                internal = "test_point_shop"
                scope = InvScope.Shared
                stack = InvStackType.Always
                size = Constants.shop_default_size
                restock = true
                allStock = true
            }
        val type = builder.build(-1)
        return Inventory.create(type)
    }

    class Deps
    @Inject
    constructor(
        val operationMap: ShopOperationMap,
        val objTypes: ObjTypeList,
        val restockProcess: ShopRestockProcess,
        val marketPrices: MarketPrices,
    )

    private companion object {
        private val TEST_CURRENCY = CurrencyType(internalId = 9999, internalName = "test_points")
        private val OP_BUY1 = IfButtonOp.Op2
        private val OP_BUY5 = IfButtonOp.Op3
        private val OP_SELL1 = IfButtonOp.Op2
    }
}
