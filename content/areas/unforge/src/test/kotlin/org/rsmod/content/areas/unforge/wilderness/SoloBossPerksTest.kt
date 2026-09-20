package org.rsmod.content.areas.unforge.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals

class SoloBossPerksTest {
    @Test
    fun `arena perks are capped at 100 levels`() {
        assertEquals(10, SoloBossPerkService.costForLevel(0))
        assertEquals(510, SoloBossPerkService.costForLevel(100))
        assertEquals(50_000, SoloBossPerkService.bonusBpsForLevel(100))
        assertEquals(50_000, SoloBossPerkService.bonusBpsForLevel(150))
    }

    @Test
    fun `arena perk bonus scales by five percent per level`() {
        assertEquals(2_500, SoloBossPerkService.bonusBpsForLevel(5))
        assertEquals(25_000, SoloBossPerkService.bonusBpsForLevel(50))
    }
}
