package org.rsmod.content.areas.unforge.raids

/** A cache map source and the chunk copied from it for one raid room. */
data class RaidRoomLayout(
    val sourceRegionX: Int,
    val sourceRegionZ: Int,
    val sourceZoneX: Int,
    val sourceZoneZ: Int,
    val sourceLevel: Int,
    val zoneWidth: Int,
    val zoneLength: Int,
    val rotation: Int = 0,
    val destinationLevel: Int = 0,
    val copyAllLevels: Boolean = false,
    val cacheVerified: Boolean = true,
) {
    val widthTiles: Int
        get() = zoneWidth * 8

    val lengthTiles: Int
        get() = zoneLength * 8

    init {
        require(rotation in 0..3)
        require(zoneWidth > 0 && zoneLength > 0)
    }
}

/** Room chunks discovered from map archive 5; entries are audited against loc-map bounds. */
object RaidMapLayouts {
    /** CoX variants with no room-specific catalogue entry in this raid implementation. */
    val unresolvedCoxVariants: Set<String> = emptySet()

    // The chunk origins are region origin + the observed loc bounding-box zone offset.
    private val tob =
        mapOf(
            "maiden" to RaidRoomLayout(46, 41, 46, 41, 0, 8, 7),
            "bloat" to RaidRoomLayout(51, 69, 51, 69, 0, 4, 8),
            "nylocas" to RaidRoomLayout(51, 66, 51, 66, 0, 6, 6),
            "sotetseg" to RaidRoomLayout(51, 67, 51, 67, 0, 4, 8),
            "xarpus" to RaidRoomLayout(49, 68, 49, 68, 0, 5, 8),
            "verzik" to RaidRoomLayout(49, 67, 49, 67, 0, 8, 8),
        )

    // CoX templates cross-reference RuneLite's InstanceTemplates.java and archive-5 loc maps.
    // Source dimensions are 96x32 tiles (12x4 zones) except the 64x32 Olm/end field.
    private val cox =
        mapOf(
            "olm" to RaidRoomLayout(51, 80, 51, 80, 0, 8, 4),
            "tekton" to RaidRoomLayout(51, 83, 51, 83, 1, 12, 4),
            "vespula" to RaidRoomLayout(51, 83, 51, 83, 2, 12, 4),
            "icedemon" to RaidRoomLayout(51, 84, 51, 84, 0, 12, 4),
            "tightrope" to RaidRoomLayout(51, 84, 51, 84, 1, 12, 4),
            "mystics" to RaidRoomLayout(51, 82, 51, 82, 1, 12, 4),
            "vanguards" to RaidRoomLayout(51, 83, 51, 83, 0, 12, 4),
            "muttadile" to RaidRoomLayout(51, 83, 51, 83, 1, 12, 4),
            "storage" to RaidRoomLayout(51, 89, 51, 89, 0, 12, 4),
            "default" to RaidRoomLayout(51, 89, 51, 89, 0, 12, 4, cacheVerified = false),
        )

    fun forRoom(kind: RaidKind, key: String): RaidRoomLayout? =
        when (kind) {
            RaidKind.THEATRE -> tob[key]
            RaidKind.CHAMBERS -> cox[key] ?: cox["default"]
        }

    fun forLobby(kind: RaidKind): RaidRoomLayout =
        when (kind) {
            RaidKind.THEATRE -> tob.getValue("maiden")
            RaidKind.CHAMBERS -> cox.getValue("storage")
        }

    fun forKey(key: String): RaidRoomLayout? =
        tob[key]
            ?: if (
                key in
                    setOf(
                        "olm",
                        "tekton",
                        "vespula",
                        "storage",
                        "vanguards",
                        "mystics",
                        "icedemon",
                        "tightrope",
                        "muttadile",
                    )
            ) {
                cox[key] ?: cox["default"]
            } else {
                null
            }
}
