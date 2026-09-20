package org.rsmod.content.skills.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.HashedObjType
import org.rsmod.game.type.stat.HashedStatType

class MakeRequestTest {
    private val input = HashedObjType(null, "input", 1)
    private val output = HashedObjType(null, "output", 2)
    private val skill = HashedStatType(null, "crafting", 12)

    private fun request() = MakeRequest(output, mapOf(input to 1), "CRAFTING", skill, 15.0)

    @Test
    fun `recipe preserves requirement costs output and xp`() {
        val recipe =
            MakeRecipe(output, "Output", 40, 22.5, listOf(MakeIngredient(input, 2)), produced = 15)
        val request = MakeRequest.of(recipe, "CRAFTING", skill)
        assertEquals(40, request.level)
        assertEquals(mapOf(input to 2), request.consumed)
        assertEquals(15, request.produced)
        assertEquals(22.5, request.xp)
    }

    @Test
    fun `nonpositive cycle output and ingredient counts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { request().copy(cycles = 0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(produced = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(consumed = mapOf(input to 0))
        }
        assertThrows(IllegalArgumentException::class.java) { request().copy(consumed = emptyMap()) }
    }

    @Test
    fun `invalid xp and unrelated saved ingredients are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { request().copy(xp = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(xp = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(saveObj = output) }
    }
}
