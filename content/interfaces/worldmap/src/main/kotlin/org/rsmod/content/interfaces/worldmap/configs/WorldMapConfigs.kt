package org.rsmod.content.interfaces.worldmap.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences

typealias worldmap_components = WorldMapComponents

typealias worldmap_varbits = WorldMapVarBits

object WorldMapComponents : ComponentReferences() {
    val orb_worldmap = find("orbs:worldmap")
    val close = find("worldmap:close")
    val esckey = find("worldmap:esckey")
}

object WorldMapVarBits : VarBitReferences() {
    val minimap_toggle = find("minimap_toggle")
    val osm_minimap_toggle = find("osm_minimap_toggle")
}
