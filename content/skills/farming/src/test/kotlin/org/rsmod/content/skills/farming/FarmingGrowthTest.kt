package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FarmingGrowthTest {
    @Test
    fun `crops mature at their deadline and remain harvestable after a long logout`() {
        for (crop in FarmingData.crops) {
            assertEquals(0, crop.stage(1000, 999))
            assertEquals(0, crop.stage(1000, 1000))
            assertTrue(crop.stage(1000, 1000 + crop.minutes - 1) < crop.stages)
            assertEquals(crop.stages, crop.stage(1000, 1000 + crop.minutes))
            assertEquals(crop.stages, crop.stage(1000, Int.MAX_VALUE))
        }
    }

    @Test
    fun `herbs consume one seed and allotments consume three`() {
        assertEquals(1, FarmingData.bySeed.getValue(5295).seedCount)
        assertEquals(3, FarmingData.bySeed.getValue(5318).seedCount)
    }

    @Test
    fun `crop progression reaches cache harvest states`() {
        assertEquals(10, FarmingData.bySeed.getValue(5318).visual(1000, 1040))
        assertEquals(36, FarmingData.bySeed.getValue(5295).visual(1000, 1080))
        assertEquals(99, FarmingData.bySeed.getValue(5304).visual(1000, 1080))
    }
}
