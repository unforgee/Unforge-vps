package org.rsmod.api.npc.pet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

public class PetCombatRuntimeTest {
    private val policy = PetRuntimePolicy(setOf(10), setOf(20), 2.0, 1.0, 1.0, 3.0)
    private val runtime = PetCombatRuntime(policy)
    private val boss = policy.classify(10, true)!!
    private val target = PetTarget(100, false, true, true, 2)
    private val profile = PetAttackProfile(1, 2, 3, 2.0, 9, 5, .5)

    @Test
    public fun `follow changes to combat only for owners valid target`() {
        assertEquals(PetState.COMBAT, runtime.state(boss, PetCombatContext(target, false)))
        assertEquals(PetState.FOLLOW, runtime.state(boss, PetCombatContext(null, false)))
    }

    @Test
    public fun `wilderness and owner lifecycle force follow`() {
        assertEquals(PetState.FOLLOW, runtime.state(boss, PetCombatContext(target, true)))
        assertEquals(
            PetState.FOLLOW,
            runtime.state(boss, PetCombatContext(target, false, ownerOnline = false)),
        )
    }

    @Test
    public fun `attack uses owner target and clamps visual area and chain`() {
        val plan =
            runtime.attack(
                boss,
                PetCombatContext(target, false),
                (101..110).map { PetTarget(it, false, true, true, 3) },
                profile,
            )!!
        assertEquals(listOf(100, 101, 102), plan.targetIds)
        assertEquals(3, plan.profile.chainMaxTargets)
        assertEquals(5, plan.profile.aoeRadius)
        assertEquals(2.0, plan.damageMultipliers.first())
        assertEquals(1.0, plan.damageMultipliers[1])
    }

    @Test
    public fun `players unreachable and dead targets are rejected`() {
        val bad = PetTarget(200, true, true, true, 1)
        assertNull(runtime.attack(boss, PetCombatContext(bad, false), listOf(target), profile))
    }
}
