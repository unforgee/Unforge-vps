package org.rsmod.content.skills.fishing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FishingMethodTest {
    @Test
    fun `river and sea bait cannot produce each other's fish`() {
        val river = FishingMethod.forSpot("0_50_50_freshfish")!!.second!!
        val sea = FishingMethod.forSpot("0_50_49_saltfish")!!.second!!
        assertEquals(listOf(349), river.catches.map { it.item })
        assertEquals(setOf(327, 345), sea.catches.map { it.item }.toSet())
    }

    @Test
    fun `large net and small net have different tools and catches`() {
        val small = FishingMethod.forSpot("saltfish")!!.first
        val big = FishingMethod.forSpot("memberfish")!!.first
        assertEquals(303, small.tool)
        assertEquals(305, big.tool)
        assertTrue(
            small.catches.map { it.item }.intersect(big.catches.map { it.item }.toSet()).isEmpty()
        )
    }

    @Test
    fun `tutorial and special minigame spots are not rebound`() {
        for (symbol in
            listOf(
                "0_48_48_newbiefishing",
                "tempoross_harpoonfish_fishingspot_north",
                "fishing_spot_aerial",
            )) assertNull(FishingMethod.forSpot(symbol))
    }

    @Test
    fun `unlock chance is positive but never guaranteed and increases with level`() {
        for (fish in FishingMethod.entries.flatMap { it.catches }) {
            assertEquals(0, fish.chance(fish.level - 1))
            assertTrue(fish.chance(fish.level) in 1..255)
            assertTrue(fish.chance(99) in fish.chance(fish.level)..255)
        }
    }
}
