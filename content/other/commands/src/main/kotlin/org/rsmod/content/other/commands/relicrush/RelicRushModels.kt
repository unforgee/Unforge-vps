package org.rsmod.content.other.commands.relicrush

public object RelicRushConfig {
    public const val MIN_COMBAT_LEVEL: Int = 15
    public const val COOLDOWN_CYCLES: Int = 5 * 60 * 10
    public const val STARTING_RELIC_HEALTH: Int = 1_000
    public const val MAX_PILLAR_ENERGY: Int = 100
    public const val PERFECT_HEALTH_PERCENT: Int = 90
    public const val STABLE_HEALTH_PERCENT: Int = 40
    public const val PARTICIPATION_REWARD_ID: Int = 995
    public const val PARTICIPATION_REWARD_AMOUNT: Int = 1_000
    public const val STABLE_REWARD_AMOUNT: Int = 5_000
    public const val PERFECT_REWARD_AMOUNT: Int = 15_000
    public val WAVE_POINTS: IntArray = intArrayOf(10, 20, 35, 50, 75)
}

public enum class RelicRushRewardTier {
    PARTICIPATION,
    STABLE,
    PERFECT,
}

public enum class RelicRushWave(public val npcIds: IntArray) {
    GOBLINS(intArrayOf(1010, 1010, 1010)),
    SKELETONS(intArrayOf(70, 70, 70, 70)),
    DARK_WIZARDS(intArrayOf(172, 172, 172, 172)),
    GREATER_DEMONS(intArrayOf(2025, 2025)),
    ELITES(intArrayOf(2025, 172, 70, 1010)),
}

public data class RelicRushRun(
    public val playerId: Int,
    public var wave: Int = 0,
    public var points: Int = 0,
    public var relicHealth: Int = RelicRushConfig.STARTING_RELIC_HEALTH,
    public var pillarEnergy: Int = 0,
    public var activeNpcs: Int = 0,
    public var bossAlive: Boolean = false,
    public var rewardGranted: Boolean = false,
) {
    public fun beginWave(): RelicRushWave? {
        if (wave >= RelicRushWave.entries.size) return null
        activeNpcs = RelicRushWave.entries[wave].npcIds.size
        return RelicRushWave.entries[wave]
    }

    public fun registerKill(): Boolean {
        if (activeNpcs <= 0) return false
        activeNpcs--
        return activeNpcs == 0
    }

    public fun finishWave(): Boolean {
        if (activeNpcs != 0 || wave >= RelicRushConfig.WAVE_POINTS.size) return false
        points += RelicRushConfig.WAVE_POINTS[wave]
        wave++
        return true
    }

    public fun collectEnergy(): Boolean {
        if (pillarEnergy >= RelicRushConfig.MAX_PILLAR_ENERGY) return false
        pillarEnergy = (pillarEnergy + 25).coerceAtMost(RelicRushConfig.MAX_PILLAR_ENERGY)
        points += 25
        return true
    }

    public fun damageRelic(amount: Int) {
        relicHealth = (relicHealth - amount.coerceAtLeast(0)).coerceAtLeast(0)
    }

    public fun repairRelic(amount: Int = 50) {
        relicHealth =
            (relicHealth + amount.coerceAtLeast(0)).coerceAtMost(
                RelicRushConfig.STARTING_RELIC_HEALTH
            )
    }

    public fun rewardTier(): RelicRushRewardTier {
        val percent = relicHealth * 100 / RelicRushConfig.STARTING_RELIC_HEALTH
        return when {
            percent >= RelicRushConfig.PERFECT_HEALTH_PERCENT -> RelicRushRewardTier.PERFECT
            percent >= RelicRushConfig.STABLE_HEALTH_PERCENT -> RelicRushRewardTier.STABLE
            else -> RelicRushRewardTier.PARTICIPATION
        }
    }
}
