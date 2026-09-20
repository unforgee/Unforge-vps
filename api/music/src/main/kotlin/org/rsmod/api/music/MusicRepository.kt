package org.rsmod.api.music

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import jakarta.inject.Inject
import org.rsmod.api.config.refs.dbcolumns
import org.rsmod.api.config.refs.dbtables
import org.rsmod.api.music.configs.music_columns
import org.rsmod.api.music.configs.music_tables
import org.rsmod.api.music.configs.music_varps
import org.rsmod.api.random.GameRandom
import org.rsmod.game.dbtable.DbTableResolver
import org.rsmod.game.type.area.AreaType
import org.rsmod.game.type.area.AreaTypeList
import org.rsmod.game.type.dbrow.DbRowType
import org.rsmod.game.type.varp.VarpType

public class MusicRepository
@Inject
constructor(
    private val random: GameRandom,
    private val dbTables: DbTableResolver,
    private val areaTypes: AreaTypeList,
) {
    private lateinit var musicRows: Int2ObjectMap<Music>
    private lateinit var musicIds: Int2ObjectMap<Music>

    private lateinit var modernAreas: Int2ObjectMap<List<Music>>
    private lateinit var classicAreas: Int2ObjectMap<Music>

    public fun forRow(row: DbRowType): Music? {
        ensureLoaded()
        return musicRows[row.id]
    }

    public fun forId(id: Int): Music? {
        ensureLoaded()
        return musicIds[id]
    }

    public fun getModernArea(area: AreaType): List<Music>? {
        ensureLoaded()
        return modernAreas[area.id]
    }

    public fun getClassicArea(area: AreaType): Music? {
        ensureLoaded()
        return classicAreas[area.id]
    }

    public fun getAll(): Collection<Music> {
        ensureLoaded()
        return musicRows.values
    }

    /** AreaType ids that have official or custom music mappings. */
    public fun areaIds(): Set<Int> {
        ensureLoaded()
        return buildSet {
            addAll(modernAreas.keys)
            addAll(classicAreas.keys)
        }
    }

    public fun load() {
        val unlockVarps = unlockVarps()

        val musicRows = loadMusicRows(unlockVarps)
        this.musicRows = Int2ObjectOpenHashMap(musicRows)

        val musicSlots = mapMusicById(musicRows)
        this.musicIds = Int2ObjectOpenHashMap(musicSlots)

        val modernAreas = loadModernAreas(musicRows)
        this.modernAreas = Int2ObjectOpenHashMap(modernAreas)

        val classicAreas = loadClassicAreas(musicRows)
        this.classicAreas = Int2ObjectOpenHashMap(classicAreas)
    }

    private fun ensureLoaded() {
        if (!::musicRows.isInitialized) {
            load()
        }
    }

    private fun loadMusicRows(unlockVarps: List<VarpType>): Map<Int, Music> {
        val rows = dbTables[music_tables.music]
        val mapped = mutableMapOf<Int, Music>()
        var currId = 1
        for (row in rows) {
            val displayName = row[music_columns.displayName]
            val unlockHint = row[music_columns.unlockHint]
            val midi = row[music_columns.midi]
            val variable = row[music_columns.variable]
            val duration = row[music_columns.duration]
            val hidden = row.getOrNull(music_columns.hidden) ?: false
            val secondary = row.getOrNull(music_columns.secondary_track)?.id
            val unlockVarp = unlockVarps.getOrNull(variable.varpIndex - 1)
            val music =
                Music(
                    id = currId++,
                    displayName = displayName,
                    unlockHint = unlockHint,
                    duration = duration,
                    midi = midi,
                    unlockVarp = unlockVarp,
                    unlockBitpos = variable.bitpos,
                    hidden = hidden,
                    secondary = secondary,
                )
            mapped[row.type.id] = music
        }
        return mapped
    }

    private fun mapMusicById(musicRows: Map<Int, Music>): Map<Int, Music> {
        return musicRows.values.associateBy(Music::id)
    }

    /**
     * Builds modern (area → playlist) maps from official `music:area` columns, then overlays custom
     * `music_modern` rows (e.g. server-only areas such as Lumbridge).
     */
    private fun loadModernAreas(musicRows: Map<Int, Music>): Map<Int, List<Music>> {
        val grouped = mutableMapOf<Int, MutableList<Music>>()

        val musicTableRows = dbTables[music_tables.music]
        for (row in musicTableRows) {
            val areaId = row.getOrNull(music_columns.area) ?: continue
            if (areaId <= 0 || areaTypes[areaId] == null) {
                continue
            }
            val music = musicRows[row.type.id] ?: continue
            grouped.computeIfAbsent(areaId) { mutableListOf() } += music
        }

        val customRows = dbTables[dbtables.music_modern]
        for (row in customRows) {
            val area = row[dbcolumns.music_modern_area]
            val trackRows = row[dbcolumns.music_modern_tracks]
            val musicList = ArrayList<Music>(trackRows.size)
            for (trackRow in trackRows) {
                val music = musicRows[trackRow.id]
                if (music == null) {
                    throw IllegalStateException("Music row not found: '${trackRow.internalName}'")
                }
                musicList += music
            }
            grouped[area.id] = musicList
        }
        return grouped
    }

    /**
     * Builds classic (area → single track) maps from official `music:area_default`, then overlays
     * custom `music_classic` rows.
     */
    private fun loadClassicAreas(musicRows: Map<Int, Music>): Map<Int, Music> {
        val areas = mutableMapOf<Int, Music>()

        val musicTableRows = dbTables[music_tables.music]
        for (row in musicTableRows) {
            val areaId = row.getOrNull(music_columns.area_default) ?: continue
            if (areaId <= 0 || areaTypes[areaId] == null) {
                continue
            }
            val music = musicRows[row.type.id] ?: continue
            areas[areaId] = music
        }

        val customRows = dbTables[dbtables.music_classic]
        for (row in customRows) {
            val area = row[dbcolumns.music_classic_area]
            val trackRow = row[dbcolumns.music_classic_track]
            val music = musicRows[trackRow.id]
            if (music == null) {
                throw IllegalStateException("Music row not found: '${trackRow.internalName}'")
            }
            // Custom rows override official area_default for the same area id.
            areas[area.id] = music
        }
        return areas
    }

    private fun unlockVarps(): List<VarpType> =
        listOf(
            music_varps.multi_1,
            music_varps.multi_2,
            music_varps.multi_3,
            music_varps.multi_4,
            music_varps.multi_5,
            music_varps.multi_6,
            music_varps.multi_7,
            music_varps.multi_8,
            music_varps.multi_9,
            music_varps.multi_10,
            music_varps.multi_11,
            music_varps.multi_12,
            music_varps.multi_13,
            music_varps.multi_14,
            music_varps.multi_15,
            music_varps.multi_16,
            music_varps.multi_17,
            music_varps.multi_18,
            music_varps.multi_19,
            music_varps.multi_20,
            music_varps.multi_21,
            music_varps.multi_22,
            music_varps.multi_23,
            music_varps.multi_24,
            music_varps.multi_25,
        )
}
