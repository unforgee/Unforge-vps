package org.rsmod.content.pvmprogression.streak

/**
 * PvM hot-streak state for one character.
 *
 * [currentStreak] increments on each boss kill and resets to 0 on a boss death. [unclaimedTokens]
 * is the pool that grows per kill while the streak runs; it is capped by the config so an endless
 * streak cannot mint unbounded currency. [milestonesClaimed] records which milestone thresholds
 * have already fired their event, so a streak that crosses the same threshold twice (after a reset)
 * can fire again without the manager needing to track history.
 */
data class StreakData(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val unclaimedTokens: Int = 0,
    val lastKillEpoch: Long = 0L,
    val milestonesClaimed: Set<Int> = emptySet(),
    /** Anti-exploit: the last boss encounter id that incremented this streak. */
    val lastKillEncounterId: Long = 0L,
) {
    companion object {
        val EMPTY = StreakData()

        /** The milestone thresholds the manager fires events for. */
        val MILESTONES = listOf(5, 10, 25, 50, 100, 250, 500)
    }
}
