package org.rsmod.api.player.perk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PerkServiceTest {
    private val service = PerkService()

    @Test
    fun `rank cost doubles for every next rank`() {
        assertEquals(1L, service.nextRankCost(0, 1L))
        assertEquals(2L, service.nextRankCost(1, 1L))
        assertEquals(4L, service.nextRankCost(2, 1L))
        assertEquals(1L shl 49, service.nextRankCost(49, 1L))
    }

    @Test
    fun `rank 50 has no next purchase`() {
        assertEquals(Perk.MAX_PERK_RANK, 50)
        assertEquals(0L, service.nextRankCost(Perk.MAX_PERK_RANK, 1L))
    }

    @Test
    fun `cost calculation saturates instead of overflowing`() {
        assertEquals(Long.MAX_VALUE, service.nextRankCost(49, Long.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) {
            service.nextRankCost(Perk.MAX_PERK_RANK + 1, 1L)
        }
    }

    @Test
    fun `legacy triangular conversion still reaches the new cap`() {
        assertEquals(1, service.levelForXp(1))
        assertEquals(2, service.levelForXp(3))
        assertEquals(50, service.levelForXp(service.xpForLevel(50)))
    }
}
