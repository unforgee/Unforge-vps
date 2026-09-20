package org.rsmod.content.areas.unforge.map

import org.rsmod.api.type.builders.map.npc.MapNpcSpawnBuilder
import org.rsmod.content.areas.unforge.UnforgeScript

object UnforgeNpcSpawns : MapNpcSpawnBuilder() {
    override fun onPackMapTask() {
        resourceFile<UnforgeScript>("npcs.toml")
    }
}
