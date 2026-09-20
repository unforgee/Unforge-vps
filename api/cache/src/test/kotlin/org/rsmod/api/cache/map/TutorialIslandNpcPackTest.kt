package org.rsmod.api.cache.map

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.openrs2.buffer.use
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.npc.MapNpcListDecoder
import org.rsmod.api.cache.util.readOrNull
import org.rsmod.api.cache.util.toInlineBuf
import org.rsmod.map.square.MapSquareKey

/** Verifies Tutorial Island NPC spawns were packed into server-only mapsquare file 100. */
class TutorialIslandNpcPackTest {
    @Test
    fun `tutorial island mapsquares have packed npc spawns`() {
        val cachePath = resolveGameCache()
        assumeTrue(cachePath.exists()) { "Missing game cache at $cachePath" }

        val surface = MapSquareKey(x = 26, z = 95)
        val dungeon = MapSquareKey(x = 26, z = 195)
        val magic = MapSquareKey(x = 27, z = 95)

        Cache.open(cachePath).use { cache ->
            fun npcCount(key: MapSquareKey): Int {
                val buf = cache.readOrNull(Js5Archives.MAPS, key.id, MapGroupFiles.NPCS)
                requireNotNull(buf) { "Missing NPC file 100 for mapsquare ${key.x}_${key.z}" }
                return buf.use { MapNpcListDecoder.decode(it.toInlineBuf()).packedSpawns.size }
            }

            val surfaceCount = npcCount(surface)
            val dungeonCount = npcCount(dungeon)
            val magicCount = npcCount(magic)

            // Surface: Guide, Survival, 3 fishing spots, Chef, Quest, Bank, Prayer, Ironman = 10+
            assertTrue(surfaceCount >= 10) {
                "Expected packed surface tutors/spots on 26_95, got $surfaceCount"
            }
            // Dungeon: Mining, Combat, 5 rats = 7
            assertTrue(dungeonCount >= 7) {
                "Expected mining/combat NPCs on 26_195, got $dungeonCount"
            }
            // Magic pen: Terrova + 3 chickens = 4
            assertTrue(magicCount >= 4) {
                "Expected magic tutor/chickens on 27_95, got $magicCount"
            }
        }
    }

    private fun resolveGameCache(): Path {
        val moduleDir = Path.of("").toAbsolutePath()
        // api/cache -> rsmod/.data/cache/game
        return moduleDir.resolve("../../.data/cache/game").normalize()
    }
}
