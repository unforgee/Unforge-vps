package org.rsmod.content.pvmpoints

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.UnpackedNpcType

class PvmTestDeps @Inject constructor(val pvm: PvmPoints)

/**
 * The kill-to-points path, exercised through the real [NpcKilledEvent] subscription: the same event
 * `NpcDeath.spawnDeathDrops` publishes on every attributed kill is what the tests emit, so the
 * handler is verified end-to-end rather than simulated.
 */
class PvmPointsScriptTest {
    private fun GameTestScope.spawnLevel(range: IntRange): Npc {
        val type: UnpackedNpcType =
            npcTypes.values.firstOrNull { it.vislevel in range }
                ?: error("No npc type with vislevel in $range")
        return spawnNpc(player.coords, type)
    }

    @Test
    fun GameTestState.`kill awards points scaled by npc difficulty`() =
        runInjectedGameTest(PvmTestDeps::class, null, PvmPointsScript::class) { deps ->
            val npc = spawnLevel(50..99)
            eventBus.publish(NpcKilledEvent(npc, player))

            assertEquals(2, deps.pvm.balance(player))
            assertEquals(2, player.vars[pvm_point_varps.points])
            assertMessageSent("<col=ffb84d>You earn 2 PvM point(s).</col>")
        }

    @Test
    fun GameTestState.`trivial mobs award nothing and stay silent`() =
        runInjectedGameTest(PvmTestDeps::class, null, PvmPointsScript::class) { deps ->
            val npc = spawnLevel(1..9)
            eventBus.publish(NpcKilledEvent(npc, player))

            assertEquals(0, deps.pvm.balance(player))
            assertMessageNotSent("<col=ffb84d>You earn 0 PvM point(s).</col>")
            assertMessageNotSent("<col=ffb84d>You earn 1 PvM point(s).</col>")
        }

    @Test
    fun GameTestState.`points accumulate across kills`() =
        runInjectedGameTest(PvmTestDeps::class, null, PvmPointsScript::class) { deps ->
            eventBus.publish(NpcKilledEvent(spawnLevel(10..49), player))
            eventBus.publish(NpcKilledEvent(spawnLevel(50..99), player))
            eventBus.publish(NpcKilledEvent(spawnLevel(10..49), player))

            assertEquals(1 + 2 + 1, deps.pvm.balance(player))
        }

    @Test
    fun GameTestState.`only the attributed killer is credited`() =
        runInjectedGameTest(PvmTestDeps::class, null, PvmPointsScript::class) { deps ->
            val bystander = registerPlayer()
            eventBus.publish(NpcKilledEvent(spawnLevel(50..99), player))

            assertEquals(2, deps.pvm.balance(player))
            assertEquals(0, deps.pvm.balance(bystander))
        }

    @Test
    fun GameTestState.`spend is all-or-nothing`() =
        runInjectedGameTest(PvmTestDeps::class, null, PvmPointsScript::class) { deps ->
            eventBus.publish(NpcKilledEvent(spawnLevel(100..199), player))
            assertEquals(5, deps.pvm.balance(player))

            assertFalse(deps.pvm.spend(player, 6))
            assertEquals(5, deps.pvm.balance(player))
            assertFalse(deps.pvm.spend(player, 0))

            assertTrue(deps.pvm.spend(player, 5))
            assertEquals(0, deps.pvm.balance(player))
        }
}
