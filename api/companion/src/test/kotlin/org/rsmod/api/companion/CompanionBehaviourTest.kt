package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

public class CompanionBehaviourTest {
    @Test
    public fun `serialize deserialize roundtrip preserves every field`() {
        val behaviour =
            CompanionBehaviour(
                targetPriority = CompanionTargetPriority.NEAREST_HOSTILE,
                followDistance = 5,
                autoTaunt = false,
                autoDefend = false,
                autoHeal = true,
                healBelowPercent = 45,
                autoBuff = false,
                catchUpTeleport = false,
            )
        assertEquals(behaviour, CompanionBehaviour.deserialize(behaviour.serialize()))
    }

    @Test
    public fun `default serialized form matches migration column default`() {
        assertEquals("OWNER_TARGET;2;1;1;1;80;1;1", CompanionBehaviour.DEFAULT_SERIALIZED)
    }

    @Test
    public fun `null blank and legacy short encodings fall back to safe defaults`() {
        val defaults = CompanionBehaviour()
        assertEquals(defaults, CompanionBehaviour.deserialize(null))
        assertEquals(defaults, CompanionBehaviour.deserialize(""))
        assertEquals(defaults, CompanionBehaviour.deserialize("   "))
        // Rows written before catchUpTeleport existed: explicit values are kept and the
        // missing trailing field defaults to enabled.
        assertEquals(
            CompanionBehaviour(
                targetPriority = CompanionTargetPriority.OWNER_TARGET,
                followDistance = 3,
                autoTaunt = false,
            ),
            CompanionBehaviour.deserialize("OWNER_TARGET;3;0;1;1;80;1"),
        )
        // Unknown priority name resets the row entirely.
        assertEquals(defaults, CompanionBehaviour.deserialize("WANDER;2;1;1;1;80;1;1"))
    }

    @Test
    public fun `out of range stored values reset to defaults instead of crashing`() {
        assertEquals(
            CompanionBehaviour(),
            CompanionBehaviour.deserialize("OWNER_TARGET;99;1;1;1;80;1;1"),
        )
        assertEquals(
            CompanionBehaviour(),
            CompanionBehaviour.deserialize("OWNER_TARGET;2;1;1;1;150;1;1"),
        )
    }

    @Test
    public fun `constructor rejects out of range values`() {
        assertThrows(IllegalArgumentException::class.java) {
            CompanionBehaviour(followDistance = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompanionBehaviour(followDistance = 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompanionBehaviour(healBelowPercent = 100)
        }
    }

    @Test
    public fun `setBehaviour persists through the service and survives into tick`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        service.setCombatMode(42, c.id, CompanionCombatMode.DEFENSIVE)

        val ownerTarget = CompanionTarget(7, false, true, true, 3, 101, 100, 0)
        val attacker = CompanionTarget(9, false, true, true, 2, 102, 100, 0, attackingOwner = true)
        val context =
            CompanionCombatContext(
                ownerOnline = true,
                ownerAlive = true,
                sameRegion = true,
                inWilderness = false,
                ownerTarget = ownerTarget,
                ownerUnderAttackTarget = attacker,
                ownerX = 100,
                ownerY = 100,
            )

        // Default OWNER_TARGET priority engages the owner's own target.
        assertEquals(listOf(7), service.tick(42, c.id, context, emptyList())!!.targetIds)

        // OWNER_ATTACKER priority prefers whatever is attacking the owner.
        service.setBehaviour(
            42,
            c.id,
            CompanionBehaviour(targetPriority = CompanionTargetPriority.OWNER_ATTACKER),
        )
        assertEquals(listOf(9), service.tick(42, c.id, context, emptyList())!!.targetIds)
    }

    @Test
    public fun `nearest hostile priority overrides owner target in defensive mode`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        service.setCombatMode(42, c.id, CompanionCombatMode.DEFENSIVE)
        service.setBehaviour(
            42,
            c.id,
            CompanionBehaviour(targetPriority = CompanionTargetPriority.NEAREST_HOSTILE),
        )

        val context =
            CompanionCombatContext(
                ownerOnline = true,
                ownerAlive = true,
                sameRegion = true,
                inWilderness = false,
                ownerTarget = CompanionTarget(7, false, true, true, 6, 106, 100, 0),
                ownerX = 100,
                ownerY = 100,
            )
        val near = CompanionTarget(3, false, true, true, 1, 101, 100, 0)
        val action = service.tick(42, c.id, context, listOf(near))!!
        // The picked target leads the candidate list for single-target roles; DPS still gets
        // the rest of the nearby list appended after the priority pick.
        assertEquals(3, action.targetIds.first())
    }

    @Test
    public fun `passive mode ignores every target priority`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        service.setCombatMode(42, c.id, CompanionCombatMode.PASSIVE)
        service.setBehaviour(
            42,
            c.id,
            CompanionBehaviour(targetPriority = CompanionTargetPriority.NEAREST_HOSTILE),
        )
        val context =
            CompanionCombatContext(
                ownerOnline = true,
                ownerAlive = true,
                sameRegion = true,
                inWilderness = false,
                ownerTarget = CompanionTarget(7, false, true, true, 2),
                ownerX = 100,
                ownerY = 100,
            )
        assertNull(
            service.tick(42, c.id, context, listOf(CompanionTarget(1, false, true, true, 1)))
        )
    }

    @Test
    public fun `aggressive nearest hostile is deterministic by distance then coords`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        service.setCombatMode(42, c.id, CompanionCombatMode.AGGRESSIVE)
        service.setBehaviour(
            42,
            c.id,
            CompanionBehaviour(targetPriority = CompanionTargetPriority.NEAREST_HOSTILE),
        )
        val context =
            CompanionCombatContext(
                ownerOnline = true,
                ownerAlive = true,
                sameRegion = true,
                inWilderness = false,
                ownerTarget = null,
                ownerX = 100,
                ownerY = 100,
            )
        val far = CompanionTarget(9, false, true, true, 5, 105, 100, 0)
        val nearB = CompanionTarget(4, false, true, true, 2, 102, 100, 0)
        val nearA = CompanionTarget(8, false, true, true, 2, 101, 100, 0)
        val action = service.tick(42, c.id, context, listOf(far, nearB, nearA))!!
        // Same distance -> lowest x wins, so id 8 at x=101 precedes id 4 at x=102.
        assertEquals(8, action.targetIds.first())
    }
}
