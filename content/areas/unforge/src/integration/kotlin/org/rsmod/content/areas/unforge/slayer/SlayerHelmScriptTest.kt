package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj

class SlayerHelmScriptTestDeps @Inject constructor()

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class SlayerHelmScriptTest {
    @Test
    fun GameTestState.`combine without the unlock keeps every component`() =
        runInjectedGameTest(SlayerHelmScriptTestDeps::class, null, SlayerHelmScript::class) {
            giveParts()

            useFacemaskOnEarmuffs()

            for (part in PARTS) {
                assertEquals(1, player.count(part), "Part should remain: $part")
            }
            assertEquals(0, player.count(objs.slayer_helm))
            assertMessageSent(
                "You must learn how to craft Slayer helmets from a Slayer master first."
            )
        }

    @Test
    fun GameTestState.`unlocked player crafts the helm and loses all six parts`() =
        runInjectedGameTest(SlayerHelmScriptTestDeps::class, null, SlayerHelmScript::class) {
            giveParts()
            player.setSlayerUnlock(CwSlayerUnlock.MALEVOLENT_MASQUERADE, on = true)

            useFacemaskOnEarmuffs()

            assertEquals(1, player.count(objs.slayer_helm))
            for (part in PARTS) {
                assertEquals(0, player.count(part), "Part should be consumed: $part")
            }
            assertMessageSent("You piece the items together and form a Slayer helmet.")
        }

    @Test
    fun GameTestState.`missing component blocks the craft`() =
        runInjectedGameTest(SlayerHelmScriptTestDeps::class, null, SlayerHelmScript::class) {
            giveParts()
            player.inv[5] = null // drop the enchanted gem
            player.setSlayerUnlock(CwSlayerUnlock.MALEVOLENT_MASQUERADE, on = true)

            useFacemaskOnEarmuffs()

            assertEquals(0, player.count(objs.slayer_helm))
            assertEquals(1, player.count(objs.black_mask))
            assertEquals(1, player.count(objs.earmuffs))
        }

    private fun GameTestScope.giveParts() {
        PARTS.forEachIndexed { slot, type -> player.inv[slot] = InvObj(type) }
    }

    private fun GameTestScope.useFacemaskOnEarmuffs() {
        val handler =
            checkNotNull(eventBus.suspend[HeldUDefaultEvents.Type::class.java, objs.facemask.id]) {
                "SlayerHelmScript did not register its HeldU handler for the facemask."
            }
        player.withProtectedAccess {
            handler(
                this,
                HeldUDefaultEvents.Type(
                    first = objTypes[objs.facemask],
                    firstSlot = 2,
                    second = objTypes[objs.earmuffs],
                    secondSlot = 1,
                ),
            )
        }
    }

    private companion object {
        private val PARTS =
            listOf(
                objs.black_mask,
                objs.earmuffs,
                objs.facemask,
                objs.nose_peg,
                objs.spiny_helmet,
                objs.enchanted_gem,
            )
    }
}
