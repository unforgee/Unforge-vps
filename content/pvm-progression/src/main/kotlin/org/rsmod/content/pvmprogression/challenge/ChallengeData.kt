package org.rsmod.content.pvmprogression.challenge

import org.rsmod.content.pvmprogression.events.ChallengeTier
import org.rsmod.content.pvmprogression.events.ChallengeType

/**
 * Aggregate challenge stats for one character plus the live offer pool. The manager appends a
 * [PendingChallenge] to [activeOffers] when a boss fight starts, and folds it back into the
 * completion counters on success / failure.
 */
data class ChallengeData(
    val totalCompleted: Int = 0,
    val completedByType: Map<ChallengeType, Int> = emptyMap(),
    val completedByTier: Map<ChallengeTier, Int> = emptyMap(),
    val lastOfferCycle: Int = -1,
    val activeOffers: List<PendingChallenge> = emptyList(),
) {
    companion object {
        val EMPTY = ChallengeData()
    }
}
