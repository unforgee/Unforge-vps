package org.rsmod.content.skills.mining.configs

import kotlin.math.floor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.params
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.assertions.assertNotNullContract

class MiningConfigTest {
    @Test
    fun GameTestState.`ensure all rock locs have required params`() = runBasicGameTest {
        val rocks = cacheTypes.locs.values.filter { it.isContentType(MiningContent.rock) }
        assertTrue(rocks.isNotEmpty())
        for (rock in rocks) {
            val rockParams = rock.paramMap
            assertNotNullContract(rockParams)
            assertTrue(params.levelrequire in rockParams) { "No level: $rock" }
            assertTrue(params.skill_productitem in rockParams) { "No ore: $rock" }
            assertTrue(params.skill_xp in rockParams) { "No xp: $rock" }
            assertTrue(params.next_loc_stage in rockParams) { "No depleted stage: $rock" }
            assertTrue(params.respawn_time in rockParams) { "No respawn time: $rock" }
            assertTrue(params.deplete_chance in rockParams) { "No deplete chance: $rock" }
            assertTrue(MiningParams.rate_low in rockParams) { "No low rate: $rock" }
            assertTrue(MiningParams.rate_high in rockParams) { "No high rate: $rock" }

            assertTrue(rock.param(params.respawn_time) > 0) { "Respawn time not set: $rock" }
            assertTrue(rock.param(params.deplete_chance) in 0..255) { "Bad deplete: $rock" }

            // Rocks respawn on a fixed timer, never a range, so the variable-respawn params that
            // trees use must stay off. `resolveRespawnTime`'s equivalent does not exist here.
            assertFalse(rock.hasParam(params.respawn_time_low)) { "Variable respawn: $rock" }
            assertFalse(rock.hasParam(params.respawn_time_high)) { "Variable respawn: $rock" }
        }
    }

    @Test
    fun GameTestState.`ensure rock rates rise with level`() = runBasicGameTest {
        val rocks = cacheTypes.locs.values.filter { it.isContentType(MiningContent.rock) }
        for (rock in rocks) {
            val low = rock.param(MiningParams.rate_low)
            val high = rock.param(MiningParams.rate_high)
            // A rock is always easier to mine at 99 than at 1. A `high` above 255 is not a mistake:
            // copper's 350 puts its chance over 256/256 well before level 99, which is exactly how
            // the live game stops low-tier rocks from ever failing at high level.
            assertTrue(high > low) { "Rate does not rise with level: $rock ($low..$high)" }
        }
    }

    /**
     * Pins the meaning of the authored low/high pair against a value published for the live game.
     *
     * The wiki plots iron rocks at exactly `0.51953125` for a level 15 miner, and its chart is
     * driven by `low=96 high=350`. If someone rescales these numbers - or swaps the pair - this is
     * what notices.
     */
    @Test
    fun GameTestState.`ensure iron rates reproduce the published chance`() = runBasicGameTest {
        val iron = cacheTypes.locs.getValue(rocks.iron_1.id)
        assertEquals(96, iron.param(MiningParams.rate_low))
        assertEquals(350, iron.param(MiningParams.rate_high))
        assertEquals(0.51953125, successRate(low = 96, high = 350, level = 15))
    }

    @Test
    fun GameTestState.`ensure all pickaxe objs have required params`() = runBasicGameTest {
        val pickaxes = cacheTypes.objs.values.filter { it.isContentType(MiningContent.pickaxe) }
        assertTrue(pickaxes.isNotEmpty())
        for (pickaxe in pickaxes) {
            val pickaxeParams = pickaxe.paramMap
            assertNotNullContract(pickaxeParams)
            assertTrue(params.levelrequire in pickaxeParams) { "No level: $pickaxe" }
            assertTrue(params.skill_anim in pickaxeParams) { "No anim: $pickaxe" }
            assertTrue(MiningParams.roll_ticks in pickaxeParams) { "No roll ticks: $pickaxe" }

            // A fast roll subtracts one tick, so anything at 2 ticks or less has nothing left to
            // give and would collapse the cycle.
            val rollTicks = pickaxe.param(MiningParams.roll_ticks)
            assertTrue(rollTicks >= 3) { "Roll interval too short: $pickaxe ($rollTicks)" }

            val fastRoll = pickaxe.param(MiningParams.fast_roll_chance)
            assertTrue(fastRoll >= 0) { "Negative fast-roll chance: $pickaxe ($fastRoll)" }
        }
    }

    /**
     * Tutorial Island binds `tut2_copperrock` and `tut2_tinrock` **by type**, and a type-level
     * handler beats a content-level one everywhere. Adding them here would not break the island -
     * its handler still wins - but it would put two rocks in the group that this script never
     * serves. Keep them out, the way silver and gold are kept out of the smithing bar group.
     */
    @Test
    fun GameTestState.`ensure tutorial island rocks are not in the content group`() =
        runBasicGameTest {
            val tutorial =
                cacheTypes.locs.values.filter {
                    it.internalName == "tut2_copperrock" || it.internalName == "tut2_tinrock"
                }
            assertEquals(2, tutorial.size)
            for (rock in tutorial) {
                assertFalse(rock.isContentType(MiningContent.rock)) { "Tutorial rock bound: $rock" }
            }
        }

    /**
     * Depleted rocks keep their `Mine` op in the cache. If one ever joined the content group the
     * loop would re-enter on an empty rock and mine it forever.
     */
    @Test
    fun GameTestState.`ensure depleted rocks are not in the content group`() = runBasicGameTest {
        val depleted =
            listOf(
                empty_rocks.rocks_1,
                empty_rocks.rocks_2,
                empty_rocks.newbie_rocks,
                empty_rocks.prif_rocks,
                empty_rocks.amethyst_empty,
            )
        for (type in depleted) {
            val loc = cacheTypes.locs.getValue(type.id)
            assertFalse(loc.isContentType(MiningContent.rock)) { "Depleted rock bound: $loc" }
        }
    }

    private fun successRate(low: Int, high: Int, level: Int, maxLevel: Int = 99): Double {
        val lowRate = (low * (maxLevel - level)) / (maxLevel - 1.0)
        val highRate = (high * (level - 1)) / (maxLevel - 1.0)
        return (1.0 + floor(lowRate + highRate + 0.5)) / 256.0
    }
}
