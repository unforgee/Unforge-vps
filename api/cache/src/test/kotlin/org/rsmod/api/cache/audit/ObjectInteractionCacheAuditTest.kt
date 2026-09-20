package org.rsmod.api.cache.audit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ObjectInteractionCacheAuditTest {
    private val root = Path.of(System.getProperty("user.dir"))

    @Test
    fun `focused cache rows preserve identity transforms operations and geometry`() {
        val rows = readTsv(root.resolve("work/object-interaction-cache-inventory.tsv"))
        assertTrue(rows.isNotEmpty())
        val header = rows.first().keys
        assertTrue(
            setOf(
                    "id",
                    "ops",
                    "transformChain",
                    "multiLocDefault",
                    "multiVarBit",
                    "multiVarp",
                    "x",
                    "z",
                    "level",
                    "orientation",
                    "width",
                    "length",
                    "collision",
                )
                .all { it in header }
        )
        assertTrue(rows.drop(1).all { it["id"].orEmpty().toIntOrNull() != null })
    }

    @Test
    fun `source join uses deterministic and non optimistic classifications`() {
        val rows = readTsv(root.resolve("work/object-interaction-source-joined.tsv")).drop(1)
        val allowed =
            setOf(
                "VERIFIED",
                "MISSING_HANDLER",
                "WRONG_OBJECT_ID",
                "WRONG_OP",
                "WRONG_TRANSFORM",
                "WRONG_COORDINATE",
                "WRONG_LEVEL",
                "WRONG_ORIENTATION",
                "WRONG_FOOTPRINT",
                "WRONG_COLLISION",
                "WRONG_ANIMATION",
                "WRONG_DESTINATION",
                "MISSING_REVERSE_PATH",
                "DUPLICATE_HANDLER",
                "CACHE_ONLY",
                "SOURCE_ONLY",
                "UNVERIFIED_INSTANCE",
            )
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it["classification"] in allowed })
        assertTrue(
            rows
                .filter { it["classification"] == "UNVERIFIED_INSTANCE" }
                .all { it["runtimeHandler"].orEmpty().contains("LocInteractions") }
        )
    }

    @Test
    fun `instance records cannot become verified without remapping evidence`() {
        val rows = readTsv(root.resolve("work/object-interaction-source-joined.tsv")).drop(1)
        assertTrue(
            rows
                .filter { it["classification"] == "UNVERIFIED_INSTANCE" }
                .none { it["reason"].orEmpty().contains("verified", ignoreCase = true) }
        )
    }

    @Test
    fun `source symbol IDs come from loc sym`() {
        val symbols =
            Files.readAllLines(root.resolve(".data/symbols/loc.sym"))
                .mapNotNull { it.substringBefore('\t').toIntOrNull() }
                .toSet()
        val rows = readTsv(root.resolve("work/object-interaction-source-inventory.tsv"))
        assertTrue(
            rows
                .filter { it["kind"] == "REFERENCE" && it["id"].orEmpty().isNotBlank() }
                .all { it["id"]!!.toInt() in symbols }
        )
    }

    private fun readTsv(path: Path): List<Map<String, String>> {
        val lines = Files.readAllLines(path).filterNot { it.startsWith("#") }
        val header = lines.first().split('\t').map { it.trim('"') }
        return lines.drop(1).map { line ->
            val values = line.split('\t').map { it.trim('"') }
            header.indices.associate { index -> header[index] to values.getOrElse(index) { "" } }
        }
    }
}
