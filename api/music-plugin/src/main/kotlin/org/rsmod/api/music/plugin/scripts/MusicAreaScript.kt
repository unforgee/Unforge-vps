package org.rsmod.api.music.plugin.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.dbcolumns
import org.rsmod.api.config.refs.dbtables
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.music.MusicPlayMode
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.dbtable.DbTableResolver
import org.rsmod.game.entity.Player
import org.rsmod.game.type.area.AreaType
import org.rsmod.game.type.area.AreaTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class MusicAreaScript
@Inject
constructor(
    private val dbTables: DbTableResolver,
    private val musicPlayer: MusicPlayer,
    private val areaTypes: AreaTypeList,
) : PluginScript() {
    private var Player.playMode by enumVarp<MusicPlayMode>(varps.musicplay)

    override fun ScriptContext.startup() {
        val scriptAreas = loadScriptAreas()
        for (area in scriptAreas) {
            onArea(area) { playAreaMusic(area) }
        }
        onPlayerLogin { player.setDefaultModes() }
    }

    private fun ProtectedAccess.playAreaMusic(area: AreaType) {
        musicPlayer.enterArea(player, area)
    }

    private fun Player.setDefaultModes() {
        playMode = MusicPlayMode.Area
    }

    private fun loadScriptAreas(): List<AreaType> {
        val areas = linkedSetOf<AreaType>()

        // Official music-region AreaType ids from music:area / music:area_default.
        // These fire only where server map area polygons (file 102) register the id.
        for (areaId in musicPlayer.musicAreaIds()) {
            val area = areaTypes.types[areaId] ?: continue
            areas += area
        }

        val classicRows = dbTables[dbtables.music_classic]
        for (row in classicRows) {
            val area = row[dbcolumns.music_classic_area]
            val autoScript = row[dbcolumns.music_classic_auto_script]
            if (autoScript) {
                areas += area
            }
        }

        val modernAreas = dbTables[dbtables.music_modern]
        for (row in modernAreas) {
            val area = row[dbcolumns.music_modern_area]
            val autoScript = row[dbcolumns.music_modern_auto_script]
            if (autoScript) {
                areas += area
            }
        }

        return areas.toList()
    }
}
