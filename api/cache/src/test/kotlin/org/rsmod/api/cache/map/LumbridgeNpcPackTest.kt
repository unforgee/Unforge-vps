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

/** Guards the production Lumbridge spawn pack used by the local r239 server. */
class LumbridgeNpcPackTest {
    @Test
    fun `Lumbridge mapsquare has server npc spawns`() {
        val cachePath = resolveGameCache()
        assumeTrue(cachePath.exists()) { "Missing game cache at $cachePath" }

        val lumbridge = MapSquareKey(x = 50, z = 50)
        Cache.open(cachePath).use { cache ->
            val bytes = cache.readOrNull(Js5Archives.MAPS, lumbridge.id, MapGroupFiles.NPCS)
            requireNotNull(bytes) {
                "Missing server-only NPC file ${MapGroupFiles.NPCS} for mapsquare 50_50"
            }
            val count = bytes.use { MapNpcListDecoder.decode(it.toInlineBuf()).packedSpawns.size }
            assertTrue(count > 0) {
                "Expected packed Lumbridge NPC spawns in mapsquare 50_50, got $count"
            }
        }
    }

    private fun resolveGameCache(): Path {
        val moduleDir = Path.of("").toAbsolutePath()
        // api/cache -> rsmod/.data/cache/game
        return moduleDir.resolve("../../.data/cache/game").normalize()
    }
}
