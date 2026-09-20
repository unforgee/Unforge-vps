package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class CombatTelemetryTest {
    @Test
    public fun `companion totals retain owner relationship and breakdown`() {
        val meter = CombatTelemetryAccumulator()
        meter.record(CombatTelemetryEvent(7, 42, 1001, "Melee", 900, "claw", damage = 12))
        meter.record(CombatTelemetryEvent(7, 42, 1001, "Melee", 900, "claw", damage = 8))
        val total = meter.snapshot().single()
        assertEquals(1001, total.ownerId)
        assertEquals(20, total.damage)
        assertEquals(2, total.hitCount)
        assertEquals(12, total.maxHit)
        assertEquals(20, total.abilityBreakdown["claw"])
    }
}
