package org.rsmod.content.areas.unforge.drops

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcDropTables.DropItem
import org.rsmod.api.death.NpcDropTables.DropTable
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Loads the generated Kronos drop tables (`unforge_drops.txt`) into [NpcDropTables]. Npcs
 * without a table keep the default bones drop.
 */
class UnforgeDrops @Inject constructor(private val dropTables: NpcDropTables) : PluginScript() {

    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        val res = "/org/rsmod/content/areas/unforge/unforge_drops.txt"
        val stream = javaClass.getResourceAsStream(res)
        if (stream == null) {
            logger.warn { "Unforge drop table resource missing: $res" }
            return
        }
        var npcId = -1
        var npcs = 0
        var guaranteed = mutableListOf<DropItem>()
        var tables = mutableListOf<DropTable>()
        var tableName = ""
        var tableWeight = 1
        var tableItems = mutableListOf<DropItem>()

        fun flushTable() {
            if (tableItems.isNotEmpty()) {
                tables += DropTable(tableName, tableWeight, tableItems)
            }
            tableItems = mutableListOf()
        }

        fun flushNpc() {
            flushTable()
            if (npcId >= 0 && (guaranteed.isNotEmpty() || tables.isNotEmpty())) {
                dropTables.register(npcId, guaranteed, tables)
                npcs++
            }
            guaranteed = mutableListOf()
            tables = mutableListOf()
        }

        stream.bufferedReader().useLines { seq ->
            for (raw in seq) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split(" ", limit = 5)
                when (p[0]) {
                    "npc" -> {
                        flushNpc()
                        npcId = p[1].toInt()
                    }
                    "g" -> guaranteed += DropItem(p[1].toInt(), p[2].toInt(), p[3].toInt(), 1)
                    "t" -> {
                        flushTable()
                        tableWeight = p[1].toInt()
                        tableName = p.getOrElse(2) { "table" }
                    }
                    "i" ->
                        tableItems +=
                            DropItem(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
                    "end" -> {
                        flushNpc()
                        npcId = -1
                    }
                }
            }
        }
        flushNpc()
        logger.info { "Unforge drop tables registered for $npcs npc types." }
    }
}
