package org.rsmod.content.areas.tutorialisland.map

import org.rsmod.api.type.builders.map.npc.MapNpcSpawnBuilder
import org.rsmod.content.areas.tutorialisland.TutorialIslandBootstrap

/**
 * Packs Tutorial Island NPC spawns from `npcs.toml` into game-cache mapsquare file 100.
 *
 * **Important:** Changes to this builder or `npcs.toml` only take effect after `gradlew packCache`
 * (or full `install`). A normal server `run` does not pack map NPC lists.
 */
object TutorialNpcSpawns : MapNpcSpawnBuilder() {
    override fun onPackMapTask() {
        resourceFile<TutorialIslandBootstrap>("npcs.toml")
    }
}
