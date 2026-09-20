package org.rsmod.content.other.commands.gauntlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

public class GauntletCleanupTest {
    @Test
    public fun `new run starts with no active npcs`() {
        val run = GauntletRun(playerId = 7)
        assertEquals(0, run.activeNpcs)
        assertFalse(run.rewardGranted)
    }
}
