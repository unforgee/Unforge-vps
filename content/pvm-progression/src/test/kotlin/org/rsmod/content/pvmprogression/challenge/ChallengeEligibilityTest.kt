package org.rsmod.content.pvmprogression.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.content.pvmprogression.events.ChallengeType

/**
 * Pure tests for [ChallengeEligibility] - no game thread, only value-type facts about which
 * challenge types a boss qualifies for and whether NO_MOVEMENT may be offered.
 */
class ChallengeEligibilityTest {
    @Test
    fun `eligibleTypes includes the always-on challenge types`() {
        val types = ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Goblin")
        assertTrue(ChallengeType.NO_FOOD in types)
        assertTrue(ChallengeType.NO_PRAYER in types)
        assertTrue(ChallengeType.NO_POTION in types)
        assertTrue(ChallengeType.SPEED_KILL in types)
        assertTrue(ChallengeType.LOW_DAMAGE_TAKEN in types)
        assertTrue(ChallengeType.STYLE_LOCK in types)
        assertTrue(ChallengeType.FINISH_WITH_STYLE in types)
        assertTrue(ChallengeType.NO_SPECIAL_ATTACK in types)
        assertTrue(ChallengeType.SPECIAL_FINISH in types)
        assertTrue(ChallengeType.NO_COMPANION in types)
        assertTrue(ChallengeType.LOW_HP_FINISH in types)
    }

    @Test
    fun `eligibleTypes grants NO_MOVEMENT for a generic boss`() {
        val types = ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Giant Mole")
        assertTrue(ChallengeType.NO_MOVEMENT in types)
    }

    @Test
    fun `eligibleTypes excludes NO_MOVEMENT for movement-required bosses`() {
        assertFalse(
            ChallengeType.NO_MOVEMENT in
                ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Vorkath")
        )
        assertFalse(
            ChallengeType.NO_MOVEMENT in
                ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Zulrah")
        )
        assertFalse(
            ChallengeType.NO_MOVEMENT in
                ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "TzTok-Jad")
        )
        assertFalse(
            ChallengeType.NO_MOVEMENT in
                ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "TzKal-Zuk")
        )
        assertFalse(
            ChallengeType.NO_MOVEMENT in
                ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Verzik Vitur")
        )
    }

    @Test
    fun `allowsNoMovement returns false for vorkath zulrah and jad`() {
        assertFalse(ChallengeEligibility.allowsNoMovement("Vorkath"))
        assertFalse(ChallengeEligibility.allowsNoMovement("Zulrah"))
        assertFalse(ChallengeEligibility.allowsNoMovement("Jad"))
    }

    @Test
    fun `allowsNoMovement returns true for a generic boss`() {
        assertTrue(ChallengeEligibility.allowsNoMovement("King Black Dragon"))
        assertTrue(ChallengeEligibility.allowsNoMovement("Giant Mole"))
    }

    @Test
    fun `allowsNoMovement is case-insensitive`() {
        assertFalse(ChallengeEligibility.allowsNoMovement("vorkath"))
        assertFalse(ChallengeEligibility.allowsNoMovement("ZULRAH"))
        assertTrue(ChallengeEligibility.allowsNoMovement("giant mole"))
    }

    @Test
    fun `eligibleTypes size is the always set plus NO_MOVEMENT for generic bosses`() {
        val generic = ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Goblin")
        val movement = ChallengeEligibility.eligibleTypes(bossId = 1, bossName = "Vorkath")
        assertEquals(generic.size - 1, movement.size)
    }
}
