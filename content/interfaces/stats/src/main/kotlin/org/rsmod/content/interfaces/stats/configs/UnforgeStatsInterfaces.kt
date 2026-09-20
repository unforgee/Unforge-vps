package org.rsmod.content.interfaces.stats.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias stats_interfaces = UnforgeStatsInterfaces

typealias stats_components = UnforgeStatsComponents

object UnforgeStatsInterfaces : InterfaceReferences() {
    val unforge_stats = find("unforge_stats")
}

object UnforgeStatsComponents : ComponentReferences() {
    val root = find("unforge_stats:root")
    val title = find("unforge_stats:title")
    val skills = find("unforge_stats:skills")
    val bonuses = find("unforge_stats:bonuses")
    val gear = find("unforge_stats:gear")
    val perks = find("unforge_stats:perks")
}
