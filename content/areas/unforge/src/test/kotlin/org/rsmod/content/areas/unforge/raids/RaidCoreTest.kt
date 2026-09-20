package org.rsmod.content.areas.unforge.raids

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RaidCoreTest {
    private fun newCore(
        kind: RaidKind = RaidKind.THEATRE,
        difficulty: RaidDifficulty = RaidDifficulty.NORMAL,
        rooms: Int = 6,
    ): RaidRunCore = RaidRunCore(kind, difficulty, rooms, startCycle = 0).also { it.activate() }

    @Test
    fun `expert difficulty multiplies stats and score`() {
        assertTrue(RaidDifficulty.EXPERT.statMultiplier > RaidDifficulty.NORMAL.statMultiplier)
        assertTrue(
            RaidDifficulty.EXPERT.scoreMultiplierBps > RaidDifficulty.NORMAL.scoreMultiplierBps
        )
        assertEquals(1.0, RaidDifficulty.NORMAL.statMultiplier)
        assertEquals(1.5, RaidDifficulty.EXPERT.statMultiplier)
        assertEquals(17_500, RaidDifficulty.EXPERT.scoreMultiplierBps)
    }

    @Test
    fun `room points combine base value and kill bonus`() {
        assertEquals(150, RaidScore.roomPoints(RaidRoomKind.COMBAT, kills = 0))
        assertEquals(230, RaidScore.roomPoints(RaidRoomKind.COMBAT, kills = 8))
        assertEquals(950, RaidScore.roomPoints(RaidRoomKind.MINIBOSS, kills = 20))
        assertEquals(3_500, RaidScore.roomPoints(RaidRoomKind.BOSS, kills = 0))
    }

    @Test
    fun `death penalty is per death and capped`() {
        assertEquals(800, RaidScore.deathPenaltyBps(1))
        assertEquals(4_000, RaidScore.deathPenaltyBps(5))
        assertEquals(4_000, RaidScore.deathPenaltyBps(50))
        assertEquals(0, RaidScore.deathPenaltyBps(-3))
    }

    @Test
    fun `final score applies difficulty multiplier death penalty and time bonus`() {
        val normal =
            RaidScore.finalScore(
                roomScore = 1_000,
                kills = 10,
                deaths = 0,
                difficulty = RaidDifficulty.NORMAL,
                elapsedCycles = 10_000,
            )
        // (1000 + 10*10) * 1.0 = 1100, no penalty, no time bonus.
        assertEquals(1_100, normal)

        val expert =
            RaidScore.finalScore(
                roomScore = 1_000,
                kills = 10,
                deaths = 0,
                difficulty = RaidDifficulty.EXPERT,
                elapsedCycles = 10_000,
            )
        assertTrue(expert > normal, "Expert should outscore Normal")

        val withDeath =
            RaidScore.finalScore(
                roomScore = 1_000,
                kills = 10,
                deaths = 2,
                difficulty = RaidDifficulty.NORMAL,
                elapsedCycles = 10_000,
            )
        // 1100 * (1 - 0.16) = 924.
        assertEquals(924, withDeath)

        val fast =
            RaidScore.finalScore(
                roomScore = 1_000,
                kills = 10,
                deaths = 0,
                difficulty = RaidDifficulty.NORMAL,
                elapsedCycles = RaidScore.FAST_CLEAR_CYCLES,
            )
        // 1100 + 5% = 1155.
        assertEquals(1_155, fast)

        assertEquals(
            0,
            RaidScore.finalScore(
                roomScore = -500,
                kills = 0,
                deaths = 0,
                difficulty = RaidDifficulty.NORMAL,
                elapsedCycles = 0,
            ),
            "score can never go negative",
        )
    }

    @Test
    fun `reward chance improves with score and expert`() {
        val low = RaidRewards.uniqueDenominator(score = 0, RaidDifficulty.NORMAL)
        val high = RaidRewards.uniqueDenominator(score = 12_000, RaidDifficulty.NORMAL)
        assertTrue(high < low, "higher score should improve unique odds")
        assertEquals(50, low)
        val expert = RaidRewards.uniqueDenominator(score = 0, RaidDifficulty.EXPERT)
        assertTrue(expert < low, "Expert should improve unique odds")
        assertTrue(
            RaidRewards.commonRolls(0, RaidDifficulty.EXPERT) >
                RaidRewards.commonRolls(0, RaidDifficulty.NORMAL)
        )
    }

    @Test
    fun `room progression advances and revives`() {
        val core = newCore(rooms = 3)
        core.addMember(1)
        core.addMember(2)
        core.markDead(2)
        assertEquals(1, core.deaths)
        assertTrue(core.advanceRoom())
        assertEquals(1, core.roomIndex)
        assertEquals(RaidMemberState.ALIVE, core.members[2], "advance revives dead members")
        assertTrue(core.advanceRoom())
        assertTrue(core.isFinalRoom())
        assertFalse(core.advanceRoom(), "cannot advance past the final room")
    }

    @Test
    fun `final room completion is terminal and idempotent`() {
        val core = newCore(rooms = 2)
        core.addMember(1)
        core.advanceRoom()
        core.complete()
        assertEquals(RaidRunState.COMPLETE, core.state)
        val score = core.score
        core.complete()
        assertEquals(score, core.score, "re-completing must not change state")
        core.fail()
        assertEquals(RaidRunState.COMPLETE, core.state, "a completed run cannot fail")
    }

    @Test
    fun `reward chest claims exactly once per member`() {
        val core = newCore()
        core.addMember(1)
        core.addMember(2)
        assertTrue(core.claimChest(1))
        assertFalse(core.claimChest(1), "second claim must be rejected")
        assertFalse(core.claimChest(3), "non-members cannot claim")
        assertTrue(core.claimChest(2))
    }

    @Test
    fun `cleanup is idempotent`() {
        val core = newCore()
        core.cleanup()
        assertEquals(RaidRunState.CLEANED, core.state)
        core.cleanup()
        assertEquals(RaidRunState.CLEANED, core.state)
        assertFalse(core.advanceRoom(), "a cleaned run cannot progress")
    }

    @Test
    fun `timeout triggers only after the limit`() {
        val core = RaidRunCore(RaidKind.THEATRE, RaidDifficulty.NORMAL, 6, startCycle = 100)
        assertFalse(core.timedOut(nowCycle = 100 + 9_000, timeoutCycles = 9_000))
        assertTrue(core.timedOut(nowCycle = 100 + 9_001, timeoutCycles = 9_000))
    }

    @Test
    fun `solo death wipes the run`() {
        val core = newCore()
        core.addMember(1)
        assertTrue(core.markDead(1), "a solo death must wipe the run")
        assertTrue(core.allDead())
    }

    @Test
    fun `party wipe needs every member dead`() {
        val core = newCore()
        core.addMember(1)
        core.addMember(2)
        core.addMember(3)
        assertFalse(core.markDead(1))
        assertFalse(core.markDead(2))
        assertTrue(core.markDead(3), "the last death wipes the party")
        assertEquals(3, core.deaths)
    }

    @Test
    fun `leaving members shrink the alive pool`() {
        val core = newCore()
        core.addMember(1)
        core.addMember(2)
        core.markDead(1)
        core.removeMember(2)
        assertTrue(core.allDead(), "a run with only dead members counts as wiped")
    }

    @Test
    fun `theatre has six linear rooms ending at verzik`() {
        val rooms = TobRaid.rooms
        assertEquals(6, rooms.size)
        assertEquals("verzik", rooms.last().key)
        assertEquals(RaidRoomKind.BOSS, rooms.last().kind)
        assertTrue(rooms.dropLast(1).all { it.kind != RaidRoomKind.BOSS })
    }

    @Test
    fun `chambers generates five to seven rooms ending at olm`() {
        repeat(20) { seed ->
            val rooms = CoxRaid.rooms(Random(seed))
            assertTrue(rooms.size in 5..7, "seed $seed produced ${rooms.size} rooms")
            assertEquals("olm", rooms.last().key)
            assertEquals(RaidRoomKind.BOSS, rooms.last().kind)
            assertTrue(rooms.any { it.kind == RaidRoomKind.PUZZLE })
            assertTrue(rooms.any { it.kind == RaidRoomKind.MINIBOSS })
            assertTrue(rooms.any { it.kind == RaidRoomKind.COMBAT })
            assertEquals(rooms.size, rooms.map { it.key }.toSet().size, "rooms must be unique")
        }
    }

    @Test
    fun `score tracking accumulates room points`() {
        val core = newCore()
        core.addScore(RaidScore.roomPoints(RaidRoomKind.COMBAT, 5))
        core.addScore(RaidScore.roomPoints(RaidRoomKind.MINIBOSS, 1))
        assertEquals(150 + 50 + 750 + 10, core.score)
        core.addScore(-100_000)
        assertEquals(0, core.score, "score cannot drop below zero")
    }
}
