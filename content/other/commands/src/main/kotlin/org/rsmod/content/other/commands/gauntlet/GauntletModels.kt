package org.rsmod.content.other.commands.gauntlet

/** Configuration and immutable state for one Gauntlet Arena run. */
public object GauntletConfig {
    public const val MIN_COMBAT_LEVEL: Int = 15
    public const val COOLDOWN_CYCLES: Int = 5 * 60 * 10
    public const val MAX_COMPANIONS: Int = 4
    public const val COMPANION_XP_PER_WAVE: Int = 100
    public const val REWARD_ITEM_ID: Int = 995
    public const val REWARD_ITEM_AMOUNT: Int = 25_000
    public val WAVE_POINTS: IntArray = intArrayOf(10, 15, 20, 30, 50)
}

public enum class GauntletWave(public val npcIds: IntArray) {
    GOBLINS(intArrayOf(1010, 1010, 1010)),
    SKELETONS(intArrayOf(70, 70, 70, 70)),
    DARK_WIZARDS(intArrayOf(172, 172, 172, 172)),
    GREATER_DEMON(intArrayOf(2025, 2025)),
    CHAMPION(intArrayOf(3994)),
}

public data class GauntletRun(
    public val playerId: Int,
    public var wave: Int = 0,
    public var points: Int = 0,
    public var activeNpcs: Int = 0,
    public var rewardGranted: Boolean = false,
) {
    public val complete: Boolean
        get() = wave >= GauntletWave.entries.size && activeNpcs == 0

    public fun beginWave(): GauntletWave? {
        if (wave >= GauntletWave.entries.size) return null
        activeNpcs = GauntletWave.entries[wave].npcIds.size
        return GauntletWave.entries[wave]
    }

    public fun registerKill(): Boolean {
        if (activeNpcs <= 0) return false
        activeNpcs--
        return activeNpcs == 0
    }

    public fun finishWave(): Boolean {
        if (activeNpcs != 0 || wave >= GauntletConfig.WAVE_POINTS.size) return false
        points += GauntletConfig.WAVE_POINTS[wave]
        wave++
        return true
    }
}
