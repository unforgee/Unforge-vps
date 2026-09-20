package org.rsmod.content.skillcapes

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.back
import org.rsmod.api.player.events.interact.HeldContentEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

class MaxCapeScriptTestDeps @Inject constructor()

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class MaxCapeScriptTest {
    @Test
    fun GameTestState.`max cape equip denied while any stat is below max`() =
        runInjectedGameTest(MaxCapeScriptTestDeps::class, null, MaxCapeScript::class) {
            player.inv[0] = InvObj(objs.max_cape_inv)

            opMaxCape()

            assertNull(player.back)
            assertEquals(InvObj(objs.max_cape_inv), player.inv[0])
            assertMessageSent("You need to have all stats at level 99 to wear the Max cape.")
        }

    @Test
    fun GameTestState.`max cape equips once every stat is maxed`() =
        runInjectedGameTest(MaxCapeScriptTestDeps::class, null, MaxCapeScript::class) {
            setMaxLevels(player)
            player.inv[0] = InvObj(objs.max_cape_inv)

            opMaxCape()

            // The worn variant is produced through the normal equip transform pipeline.
            assertEquals(InvObj(objs.max_cape_worn), player.back)
            assertNull(player.inv[0])
        }

    @Test
    fun GameTestState.`a single unmaxed stat keeps the cape off`() =
        runInjectedGameTest(MaxCapeScriptTestDeps::class, null, MaxCapeScript::class) {
            setMaxLevels(player)
            val stat = cacheTypes.stats.values.first()
            player.statMap.setBaseLevel(stat, (stat.maxLevel - 1).toByte())
            player.statMap.setCurrentLevel(stat, (stat.maxLevel - 1).toByte())
            player.inv[0] = InvObj(objs.max_cape_inv)

            opMaxCape()

            assertNull(player.back)
            assertEquals(InvObj(objs.max_cape_inv), player.inv[0])
        }

    private fun GameTestState.setMaxLevels(player: Player) {
        for (stat in cacheTypes.stats.values) {
            player.statMap.setBaseLevel(stat, stat.maxLevel.toByte())
            player.statMap.setCurrentLevel(stat, stat.maxLevel.toByte())
        }
    }

    private fun GameTestScope.opMaxCape() {
        val handler =
            checkNotNull(eventBus.suspend[HeldContentEvents.Op2::class.java, content.max_cape.id]) {
                "MaxCapeScript did not register its content group op handler."
            }
        player.withProtectedAccess {
            val cape = player.inv[0] ?: error("Test setup: no max cape in slot 0.")
            handler(this, HeldContentEvents.Op2(0, cape, objTypes[cape], player.inv))
        }
    }
}
