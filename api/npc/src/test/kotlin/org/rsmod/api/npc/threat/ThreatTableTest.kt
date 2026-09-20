package org.rsmod.api.npc.threat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

public class ThreatTableTest {
    private val alwaysValid: (Long) -> Boolean = { true }
    private val adjacent: (Long) -> Int = { 0 }

    private fun table(): ThreatTable = ThreatTable()

    @Test
    public fun `tank holds aggro when ahead on threat`() {
        // TEST A: Tank threat 5000, DPS 3000 - npc stays on tank (no switch returned).
        val t = table()
        t.add(1, 5000, 0, 0)
        t.add(2, 3000, 0, 0)
        assertNull(t.selectTarget(1, 0, 0, alwaysValid, adjacent))
    }

    @Test
    public fun `challenger must exceed threshold to pull aggro`() {
        // TEST B shape: support out-threats the current holder past the 110% melee threshold.
        val t = table()
        t.add(1, 100, 0, 0)
        t.add(2, 500, 0, 0)
        assertEquals(2, t.selectTarget(1, 0, 0, alwaysValid, adjacent))
    }

    @Test
    public fun `small threat lead does not switch`() {
        // Proximity protection: 1050 vs 1000 at range needs 120% - no switch.
        val t = table()
        t.add(1, 1000, 0, 0)
        t.add(2, 1050, 0, 0)
        assertNull(t.selectTarget(1, 0, 0, alwaysValid, distance = { 5 }))
        // Same lead in melee range (110%): still no switch.
        assertNull(t.selectTarget(1, 0, 0, alwaysValid, adjacent))
        // 1300 at range exceeds 120%: switch.
        t.add(2, 250, 0, 0)
        assertEquals(2, t.selectTarget(1, 0, 0, alwaysValid, distance = { 5 }))
    }

    @Test
    public fun `taunt forces target and lifts holder above top`() {
        // TEST C: tank taunts - immediately selected, threat raised past the leader.
        val t = table()
        t.add(2, 5000, 0, 0) // dps far ahead
        t.forceTarget(1, untilCycle = 8, cycle = 0, tauntBonus = 250, decayBps = 0)
        assertEquals(1, t.selectTarget(2, 0, 0, alwaysValid, adjacent))
        assertEquals(5250, t.entries[1]!!.threat)
    }

    @Test
    public fun `damage during taunt cannot override it`() {
        // TEST D: dps deals more damage mid-taunt - forced target holds until expiry.
        val t = table()
        t.add(1, 500, 0, 0)
        t.forceTarget(1, untilCycle = 8, cycle = 0, tauntBonus = 250, decayBps = 0)
        t.add(2, 4000, 1, 0)
        assertEquals(1, t.selectTarget(1, 1, 0, alwaysValid, adjacent))
        assertEquals(1, t.selectTarget(1, 7, 0, alwaysValid, adjacent))
        // Cycle 8: taunt expired - the dps's threat lead wins.
        assertEquals(2, t.selectTarget(1, 8, 0, alwaysValid, adjacent))
    }

    @Test
    public fun `threat decays during inactivity`() {
        val t = table()
        t.add(1, 10_000, 0, 0)
        // 10 bps/cycle * 100 cycles idle = -10% decayed on next touch.
        assertEquals(9_000, t.threatOf(1, 100, 10))
        // Decay can fully expire an entry.
        assertEquals(0, t.threatOf(1, 5_000, 10))
    }

    @Test
    public fun `invalid current target falls to next valid holder`() {
        // Dead/left player is skipped: select returns the best valid challenger.
        val t = table()
        t.add(1, 5000, 0, 0)
        t.add(2, 100, 0, 0)
        val valid: (Long) -> Boolean = { it == 2L }
        assertEquals(2, t.selectTarget(1, 0, 0, valid, adjacent))
    }

    @Test
    public fun `invalid forced target drops the force`() {
        val t = table()
        t.add(1, 100, 0, 0)
        t.add(2, 500, 0, 0)
        t.forceTarget(1, untilCycle = 8, cycle = 0, tauntBonus = 250, decayBps = 0)
        // Tank logged out mid-taunt - force clears and the dps (valid) is picked.
        val valid: (Long) -> Boolean = { it == 2L }
        assertEquals(2, t.selectTarget(1, 0, 0, valid, adjacent))
        assertNull(t.forcedTarget(0))
    }

    @Test
    public fun `remove clears entries and pending forces`() {
        // TEST J shape: cleanup on death/logout.
        val t = table()
        t.add(1, 100, 0, 0)
        t.forceTarget(1, untilCycle = 8, cycle = 0, tauntBonus = 250, decayBps = 0)
        t.remove(1)
        assertNull(t.forcedTarget(0))
        assertNull(t.selectTarget(1, 0, 0, alwaysValid, adjacent))
    }

    @Test
    public fun `every table is independent`() {
        // Two npcs = two tables: threat on one never leaks to the other.
        val a = table()
        val b = table()
        a.add(1, 5000, 0, 0)
        b.add(2, 100, 0, 0)
        assertEquals(5000, a.threatOf(1, 0, 0))
        assertEquals(0, a.threatOf(2, 0, 0))
        assertEquals(100, b.threatOf(2, 0, 0))
        assertEquals(0, b.threatOf(1, 0, 0))
    }

    @Test
    public fun `scripted override does not inflate threat`() {
        val t = table()
        t.add(1, 500, 0, 0)
        t.forceTarget(2, untilCycle = 5, cycle = 0, tauntBonus = 0, decayBps = 0, scripted = true)
        assertEquals(2, t.selectTarget(1, 0, 0, alwaysValid, adjacent))
        assertEquals(0, t.threatOf(2, 0, 0))
        // Expiry returns selection to the real threat leader.
        assertEquals(1, t.selectTarget(2, 6, 0, alwaysValid, adjacent))
    }
}
