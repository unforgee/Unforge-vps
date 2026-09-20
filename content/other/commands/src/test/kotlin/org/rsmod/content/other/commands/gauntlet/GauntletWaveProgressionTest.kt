package org.rsmod.content.other.commands.gauntlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class GauntletWaveProgressionTest {
    @Test
    public fun `waves award points and finish after final wave`() {
        val run = GauntletRun(playerId = 1)
        repeat(GauntletWave.entries.size) {
            val wave = run.beginWave()
            check(wave != null)
            repeat(wave.npcIds.size) { run.registerKill() }
            assertTrue(run.finishWave())
        }
        assertEquals(125, run.points)
        assertTrue(run.wave == GauntletWave.entries.size)
    }
}
