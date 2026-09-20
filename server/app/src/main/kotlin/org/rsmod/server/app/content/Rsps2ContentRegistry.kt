package org.rsmod.server.app.content

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Shared server-owned r239 content index for UI, NPC, boss, shop and quest data. */
class Rsps2ContentRegistry(private val root: Path) {
    data class Record(val id: String, val type: String, val path: Path)

    private val allowed = setOf("ui", "npc", "boss", "shop", "quest")

    fun load(): List<Record> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.toString().endsWith(".json") }
                .toList()
                .mapNotNull { path -> record(path) }
        }
    }

    fun byType(records: List<Record>): Map<String, List<Record>> = records.groupBy { it.type }

    fun validate(records: List<Record>): List<String> = buildList {
        val ids = mutableSetOf<String>()
        records.forEach { r ->
            if (!allowed.contains(r.type)) add("unsupported content type: ${r.type}")
            if (!ids.add("${r.type}:${r.id}")) add("duplicate content: ${r.type}:${r.id}")
        }
    }

    fun publish(record: Record): Path {
        require(record.type in allowed) { "unsupported content type: ${record.type}" }
        val backup =
            record.path.resolveSibling(
                "${record.path.fileName}.bak-${Instant.now().toEpochMilli()}"
            )
        Files.copy(record.path, backup, StandardCopyOption.COPY_ATTRIBUTES)
        return backup
    }

    fun rollback(record: Record, backup: Path) {
        require(backup.normalize().parent == record.path.normalize().parent) {
            "rollback path must be adjacent to content"
        }
        Files.copy(backup, record.path, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun record(path: Path): Record? =
        try {
            val text = Files.readString(path)
            val id =
                Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
                    ?: return null
            val relative = root.relativize(path).toString().replace('\\', '/').lowercase()
            val type =
                when {
                    relative.contains("/interfaces/") || relative.startsWith("interfaces/") -> "ui"
                    relative.contains("/shops/") || relative.startsWith("shops/") -> "shop"
                    relative.contains("/quests/") || relative.startsWith("quests/") -> "quest"
                    relative.contains("/bosses/") || relative.startsWith("bosses/") -> "boss"
                    relative.contains("/npcs/") || relative.startsWith("npcs/") -> "npc"
                    path.parent.fileName.toString().equals("custom", ignoreCase = true) -> "ui"
                    else -> path.parent.fileName.toString().lowercase()
                }
            Record(id, type, path)
        } catch (_: Exception) {
            null
        }
}
