package org.rsmod.content.travel.canoe.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias canoe_components = CanoeComponents

object CanoeComponents : ComponentReferences() {
    val shape_log = find("canoeing:log", 5540955978932720971)
    val shape_dugout = find("canoeing:dugout", 574740175170890111)
    val shape_stable_dugout = find("canoeing:stable_dugout", 5904149818566193745)
    val shape_waka = find("canoeing:waka", 558687581070453046)
    val shape_close = find("canoeing:close", 1179288482004399632)

    val destination_edgeville = find("canoe_map:edgeville", 7166808956885884847)
    val destination_lumbridge = find("canoe_map:lumbridge", 2579143122927128215)
    val destination_champs_guild = find("canoe_map:champions", 4289613900112868012)
    val destination_barb_village = find("canoe_map:barbarian", 1963563152042074078)
    val destination_wild_pond = find("canoe_map:wilderness", 1963563152042074079)
    val destination_ferox_enclave = find("canoe_map:sanctuary", 1189437039674251846)
    val destination_close = find("canoe_map:close")
}
