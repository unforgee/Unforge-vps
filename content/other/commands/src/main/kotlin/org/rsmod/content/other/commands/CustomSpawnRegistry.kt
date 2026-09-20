package org.rsmod.content.other.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import org.rsmod.map.CoordGrid
import org.rsmod.module.RuntimePaths

/**
 * Persists admin-spawned and admin-deleted npcs/locs across restarts.
 *
 * Spawn commands that run with `Int.MAX_VALUE` duration ("permanent") are recorded here and
 * re-applied on server startup by [CustomSpawnScript]. Deletes record a suppression entry so
 * map/cached spawns of the same entity at the same coordinate stay gone.
 */
@Singleton
public class CustomSpawnRegistry @Inject constructor() {
    private val mapper = ObjectMapper()
    private val file: Path = RuntimePaths.data.resolve("custom-spawns.json")

    public data class NpcEntry(val id: Int, val coords: Int)

    public data class LocEntry(val id: Int, val coords: Int, val shape: Int, val angle: Int)

    private val npcSpawns = LinkedHashSet<NpcEntry>()
    private val locSpawns = LinkedHashSet<LocEntry>()
    private val npcDeletes = LinkedHashSet<NpcEntry>()
    private val locDeletes = LinkedHashSet<LocEntry>()

    @Volatile private var loaded = false

    @Synchronized
    public fun load() {
        if (loaded) {
            return
        }
        loaded = true
        if (!file.exists()) {
            return
        }
        try {
            val root = mapper.readTree(file.toFile())
            root.path("npcs").forEach { node ->
                npcSpawns += NpcEntry(node.path("id").asInt(), node.path("coords").asInt())
            }
            root.path("locs").forEach { node ->
                locSpawns +=
                    LocEntry(
                        id = node.path("id").asInt(),
                        coords = node.path("coords").asInt(),
                        shape = node.path("shape").asInt(),
                        angle = node.path("angle").asInt(),
                    )
            }
            root.path("deletedNpcs").forEach { node ->
                npcDeletes += NpcEntry(node.path("id").asInt(), node.path("coords").asInt())
            }
            root.path("deletedLocs").forEach { node ->
                locDeletes +=
                    LocEntry(
                        id = node.path("id").asInt(),
                        coords = node.path("coords").asInt(),
                        shape = node.path("shape").asInt(),
                        angle = node.path("angle").asInt(),
                    )
            }
            logger.info {
                "Loaded custom spawn state: npcs=${npcSpawns.size}, locs=${locSpawns.size}, " +
                    "deletedNpcs=${npcDeletes.size}, deletedLocs=${locDeletes.size}"
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load custom spawn state from $file" }
        }
    }

    public fun npcSpawns(): List<NpcEntry> = synchronized(this) { npcSpawns.toList() }

    public fun locSpawns(): List<LocEntry> = synchronized(this) { locSpawns.toList() }

    public fun locDeletes(): List<LocEntry> = synchronized(this) { locDeletes.toList() }

    public fun isNpcDeleted(id: Int, coords: CoordGrid): Boolean =
        synchronized(this) { NpcEntry(id, coords.packed) in npcDeletes }

    /** Lifts a suppression entry without recording a spawn row (map npc restored). */
    public fun liftNpcDelete(id: Int, coords: CoordGrid) {
        synchronized(this) {
            if (npcDeletes.remove(NpcEntry(id, coords.packed))) {
                save()
            }
        }
    }

    /** Records a permanent npc spawn; also lifts a matching suppression entry if present. */
    public fun recordNpcSpawn(id: Int, coords: CoordGrid) {
        synchronized(this) {
            val entry = NpcEntry(id, coords.packed)
            npcDeletes -= entry
            npcSpawns += entry
            save()
        }
    }

    /**
     * Records a permanent npc deletion. If the npc was a recorded custom spawn the spawn row is
     * simply removed; otherwise a suppression entry is added so map spawns stay gone.
     */
    public fun recordNpcDelete(id: Int, coords: CoordGrid) {
        synchronized(this) {
            val entry = NpcEntry(id, coords.packed)
            if (!npcSpawns.remove(entry)) {
                npcDeletes += entry
            }
            save()
        }
    }

    public fun recordLocSpawn(id: Int, coords: CoordGrid, shape: Int, angle: Int) {
        synchronized(this) {
            val entry = LocEntry(id, coords.packed, shape, angle)
            locDeletes -= entry
            locSpawns += entry
            save()
        }
    }

    public fun recordLocDelete(id: Int, coords: CoordGrid, shape: Int, angle: Int) {
        synchronized(this) {
            val entry = LocEntry(id, coords.packed, shape, angle)
            if (!locSpawns.remove(entry)) {
                locDeletes += entry
            }
            save()
        }
    }

    private fun save() {
        val root = mapper.createObjectNode()
        root.putArray("npcs").addNpcEntries(npcSpawns)
        root.putArray("locs").addLocEntries(locSpawns)
        root.putArray("deletedNpcs").addNpcEntries(npcDeletes)
        root.putArray("deletedLocs").addLocEntries(locDeletes)
        try {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save custom spawn state to $file" }
        }
    }

    private fun ArrayNode.addNpcEntries(entries: Collection<NpcEntry>) {
        for (entry in entries) {
            addObject().put("id", entry.id).put("coords", entry.coords)
        }
    }

    private fun ArrayNode.addLocEntries(entries: Collection<LocEntry>) {
        for (entry in entries) {
            addObject()
                .put("id", entry.id)
                .put("coords", entry.coords)
                .put("shape", entry.shape)
                .put("angle", entry.angle)
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
