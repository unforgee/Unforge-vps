package org.rsmod.content.skills.hunter

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HunterRulesTest {
    @Test
    fun `trap allowance changes only at each twenty levels`() {
        assertEquals(1, HunterData.trapLimit(1))
        assertEquals(1, HunterData.trapLimit(19))
        assertEquals(2, HunterData.trapLimit(20))
        assertEquals(4, HunterData.trapLimit(79))
        assertEquals(5, HunterData.trapLimit(80))
        assertEquals(5, HunterData.trapLimit(126))
    }

    @Test
    fun `quarry cannot be caught below requirement and always retains a failure chance`() {
        for (quarry in HunterData.quarry) {
            assertEquals(0, quarry.chance(quarry.level - 1))
            assertTrue(quarry.chance(quarry.level) in 1..255)
            assertTrue(quarry.chance(99) in quarry.chance(quarry.level)..255)
        }
    }

    @Test
    fun `jar captures and trap captures use distinct resource lifecycles`() {
        assertTrue(
            HunterData.quarry.filter { it.jar != null }.all { it.fullTrap == 0 && it.tool == 10010 }
        )
        assertTrue(
            HunterData.quarry
                .filter { it.jar == null }
                .all { it.fullTrap > 0 && it.tool in listOf(10006, 10008) }
        )
        assertEquals(1, HunterData.quarry.minOf { it.level })
    }
}
