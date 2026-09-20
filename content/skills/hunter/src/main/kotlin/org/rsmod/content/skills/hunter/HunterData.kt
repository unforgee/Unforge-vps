package org.rsmod.content.skills.hunter

import org.rsmod.api.type.refs.timer.TimerReferences

internal data class Quarry(
    val name: String,
    val level: Int,
    val xp: Double,
    val product: Int,
    val tool: Int,
    val fullTrap: Int = 0,
    val jar: Int? = null,
    val respawn: Int = 50,
) {
    fun chance(level: Int): Int =
        if (level < this.level) 0 else (90 + (level - this.level) * 3).coerceAtMost(240)
}

internal object HunterData {
    val quarry =
        listOf(
            Quarry("crimson swift", 1, 34.0, 10088, 10006, 9373),
            Quarry("golden warbler", 5, 47.0, 10090, 10006, 9377),
            Quarry("copper longtail", 9, 61.0, 10091, 10006, 9379),
            Quarry("cerulean twitch", 11, 64.5, 10089, 10006, 9375),
            Quarry("tropical wagtail", 19, 95.0, 10087, 10006, 9348),
            Quarry("chinchompa", 53, 198.4, 10033, 10008, 9382),
            Quarry("carnivorous chinchompa", 63, 265.0, 10034, 10008, 9383),
            Quarry("black chinchompa", 73, 315.0, 11959, 10008, 721),
            Quarry("ruby harvest", 15, 24.0, 10020, 10010, jar = 10012),
            Quarry("sapphire glacialis", 25, 34.0, 10018, 10010, jar = 10012),
            Quarry("snowy knight", 35, 44.0, 10016, 10010, jar = 10012),
            Quarry("black warlock", 45, 54.0, 10014, 10010, jar = 10012),
            Quarry("baby impling", 17, 20.0, 11238, 10010, jar = 11260),
            Quarry("young impling", 22, 22.0, 11240, 10010, jar = 11260),
            Quarry("gourmet impling", 28, 22.0, 11242, 10010, jar = 11260),
            Quarry("earth impling", 36, 25.0, 11244, 10010, jar = 11260),
            Quarry("essence impling", 42, 27.0, 11246, 10010, jar = 11260),
            Quarry("eclectic impling", 50, 30.0, 11248, 10010, jar = 11260),
            Quarry("nature impling", 58, 34.0, 11250, 10010, jar = 11260),
            Quarry("magpie impling", 65, 44.0, 11252, 10010, jar = 11260),
            Quarry("ninja impling", 74, 50.0, 11254, 10010, jar = 11260),
            Quarry("dragon impling", 83, 65.0, 11256, 10010, jar = 11260),
        )
    val byName = quarry.associateBy { it.name }

    fun trapLimit(level: Int): Int = (1 + level / 20).coerceIn(1, 5)

    fun emptyTrap(tool: Int): Int = if (tool == 10006) 9345 else 9380

    fun failedTrap(tool: Int): Int = if (tool == 10006) 9344 else 9385
}

internal object HunterTimers : TimerReferences() {
    val traps = find("gathering_hunter_traps")
}
