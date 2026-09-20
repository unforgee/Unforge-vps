package org.rsmod.content.areas.unforge.shops

import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.content.areas.unforge.configs.UnforgeCustomNpcRefs
import org.rsmod.game.type.npc.NpcType

/**
 * Marks npc types as shopkeepers by assigning [ShopParams.npc_shop] (the shop inventory's symbolic
 * name) and [ShopParams.npc_shop_title]. The generic shopkeeper script in `api:shops` opens the
 * bound shop on the npc's trade op wherever the npc spawns - no coordinate checks.
 *
 * Npcs with a multi-shop dialogue menu (gunnjorn, route_radigad_ponfit, traiborn) are bound
 * directly in [UnforgeShops] instead, since a single npc type can only carry one shop here.
 *
 * Kronos shops whose currency is not implemented in Unforge (vote tickets, blood money, pest
 * control points, survival tokens, golden nuggets, marks of grace, molch pearls, mage arena points,
 * appreciation points, warrior guild tokens, wilderness points, easter eggs, tokkul, unidentified
 * minerals, bounty emblems, refunded credits) are deliberately left unbound - a gp fallback would
 * misprice reward gear. They stay defined as inventories so a future currency implementation can
 * rebind them. See docs/beta-shop-audit.md.
 */
internal object UnforgeShopEdits : NpcEditor() {
    init {
        shopkeeper(
            UnforgeShopNpcs.deal_50luke,
            "cw_pvm_point_shop",
            "PvM Points Shop",
            currency = "pvm_points",
        )
        shopkeeper(UnforgeShopNpcs.diary_queen, "cw_achievement_rewards", "Achievement Rewards")
        shopkeeper(
            UnforgeShopNpcs.fairy2_repair_2ops,
            "cw_fairy_fixit_s_fairy_enchantment",
            "Fairy Fixit's Fairy Enchantment",
        )
        shopkeeper(UnforgeShopNpcs.farming_shopkeeper_2, "cw_farmer_s_shop", "Farmer's Shop")
        shopkeeper(UnforgeShopNpcs.generalassistant4, "cw_general_store", "General Store")
        shopkeeper(UnforgeCustomNpcRefs.edgeville_melee_keeper, "cw_melee_shop", "Melee Shop")
        shopkeeper(
            UnforgeCustomNpcRefs.edgeville_skilling_keeper,
            "cw_skilling_supply",
            "Skilling Shop",
        )
        shopkeeper(UnforgeShopNpcs.hatius_lumbridge_diary, "cw_gambling_shop", "Gambling Shop")
        shopkeeper(UnforgeShopNpcs.ironman_tutor_1, "cw_ironman", "Ironman Store", tradeOp = 2)
        shopkeeper(UnforgeCustomNpcRefs.edgeville_magic_keeper, "cw_magic_shop", "Magic Shop")
        shopkeeper(
            UnforgeCustomNpcRefs.edgeville_range_keeper,
            "cw_ranged_ammo_and_weapons_shop",
            "Range Shop",
        )
        shopkeeper(
            UnforgeCustomNpcRefs.edgeville_supply_keeper,
            "cw_skilling_supply",
            "Supply Shop",
        )
        shopkeeper(UnforgeShopNpcs.myarm_leprechaun, "cw_farmer_s_shop", "Farmer's Shop")
        shopkeeper(
            UnforgeShopNpcs.piscarilius_fishing_supplies_trader,
            "cw_tynan_s_fishing_supplies",
            "Tynan's Fishing Supplies",
        )
        shopkeeper(UnforgeShopNpcs.poh_garden_supplier, "cw_garden_centre", "Garden Centre")
        shopkeeper(
            UnforgeShopNpcs.poh_sawmill_opp,
            "cw_construction_supplies",
            "Construction Supplies",
        )
        shopkeeper(UnforgeShopNpcs.rcu_zammy_mage1b, "cw_battle_runes", "Battle Runes")
        shopkeeper(
            UnforgeShopNpcs.sailing_transport_trader_stan_base,
            "cw_trader_stan_s_trading_post",
            "Trader Stan's Trading Post",
        )
        shopkeeper(
            UnforgeShopNpcs.sheepsheeredg,
            "cw_fishing_guild_shop",
            "Fishing Guild Shop",
            tradeOp = 1,
        )
        shopkeeper(
            UnforgeShopNpcs.slayer_master_7,
            "cw_wilderness_slayer_shop",
            "Wilderness Equipment",
            currency = "wilderness_slayer_points",
        )
        shopkeeper(
            UnforgeShopNpcs.slayer_master_nieve,
            "cw_slayer_equipment",
            "Slayer Equipment",
        )
        shopkeeper(
            UnforgeShopNpcs.swan_arnold,
            "cw_arnold_s_electic_supplies",
            "Arnold's Electic Supplies",
        )
        shopkeeper(
            UnforgeShopNpcs.wgs_heroes_tureal,
            "cw_slayer_equipment",
            "Slayer Equipment",
            tradeOp = 1,
        )
        shopkeeper(
            UnforgeShopNpcs.wilderness_capeseller_6,
            "cw_richard_s_team_capes",
            "Richard's Team Capes",
        )
    }

    private fun NpcEditor.shopkeeper(
        npc: NpcType,
        shop: String,
        title: String,
        currency: String? = null,
        tradeOp: Int = 0,
    ) {
        edit(npc) {
            param[ShopParams.npc_shop] = shop
            param[ShopParams.npc_shop_title] = title
            if (currency != null) {
                param[ShopParams.npc_shop_currency] = currency
            }
            if (tradeOp > 0) {
                setOp(tradeOp, "Trade")
            }
        }
    }

    private fun NpcPluginBuilder.setOp(slot: Int, label: String) {
        when (slot) {
            1 -> op1 = label
            2 -> op2 = label
            3 -> op3 = label
            4 -> op4 = label
            5 -> op5 = label
        }
    }
}
