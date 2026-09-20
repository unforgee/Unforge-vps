package org.rsmod.content.pvmprogression.record

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pure tests for the [BossRecord] value type and [BossRecordOverall] aggregate - the per-boss
 * record sheet and the cross-boss overview numbers the PvM hub renders.
 */
class BossRecordTest {
    @Test
    fun `EMPTY has sentinel boss id and blank name`() {
        assertEquals(-1, BossRecord.EMPTY.bossId)
        assertEquals("", BossRecord.EMPTY.bossName)
    }

    @Test
    fun `EMPTY starts with zero kills`() {
        assertEquals(0, BossRecord.EMPTY.totalKills)
        assertEquals(0, BossRecord.EMPTY.deaths)
        assertEquals(0, BossRecord.EMPTY.currentStreak)
        assertEquals(0, BossRecord.EMPTY.bestStreak)
    }

    @Test
    fun `EMPTY fastestKillTenths is the max-value sentinel`() {
        assertEquals(Int.MAX_VALUE, BossRecord.EMPTY.fastestKillTenths)
        assertEquals(0, BossRecord.EMPTY.slowestKillTenths)
    }

    @Test
    fun `copy produces an equal but independent record`() {
        val record = BossRecord(bossId = 42, bossName = "King Black Dragon", totalKills = 5)
        val copy = record.copy()
        assertEquals(record, copy)
        assertEquals(5, copy.totalKills)
    }

    @Test
    fun `copy with overrides changes only the selected fields`() {
        val record =
            BossRecord(
                bossId = 42,
                bossName = "King Black Dragon",
                totalKills = 5,
                fastestKillTenths = 600,
                currentStreak = 3,
            )
        val updated = record.copy(totalKills = 6, currentStreak = 4)
        assertEquals(6, updated.totalKills)
        assertEquals(4, updated.currentStreak)
        // Untouched fields are preserved.
        assertEquals(42, updated.bossId)
        assertEquals("King Black Dragon", updated.bossName)
        assertEquals(600, updated.fastestKillTenths)
    }

    @Test
    fun `records with different boss ids are not equal`() {
        val a = BossRecord(bossId = 1, bossName = "A")
        val b = BossRecord(bossId = 2, bossName = "A")
        assertNotEquals(a, b)
    }
}

class BossRecordOverallTest {
    @Test
    fun `EMPTY has zeroed aggregate counters`() {
        assertEquals(0, BossRecordOverall.EMPTY.totalBossKills)
        assertEquals(0, BossRecordOverall.EMPTY.totalBossDeaths)
        assertEquals(0, BossRecordOverall.EMPTY.mostKilledCount)
        assertEquals(0, BossRecordOverall.EMPTY.bestStreak)
        assertEquals(0, BossRecordOverall.EMPTY.totalBossDamage)
    }

    @Test
    fun `EMPTY has null favourite and most-killed boss`() {
        assertEquals(null, BossRecordOverall.EMPTY.favouriteBoss)
        assertEquals(null, BossRecordOverall.EMPTY.mostKilledBoss)
        assertEquals(null, BossRecordOverall.EMPTY.fastestKillBoss)
    }

    @Test
    fun `EMPTY fastestKillTenths is the max-value sentinel`() {
        assertEquals(Int.MAX_VALUE, BossRecordOverall.EMPTY.fastestKillTenths)
    }

    @Test
    fun `copy with overrides changes only the selected fields`() {
        val overall =
            BossRecordOverall(totalBossKills = 10, mostKilledBoss = "Vorkath", mostKilledCount = 4)
        val updated = overall.copy(totalBossKills = 11, mostKilledCount = 5)
        assertEquals(11, updated.totalBossKills)
        assertEquals(5, updated.mostKilledCount)
        assertEquals("Vorkath", updated.mostKilledBoss)
        assertTrue(updated.totalBossDeaths == 0)
    }
}
