package org.rsmod.content.generic.locs.signpost

import jakarta.inject.Inject
import org.rsmod.api.player.output.Camera.camReset
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.game.enums.EnumTypeMap
import org.rsmod.game.enums.EnumTypeMapResolver
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SignpostScript @Inject constructor(private val enums: EnumTypeMapResolver) : PluginScript() {
    private lateinit var signposts: EnumTypeMap<CoordGrid, String>

    override fun ScriptContext.startup() {
        signposts = enums[SignpostEnums.signpost_directions]
        onApLoc1(signpost_locs.signpost) { apReadSignpost(it.loc) }
        onOpLoc1(signpost_locs.signpost) { readSignpost(it.loc) }
        onIfClose(signpost_interfaces.signpost) { player.exitSignpost() }
    }

    private fun ProtectedAccess.apReadSignpost(loc: BoundLocInfo) {
        if (isWithinApRange(loc, distance = 5)) {
            readSignpost(loc)
        }
    }

    private fun ProtectedAccess.readSignpost(loc: BoundLocInfo) {
        camForceAngle(rate = 280, rate2 = 0)

        val camMoveTo = coords.translateZ(-6)
        camMoveTo(camMoveTo, height = 1500, rate = 2, rate2 = 10)

        val camLookAt = coords.translateZ(3)
        camLookAt(camLookAt, height = 450, rate = 2, rate2 = 10)

        val directions = signposts.getValue(loc.coords).split("|")
        require(directions.size >= 4) {
            "signpost_directions for ${loc.coords} must have 4 parts, got ${directions.size}"
        }
        ifSetText(signpost_components.signpost_west, directions[0])
        ifSetText(signpost_components.signpost_south, directions[1])
        ifSetText(signpost_components.signpost_north, directions[2])
        ifSetText(signpost_components.signpost_east, directions[3])
        ifOpenMainModal(signpost_interfaces.signpost)

        faceDirection(Direction.North)
    }

    private fun Player.exitSignpost() {
        camReset(this)
    }
}
