package org.rsmod.api.player.perk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

class PerkServiceIntegrationTest {
    @Test
    fun GameTestState.`each train call buys one rank and doubles the next cost`() = runGameTest {
        val service = PerkService()
        player.setVarp(PerkVarps.rankFormat, 1)
        service.addPoints(player, 7L)

        assertEquals(1L, service.train(player, Perk.Power))
        assertEquals(1, service.rank(player, Perk.Power))
        assertEquals(6L, service.pointsLong(player))

        assertEquals(2L, service.train(player, Perk.Power))
        assertEquals(2, service.rank(player, Perk.Power))
        assertEquals(4L, service.pointsLong(player))

        assertEquals(4L, service.train(player, Perk.Power))
        assertEquals(3, service.rank(player, Perk.Power))
        assertEquals(0L, service.pointsLong(player))
        assertEquals(0L, service.train(player, Perk.Power))
    }

    @Test
    fun GameTestState.`rank 49 can reach 50 but never 51`() = runGameTest {
        val service = PerkService()
        player.setVarp(PerkVarps.rankFormat, 1)
        player.setVarp(Perk.Power.xpVarp, 49)
        service.addPoints(player, 1L shl 49)

        assertTrue(service.train(player, Perk.Power) > 0L)
        assertEquals(Perk.MAX_PERK_RANK, service.rank(player, Perk.Power))
        assertEquals(0L, service.train(player, Perk.Power))
        assertEquals(Perk.MAX_PERK_RANK, service.rank(player, Perk.Power))
    }

    @Test
    fun GameTestState.`insufficient points do not mutate rank or balance`() = runGameTest {
        val service = PerkService()
        player.setVarp(PerkVarps.rankFormat, 1)
        service.addPoints(player, 1L)

        assertEquals(0L, service.train(player, Perk.Vitality))
        assertEquals(0, service.rank(player, Perk.Vitality))
        assertEquals(1L, service.pointsLong(player))
    }
}
