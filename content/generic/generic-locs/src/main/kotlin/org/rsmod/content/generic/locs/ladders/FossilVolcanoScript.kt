package org.rsmod.content.generic.locs.ladders

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fossil Island volcano has a world-map entrance and a separate dungeon
 * exit. They are not a normal one-level ladder: the cache places the two
 * locs at different map coordinates and planes.
 */
class FossilVolcanoScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(fossil_volcano_locs.entrance) { climbDown(it.loc) }
        onOpLoc1(fossil_volcano_locs.exit) { climbUp(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDown(loc: BoundLocInfo) {
        if (loc.coords != OUTSIDE_ENTRANCE) return
        arriveDelay()
        telejump(UNDERGROUND_EXIT)
    }

    private suspend fun ProtectedAccess.climbUp(loc: BoundLocInfo) {
        if (loc.coords != UNDERGROUND_EXIT) return
        arriveDelay()
        telejump(OUTSIDE_ENTRANCE)
    }

    private companion object {
        // These coordinates are the unique vanilla-cache map-loc pair for
        // fossil_volcano_entrance (31031) and fossil_mining_start_exit (31032).
        val OUTSIDE_ENTRANCE = CoordGrid(3813, 3807, 0)
        val UNDERGROUND_EXIT = CoordGrid(3807, 10194, 3)
    }
}

private typealias fossil_volcano_locs = FossilVolcanoLocs

public object FossilVolcanoLocs : LocReferences() {
    val entrance = find("fossil_volcano_entrance")
    val exit = find("fossil_mining_start_exit")
}
