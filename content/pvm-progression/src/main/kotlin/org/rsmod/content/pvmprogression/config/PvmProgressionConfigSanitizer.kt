package org.rsmod.content.pvmprogression.config

/** Upper bound for a basis-point value (100%). */
const val MAX_BPS = 10_000

/** Upper bound for any single coin reward, so a bad config cannot mint wealth. */
const val MAX_REWARD = 10_000_000

/** Upper bound for any single token reward. */
const val MAX_TOKENS = 1_000

/**
 * A "1 in N" chance denominator: `0` (or anything below) means disabled, otherwise clamped so a
 * stray huge value cannot overflow when multiplied.
 */
fun clampDenominator(value: Int): Int = if (value < 1) 0 else value.coerceAtMost(1_000_000)

/**
 * Clamps every tunable into a range that cannot break the feature or the economy.
 *
 * This is the defence-in-depth half of the data-driven design: values come from config, but config
 * is never trusted blindly. Anything out of range is pulled back to the nearest sane bound instead
 * of throwing, so a bad value degrades the feature rather than crashing the game thread.
 */
fun sanitizeConfig(config: PvmProgressionConfig): PvmProgressionConfig =
    config.copy(
        bounty = sanitizeBounty(config.bounty),
        mutation = sanitizeMutation(config.mutation),
        record = sanitizeRecord(config.record),
        challenge = sanitizeChallenge(config.challenge),
        streak = sanitizeStreak(config.streak),
        encounter = sanitizeEncounter(config.encounter),
        bossSystems = sanitizeBossSystems(config.bossSystems),
    )

private fun sanitizeBounty(b: PvmProgressionConfig.BountyConfig) =
    b.copy(
        dailyCount = b.dailyCount.coerceIn(0, 10),
        weeklyCount = b.weeklyCount.coerceIn(0, 10),
        eliteCount = b.eliteCount.coerceIn(0, 5),
        dailyResetHour = b.dailyResetHour.coerceIn(0, 23),
        weeklyResetDay = b.weeklyResetDay.coerceIn(1, 7),
        maxOpenPerCategory = b.maxOpenPerCategory.coerceIn(1, 20),
        coinsEasy = b.coinsEasy.coerceIn(0, MAX_REWARD),
        coinsNormal = b.coinsNormal.coerceIn(0, MAX_REWARD),
        coinsHard = b.coinsHard.coerceIn(0, MAX_REWARD),
        coinsElite = b.coinsElite.coerceIn(0, MAX_REWARD),
        tokensEasy = b.tokensEasy.coerceIn(0, MAX_TOKENS),
        tokensNormal = b.tokensNormal.coerceIn(0, MAX_TOKENS),
        tokensHard = b.tokensHard.coerceIn(0, MAX_TOKENS),
        tokensElite = b.tokensElite.coerceIn(0, MAX_TOKENS),
        bonusRollBps = b.bonusRollBps.coerceIn(0, MAX_BPS),
    )

private fun sanitizeMutation(m: PvmProgressionConfig.MutationConfig) =
    m.copy(
        chanceDenominator = clampDenominator(m.chanceDenominator),
        respawnChanceDenominator = clampDenominator(m.respawnChanceDenominator),
        epicDoubleBps = m.epicDoubleBps.coerceIn(0, MAX_BPS),
        minCombatLevel = m.minCombatLevel.coerceIn(1, 1_000),
        tokensPerKill = m.tokensPerKill.coerceIn(0, MAX_TOKENS),
        bonusLootBps = m.bonusLootBps.coerceIn(0, MAX_BPS),
        unstableExplosionRadius = m.unstableExplosionRadius.coerceIn(0, 5),
        unstableExplosionMaxDamage = m.unstableExplosionMaxDamage.coerceIn(0, 50),
        unstableWarningCycles = m.unstableWarningCycles.coerceIn(1, 20),
        vampiricLifestealBps = m.vampiricLifestealBps.coerceIn(0, MAX_BPS),
    )

