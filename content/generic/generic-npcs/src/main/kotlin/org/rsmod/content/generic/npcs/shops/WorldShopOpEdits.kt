package org.rsmod.content.generic.npcs.shops

import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.type.npc.NpcType

/**
 * Adds a `Trade` option to shopkeeper npc types whose cache definition has no ops at all.
 *
 * [WorldShopEdits] already binds these npcs to a shop inventory, but `ShopkeeperScript` needs a
 * visible option to bind the open handler to; with `[null, null, null, null, null]` ops the npc was
 * completely non-interactive and the binding only logged an error at startup.
 */
internal object WorldShopOpEdits : NpcEditor() {
    init {
        tradeOp(WorldShopNpcRefs.aubury)
        tradeOp(WorldShopNpcRefs.fortis_shop_seamstress)
        tradeOp(WorldShopNpcRefs.gnomeshopkeeper)
        tradeOp(WorldShopNpcRefs.thessalia)

        tradeOp(WorldShopNpcRefs.dwarf_city_blue_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_blue_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_brown_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_brown_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_green_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_green_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_purple_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_purple_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_red_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_red_trader1_multi)
        tradeOp(WorldShopNpcRefs.dwarf_city_red_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_red_trader2_multi)
        tradeOp(WorldShopNpcRefs.dwarf_city_silver_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_silver_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_white_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_white_trader2)
        tradeOp(WorldShopNpcRefs.dwarf_city_yellow_trader1)
        tradeOp(WorldShopNpcRefs.dwarf_city_yellow_trader2)
    }

    private fun NpcEditor.tradeOp(npc: NpcType) {
        edit(npc) { op1 = "Trade" }
    }
}
