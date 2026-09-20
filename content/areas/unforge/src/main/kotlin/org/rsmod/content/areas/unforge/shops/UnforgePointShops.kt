package org.rsmod.content.areas.unforge.shops

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.operation.VarpShopOperations
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.api.type.refs.currency.CurrencyReferences
import org.rsmod.content.areas.unforge.slayer.UnforgeSlayerVarps
import org.rsmod.content.areas.unforge.slayer.cwSetVarp
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shop currencies that are not standard gp. These are name+id keys only (`currency.sym`) - the
 * actual balance lives in each currency's own varp, and the operations below know how to charge it.
 * An npc shop opts in through `npc_shop_currency`.
 */
object UnforgeShopCurrencies : CurrencyReferences() {
    val pvm_points = find("pvm_points")
    val wilderness_slayer_points = find("wilderness_slayer_points")
}

/**
 * Fixed prices for `cw_pvm_point_shop`, ported from `kronos-data/shops/Pvm_Point_Shop.yaml` (the
 * `price:` field of each stocked item). Stock slots without a kronos price - the three god books
 * kronos did not stock - reuse the 250 the other god books cost.
 */
private val PVM_SHOP_PRICES: Map<Int, Int>
    get() =
        mapOf(
            // Core PvM reward/unlock items.
            UnforgeObjs.crystal_key.id to 250,
            UnforgeObjs.macro_quiz_mystery_box.id to 500,
            UnforgeObjs.tattered_scroll.id to 250,
            UnforgeObjs.crumpled_scroll.id to 350,
            UnforgeObjs.hazelmere_scroll.id to 250,
            UnforgeObjs.spell_scroll.id to 400,
            UnforgeObjs.barbassault_penance_fighter_hat.id to 275,
            UnforgeObjs.barbassault_penance_healer_hat.id to 275,
            UnforgeObjs.barbassault_penance_runner_hat.id to 275,
            UnforgeObjs.barbassault_penance_ranger_hat.id to 275,
            UnforgeObjs.barbassault_penance_fighter_torso.id to 375,
            UnforgeObjs.barbassault_penance_gloves.id to 375,
            UnforgeObjs.barbassault_penance_runner_boots.id to 100,
            UnforgeObjs.barbassault_penance_ranger_legs.id to 150,
            UnforgeObjs.hundred_gauntlets_level_1.id to 25,
            UnforgeObjs.hundred_gauntlets_level_2.id to 50,
            UnforgeObjs.hundred_gauntlets_level_3.id to 75,
            UnforgeObjs.hundred_gauntlets_level_4.id to 100,
            UnforgeObjs.hundred_gauntlets_level_5.id to 125,
            UnforgeObjs.hundred_gauntlets_level_6.id to 150,
            UnforgeObjs.hundred_gauntlets_level_7.id to 175,
            UnforgeObjs.hundred_gauntlets_level_8.id to 200,
            UnforgeObjs.hundred_gauntlets_level_9.id to 225,
            UnforgeObjs.hundred_gauntlets_level_10.id to 250,
            UnforgeObjs.saradominbook_complete.id to 250,
            UnforgeObjs.zamorakbook_complete.id to 250,
            UnforgeObjs.guthixbook_complete.id to 250,
            UnforgeObjs.bandosbook_complete.id to 250,
            UnforgeObjs.armadylbook_complete.id to 250,
            UnforgeObjs.zarosbook_complete.id to 250,
            UnforgeObjs.set_cannon.id to 650,
            UnforgeObjs.crystalshard_necklace.id to 20,
        )

/** Fixed prices for `cw_wilderness_slayer_shop` from `Wilderness_Slayer_Shop.yaml`. */
private val WILD_SLAYER_SHOP_PRICES: Map<Int, Int>
    get() =
        mapOf(
            UnforgeObjs.looting_bag.id to 90,
            UnforgeObjs.bh_rune_pouch.id to 150,
            UnforgeObjs.imbued_heart.id to 600,
        )

/** Charges [PvmPoints] - minted by attributed npc kills - for `cw_pvm_point_shop`. */
@Singleton
class PvmPointShopOperations
@Inject
constructor(
    objTypes: ObjTypeList,
    restockProcess: ShopRestockProcess,
    marketPrices: MarketPrices,
    private val pvmPoints: PvmPoints,
) : VarpShopOperations(objTypes, restockProcess, marketPrices) {
    override val currencyName: String = "PvM points"

    override fun balanceOf(player: Player): Int = pvmPoints.balance(player)

    override fun debit(player: Player, amount: Int) {
        check(pvmPoints.spend(player, amount)) {
            "PvmPoints.spend failed after a successful shop transaction (amount=$amount)"
        }
    }

    // The old Blood Money inventories are now part of this shop as well. Their legacy prices are
    // not represented in the PvM price table, so keep every merged item purchasable using its
    // cache value instead of silently hiding it from the shop.
    override fun priceOf(type: ObjType): Int? =
        PVM_SHOP_PRICES[type.id] ?: (type as? UnpackedObjType)?.cost?.coerceAtLeast(1)
}

/** Charges `cw_slayer_wild_points` - minted by wilderness slayer task completion. */
@Singleton
class WildSlayerPointShopOperations
@Inject
constructor(objTypes: ObjTypeList, restockProcess: ShopRestockProcess, marketPrices: MarketPrices) :
    VarpShopOperations(objTypes, restockProcess, marketPrices) {
    override val currencyName: String = "wilderness slayer points"

    override fun balanceOf(player: Player): Int = player.vars[UnforgeSlayerVarps.wildPoints]

    override fun debit(player: Player, amount: Int) {
        cwSetVarp(player, UnforgeSlayerVarps.wildPoints, balanceOf(player) - amount)
    }

    override fun priceOf(type: ObjType): Int? = WILD_SLAYER_SHOP_PRICES[type.id]
}

/** Registers the non-gp currencies so `ShopScript` can route their buy/sell ops. */
class UnforgePointShopScript
@Inject
constructor(
    private val operationMap: ShopOperationMap,
    private val pvmOperations: PvmPointShopOperations,
    private val wildSlayerOperations: WildSlayerPointShopOperations,
) : PluginScript() {
    override fun ScriptContext.startup() {
        operationMap.register(UnforgeShopCurrencies.pvm_points, pvmOperations)
        operationMap.register(
            UnforgeShopCurrencies.wilderness_slayer_points,
            wildSlayerOperations,
        )
    }
}
