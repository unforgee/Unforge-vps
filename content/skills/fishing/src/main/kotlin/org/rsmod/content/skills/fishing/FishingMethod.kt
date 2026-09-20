package org.rsmod.content.skills.fishing

internal data class FishCatch(val item: Int, val level: Int, val xp: Double) {
    // Standalone balancing: 25% at unlock, increasing with level to a bounded 90%.
    fun chance(level: Int): Int =
        if (level < this.level) 0 else (64 + (level - this.level) * 3).coerceAtMost(230)
}

internal enum class FishingMethod(
    val tool: Int,
    val bait: Int?,
    val animation: Int,
    val catches: List<FishCatch>,
) {
    SMALL_NET(303, null, 621, listOf(FishCatch(317, 1, 10.0), FishCatch(321, 15, 40.0))),
    SEA_BAIT(307, 313, 622, listOf(FishCatch(327, 5, 20.0), FishCatch(345, 10, 30.0))),
    RIVER_BAIT(307, 313, 622, listOf(FishCatch(349, 25, 60.0))),
    LURE(309, 314, 622, listOf(FishCatch(335, 20, 50.0), FishCatch(331, 30, 70.0))),
    CAGE(301, null, 619, listOf(FishCatch(377, 40, 90.0))),
    HARPOON(311, null, 618, listOf(FishCatch(359, 35, 80.0), FishCatch(371, 50, 100.0))),
    BIG_NET(
        305,
        null,
        620,
        listOf(FishCatch(353, 16, 20.0), FishCatch(341, 23, 45.0), FishCatch(363, 46, 100.0)),
    ),
    SHARK(311, null, 618, listOf(FishCatch(383, 76, 110.0))),
    MONKFISH(303, null, 621, listOf(FishCatch(7944, 62, 120.0)));

    companion object {
        fun forSpot(symbol: String): Pair<FishingMethod, FishingMethod?>? =
            when {
                symbol == "freshfish" || symbol.endsWith("_freshfish") -> LURE to RIVER_BAIT
                symbol == "saltfish" || symbol.endsWith("_saltfish") -> SMALL_NET to SEA_BAIT
                symbol == "rarefish" || symbol.endsWith("_rarefish") -> CAGE to HARPOON
                symbol == "memberfish" || symbol.endsWith("_memberfish") -> BIG_NET to SHARK
                symbol == "swan_fishingspot" -> MONKFISH to null
                else -> null
            }
    }
}
