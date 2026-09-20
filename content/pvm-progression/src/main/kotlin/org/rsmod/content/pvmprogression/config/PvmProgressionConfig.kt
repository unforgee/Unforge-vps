package org.rsmod.content.pvmprogression.config

/**
 * Every tunable of PvM Progression 2.0, in one immutable snapshot.
 *
 * Nothing in the feature hard-codes a number: managers read the values off this object, and
 * [PvmProgressionConfigSource] can replace the whole snapshot at runtime (`::pvmconfig`, or a
 * test). Because it is a value type, a swap is atomic - a game cycle never observes a half-updated
 * configuration.
 *
 * The defaults are deliberately conservative: the feature is meant to add replay value on top of
 * existing content, not to hand out power. Reward amounts are capped low, mutation chance is rare,
 * and no reward grants a permanent combat boost.
 */
data class PvmProgressionConfig(
    val bounty: BountyConfig = BountyConfig(),
    val mutation: MutationConfig = MutationConfig(),
    val record: RecordConfig = RecordConfig(),
    val challenge: ChallengeConfig = ChallengeConfig(),
    val streak: StreakConfig = StreakConfig(),
    val encounter: EncounterConfig = EncounterConfig(),
    val bossSystems: BossSystemsConfig = BossSystemsConfig(),
    val notify: NotifyConfig = NotifyConfig(),
) {
    /** Bounty board tuning. */
    data class BountyConfig(
        /** Bounties generated per category on reset. */
        val dailyCount: Int = 3,
        val weeklyCount: Int = 3,
        val eliteCount: Int = 1,
        /** Server-time hour (0-23) at which the daily board rolls over. */
        val dailyResetHour: Int = 4,
        /** ISO day-of-week (1=Monday .. 7=Sunday) on which the weekly board rolls over. */
        val weeklyResetDay: Int = 1,
        /** Hard cap on simultaneously open tasks per category, guards against config abuse. */
        val maxOpenPerCategory: Int = 6,
        /** Coin payouts, scaled by difficulty tier. Kept small on purpose. */
        val coinsEasy: Int = 2_500,
        val coinsNormal: Int = 6_000,
        val coinsHard: Int = 15_000,
        val coinsElite: Int = 40_000,
        /** PvM token payouts per difficulty tier. */
        val tokensEasy: Int = 1,
        val tokensNormal: Int = 2,
        val tokensHard: Int = 5,
        val tokensElite: Int = 12,
        /** Extra bonus-roll chance (basis points) granted on completion. */
        val bonusRollBps: Int = 1_500,
    )

    /** Monster mutation tuning. */
    data class MutationConfig(
        val enabled: Boolean = true,
        /** 1-in-N eligible spawn chance. `0` or negative disables rolling. */
        val chanceDenominator: Int = 150,
        /** Additional roll denominator applied on respawn (usually identical). */
        val respawnChanceDenominator: Int = 150,
        /** Epic (double-modifier) chance, in basis points of a successful mutation roll. */
        val epicDoubleBps: Int = 300,
        /** Minimum npc combat level eligible for mutation. */
        val minCombatLevel: Int = 10,
        /** Mutation tokens awarded on a mutation kill. */
        val tokensPerKill: Int = 1,
        /** Bonus loot roll chance on a mutation kill, in basis points. */
        val bonusLootBps: Int = 2_500,
        /** Unstable death explosion radius, in tiles. */
        val unstableExplosionRadius: Int = 2,
        /** Unstable explosion max damage. */
        val unstableExplosionMaxDamage: Int = 12,
        /** Cycles of warning shown before an Unstable corpse detonates. */
        val unstableWarningCycles: Int = 4,
        /**
         * Guard against chain reactions: an explosion may never itself detonate another Unstable
         * npc. This is the anti-exploit switch for the death explosion.
         */
        val unstableChainAllowed: Boolean = false,
        /** Vampiric lifesteal, in basis points of damage dealt. */
        val vampiricLifestealBps: Int = 500,
    )

    /** Personal boss records tuning. */
    data class RecordConfig(
        /** Combat level at which an npc counts as a "boss" for records/streak purposes. */
        val bossCombatLevel: Int = 100,
        /**
         * A kill only counts as a personal best when it beats the stored best by at least this many
         * tenths of a second - stops the chatbox from spamming trivial 0.1s improvements.
         */
        val personalBestMarginTenths: Int = 10,
        /** How many recent personal bests the hub overview keeps. */
        val recentBestCount: Int = 5,
    )

    /** Random combat challenge tuning. */
    data class ChallengeConfig(
        val enabled: Boolean = true,
        /** Chance (basis points) that an offered challenge appears when a boss fight starts. */
        val offerChanceBps: Int = 2_000,
        /** Cycles the offer stays open before it is auto-ignored. */
        val offerTimeoutCycles: Int = 60,
        /** Minimum cycles between two offers for the same player. */
        val offerCooldownCycles: Int = 400,
        /** Reward coin amounts per tier. */
        val coinsBronze: Int = 3_000,
        val coinsSilver: Int = 8_000,
        val coinsGold: Int = 20_000,
        val coinsElite: Int = 50_000,
        /** Reward token amounts per tier. */
        val tokensBronze: Int = 1,
        val tokensSilver: Int = 3,
        val tokensGold: Int = 6,
        val tokensElite: Int = 15,
        /** Default speed-kill window, in seconds, scaled per boss by combat level. */
        val speedKillBaseSeconds: Int = 90,
        /** Default low-damage-taken cap, scaled per boss by combat level. */
        val lowDamageBaseAmount: Int = 150,
        /** HP fraction (bps) under which a LOW_HP_FINISH challenge is satisfied. */
        val lowHpFinishBps: Int = 2_500,
        /** Boss HP fraction (bps) at which NO_MOVEMENT starts watching. */
        val noMovementPhaseBps: Int = 8_000,
    )

    /** PvM hot streak tuning. */
    data class StreakConfig(
        /** Streak gained per boss kill. */
        val incrementPerKill: Int = 1,
        /** Kill count after which CASH OUT becomes available. */
        val cashOutUnlockKills: Int = 10,
        /**
         * Fraction (bps) of the unclaimed streak pool lost on a boss death. Only the pool is at
         * risk - never the player's own items.
         */
        val deathPoolLossBps: Int = 10_000,
        /** Tokens accumulated per kill while a streak is running. */
        val tokensPerKill: Int = 2,
        /** Growth applied to milestone rewards after a cash-out (basis points). */
        val milestoneGrowthBps: Int = 1_000,
        /** Hard cap on the unclaimed pool, so an endless streak cannot mint unbounded currency. */
        val maxUnclaimedTokens: Int = 5_000,
    )

    /** Encounter detection tuning. */
    data class EncounterConfig(
        /** Cycles a player may be out of boss combat before the encounter is considered over. */
        val idleTimeoutCycles: Int = 40,
        /** Maximum encounter duration (cycles) before it is force-ended - guards stuck state. */
        val maxDurationCycles: Int = 6_000,
        /**
         * Distance (tiles) beyond which a player is treated as having left the encounter (teleport
         * away). Prevents a teleported player from banking an in-progress encounter.
         */
        val abandonDistance: Int = 30,
    )

    /** Shared, conservative tuning for Lucky Kill, Last Stand and boss modifiers. */
    data class BossSystemsConfig(
        val luckyKillDenominator: Int = 100,
        val modifierSpawnChanceBps: Int = 2_000,
        val mythicSpawnDenominator: Int = 250,
        val lastStandHpBps: Int = 1_000,
        val mythicHpBps: Int = 7_500,
        val mythicDamageBps: Int = 3_500,
        val mythicDefenceBps: Int = 3_000,
        val vampiricHealBps: Int = 800,
        val cursedStrengthBps: Int = 1_000,
    )

    /** Notification tuning. */
    data class NotifyConfig(
        val mutationAnnounce: Boolean = true,
        val bountyAnnounce: Boolean = true,
        val streakAnnounce: Boolean = true,
        val recordAnnounce: Boolean = true,
        val challengeAnnounce: Boolean = true,
    )

    companion object {
        val DEFAULT = PvmProgressionConfig()
    }
}
