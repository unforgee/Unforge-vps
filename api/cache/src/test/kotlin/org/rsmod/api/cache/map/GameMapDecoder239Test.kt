package org.rsmod.api.cache.map

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.openrs2.buffer.use
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.loc.MapLocListDecoder
import org.rsmod.api.cache.map.tile.MapTileDecoder
import org.rsmod.api.cache.util.toInlineBuf
import org.rsmod.map.square.MapSquareKey

class GameMapDecoder239Test {
    @Test
    fun `decode Lumbridge mapsquare from openrs2 239 extract without XTEA`() {
        val cachePath = resolveOpenRs2239Cache()
        assumeTrue(cachePath.exists()) { "Missing local 239 cache extract at $cachePath" }

        val lumbridge = MapSquareKey(x = 50, z = 50)
        Cache.open(cachePath).use { cache ->
            assertTrue(cache.exists(Js5Archives.MAPS, lumbridge.id)) {
                "Expected mapsquare group ${lumbridge.id} (50_50) in MAPS archive"
            }

            val mapBytes =
                cache.read(Js5Archives.MAPS, lumbridge.id, MapGroupFiles.TILES).use {
                    it.toInlineBuf()
                }
            val locBytes =
                cache.read(Js5Archives.MAPS, lumbridge.id, MapGroupFiles.LOCS).use {
                    it.toInlineBuf()
                }

            val tiles = MapTileDecoder.decode(mapBytes)
            val locs = MapLocListDecoder.decode(locBytes)

            var nonEmptyTiles = 0
            for (level in 0 until 4) {
                for (x in 0 until 64) {
                    for (z in 0 until 64) {
                        if (tiles[x, z, level].toInt() != 0) {
                            nonEmptyTiles++
                        }
                    }
                }
            }

            assertTrue(nonEmptyTiles > 0) { "Expected non-empty tile flags for Lumbridge" }
            assertTrue(locs.spawns.isNotEmpty()) { "Expected non-empty loc list for Lumbridge" }
        }
    }

    private fun resolveOpenRs2239Cache(): Path {
        val moduleDir = Path.of("").toAbsolutePath()
        // api/cache -> rsmod -> workspace .data
        return moduleDir.resolve("../../../.data/openrs2-239/cache-extract/cache").normalize()
    }
}
