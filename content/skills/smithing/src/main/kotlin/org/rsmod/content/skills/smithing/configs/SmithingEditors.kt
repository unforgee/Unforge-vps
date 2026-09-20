package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType

object SmithingLocContent : LocEditor() {
    init {
        furnace(smithing_locs.furnace)
        furnace(smithing_locs.furnace_upass)
        furnace(smithing_locs.viking_furnace)
        furnace(smithing_locs.dwarf_keldagrim_furnace)
        furnace(smithing_locs.enakh_new_furnace_lit)
        furnace(smithing_locs.fairy_furnace)
        furnace(smithing_locs.swan_furnace)
        furnace(smithing_locs.burgh_furnace_fired)
        furnace(smithing_locs.varrock_diary_furnace)
        furnace(smithing_locs.ahoy_new_furnace)
        furnace(smithing_locs.fai_falador_furnace)
        furnace(smithing_locs.wilderness_resource_furnace)
        furnace(smithing_locs.lovakengj_furnace_large)
        furnace(smithing_locs.lovakengj_furnace)
        furnace(smithing_locs.zqfurnace_lit)
        furnace(smithing_locs.lovaquest_tower_furnace_lit)
        furnace(smithing_locs.brimstone_furnace)
        furnace(smithing_locs.prif_furnace)
        furnace(smithing_locs.darkm_furnace)

        anvil(smithing_locs.anvil)
        anvil(smithing_locs.dorics_anvil)
        anvil(smithing_locs.viking_anvil)
        anvil(smithing_locs.dwarf_keldagrim_anvil)
        anvil(smithing_locs.dorgesh_blacksmith_anvil)
        anvil(smithing_locs.brut_anvil)
        anvil(smithing_locs.lovakengj_anvil)
        anvil(smithing_locs.ds2_guild_blacksmith_anvil)
        anvil(smithing_locs.ds2_ac_forge_anvil)
        anvil(smithing_locs.darkm_anvil)
        anvil(smithing_locs.lumbridge_anvil)
    }

    private fun furnace(type: LocType) {
        edit(type) { contentGroup = smithing_content.furnace }
    }

    private fun anvil(type: LocType) {
        edit(type) { contentGroup = smithing_content.anvil }
    }
}

/**
 * Groups every bar that appears in [SmithingProducts] so a bar can be used directly on an anvil.
 *
 * Silver and gold are deliberately absent - they are smelted, but the anvil grid has no tier for
 * them (`smithing_setup`'s switch only covers bronze, iron, steel, mithril, adamantite, runite and
 * lovakite), so using one on an anvil should fall through to the normal "nothing happens".
 */
object SmithingBarContent : ObjEditor() {
    init {
        ore(smithing_objs.copper_ore)
        ore(smithing_objs.tin_ore)
        ore(smithing_objs.iron_ore)
        ore(smithing_objs.silver_ore)
        ore(smithing_objs.gold_ore)
        ore(smithing_objs.mithril_ore)
        ore(smithing_objs.adamantite_ore)
        ore(smithing_objs.runite_ore)
        ore(smithing_objs.blurite_ore)
        ore(smithing_objs.lovakite_ore)
        ore(smithing_objs.coal)

        bar(smithing_objs.bronze_bar)
        bar(smithing_objs.iron_bar)
        bar(smithing_objs.steel_bar)
        bar(smithing_objs.mithril_bar)
        bar(smithing_objs.adamantite_bar)
        bar(smithing_objs.runite_bar)
        bar(smithing_objs.lovakite_bar)
    }

    private fun bar(type: ObjType) {
        edit(type) { contentGroup = smithing_content.smithable_bar }
    }

    private fun ore(type: ObjType) {
        edit(type) { contentGroup = smithing_content.smeltable_ore }
    }
}
