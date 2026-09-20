package org.rsmod.content.skills.farming

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varp.VarpReferences

internal enum class PatchKind {
    ALLOTMENT,
    HERB,
}

internal data class Crop(
    val seed: Int,
    val product: Int,
    val level: Int,
    val plantXp: Double,
    val harvestXp: Double,
    val minutes: Int,
    val kind: PatchKind,
    val stageStart: Int,
    val stages: Int = 4,
) {
    val seedCount: Int
        get() = if (kind == PatchKind.ALLOTMENT) 3 else 1

    fun stage(planted: Int, now: Int): Int =
        ((now.toLong() - planted).coerceAtLeast(0) * stages / minutes).toInt().coerceIn(0, stages)

    fun visual(planted: Int, now: Int): Int = stageStart + stage(planted, now)
}

internal object FarmingData {
    val patches: Map<Int, PatchKind> = buildMap {
        for (id in
            (8550..8557).toList() +
                listOf(21950, 27113, 27114, 33693, 33694, 34921, 34922, 50695, 50696)) put(
            id,
            PatchKind.ALLOTMENT,
        )
        for (id in listOf(8150, 8151, 8152, 8153, 9372, 27115, 33979, 50697)) put(
            id,
            PatchKind.HERB,
        )
    }
    val crops =
        listOf(
            Crop(5318, 1942, 1, 8.0, 9.0, 40, PatchKind.ALLOTMENT, 6),
            Crop(5319, 1957, 5, 9.5, 10.5, 40, PatchKind.ALLOTMENT, 13),
            Crop(5324, 1965, 7, 10.0, 11.5, 40, PatchKind.ALLOTMENT, 20),
            Crop(5322, 1982, 12, 12.5, 14.0, 40, PatchKind.ALLOTMENT, 27),
            Crop(5320, 5986, 20, 17.0, 19.0, 60, PatchKind.ALLOTMENT, 34, 6),
            Crop(5323, 5504, 31, 26.0, 29.0, 60, PatchKind.ALLOTMENT, 43, 6),
            Crop(5321, 5982, 47, 48.5, 54.5, 80, PatchKind.ALLOTMENT, 52, 8),
            Crop(5291, 199, 9, 11.0, 12.5, 80, PatchKind.HERB, 4),
            Crop(5292, 201, 14, 13.5, 15.0, 80, PatchKind.HERB, 11),
            Crop(5293, 203, 19, 16.0, 18.0, 80, PatchKind.HERB, 18),
            Crop(5294, 205, 26, 21.5, 24.0, 80, PatchKind.HERB, 25),
            Crop(5295, 207, 32, 27.0, 30.5, 80, PatchKind.HERB, 32),
            Crop(5296, 3049, 38, 34.0, 38.5, 80, PatchKind.HERB, 39),
            Crop(5297, 209, 44, 43.0, 48.5, 80, PatchKind.HERB, 46),
            Crop(5298, 211, 50, 54.5, 61.5, 80, PatchKind.HERB, 53),
            Crop(5299, 213, 56, 69.0, 78.0, 80, PatchKind.HERB, 60),
            Crop(5300, 3051, 62, 87.5, 98.5, 80, PatchKind.HERB, 67),
            Crop(5301, 215, 67, 106.0, 119.5, 80, PatchKind.HERB, 74),
            Crop(5302, 2485, 73, 134.5, 151.5, 80, PatchKind.HERB, 81),
            Crop(5303, 217, 79, 170.5, 192.0, 80, PatchKind.HERB, 88),
            Crop(5304, 219, 85, 199.5, 224.5, 80, PatchKind.HERB, 95),
        )
    val bySeed = crops.associateBy { it.seed }

    fun nowMinutes(): Int = (System.currentTimeMillis() / 60_000).toInt()
}

internal object FarmingVars : VarpReferences() {
    val seed = FarmingData.patches.keys.associateWith { find("gathering_farm_${it}_seed") }
    val planted = FarmingData.patches.keys.associateWith { find("gathering_farm_${it}_planted") }
    val remaining =
        FarmingData.patches.keys.associateWith { find("gathering_farm_${it}_remaining") }
    val compost = FarmingData.patches.keys.associateWith { find("gathering_farm_${it}_compost") }
}

internal object FarmingVarBuilds : VarpBuilder() {
    init {
        for (id in FarmingData.patches.keys) for (field in
            listOf("seed", "planted", "remaining", "compost")) {
            build("gathering_farm_${id}_$field") {
                permanent = true
                transmitNever = true
            }
        }
    }
}

internal object FarmingTimers : TimerReferences() {
    val growth = find("gathering_farm_growth")
}
