package org.rsmod.content.pvmprogression.challenge

import org.rsmod.content.pvmprogression.events.ChallengeType

/**
 * Pure mapping from a boss to the set of challenge types that can be offered for it.
 *
 * A small blacklist of bosses whose fights inherently require movement (Vorkath acid, Zulrah
 * rotations, Jad/Zuk Jad) excludes NO_MOVEMENT for those; everything else that is attackable gets
 * the full set. This object is game-thread-free so the eligibility table can be unit-tested.
 */
object ChallengeEligibility {
    /** Lower-cased boss-name fragments that disqualify NO_MOVEMENT. */
    private val movementRequiredBosses =
        setOf("vorkath", "zulrah", "jad", "tzkal-zuk", "tztok-jad", "verzik")

    /** The challenge types offered for [bossName] at combat level [visLevel]. */
    fun eligibleTypes(bossId: Int, bossName: String): Set<ChallengeType> {
        val name = bossName.lowercase()
        val always =
            linkedSetOf(
                ChallengeType.NO_FOOD,
                ChallengeType.NO_PRAYER,
                ChallengeType.NO_POTION,
                ChallengeType.SPEED_KILL,
                ChallengeType.LOW_DAMAGE_TAKEN,
                ChallengeType.STYLE_LOCK,
                ChallengeType.FINISH_WITH_STYLE,
                ChallengeType.NO_SPECIAL_ATTACK,
                ChallengeType.SPECIAL_FINISH,
                ChallengeType.NO_COMPANION,
                ChallengeType.LOW_HP_FINISH,
            )
        if (movementRequiredBosses.none { name.contains(it) }) {
            always += ChallengeType.NO_MOVEMENT
        }
        return always
    }

    /** `true` when NO_MOVEMENT may be offered for a boss with this [bossName]. */
    fun allowsNoMovement(bossName: String): Boolean {
        val name = bossName.lowercase()
        return movementRequiredBosses.none { name.contains(it) }
    }
}
