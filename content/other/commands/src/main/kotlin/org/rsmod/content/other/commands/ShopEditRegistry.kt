package org.rsmod.content.other.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import org.rsmod.module.RuntimePaths

/**
 * Persists in-game shop stock edits across restarts.
 *
 * Keyed by shop inventory type id, then slot. A `null` slot value means "cleared" (the slot is
 * emptied even if the type's default stock defines it). Entries are re-applied every time the shop
 * is opened, via [org.rsmod.api.shops.ShopEvents.Open].
 */
@Singleton
public class ShopEditRegistry @Inject constructor() {
    private val mapper = ObjectMapper()
    private val file: Path = RuntimePaths.data.resolve("shop-edits.json")

    public data class SlotEdit(val id: Int, val count: Int)

    /** invTypeId -> (slot -> edit or null for cleared). */
    private val edits = sortedMapOf<Int, MutableMap<Int, SlotEdit?>>()

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
            root.path("shops").fields().forEach { (invTypeKey, slotsNode) ->
                val invType = invTypeKey.toIntOrNull() ?: return@forEach
                val slots = edits.getOrPut(invType) { sortedMapOf() }
                slotsNode.fields().forEach { (slotKey, slotNode) ->
                    val slot = slotKey.toIntOrNull() ?: return@forEach
                    slots[slot] =
                        if (slotNode.isNull) {
                            null
                        } else {
                            SlotEdit(slotNode.path("id").asInt(), slotNode.path("count").asInt(1))
                        }
                }
            }
            logger.info { "Loaded shop edits for ${edits.size} shop(s) from $file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load shop edits from $file" }
        }
    }

    /** Returns slot overrides for [invType], or null when the shop has no edits. */
    public fun editsFor(invType: Int): Map<Int, SlotEdit?>? =
        synchronized(this) { edits[invType]?.toMap() }

    /** Clears [slot] in [invType]'s shop permanently. */
    public fun recordSlotClear(invType: Int, slot: Int) {
        synchronized(this) {
            edits.getOrPut(invType) { sortedMapOf() }[slot] = null
            save()
        }
    }

    /** Sets [slot] in [invType]'s shop to the given obj permanently. */
    public fun recordSlotSet(invType: Int, slot: Int, objId: Int, count: Int) {
        synchronized(this) {
            edits.getOrPut(invType) { sortedMapOf() }[slot] = SlotEdit(objId, count)
            save()
        }
    }

    private fun save() {
        val root = mapper.createObjectNode()
        val shopsNode = root.putObject("shops")
        for ((invType, slots) in edits) {
            val slotsNode = shopsNode.putObject(invType.toString())
            for ((slot, edit) in slots) {
                if (edit == null) {
                    slotsNode.putNull(slot.toString())
                } else {
                    slotsNode.putObject(slot.toString()).put("id", edit.id).put("count", edit.count)
                }
            }
        }
        try {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save shop edits to $file" }
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
