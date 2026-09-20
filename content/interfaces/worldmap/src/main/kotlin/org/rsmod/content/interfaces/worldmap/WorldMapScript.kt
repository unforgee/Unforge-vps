package org.rsmod.content.interfaces.worldmap

import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.worldmap.configs.worldmap_components
import org.rsmod.content.interfaces.worldmap.configs.worldmap_varbits
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Opens/closes the world map from the minimap orb.
 *
 * Live client CS2 (`orbs_worldmap_setup`) only configures menu ops; the server must `IfOpenSub`
 * interface 595. Ops match that script: Op2 floating, Op3 fullscreen, Op4 minimap minimise/restore.
 * When the floater already hosts the map, Op2 closes it.
 *
 * Rev 239 click target is `orbs:55` (`orbs:worldmap`); older symbols pointed at `orbs:53`.
 */
class WorldMapScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    private var Player.minimapToggle by boolVarBit(worldmap_varbits.minimap_toggle)
    private var Player.osmMinimapToggle by boolVarBit(worldmap_varbits.osm_minimap_toggle)

    override fun ScriptContext.startup() {
        onIfOverlayButton(worldmap_components.orb_worldmap) { player.handleWorldMapOrb(op) }
        onIfOverlayButton(worldmap_components.close) { player.closeWorldMap() }
        onIfOverlayButton(worldmap_components.esckey) { player.closeWorldMap() }
    }

    private fun Player.handleWorldMapOrb(op: IfButtonOp) {
        when (op) {
            IfButtonOp.Op1,
            IfButtonOp.Op2 -> toggleFloatingWorldMap()
            IfButtonOp.Op3 -> openFullscreenWorldMap()
            IfButtonOp.Op4 -> toggleMinimap()
            else -> Unit
        }
    }

    private fun Player.toggleFloatingWorldMap() {
        if (ui.containsOverlay(interfaces.worldmap)) {
            closeWorldMap()
            return
        }
        ifOpenOverlay(interfaces.worldmap, eventBus)
    }

    private fun Player.openFullscreenWorldMap() {
        ifOpenFullOverlay(interfaces.worldmap, eventBus)
    }

    private fun Player.closeWorldMap() {
        ifCloseOverlay(interfaces.worldmap, eventBus)
    }

    private fun Player.toggleMinimap() {
        // Fixed/resizable and enhanced clients use different varbits (see orbs_worldmap_setup).
        minimapToggle = !minimapToggle
        osmMinimapToggle = !osmMinimapToggle
    }
}
