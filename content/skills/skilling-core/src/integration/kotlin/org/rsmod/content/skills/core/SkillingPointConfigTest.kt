package org.rsmod.content.skills.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

/**
 * Pins the `skill_points` varp to the cache pipeline.
 *
 * The balance lives on a varp rather than in a database field, so the integration contract is
 * twofold: the type must actually resolve (a renamed or unbuilt varp fails here, not at some later
 * purchase) and a player who has never touched the economy - including an old save predating the
 * varp - reads a balance of `0` rather than crashing or minting points.
 */
class SkillingPointConfigTest {
    @Test
    fun GameTestState.`skill points varp resolves and defaults to zero`() = runBasicGameTest {
        withPlayer {
            assertEquals(
                0,
                vars[skilling_point_varps.points],
                "Fresh player should read 0 skill points",
            )
        }
    }
}
