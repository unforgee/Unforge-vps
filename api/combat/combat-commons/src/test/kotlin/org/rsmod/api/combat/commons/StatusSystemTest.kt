package org.rsmod.api.combat.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

public class StatusSystemTest {
    @Test
    public fun everyStatusEffectHasADefinition() {
        for (effect in StatusEffect.entries) {
            val def = StatusDefinitions[effect]
            assertEquals(effect, def.effect)
            assertTrue(def.tickInterval >= 0)
            assertTrue(def.stackCap > 0)
        }
    }

    @Test
    public fun freshApplicationClampsStacksToDefinitionCap() {
        val incoming = status(StatusEffect.VULNERABLE, duration = 20, stacks = 9, stackCap = 9)
        val resolved =
            StatusDefinitions.resolve(null, incoming, StatusDefinitions[StatusEffect.VULNERABLE])

        assertEquals(StatusDefinitions[StatusEffect.VULNERABLE].stackCap, resolved.stacks)
    }

    @Test
    public fun keepStrongestIgnoresWeakerPotency() {
        val def = StatusDefinitions[StatusEffect.POISON]
        val existing = status(StatusEffect.POISON, duration = 60, potency = 8, nextTickDelay = 30)
        val weaker = status(StatusEffect.POISON, duration = 90, potency = 4, nextTickDelay = 30)

        val resolved = StatusDefinitions.resolve(existing, weaker, def)

        assertEquals(8, resolved.potency)
        assertEquals(60, resolved.duration)
    }

    @Test
    public fun keepStrongestReplacesWithStrongerPotency() {
        val def = StatusDefinitions[StatusEffect.POISON]
        val existing = status(StatusEffect.POISON, duration = 60, potency = 4, nextTickDelay = 30)
        val stronger = status(StatusEffect.POISON, duration = 45, potency = 9, nextTickDelay = 30)

        val resolved = StatusDefinitions.resolve(existing, stronger, def)

        assertEquals(9, resolved.potency)
        assertEquals(45, resolved.duration)
    }

    @Test
    public fun keepStrongestRefreshesDurationOnEqualPotency() {
        val def = StatusDefinitions[StatusEffect.POISON]
        val existing = status(StatusEffect.POISON, duration = 20, potency = 6, nextTickDelay = 10)
        val equal = status(StatusEffect.POISON, duration = 80, potency = 6, nextTickDelay = 30)

        val resolved = StatusDefinitions.resolve(existing, equal, def)

        assertEquals(80, resolved.duration)
        assertEquals(6, resolved.potency)
        // A pending proc is never postponed by a re-application.
        assertEquals(10, resolved.nextTickDelay)
    }

    @Test
    public fun refreshDurationTakesLongerDurationWithoutStacking() {
        val def = StatusDefinitions[StatusEffect.FROZEN]
        val existing = status(StatusEffect.FROZEN, duration = 10, potency = 2)
        val incoming = status(StatusEffect.FROZEN, duration = 25, potency = 5)

        val resolved = StatusDefinitions.resolve(existing, incoming, def)

        assertEquals(25, resolved.duration)
        assertEquals(1, resolved.stacks)
        assertEquals(5, resolved.potency)
    }

    @Test
    public fun incrementStacksAddsUpToCapAndRefreshesDuration() {
        val def = StatusDefinitions[StatusEffect.BLEED]
        val existing =
            status(StatusEffect.BLEED, duration = 30, stacks = 4, stackCap = 5, potency = 2)
        val incoming =
            status(StatusEffect.BLEED, duration = 40, stacks = 3, stackCap = 5, potency = 1)

        val resolved = StatusDefinitions.resolve(existing, incoming, def)

        assertEquals(5, resolved.stacks)
        assertEquals(40, resolved.duration)
        assertEquals(2, resolved.potency)
    }

    @Test
    public fun extendDurationAddsToRemainingDuration() {
        val def =
            StatusDefinition(
                effect = StatusEffect.BURN,
                tickInterval = 5,
                stackPolicy = StackPolicy.EXTEND_DURATION,
            )
        val existing = status(StatusEffect.BURN, duration = 12)
        val incoming = status(StatusEffect.BURN, duration = 9)

        val resolved = StatusDefinitions.resolve(existing, incoming, def)

        assertEquals(21, resolved.duration)
    }

