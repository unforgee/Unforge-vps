package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType

class SlayerHelmUpgradeScriptTestDeps @Inject constructor()

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class SlayerHelmUpgradeScriptTest {
    @Test
    fun GameTestState.`recolor without the unlock keeps both items`() =
        runInjectedGameTest(
            SlayerHelmUpgradeScriptTestDeps::class,
            null,
            SlayerHelmRecolorScript::class,
        ) {
            give(objs.vorkath_head, objs.slayer_helm)

            useOn(objs.vorkath_head, objs.slayer_helm)

            assertEquals(1, player.count(objs.vorkath_head))
            assertEquals(1, player.count(objs.slayer_helm))
            assertEquals(0, player.count(objs.slayer_helm_turquoise))
            assertMessageSent(
                "You must learn how to recolour a Slayer helmet from a Slayer master first."
            )
        }

    @Test
    fun GameTestState.`undead head unlock recolours to turquoise`() =
        runInjectedGameTest(
            SlayerHelmUpgradeScriptTestDeps::class,
            null,
            SlayerHelmRecolorScript::class,
        ) {
            give(objs.vorkath_head, objs.slayer_helm)
            player.setSlayerUnlock(CwSlayerUnlock.UNDEAD_HEAD, true)

            useOn(objs.vorkath_head, objs.slayer_helm)

            assertEquals(1, player.count(objs.slayer_helm_turquoise))
            assertEquals(0, player.count(objs.vorkath_head))
            assertEquals(0, player.count(objs.slayer_helm))
        }

    @Test
    fun GameTestState.`recoloring an imbued helm keeps the imbue`() =
        runInjectedGameTest(
            SlayerHelmUpgradeScriptTestDeps::class,
            null,
            SlayerHelmRecolorScript::class,
        ) {
            give(objs.twisted_horns, objs.slayer_helm_i)
            player.setSlayerUnlock(CwSlayerUnlock.TWISTED_VISION, true)

            useOn(objs.twisted_horns, objs.slayer_helm_i)

            assertEquals(1, player.count(objs.slayer_helm_i_twisted))
            assertEquals(0, player.count(objs.twisted_horns))
            assertEquals(0, player.count(objs.slayer_helm_i))
        }

    @Test
    fun GameTestState.`infernal cape makes the TzKal helm without an unlock`() =
        runInjectedGameTest(
            SlayerHelmUpgradeScriptTestDeps::class,
            null,
            SlayerHelmRecolorScript::class,
        ) {
            give(objs.infernal_cape, objs.slayer_helm)

            useOn(objs.infernal_cape, objs.slayer_helm)

            assertEquals(1, player.count(objs.slayer_helm_zuk))
            assertEquals(0, player.count(objs.infernal_cape))
            assertEquals(0, player.count(objs.slayer_helm))
        }

    @Test
    fun GameTestState.`gem imbues the helm for slayer points`() =
        runInjectedGameTest(SlayerHelmUpgradeScriptTestDeps::class, null, SlayerHelmScript::class) {
            give(objs.enchanted_gem, objs.slayer_helm)
            setPoints(500)

            useGemOn(objs.slayer_helm)

            assertEquals(1, player.count(objs.slayer_helm_i))
            assertEquals(0, player.count(objs.slayer_helm))
            assertEquals(1, player.count(objs.enchanted_gem))
            assertEquals(250, player.vars[UnforgeSlayerVarps.points])
        }

    @Test
    fun GameTestState.`imbue without enough points keeps the helm`() =
        runInjectedGameTest(SlayerHelmUpgradeScriptTestDeps::class, null, SlayerHelmScript::class) {
            give(objs.enchanted_gem, objs.slayer_helm)
            setPoints(100)

            useGemOn(objs.slayer_helm)

            assertEquals(0, player.count(objs.slayer_helm_i))
            assertEquals(1, player.count(objs.slayer_helm))
            assertEquals(100, player.vars[UnforgeSlayerVarps.points])
            assertMessageSent("You need 250 Slayer reward points to imbue your Slayer helmet.")
        }

    private fun GameTestScope.give(vararg types: ObjType) {
        types.forEachIndexed { slot, type -> player.inv[slot] = InvObj(type) }
    }

    private fun GameTestScope.setPoints(points: Int) {
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.points, points)
    }

    /** Fires the pairwise HeldU handler; [material] is always `first` per registration. */
    private fun GameTestScope.useOn(material: ObjType, helm: ObjType) {
        val key = EventBus.composeLongKey(material.id, helm.id)
        val handler =
            checkNotNull(eventBus.suspend[HeldUEvents.Type::class.java, key]) {
                "No HeldU handler for $material on $helm"
            }
        player.withProtectedAccess {
            handler(
                this,
                HeldUEvents.Type(
                    first = objTypes[material],
                    firstSlot = 0,
                    second = objTypes[helm],
                    secondSlot = 1,
                ),
            )
        }
    }

    private fun GameTestScope.useGemOn(target: ObjType) {
        val handler =
            checkNotNull(
                eventBus.suspend[HeldUDefaultEvents.Type::class.java, objs.enchanted_gem.id]
            ) {
                "SlayerHelmScript did not register a HeldU handler for the gem."
            }
        player.withProtectedAccess {
            handler(
                this,
                HeldUDefaultEvents.Type(
                    first = objTypes[objs.enchanted_gem],
                    firstSlot = 0,
                    second = objTypes[target],
                    secondSlot = 1,
                ),
            )
        }
    }
}
