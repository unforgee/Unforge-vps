package org.rsmod.content.skills.herblore.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PotionTableTest {
    private val containers = setOf(PotionTable.VIAL, 1980)

    @Test
    fun `every potion does something`() {
        for ((id, potion) in PotionTable.all) {
            assertTrue(potion.effects.isNotEmpty()) { "Potion $id has no effect" }
        }
    }

    @Test
    fun `dose chains end in an empty container and never loop`() {
        for ((id, potion) in PotionTable.all) {
            val seen = mutableSetOf(id)
            var cursor: Int? = potion.next
            while (cursor != null) {
                if (cursor in containers) {
                    break
                }
                val next = PotionTable[cursor]
                assertNotNull(next) { "Potion $id hands back unknown obj $cursor" }
                assertTrue(seen.add(cursor)) { "Potion $id loops back to $cursor" }
                cursor = next!!.next
            }
        }
    }

    @Test
    fun `a dose is never handed back to itself`() {
        for ((id, potion) in PotionTable.all) {
            assertTrue(potion.next != id) { "Potion $id hands itself back" }
        }
    }

    @Test
    fun `attack potion boosts attack by ten percent plus three on every dose`() {
        val expected = PotionEffect.Boost(PotionStat.ATTACK, 3, 10)
        for (id in listOf(2428, 121, 123, 125)) {
            assertEquals(listOf(expected), PotionTable[id]?.effects) { "Attack potion $id" }
        }
        assertEquals(123, PotionTable[121]?.next)
        assertEquals(PotionTable.VIAL, PotionTable[125]?.next)
    }

    @Test
    fun `saradomin brew heals hitpoints and drains the offensive stats`() {
        val brew = PotionTable[6685]
        assertNotNull(brew)
        assertTrue(brew!!.effects.contains(PotionEffect.Boost(PotionStat.HITPOINTS, 2, 15)))
        assertTrue(brew.effects.contains(PotionEffect.Boost(PotionStat.DEFENCE, 2, 20)))
        assertTrue(brew.effects.contains(PotionEffect.Drain(PotionStat.ATTACK, 2, 10)))
    }

    @Test
    fun `antipoison runs out into an empty vial`() {
        assertEquals(PotionTable.VIAL, PotionTable[179]?.next)
        assertEquals(PotionEffect.CurePoison, PotionTable[2446]?.effects?.single())
        assertNull(PotionTable[0])
    }
}
