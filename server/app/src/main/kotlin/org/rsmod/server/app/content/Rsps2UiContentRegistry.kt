package org.rsmod.server.app.content

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Server-owned r239 UI definitions. No Lua and no client-cache writes. */
class Rsps2UiContentRegistry(private val root: Path) {
    data class Element(
        val id: String,
        val kind: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val text: String = "",
        val action: String = "",
        val image: String = "",
    )

    data class Definition(
        val id: String,
        val group: String,
        val version: Int = 1,
        val status: String = "draft",
        val width: Int = 512,
        val height: Int = 334,
        val elements: List<Element> = emptyList(),
        val updatedAt: String = Instant.now().toString(),
    )

    fun validate(definition: Definition): List<String> = buildList {
        if (!definition.id.matches(Regex("[A-Za-z0-9._-]+"))) add("invalid interface id")
        if (!definition.group.matches(Regex("[A-Za-z0-9._-]+"))) add("invalid interface group")
        if (definition.width !in 1..4096 || definition.height !in 1..4096)
            add("invalid canvas size")
        if (definition.status !in setOf("draft", "test", "live")) add("invalid status")
        definition.elements.forEach { e ->
            if (e.id.isBlank()) add("element id missing")
            if (e.width < 1 || e.height < 1) add("invalid element size: ${e.id}")
        }
    }

    fun save(definition: Definition) {
        require(validate(definition).isEmpty()) { validate(definition).joinToString(", ") }
        val target =
            root
                .resolve("ui-builder/interfaces/${definition.group}/${definition.id}.json")
                .normalize()
        require(target.startsWith(root.normalize()))
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(".${target.fileName}.tmp")
        Files.writeString(tmp, json(definition))
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun loadAll(): List<Definition> {
        val dir = root.resolve("ui-builder/interfaces")
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.walk(dir).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.toList().mapNotNull { read(it) }
        }
    }

    private fun read(path: Path): Definition? =
        try {
            val text = Files.readString(path)
            val id =
                Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
                    ?: return null
            val group =
                Regex("\\\"group\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
                    ?: "custom"
            Definition(id, group)
        } catch (_: Exception) {
            null
        }

    private fun json(d: Definition): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine(
            "  \"id\": \"${d.id}\", \"group\": \"${d.group}\", \"version\": ${d.version}, \"status\": \"${d.status}\","
        )
        appendLine("  \"width\": ${d.width}, \"height\": ${d.height},")
        appendLine("  \"elements\": [")
        d.elements.forEachIndexed { i, e ->
            append(
                    "    {\"id\":\"${e.id}\",\"kind\":\"${e.kind}\",\"x\":${e.x},\"y\":${e.y},\"width\":${e.width},\"height\":${e.height},\"text\":\"${e.text}\",\"action\":\"${e.action}\",\"image\":\"${e.image}\"}"
                )
                .appendLine(if (i == d.elements.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }
}
