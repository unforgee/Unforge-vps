package org.rsmod.content.areas.city.lumbridge.map

import org.rsmod.api.type.builders.map.npc.MapNpcSpawnBuilder
import org.rsmod.content.areas.city.lumbridge.LumbridgeScript

object LumbridgeNpcSpawnsR239 : MapNpcSpawnBuilder() {
    override fun onPackMapTask() {
        resourceFile<LumbridgeScript>("r239-boss-spawn.toml")
    }
}
