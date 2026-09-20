package org.rsmod.api.cache.types.enums

import java.nio.file.Path
import kotlin.io.path.isDirectory
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.Js5Configs
import org.rsmod.api.cache.util.EncoderContext
import org.rsmod.game.type.literal.CacheVarLiteral

/**
 * CS2 2611 resolves `enum(submap, spellbook_sublist)`. Stock submaps 5281–5284 omit key 0 and rely
 * on `defaultInt` for the full book; clients that miss that default early-return with no icons.
 *
 * This patch writes explicit key 0 → full book (1982–1985) while preserving vanilla ENUM value
 * types and combat key 1 on standard. Must run after TypeUpdaterCacheSync (which restores js5 from
 * vanilla) so the entries survive pack cycles.
 */
public object SpellbookSublistEnumPatch {
    /** enumId → (fullBookEnumId, extraEntries) */
    private val patches =
        mapOf(
            5281 to (1982 to mapOf(1 to 5285)),
            5282 to (1983 to emptyMap()),
            5283 to (1984 to emptyMap()),
            5284 to (1985 to emptyMap()),
        )

    public fun apply(vararg cacheDirs: Path) {
        for (dir in cacheDirs) {
            if (!dir.isDirectory()) continue
            apply(dir)
        }
    }

    public fun apply(cacheDir: Path) {
        Cache.open(cacheDir).use { cache ->
            for ((enumId, spec) in patches) {
                val (fullBook, extras) = spec
                if (!cache.exists(Js5Archives.CONFIG, Js5Configs.ENUM, enumId)) {
                    continue
                }
                val oldBuf = cache.read(Js5Archives.CONFIG, Js5Configs.ENUM, enumId)
                val decoded =
                    try {
                        EnumTypeDecoder.decode(oldBuf).build(enumId)
                    } finally {
                        oldBuf.release()
                    } ?: continue
                val exact0 = decoded.primitiveMap[0] as? Int
                val valOk = decoded.valLiteral == CacheVarLiteral.ENUM
                val extrasOk = extras.all { (k, v) -> decoded.primitiveMap[k] == v }
                if (exact0 == fullBook && valOk && extrasOk && decoded.defaultInt == fullBook) {
                    continue
                }
                val map = LinkedHashMap<Any, Any?>()
                map[0] = fullBook
                for ((k, v) in extras) {
                    map[k] = v
                }
                for ((k, v) in decoded.primitiveMap) {
                    val key = k as? Int ?: continue
                    if (key == 0 || extras.containsKey(key)) continue
                    map[key] = v
                }
                val fixed =
                    decoded.copy(
                        keyLiteral = CacheVarLiteral.INT,
                        valLiteral = CacheVarLiteral.ENUM,
                        primitiveMap = map,
                        defaultInt = fullBook,
                        defaultStr = null,
                        default = null,
                        typedMap = emptyMap(),
                        transmit = true,
                    )
                EnumTypeEncoder.encodeAll(
                    cache,
                    listOf(fixed),
                    EncoderContext.server(emptySet(), emptySet(), emptySet()),
                )
            }
        }
    }
}
