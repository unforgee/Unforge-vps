package org.rsmod.content.interfaces.combat.tab

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.combat.tab.configs.combat_components
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.comp.ComponentType

/**
 * The combat tab buttons are static components, so their clicks arrive with `comsub == -1` and are
 * validated against the cache-baked events. Only the op-carrying children have Op1 baked - the
 * `combat_interface:0`..`:3`, `:retaliate` and `:special_attack` layers have no ops and no events,
 * so a handler registered on one of them can never fire.
 */
class CombatTabStanceButtonTest {
    private val ComponentType.debugName: String
        get() = "$interfaceId:$component"

    @Test
    fun GameTestState.`stance buttons set com_mode`() =
        runInjectedGameTest(
            StanceTestDependencies::class,
            StanceTestModule,
            CombatTabScript::class,
            WeaponAttackStylesScript::class,
        ) {
            // An axe has all four stances, so no click gets validated back to `Stance1`.
            player.righthand = InvObj(objs.bronze_axe)
            player.ifOpenOverlay(interfaces.combat_interface, components.toplevel_target_side0)

            val buttons =
                listOf(
                    combat_components.stance1 to CombatStance.Stance1,
                    combat_components.stance2 to CombatStance.Stance2,
                    combat_components.stance3 to CombatStance.Stance3,
                    combat_components.stance4 to CombatStance.Stance4,
                )

            for ((button, expected) in buttons) {
                player.ifButton(button)
                advance()

                assertEquals(expected.varValue, player.vars[varps.com_mode]) {
                    "Expected $expected after clicking ${button.debugName}:"
                }
            }
        }

    @Test
    fun GameTestState.`auto retaliate button toggles option_nodef`() =
        runGameTest(CombatTabScript::class) {
            player.ifOpenOverlay(interfaces.combat_interface, components.toplevel_target_side0)
            assertEquals(0, player.vars[varps.option_nodef])

            player.ifButton(combat_components.auto_retaliate)
            advance()
            assertEquals(1, player.vars[varps.option_nodef])

            player.ifButton(combat_components.auto_retaliate)
            advance()
            assertEquals(0, player.vars[varps.option_nodef])
        }

    /**
     * A bronze axe has no special attack, so the click is only observable through the rejection
     * message - which is enough to prove it reached [CombatTabScript] at all.
     */
    @Test
    fun GameTestState.`special attack button reaches the script`() =
        runGameTest(CombatTabScript::class) {
            player.righthand = InvObj(objs.bronze_axe)
            player.ifOpenOverlay(interfaces.combat_interface, components.toplevel_target_side0)

            player.ifButton(combat_components.special_attack)
            advance()

            assertMessageSent("This weapon does not have a special attack.")
        }

    private class StanceTestDependencies @Inject constructor(val styles: AttackStyles)

    /**
     * `AttackStyles` keeps its weapon-style map in a `lateinit` filled by
     * [WeaponAttackStylesScript]. Without a singleton binding the script would start up a different
     * instance than the one [CombatTabScript] resolves stances against.
     */
    private object StanceTestModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }
}
