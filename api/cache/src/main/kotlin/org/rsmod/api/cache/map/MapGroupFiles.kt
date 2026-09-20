package org.rsmod.api.cache.map

/**
 * File ids within an unnamed mapsquare group (archive [org.rsmod.api.cache.Js5Archives.MAPS]).
 *
 * As of OSRS 237+, each mapsquare is a single group whose id equals
 * [org.rsmod.map.square.MapSquareKey.id]. Vanilla clients use files 0–1 (and keep proprietary files
 * 2–4). Server-only spawn/area packs use high ids so packing the game cache does not overwrite
 * client data.
 */
public object MapGroupFiles {
    public const val TILES: Int = 0
    public const val LOCS: Int = 1

    /** Server-only npc spawn list (former named `n{x}_{z}` group). */
    public const val NPCS: Int = 100

    /** Server-only obj spawn list (former named `o{x}_{z}` group). */
    public const val OBJS: Int = 101

    /** Server-only area list (former named `a{x}_{z}` group). */
    public const val AREAS: Int = 102
}