    @Test
    public fun replaceAlwaysDiscardsTheActiveInstance() {
        val def =
            StatusDefinition(
                effect = StatusEffect.BURN,
                tickInterval = 5,
                stackPolicy = StackPolicy.REPLACE,
                stackCap = 3,
            )
        val existing =
            status(StatusEffect.BURN, duration = 99, stacks = 3, stackCap = 3, potency = 7)
        val incoming = status(StatusEffect.BURN, duration = 10, potency = 1)

        val resolved = StatusDefinitions.resolve(existing, incoming, def)

        assertEquals(10, resolved.duration)
        assertEquals(1, resolved.potency)
    }

    @Test
    public fun advanceDecrementsDurationAndExpires() {
        val def = StatusDefinitions[StatusEffect.CHILL]
        val instance = status(StatusEffect.CHILL, duration = 2)

        val first = StatusDefinitions.advance(instance, def)
        assertNotNull(first.instance)
        assertEquals(1, first.instance!!.duration)
        assertEquals(0, first.damage)

        val second = StatusDefinitions.advance(first.instance!!, def)
        assertNull(second.instance)
    }

    @Test
    public fun periodicProcFiresAtTickInterval() {
        val def = StatusDefinitions[StatusEffect.BLEED]
        val instance = status(StatusEffect.BLEED, duration = 12, potency = 3, nextTickDelay = 5)

        var ticks = 0
        var last: StatusInstance? = instance
        var totalDamage = 0
        while (last != null) {
            val advance = StatusDefinitions.advance(last, def)
            totalDamage += advance.damage
            if (advance.damage > 0) {
                ticks++
            }
            last = advance.instance
        }

        // Duration 12 with a 5-cycle interval starting at delay 5: procs at cycles 5 and 10.
        assertEquals(2, ticks)
        assertEquals(6, totalDamage)
    }

    @Test
    public fun poisonPotencyDecaysPerProcButNeverBelowOne() {
        val def = StatusDefinitions[StatusEffect.POISON]
        var instance = status(StatusEffect.POISON, duration = 200, potency = 2, nextTickDelay = 30)

        // Advance to the first proc (cycle 30).
        repeat(29) { instance = StatusDefinitions.advance(instance, def).instance!! }
        val first = StatusDefinitions.advance(instance, def)

        assertEquals(2, first.damage)
        assertNotNull(first.instance)
        assertEquals(1, first.instance!!.potency)
        assertEquals(30, first.instance!!.nextTickDelay)

        // Advance to the second proc: potency is clamped at 1, not 0.
        instance = first.instance!!
        repeat(29) { instance = StatusDefinitions.advance(instance, def).instance!! }
        val second = StatusDefinitions.advance(instance, def)

        assertEquals(1, second.damage)
        assertEquals(1, second.instance!!.potency)
    }

    @Test
    public fun poisonExpiresWhenDurationRunsOut() {
        val def = StatusDefinitions[StatusEffect.POISON]
        var instance: StatusInstance? =
            status(StatusEffect.POISON, duration = 40, potency = 6, nextTickDelay = 30)

        var procs = 0
        while (instance != null) {
            val advance = StatusDefinitions.advance(instance, def)
            if (advance.damage > 0) {
                procs++
            }
            instance = advance.instance
        }

        assertEquals(1, procs)
    }

    @Test
    public fun venomGrowsInPotencyEachTickUpToCap() {
        val def = StatusDefinitions[StatusEffect.VENOM]
        assertEquals(30, def.tickInterval)
        assertEquals(20, def.maxPotency)

        var instance = status(StatusEffect.VENOM, duration = 300, potency = 6, nextTickDelay = 1)
        val first = StatusDefinitions.advance(instance, def)
        assertEquals(6, first.damage)
        assertEquals(8, first.instance!!.potency)

        instance = status(StatusEffect.VENOM, duration = 300, potency = 19, nextTickDelay = 1)
        val capped = StatusDefinitions.advance(instance, def)
        assertEquals(19, capped.damage)
        assertEquals(20, capped.instance!!.potency)
    }

    private fun status(
        effect: StatusEffect,
        duration: Int,
        stacks: Int = 1,
        stackCap: Int = 5,
        potency: Int = 0,
        nextTickDelay: Int = 0,
    ): StatusInstance =
        StatusInstance(
            effect = effect,
            duration = duration,
            stacks = stacks,
            stackCap = stackCap,
            potency = potency,
            nextTickDelay = nextTickDelay,
        )
}
