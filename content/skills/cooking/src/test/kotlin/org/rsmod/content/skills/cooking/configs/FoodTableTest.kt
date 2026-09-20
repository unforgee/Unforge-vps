package org.rsmod.content.skills.cooking.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoodTableTest {
    @Test
    fun `every food heals for a positive amount`() {
        assertTrue(FoodTable.DEFAULT_HEAL > 0)
        for ((id, food) in FoodTable.all) {
            assertTrue(food.heal > 0) { "Food $id heals ${food.heal}" }
        }
    }

    @Test
    fun `multi bite chains terminate and hand back a known food`() {
        for ((id, food) in FoodTable.all) {
            val next = food.next ?: continue
            assertTrue(next != id) { "Food $id hands itself back" }
            assertNotNull(FoodTable[next]) { "Food $id hands back unknown obj $next" }

            val seen = mutableSetOf(id)
            var cursor: Int? = next
            while (cursor != null) {
                assertTrue(seen.add(cursor)) { "Food $id loops back to $cursor" }
                cursor = FoodTable[cursor]?.next
            }
        }
    }

    @Test
    fun `the servers cooked fish and starter food are covered`() {
        val starters = listOf(379, 385, 373, 361, 329, 333, 315, 319, 325, 347, 351, 355, 365)
        for (id in starters) {
            assertNotNull(FoodTable[id]) { "Cooked food $id has no heal value" }
        }
    }

    @Test
    fun `heals are read back by id`() {
        assertEquals(EdibleFood(12), FoodTable[379])
        assertEquals(EdibleFood(20), FoodTable[385])
        assertNull(FoodTable[0])
    }
}
