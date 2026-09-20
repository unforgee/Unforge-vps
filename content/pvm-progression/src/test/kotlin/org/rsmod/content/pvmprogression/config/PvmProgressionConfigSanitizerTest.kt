package org.rsmod.content.pvmprogression.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure tests for the config sanitiser - the defence-in-depth layer that clamps every tunable into a
 * range that cannot break the feature or the economy.
 */
class PvmProgressionConfigSanitizerTest {
    @Test
    fun `clampDenominator maps zero and negatives to disabled`() {
        assertEquals(0, clampDenominator(0))
        assertEquals(0, clampDenominator(-5))
        assertEquals(0, clampDenominator(-1))
    }

    @Test
    fun `clampDenominator keeps small positive denominators unchanged`() {
        assertEquals(1, clampDenominator(1))
        assertEquals(150, clampDenominator(150))
        assertEquals(1000, clampDenominator(1000))
    }

    @Test
    fun `clampDenominator caps huge values at one million`() {
        assertEquals(1_000_000, clampDenominator(1_000_001))
        assertEquals(1_000_000, clampDenominator(Int.MAX_VALUE))
    }

    @Test
    fun `sanitizeConfig clamps a 1-in-1 mutation chance`() {
        val bad =
            PvmProgressionConfig(
                mutation = PvmProgressionConfig.MutationConfig(chanceDenominator = 1)
            )
        val sanitized = sanitizeConfig(bad)
        assertEquals(1, sanitized.mutation.chanceDenominator)
        // A 0/negative denominator is disabled, not left at 1.
        val disabled =
            PvmProgressionConfig(
                mutation =
                    PvmProgressionConfig.MutationConfig(
                        chanceDenominator = 0,
                        respawnChanceDenominator = -10,
                    )
            )
        val s = sanitizeConfig(disabled)
        assertEquals(0, s.mutation.chanceDenominator)
        assertEquals(0, s.mutation.respawnChanceDenominator)
    }

    @Test
    fun `sanitizeConfig clamps negative coin rewards to zero`() {
        val bad =
            PvmProgressionConfig(
                bounty =
                    PvmProgressionConfig.BountyConfig(
                        coinsEasy = -5,
                        coinsNormal = -1,
                        coinsHard = -1000,
                        coinsElite = -2_000_000,
                    )
            )
        val s = sanitizeConfig(bad)
        assertEquals(0, s.bounty.coinsEasy)
        assertEquals(0, s.bounty.coinsNormal)
        assertEquals(0, s.bounty.coinsHard)
        assertEquals(0, s.bounty.coinsElite)
    }

    @Test
    fun `sanitizeConfig clamps dailyCount above the maximum`() {
        val bad =
            PvmProgressionConfig(
                bounty = PvmProgressionConfig.BountyConfig(dailyCount = 20, weeklyCount = 99)
            )
        val s = sanitizeConfig(bad)
        assertEquals(10, s.bounty.dailyCount)
        assertEquals(10, s.bounty.weeklyCount)
    }

    @Test
    fun `sanitizeConfig clamps epicDoubleBps above one hundred percent`() {
        val bad =
            PvmProgressionConfig(
                mutation = PvmProgressionConfig.MutationConfig(epicDoubleBps = 20_000)
            )
        val s = sanitizeConfig(bad)
        assertEquals(10_000, s.mutation.epicDoubleBps)
    }

    @Test
    fun `sanitizeConfig clamps token rewards into range`() {
        val bad =
            PvmProgressionConfig(
                bounty =
                    PvmProgressionConfig.BountyConfig(
                        tokensEasy = -1,
                        tokensNormal = 50_000,
                        tokensHard = 0,
                        tokensElite = -100,
                    )
            )
        val s = sanitizeConfig(bad)
        assertEquals(0, s.bounty.tokensEasy)
        assertEquals(1_000, s.bounty.tokensNormal)
        assertEquals(0, s.bounty.tokensHard)
        assertEquals(0, s.bounty.tokensElite)
    }

    @Test
    fun `sanitizeConfig clamps streak increment and cash-out unlock`() {
        val bad =
            PvmProgressionConfig(
                streak =
                    PvmProgressionConfig.StreakConfig(
                        incrementPerKill = 0,
                        cashOutUnlockKills = 0,
                        maxUnclaimedTokens = -5,
                    )
            )
        val s = sanitizeConfig(bad)
        assertEquals(1, s.streak.incrementPerKill)
        assertEquals(1, s.streak.cashOutUnlockKills)
        assertEquals(0, s.streak.maxUnclaimedTokens)
    }

    @Test
    fun `sanitizeConfig leaves a default config untouched in its safe bounds`() {
        val s = sanitizeConfig(PvmProgressionConfig.DEFAULT)
        assertTrue(s.mutation.chanceDenominator >= 1)
        assertTrue(s.bounty.coinsEasy >= 0)
        assertTrue(s.challenge.offerChanceBps in 0..MAX_BPS)
    }

    @Test
    fun `configSource publishes only sanitised snapshots`() {
        val source = PvmProgressionConfigSource()
        source.update {
            it.copy(
                bounty = it.bounty.copy(dailyCount = 999, coinsEasy = -50),
                mutation = it.mutation.copy(chanceDenominator = 0),
            )
        }
        val live = source.config
        assertEquals(10, live.bounty.dailyCount)
        assertEquals(0, live.bounty.coinsEasy)
        assertEquals(0, live.mutation.chanceDenominator)
    }
}
