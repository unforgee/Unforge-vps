package org.rsmod.api.utils.zone

import io.netty.buffer.ByteBuf
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import jakarta.inject.Inject
import java.util.EnumMap
import net.rsprot.protocol.api.util.ZonePartialEnclosedCacheBuffer
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.map.zone.ZoneKey

public class SharedZoneEnclosedBuffers
@Inject
constructor(
    private val zoneUpdates: ZoneUpdateMap,
    private val enclosedCache: ZonePartialEnclosedCacheBuffer,
) {
    public val buffers: Int2ObjectMap<EnumMap<OldSchoolClientType, ByteBuf>> =
        Int2ObjectOpenHashMap()

    public fun computeSharedBuffers() {
        val activeZones = zoneUpdates.updatedZones
        for ((zone, updates) in activeZones.int2ObjectEntrySet()) {
            val protList = ZoneUpdateTransformer.collectEnclosedProtList(updates)
            // Some zone prots vary per observer, such as any privately-owned obj `ObjAdd` updates,
            // so they cannot go in the buffer shared between every observer of the zone. Those are
            // encoded per-player instead, by `PlayerZoneUpdateProcessor`.
            val filtered = protList.filterNot { it is ZoneUpdateTransformer.PerObserverZoneProt }
            if (filtered.isEmpty()) {
                continue
            }
            buffers[zone] = enclosedCache.computeZone(filtered)
        }
    }

    /** Encodes an ad-hoc zone prot list (e.g. persistent loc/obj rebuild after FullFollows). */
    public fun encodeZone(prots: Collection<ZoneProt>): EnumMap<OldSchoolClientType, ByteBuf>? {
        if (prots.isEmpty()) {
            return null
        }
        return enclosedCache.computeZone(prots)
    }

    public fun clear() {
        buffers.clear()
        enclosedCache.releaseBuffers()
    }

    public operator fun get(zone: ZoneKey): EnumMap<OldSchoolClientType, ByteBuf>? =
        buffers.getOrDefault(zone.packed, null)
}
