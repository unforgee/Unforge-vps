package org.rsmod.api.npc.pet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

public class PetRuntimePolicyTest {
    private val policy = PetRuntimePolicy(setOf(10), setOf(20), 2.0, 1.5, 3.0, 4.0)

    @Test
    public fun `boss pet is combat outside wilderness`() {
        val pet = policy.classify(10, true)!!
        assertEquals(PetKind.BossCombat, pet.kind)
        assertEquals(2.0, policy.combatProfile(pet, false)!!.damageMultiplier)
    }

    @Test
    public fun `boss pet combat is blocked in wilderness`() {
        val pet = policy.classify(10, true)!!
        assertNull(policy.combatProfile(pet, true))
    }

    @Test
    public fun `skilling pet scales xp but does not get combat profile`() {
        val pet = policy.classify(20, true)!!
        assertEquals(PetKind.Skilling, pet.kind)
        assertEquals(400, policy.skillXp(100, pet))
        assertNull(policy.combatProfile(pet, false))
    }

    @Test
    public fun `vanilla follower remains vanilla`() {
        val pet = policy.classify(30, true)!!
        assertEquals(PetKind.Vanilla, pet.kind)
        assertEquals(100, policy.skillXp(100, pet))
        assertNull(policy.combatProfile(pet, false))
    }
}
