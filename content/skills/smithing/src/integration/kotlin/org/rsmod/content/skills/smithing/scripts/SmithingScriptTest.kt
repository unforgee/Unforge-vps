package org.rsmod.content.skills.smithing.scripts

import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.smithing.configs.smithing_components
import org.rsmod.content.skills.smithing.configs.smithing_interfaces
import org.rsmod.content.skills.smithing.configs.smithing_locs
import org.rsmod.content.skills.smithing.configs.smithing_objs
import org.rsmod.content.skills.smithing.configs.smithing_varbits
import org.rsmod.content.skills.smithing.configs.smithing_varps
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

/**
 * Behaviour of the anvil half of the module.
 *
 * The anvil is the half that can be driven end to end here: its buttons are baked into the cache,
 * so a click goes straight through the button handler with no `skillmulti` dialog in the way.
 */
@Execution(ExecutionMode.SAME_THREAD)
class SmithingScriptTest {
    @Test
    fun GameTestState.`anvil needs a hammer`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.bronze_bar, 1)

            player.opLoc1(anvil)
            advance(ticks = 1)
            assertMessageSent("You need a hammer to work the metal with.")
        }

    @Test
    fun GameTestState.`anvil needs a bar`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)

            player.opLoc1(anvil)
            advance(ticks = 1)
            assertMessageSent("You don't have any bars to smith with.")
        }

    @Test
    fun GameTestState.`anvil opens on the best bar the player carries`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 1)
            give(smithing_objs.steel_bar, 1)
            player.stats[stats.smithing] = 99

            // One tick only: `advance` clears the capture client at the start of each tick, so
            // packets written while handling the op do not survive a second one.
            player.opLoc1(anvil)
            advance(ticks = 1)

            // Steel is tier 3 in the client's own `enum 1253` ordering.
            assertEquals(3, player.vars[smithing_varbits.bar_type])
            assertTrue(
                client.anyOf<IfOpenSub> { it.interfaceId == smithing_interfaces.smithing.id }
            )
        }

    @Test
    fun GameTestState.`anvil skips tiers the player cannot smith anything from`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 1)
            give(smithing_objs.runite_bar, 1)
            player.stats[stats.smithing] = 1

            player.opLoc1(anvil)
            advance(ticks = 2)
            assertEquals(1, player.vars[smithing_varbits.bar_type])
        }

    @Test
    fun GameTestState.`smithing a dagger consumes a bar`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 2)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 1)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DAGGER_SLOT))
            advance(ticks = 10)

            assertEquals(1, player.count(smithing_objs.bronze_bar))
            assertEquals(1, player.count(obj(BRONZE_DAGGER)))
        }

    @Test
    fun GameTestState.`smithing respects the chosen quantity`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 5)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 3)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DAGGER_SLOT))
            advance(ticks = 25)

            assertEquals(2, player.count(smithing_objs.bronze_bar))
            assertEquals(3, player.count(obj(BRONZE_DAGGER)))
        }

    @Test
    fun GameTestState.`smithing stops when the bars run out`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 2)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 28)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DAGGER_SLOT))
            advance(ticks = 40)

            assertEquals(0, player.count(smithing_objs.bronze_bar))
            assertEquals(2, player.count(obj(BRONZE_DAGGER)))
        }

    @Test
    fun GameTestState.`the player is never delayed while smithing`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 10)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 28)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DAGGER_SLOT))

            // `player.delay` is what makes every input handler drop its packet, so a delayed player
            // cannot walk away, cannot re-open the anvil, cannot do anything until the run ends.
            repeat(12) {
                advance(ticks = 1)
                assertFalse(player.isDelayed, "Player was delayed mid-run.")
            }
            assertTrue(player.count(obj(BRONZE_DAGGER)) > 0, "Nothing was made.")
        }

    @Test
    fun GameTestState.`clicking away cancels the rest of the run`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 10)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 28)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DAGGER_SLOT))
            advance(ticks = 8)

            val madeBeforeClick = player.count(obj(BRONZE_DAGGER))
            assertTrue(madeBeforeClick in 1..2, "Expected a dagger or two by now.")

            // Any op or move handler calls `clearPendingAction` -> `ifClose` -> weak queues
            // cleared, which is what drops the rest of the run.
            player.moveGameClick(anvil.coords.translateX(-3))
            advance(ticks = 30)

            assertEquals(madeBeforeClick, player.count(obj(BRONZE_DAGGER)))
            assertEquals(10 - madeBeforeClick, player.count(smithing_objs.bronze_bar))
        }

    @Test
    fun GameTestState.`each bar tier pays its own xp rate`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            player.stats[stats.smithing] = 99
            player.setVarp(smithing_varps.make_quantity, 1)

            for ((bar, perBar) in TIER_XP) {
                player.clearInv()
                give(smithing_objs.hammer, 1)
                give(bar, 1)

                val before = player.statMap.getFineXP(stats.smithing)
                player.opLoc1(anvil)
                advance(ticks = 2)
                player.ifButton(slot(ONE_BAR_SLOT))
                advance(ticks = 10)

                val gained = player.statMap.getFineXP(stats.smithing) - before
                assertEquals(fineXp(perBar), gained, "Wrong xp for bar ${bar.internalName}")
            }
        }

    @Test
    fun GameTestState.`a multi-bar product pays the tier rate for every bar`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.steel_bar, 5)
            player.stats[stats.smithing] = 99
            player.setVarp(smithing_varps.make_quantity, 1)

            val before = player.statMap.getFineXP(stats.smithing)
            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(PLATEBODY_SLOT))
            advance(ticks = 10)

            // Five steel bars at 37.5 each. Both halves of the product matter: the tier picks the
            // rate, enum 845's bar cost multiplies it.
            assertEquals(1, player.count(obj(STEEL_PLATEBODY)))
            assertEquals(
                fineXp(STEEL_PER_BAR * 5),
                player.statMap.getFineXP(stats.smithing) - before,
            )
        }

    @Test
    fun GameTestState.`a multi-output product pays for the bar, not the items`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.steel_bar, 1)
            player.stats[stats.smithing] = 99
            player.setVarp(smithing_varps.make_quantity, 1)

            val before = player.statMap.getFineXP(stats.smithing)
            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(slot(DART_TIP_SLOT))
            advance(ticks = 10)

            // Ten tips out of the one bar, and it is the bar that pays: enum 844's output count
            // must not reach the xp, or a dart run would be worth ten times a dagger run.
            assertEquals(10, player.count(obj(STEEL_DART_TIP)))
            assertEquals(fineXp(STEEL_PER_BAR), player.statMap.getFineXP(stats.smithing) - before)
        }

    @Test
    fun GameTestState.`a slot above the player's level is refused`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 6)
            player.stats[stats.smithing] = 1
            player.setVarp(smithing_varps.make_quantity, 1)

            player.opLoc1(anvil)
            advance(ticks = 2)
            // `smithing:platebody` costs five bars and needs level 18, both read from the enums.
            player.ifButton(slot(PLATEBODY_SLOT))
            advance(ticks = 1)

            assertMessageSent("You need a Smithing level of 18 to make bronze platebody.")
            assertEquals(6, player.count(smithing_objs.bronze_bar))
        }

    @Test
    fun GameTestState.`quantity buttons write the shared varp`() =
        runGameTest(AnvilScript::class) {
            val anvil = placeAnvil()
            give(smithing_objs.hammer, 1)
            give(smithing_objs.bronze_bar, 1)

            player.opLoc1(anvil)
            advance(ticks = 2)
            player.ifButton(smithing_components.make_10)
            advance(ticks = 2)

            assertEquals(10, player.vars[smithing_varps.make_quantity])
        }

    private fun GameTestScope.placeAnvil(): BoundLocInfo {
        val anvil = placeMapLoc(ANVIL_COORDS, smithing_locs.anvil)
        player.teleport(anvil.coords.translateX(-1))
        player.clearInv()
        return anvil
    }

    private fun GameTestScope.give(type: ObjType, count: Int) {
        player.invAdd(player.inv, type, count)
    }

    private fun GameTestState.slot(component: Int): ComponentType =
        cacheTypes.components.getValue(Component(smithing_interfaces.smithing.id, component).packed)

    private fun GameTestState.obj(id: Int): ObjType = cacheTypes.objs.getValue(id)

    /** Xp is stored to a tenth, so a 12.5 award reads back as 125. */
    private fun fineXp(xp: Double): Int = (xp * 10).toInt()

    private companion object {
        /** `smithing:dagger`, the first item slot on interface 312. */
        private const val DAGGER_SLOT = 9

        /**
         * The same slot as [DAGGER_SLOT], named for what the xp test uses it for: slot 9 costs
         * exactly one bar on every one of the seven tiers, so what it awards *is* the tier's
         * per-bar rate with nothing multiplied on top.
         */
        private const val ONE_BAR_SLOT = 9

        /** `smithing:platebody` - five bars, level 18. */
        private const val PLATEBODY_SLOT = 22

        /** `smithing:dart_tip` - one bar in, ten tips out. */
        private const val DART_TIP_SLOT = 29

        private const val BRONZE_DAGGER = 1205

        private const val STEEL_PLATEBODY = 1119

        private const val STEEL_DART_TIP = 821

        private const val STEEL_PER_BAR = 37.5

        /**
         * Per-bar anvil rates, observed from live OSRS. Lovakite is the one that is not on the
         * 12.5-per-step ladder - Shayzien armour pays a flat 60.
         */
        private val TIER_XP: List<Pair<ObjType, Double>> =
            listOf(
                smithing_objs.bronze_bar to 12.5,
                smithing_objs.iron_bar to 25.0,
                smithing_objs.steel_bar to 37.5,
                smithing_objs.mithril_bar to 50.0,
                smithing_objs.adamantite_bar to 62.5,
                smithing_objs.runite_bar to 75.0,
                smithing_objs.lovakite_bar to 60.0,
            )

        private val ANVIL_COORDS = CoordGrid(0, 50, 50, 34, 31)
    }
}