private fun sanitizeRecord(r: PvmProgressionConfig.RecordConfig) =
    r.copy(
        bossCombatLevel = r.bossCombatLevel.coerceIn(1, 1_000),
        personalBestMarginTenths = r.personalBestMarginTenths.coerceIn(0, 600),
        recentBestCount = r.recentBestCount.coerceIn(0, 20),
    )

private fun sanitizeChallenge(c: PvmProgressionConfig.ChallengeConfig) =
    c.copy(
        offerChanceBps = c.offerChanceBps.coerceIn(0, MAX_BPS),
        offerTimeoutCycles = c.offerTimeoutCycles.coerceIn(5, 600),
        offerCooldownCycles = c.offerCooldownCycles.coerceIn(0, 10_000),
        coinsBronze = c.coinsBronze.coerceIn(0, MAX_REWARD),
        coinsSilver = c.coinsSilver.coerceIn(0, MAX_REWARD),
        coinsGold = c.coinsGold.coerceIn(0, MAX_REWARD),
        coinsElite = c.coinsElite.coerceIn(0, MAX_REWARD),
        tokensBronze = c.tokensBronze.coerceIn(0, MAX_TOKENS),
        tokensSilver = c.tokensSilver.coerceIn(0, MAX_TOKENS),
        tokensGold = c.tokensGold.coerceIn(0, MAX_TOKENS),
        tokensElite = c.tokensElite.coerceIn(0, MAX_TOKENS),
        speedKillBaseSeconds = c.speedKillBaseSeconds.coerceIn(10, 3_600),
        lowDamageBaseAmount = c.lowDamageBaseAmount.coerceIn(1, 100_000),
        lowHpFinishBps = c.lowHpFinishBps.coerceIn(100, 9_000),
        noMovementPhaseBps = c.noMovementPhaseBps.coerceIn(100, 9_000),
    )

private fun sanitizeStreak(s: PvmProgressionConfig.StreakConfig) =
    s.copy(
        incrementPerKill = s.incrementPerKill.coerceIn(1, 10),
        cashOutUnlockKills = s.cashOutUnlockKills.coerceIn(1, 1_000),
        deathPoolLossBps = s.deathPoolLossBps.coerceIn(0, MAX_BPS),
        tokensPerKill = s.tokensPerKill.coerceIn(0, MAX_TOKENS),
        milestoneGrowthBps = s.milestoneGrowthBps.coerceIn(0, MAX_BPS),
        maxUnclaimedTokens = s.maxUnclaimedTokens.coerceIn(0, 1_000_000),
    )

private fun sanitizeEncounter(e: PvmProgressionConfig.EncounterConfig) =
    e.copy(
        idleTimeoutCycles = e.idleTimeoutCycles.coerceIn(5, 600),
        maxDurationCycles = e.maxDurationCycles.coerceIn(100, 100_000),
        abandonDistance = e.abandonDistance.coerceIn(5, 200),
    )

private fun sanitizeBossSystems(b: PvmProgressionConfig.BossSystemsConfig) =
    b.copy(
        luckyKillDenominator = clampDenominator(b.luckyKillDenominator).coerceAtLeast(1),
        modifierSpawnChanceBps = b.modifierSpawnChanceBps.coerceIn(0, MAX_BPS),
        mythicSpawnDenominator = clampDenominator(b.mythicSpawnDenominator).coerceAtLeast(1),
        lastStandHpBps = b.lastStandHpBps.coerceIn(100, 10_000),
        mythicHpBps = b.mythicHpBps.coerceIn(0, 20_000),
        mythicDamageBps = b.mythicDamageBps.coerceIn(0, 10_000),
        mythicDefenceBps = b.mythicDefenceBps.coerceIn(0, 10_000),
        vampiricHealBps = b.vampiricHealBps.coerceIn(0, 10_000),
        cursedStrengthBps = b.cursedStrengthBps.coerceIn(0, 10_000),
    )
