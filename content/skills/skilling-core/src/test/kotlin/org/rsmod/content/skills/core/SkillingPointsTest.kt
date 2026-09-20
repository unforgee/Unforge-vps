package org.rsmod.content.skills.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The pure half of the skilling point model: award validation and the spend contract.
 *
 * These run without the game harness - the varp-level facts (the `skill_points` type exists and a
 * fresh player reads `0`) live in `SkillingPointConfigTest`, and the end-to-end grant is pinned in
 * `MiningScriptTest`.
 */
class SkillingPointsTest {
    @Test
    fun `award outside one to cap is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SkillingPointAward(0) }
        assertThrows(IllegalArgumentException::class.java) { SkillingPointAward(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            SkillingPointAward(SkillingPointAward.MAX_PER_ACTION + 1)
        }
        // Boundary: exactly the cap is a legal award.
        assertEquals(
            SkillingPointAward.MAX_PER_ACTION,
            SkillingPointAward(SkillingPointAward.MAX_PER_ACTION).amount,
        )
    }

    @Test
    fun `award accumulates onto the balance`() {
        val balance = applySkillingPointAward(41, SkillingPointAward(1))
        assertEquals(42, balance)
    }

    @Test
    fun `duplicate call applies the award again instead of silently deduplicating`() {
        val award = SkillingPointAward(1)
        val first = applySkillingPointAward(0, award)
        val second = applySkillingPointAward(first, award)

        assertEquals(1, first)
        assertEquals(2, second)
        // Per-action idempotency is the caller's contract: one grant per successful action. This
        // test documents that the model does not hide duplicates - a caller bug shows up as a
        // doubled balance instead of as a silent no-op.
    }

    @Test
    fun `spend deducts the full cost`() {
        assertEquals(75, applySkillingPointSpend(100, 25))
    }

    @Test
    fun `spend may empty the balance but never goes negative`() {
        assertEquals(0, applySkillingPointSpend(25, 25))
        assertNull(applySkillingPointSpend(25, 26))
    }

    @Test
    fun `insufficient funds spend changes nothing`() {
        assertNull(applySkillingPointSpend(10, 25))
        assertNull(applySkillingPointSpend(0, 1))
        // Non-positive costs are refused outright - a free or negative "purchase" is a bug, not a
        // transaction.
        assertNull(applySkillingPointSpend(100, 0))
        assertNull(applySkillingPointSpend(100, -5))
    }
}
