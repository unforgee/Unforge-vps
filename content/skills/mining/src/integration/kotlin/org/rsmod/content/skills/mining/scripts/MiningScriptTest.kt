package org.rsmod.content.skills.mining.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.core.skilling_point_varps
import org.rsmod.content.skills.mining.configs.MiningContent
import org.rsmod.content.skills.mining.configs.MiningParams
import org.rsmod.content.skills.mining.configs.mining_objs
import org.rsmod.content.skills.mining.configs.rocks
import org.rsmod.content.skills.mining.scripts.Mining.Companion.rockDepletedStage
import org.rsmod.content.skills.mining.scripts.Mining.Companion.rockLevelReq
import org.rsmod.content.skills.mining.scripts.Mining.Companion.rockOre
import org.rsmod.content.skills.mining.scripts.Mining.Companion.rockRespawnTime
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class MiningScriptTest {
    @Test
    fun GameTestState.`validate pickaxe requirement`() =
        runGameTest(Mining::class) {
            val type = findLocType(MiningContent.rock) { it.rockLevelReq == 1 }
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            // A rune pickaxe is a pickaxe the player cannot yet swing, which is a different failure
            // from carrying none at all - and it must read the same way to the player.
            player.righthand = InvObj(mining_objs.rune_pickaxe)
            player.stats[stats.mining] = 1
            player.opLoc1(rock)
            advance(ticks = 1)
            assertMessagesSent(
                "You need a pickaxe to mine this rock.",
                "You do not have a pickaxe which you have the Mining level to use.",
            )

            player.stats[stats.mining] = 99
            player.opLoc1(rock)
            advance(ticks = 1)
            assertMessageSent("You swing your pick at the rock.")
        }

    @Test
    fun GameTestState.`validate rock requirement`() =
        runGameTest(Mining::class) {
            val type = findLocType(MiningContent.rock) { it.rockLevelReq > 1 }
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(objs.bronze_pickaxe)
            player.stats[stats.mining] = type.rockLevelReq - 1
            player.opLoc1(rock)
            advance(ticks = 1)
            assertMessageSent("You need a Mining level of ${type.rockLevelReq} to mine this rock.")

            player.stats[stats.mining] = type.rockLevelReq
            player.opLoc1(rock)
            advance(ticks = 1)
            assertMessageSent("You swing your pick at the rock.")
        }

    /**
     * A bronze pickaxe rolls every eight ticks, so the ore cannot arrive before the eighth. This is
     * the test that fails if the pickaxe stops driving the cadence.
     */
    @Test
    fun GameTestState.`mine copper with a bronze pickaxe`() =
        runGameTest(Mining::class) {
            val type = cacheTypes.locs.getValue(rocks.copper_1.id)
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            val ore = type.rockOre
            val oreName = objTypes[ore].name.lowercase()
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(objs.bronze_pickaxe)
            player.stats[stats.mining] = type.rockLevelReq
            player.opLoc1(rock)
            advance(ticks = 1)
            assertMessageSent("You swing your pick at the rock.")
            assertDoesNotContain(player.inv, ore)

            // Ticks two through seven of the cycle: swinging, not rolling. Nothing is queued into
            // `random` here on purpose - an unconsumed value would still be waiting when the roll
            // finally comes, and would be read as that roll's result.
            repeat(6) {
                advance(ticks = 1)
                assertMessageNotSent("You manage to mine some $oreName.")
                assertDoesNotContain(player.inv, ore)
            }

            random.next = 0 // Guarantee the success roll.
            random.then = 1 // Any positive value depletes a rock whose deplete chance is 0.
            advance(ticks = 1)
            assertMessageSent("You manage to mine some $oreName.")
            assertContains(player.inv, ore)
            assertDoesNotExist(rock)
            assertExists(rock.coords, type.rockDepletedStage)

            advance(ticks = type.rockRespawnTime)
            assertExists(rock)
            assertDoesNotExist(rock.coords, type.rockDepletedStage)
        }

    /**
     * The same rock with a rune pickaxe, which rolls every three ticks. Nothing about the rock
     * changes, so anything that made the rate follow the rock's own tier - or fixed the cadence the
     * way woodcutting fixes it - would land the ore on the same tick as the bronze run.
     */
    @Test
    fun GameTestState.`rune pickaxe rolls sooner than bronze`() =
        runGameTest(Mining::class) {
            val type = cacheTypes.locs.getValue(rocks.copper_1.id)
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            val ore = type.rockOre
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(mining_objs.rune_pickaxe)
            player.stats[stats.mining] = 41
            player.opLoc1(rock)
            advance(ticks = 1)
            assertDoesNotContain(player.inv, ore)

            advance(ticks = 1)
            assertDoesNotContain(player.inv, ore)

            random.next = 0
            random.then = 1
            advance(ticks = 1)
            assertContains(player.inv, ore)
        }

    /** A failed roll costs a cycle and nothing else: the rock survives and the run carries on. */
    @Test
    fun GameTestState.`failed roll keeps the run going`() =
        runGameTest(Mining::class) {
            val type = cacheTypes.locs.getValue(rocks.copper_1.id)
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            val ore = type.rockOre
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(mining_objs.rune_pickaxe)
            player.stats[stats.mining] = 41
            player.opLoc1(rock)
            advance(ticks = 2)

            random.next = 100 // `randomDouble` reads this as 1.0, which no rate can beat.
            advance(ticks = 1)
            assertDoesNotContain(player.inv, ore)
            assertEquals(0, player.vars[skilling_point_varps.points])
            assertExists(rock)

            // Three ticks later the next roll lands, which only happens if the loop rescheduled
            // itself after the failure.
            advance(ticks = 2)
            random.next = 0
            random.then = 1
            advance(ticks = 1)
            assertContains(player.inv, ore)
            assertEquals(1, player.vars[skilling_point_varps.points])
        }

    /**
     * The point reward rides on the successful roll and nothing else: exactly one `skill_points`
     * point per action that yields ore, zero for a run that never lands one, and the completed
     * action cannot be replayed into a duplicate grant.
     */
    @Test
    fun GameTestState.`successful mining awards one skill point per action`() =
        runGameTest(Mining::class) {
            val type = cacheTypes.locs.getValue(rocks.copper_1.id)
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(mining_objs.rune_pickaxe)
            player.stats[stats.mining] = 41
            assertEquals(0, player.vars[skilling_point_varps.points])

            // A run that never rolls (interrupted before the cycle lands) grants nothing.
            player.opLoc1(rock)
            advance(ticks = 1)
            player.clearPendingAction()
            advance(ticks = 4)
            assertEquals(0, player.vars[skilling_point_varps.points])

            player.opLoc1(rock)
            advance(ticks = 2)
            random.next = 0
            random.then = 1
            advance(ticks = 1)
            assertEquals(1, player.vars[skilling_point_varps.points])

            // The rock is spent, so the action ended with the roll: more ticks cannot mint more.
            advance(ticks = type.rockRespawnTime)
            assertEquals(1, player.vars[skilling_point_varps.points])
            assertExists(rock)

            // A fresh click is a fresh action and mints exactly one more point.
            player.opLoc1(rock)
            advance(ticks = 2)
            random.next = 0
            random.then = 1
            advance(ticks = 1)
            assertEquals(2, player.vars[skilling_point_varps.points])
        }

    /** An action that cannot start grants nothing: no pickaxe, no point, no xp. */
    @Test
    fun GameTestState.`mining without a pickaxe grants no skill point`() =
        runGameTest(Mining::class) {
            val type = findLocType(MiningContent.rock) { it.rockLevelReq == 1 }
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.stats[stats.mining] = 41
            val xpBefore = player.statMap.getFineXP(stats.mining)
            player.opLoc1(rock)
            advance(ticks = 8)
            assertEquals(0, player.vars[skilling_point_varps.points])
            assertEquals(xpBefore, player.statMap.getFineXP(stats.mining))
        }

    /** Mining pays the rock's own xp, in the rock's own units. */
    @Test
    fun GameTestState.`mining awards the rock's experience`() =
        runGameTest(Mining::class) {
            val type = cacheTypes.locs.getValue(rocks.iron_1.id)
            val rock = placeMapLoc(CoordGrid(0, 50, 50, 34, 31), type)
            player.teleport(rock.coords.translateX(-1))
            player.clearInv()

            player.righthand = InvObj(mining_objs.rune_pickaxe)
            player.stats[stats.mining] = 41
            val before = player.statMap.getFineXP(stats.mining)

            player.opLoc1(rock)
            advance(ticks = 2)
            random.next = 0
            random.then = 1
            advance(ticks = 1)

            // Iron pays 35, and fine xp is ten times that.
            assertEquals(350, player.statMap.getFineXP(stats.mining) - before)
        }

    /** Rate is a property of the rock, not of the pickaxe. */
    @Test
    fun GameTestState.`rock rates are keyed by rock`() =
        runGameTest(Mining::class) {
            val copper = cacheTypes.locs.getValue(rocks.copper_1.id)
            val runite = cacheTypes.locs.getValue(rocks.runite_1.id)
            assertEquals(100, copper.param(MiningParams.rate_low))
            assertEquals(3, runite.param(MiningParams.rate_low))
        }
}
