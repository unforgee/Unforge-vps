package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * Applies [CustomSpawnRegistry] state: re-spawns recorded permanent npcs/locs on startup and keeps
 * map-spawned npcs/locs hidden when they were deleted in-game.
 *
 * Map npcs spawn through the registry's normal `add` path, so suppression hooks
 * [NpcStateEvents.Create] and hides matching npcs before they ever reach players. Map locs are all
 * decoded before scripts run, so loc deletions are applied once at [GameLifecycle.Startup].
 */
class CustomSpawnScript
@Inject
constructor(
    private val store: CustomSpawnRegistry,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        store.load()

        onEvent<NpcStateEvents.Create> {
            if (store.isNpcDeleted(npc.type.id, npc.spawnCoords)) {
                // `hide` (not `del`): at this point the npc is not yet registered to its zone,
                // and `hide` is the engine's supported "spawned but absent" state.
                npcRepo.hide(npc, Int.MAX_VALUE)
            }
        }

        onGameStartup {
            applyStoredSpawns()
            applyStoredLocDeletes()
        }
    }

    private fun GameLifecycle.Startup.applyStoredSpawns() {
        var locCount = 0
        for (entry in store.locSpawns()) {
            val type = locTypes[entry.id] ?: continue
            val coords = CoordGrid(entry.coords)
            val layer = LocLayerConstants.of(entry.shape)
            val loc = LocInfo(layer, coords, LocEntity(type.id, entry.shape, entry.angle))
            if (locRepo.add(loc, Int.MAX_VALUE)) {
                locCount++
            }
        }

        var npcCount = 0
        for (entry in store.npcSpawns()) {
            val type = npcTypes[entry.id] ?: continue
            val npc = Npc(type, CoordGrid(entry.coords))
            npc.mode = NpcMode.None
            // Delayed so every script's spawn hooks are registered before these appear.
            npcRepo.addDelayed(npc, spawnDelay = 1, duration = Int.MAX_VALUE)
            npcCount++
        }

        if (locCount > 0 || npcCount > 0) {
            logger.info { "Restored custom spawns: npcs=$npcCount, locs=$locCount" }
        }
    }

    private fun GameLifecycle.Startup.applyStoredLocDeletes() {
        var count = 0
        for (entry in store.locDeletes()) {
            val coords = CoordGrid(entry.coords)
            val loc =
                locRepo.findAll(coords).firstOrNull {
                    it.id == entry.id && it.shapeId == entry.shape
                } ?: locRepo.findAll(coords).firstOrNull { it.id == entry.id }
            if (loc != null && locRepo.del(loc, Int.MAX_VALUE)) {
                count++
            }
        }
        if (count > 0) {
            logger.info { "Applied stored loc deletions: $count" }
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
