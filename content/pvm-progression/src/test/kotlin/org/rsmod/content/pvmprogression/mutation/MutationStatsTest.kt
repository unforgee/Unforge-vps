package org.rsmod.content.pvmprogression.mutation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.content.pvmprogression.events.MutationKind

/**
 * Pure tests for [MutationStats] - the lifetime mutation-kill counters for one character. Only
 * mutated-npc kills touch this, so the counters are a clean signal of mutation engagement.
 */
class MutationStatsTest {
    @Test
    fun `EMPTY has no kills and no rarest kind`() {
        assertEquals(0, MutationStats.EMPTY.totalKills)
        assertTrue(MutationStats.EMPTY.killsByType.isEmpty())
        assertNull(MutationStats.EMPTY.rarestKilled)
    }

    @Test
    fun `add increments totalKills`() {
        val stats = MutationStats.EMPTY.add(MutationKind.BERSERK)
        assertEquals(1, stats.totalKills)
        val stats2 = stats.add(MutationKind.UNSTABLE)
        assertEquals(2, stats2.totalKills)
    }

    @Test
    fun `add accumulates killsByType per kind`() {
        val stats =
            MutationStats.EMPTY.add(MutationKind.BERSERK)
                .add(MutationKind.BERSERK)
                .add(MutationKind.UNSTABLE)
        assertEquals(2, stats.killsByType[MutationKind.BERSERK])
        assertEquals(1, stats.killsByType[MutationKind.UNSTABLE])
    }

    @Test
    fun `add does not mutate the original stats`() {
        val original = MutationStats.EMPTY
        original.add(MutationKind.BERSERK)
        assertEquals(0, original.totalKills)
    }

    @Test
    fun `rarestKilled updates to the rarest kind seen so far`() {
        // UNSTABLE is rarer than BERSERK in the pick order (listed earlier = rarer).
        val stats = MutationStats.EMPTY.add(MutationKind.BERSERK)
        assertEquals(MutationKind.BERSERK, stats.rarestKilled)
        val rarer = stats.add(MutationKind.UNSTABLE)
        assertEquals(MutationKind.UNSTABLE, rarer.rarestKilled)
    }

    @Test
    fun `rarestKilled prefers the rarest kind even when added later`() {
        // GIANT is the rarest in the pick order; adding it after a common kind wins.
        val stats =
            MutationStats.EMPTY.add(MutationKind.VAMPIRIC)
                .add(MutationKind.BERSERK)
                .add(MutationKind.GIANT)
        assertEquals(MutationKind.GIANT, stats.rarestKilled)
    }

    @Test
    fun `rarestKilled stays the rarest once seen`() {
        val stats =
            MutationStats.EMPTY.add(MutationKind.GIANT)
                .add(MutationKind.BERSERK)
                .add(MutationKind.UNSTABLE)
        // GIANT was the rarest; subsequent common kills do not replace it.
        assertEquals(MutationKind.GIANT, stats.rarestKilled)
    }

    @Test
    fun `repeated adds of the same kind keep counting`() {
        var stats = MutationStats.EMPTY
        repeat(5) { stats = stats.add(MutationKind.CORRUPTED) }
        assertEquals(5, stats.totalKills)
        assertEquals(5, stats.killsByType[MutationKind.CORRUPTED])
        assertEquals(MutationKind.CORRUPTED, stats.rarestKilled)
    }
}
