package org.rsmod.api.cache.map.tile

import io.netty.buffer.Unpooled
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.MapGroupFiles
import org.rsmod.map.square.MapSquareKey

/*
 * Currently, we do not decode and re-encode map tile bytes before packing. While consistency with
 * other codecs is typically preferred, map files are large and do not require decoding unless
 * merging multiple files - a step not permitted for tile files.
 *
 * That is why this encoder accepts a raw byte data wrapper from the file instead of
 * `MapTileDefinition`.
 */
public object MapTileByteEncoder {
    public fun encodeAll(cache: Cache, definitions: Map<MapSquareKey, MapTileByteDefinition>) {
        val archive = Js5Archives.MAPS
        for ((key, definition) in definitions) {
            val newBuf = Unpooled.wrappedBuffer(definition.data)
            cache.write(archive, key.id, MapGroupFiles.TILES, newBuf)
        }
    }
}
