package org.rsmod.content.other.commands.relicrush

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class RelicRushModelsTest {
    @Test
    public fun `perfect defense gets perfect reward tier`() {
        val run = RelicRushRun(playerId = 1)
        run.collectEnergy()
        assertEquals(RelicRushRewardTier.PERFECT, run.rewardTier())
    }

    @Test
    public fun `relic damage lowers reward tier`() {
        val run = RelicRushRun(playerId = 1)
        run.damageRelic(550)
        assertEquals(RelicRushRewardTier.STABLE, run.rewardTier())
        run.damageRelic(400)
        assertEquals(RelicRushRewardTier.PARTICIPATION, run.rewardTier())
    }

    @Test
    public fun `waves and boss points are tracked`() {
        val run = RelicRushRun(playerId = 1)
        repeat(RelicRushWave.entries.size) {
            val wave = run.beginWave()!!
            repeat(wave.npcIds.size) { run.registerKill() }
            assertTrue(run.finishWave())
        }
        assertEquals(190, run.points)
    }
}
