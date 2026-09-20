package org.rsmod.content.pvmprogression.rewards

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure tests for [RewardLedger] - the idempotency guard that stops a repeated death callback or
 * reconnect-replay from double-paying the same reward id.
 */
class RewardLedgerTest {
    @Test
    fun `record returns true the first time an id is seen`() {
        val ledger = RewardLedger()
        assertTrue(ledger.record("challenge:1:100"))
    }

    @Test
    fun `record returns false for a duplicate id`() {
        val ledger = RewardLedger()
        assertTrue(ledger.record("challenge:1:100"))
        assertFalse(ledger.record("challenge:1:100"))
    }

    @Test
    fun `record is idempotent across many repeated calls`() {
        val ledger = RewardLedger()
        assertTrue(ledger.record("streak:1:cashout:5"))
        for (i in 1..20) {
            assertFalse(ledger.record("streak:1:cashout:5"))
        }
    }

    @Test
    fun `distinct ids each record true once`() {
        val ledger = RewardLedger()
        assertTrue(ledger.record("a"))
        assertTrue(ledger.record("b"))
        assertTrue(ledger.record("c"))
        assertFalse(ledger.record("a"))
        assertFalse(ledger.record("b"))
        assertFalse(ledger.record("c"))
    }

    @Test
    fun `contains reports recorded ids`() {
        val ledger = RewardLedger()
        ledger.record("challenge:1:100")
        assertTrue(ledger.contains("challenge:1:100"))
        assertFalse(ledger.contains("challenge:1:101"))
    }

    @Test
    fun `clear removes all recorded ids`() {
        val ledger = RewardLedger()
        ledger.record("a")
        ledger.record("b")
        assertTrue(ledger.contains("a"))
        ledger.clear()
        assertFalse(ledger.contains("a"))
        assertFalse(ledger.contains("b"))
        // After clearing, the ids can be recorded again.
        assertTrue(ledger.record("a"))
    }
}
