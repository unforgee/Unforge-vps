package org.rsmod.content.pvmpoints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The difficulty table is pure - every band boundary is pinned down without the game harness. */
class PvmPointsTableTest {
    @Test
    fun `trivial mobs award nothing`() {
        for (level in -50 until 10) {
            assertEquals(0, pvmPointsForLevel(level), "level $level should award nothing")
        }
    }

    @Test
    fun `each band awards its tier`() {
        val bands = mapOf(10..49 to 1, 50..99 to 2, 100..199 to 5, 200..349 to 10)
        for ((band, expected) in bands) {
            for (level in band) {
                assertEquals(expected, pvmPointsForLevel(level), "level $level")
            }
        }
    }

    @Test
    fun `endgame bosses award the top tier`() {
        assertEquals(20, pvmPointsForLevel(350))
        assertEquals(20, pvmPointsForLevel(999))
    }

    @Test
    fun `the table is monotonically non-decreasing`() {
        var previous = 0
        for (level in 0..500) {
            val current = pvmPointsForLevel(level)
            assertTrue(current >= previous, "tier dropped at level $level")
            previous = current
        }
    }
}
