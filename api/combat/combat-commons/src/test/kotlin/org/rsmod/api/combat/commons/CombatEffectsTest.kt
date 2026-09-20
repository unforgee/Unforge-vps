package org.rsmod.api.combat.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class CombatEffectsTest {
    @Test
    public fun elementValuesAreStable() {
        assertEquals(
            listOf("NONE", "PHYSICAL", "FIRE", "WATER", "ICE", "BLOOD", "SHADOW", "SMOKE"),
            Element.entries.map { it.name },
        )
    }

    @Test
    public fun statusEffectValuesAreStable() {
        assertEquals(
            listOf(
                "POISON",
                "BLEED",
                "BURN",
                "CHILL",
                "SLOW",
                "FROZEN",
                "VULNERABLE",
                "WEAKEN",
                "BLIND",
                "VENOM",
            ),
            StatusEffect.entries.map { it.name },
        )
    }

    @Test
    public fun attackElementDefaultsToNone() {
        assertEquals(Element.NONE, AttackElement().element)
    }

    @Test
    public fun statusDurationAndStackCapAreRetained() {
        val status = StatusInstance(StatusEffect.BURN, duration = 12, stacks = 3, stackCap = 5)

        assertEquals(12, status.duration)
        assertEquals(3, status.stacks)
        assertEquals(5, status.stackCap)
    }

    @Test
    public fun statusStacksCannotExceedStackCap() {
        assertFailsWith<IllegalArgumentException> {
            StatusInstance(StatusEffect.POISON, duration = 10, stacks = 4, stackCap = 3)
        }
    }
}
