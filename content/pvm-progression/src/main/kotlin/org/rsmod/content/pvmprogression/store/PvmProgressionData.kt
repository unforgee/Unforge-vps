package org.rsmod.content.pvmprogression.store

import org.rsmod.content.pvmprogression.bounty.BountyData
import org.rsmod.content.pvmprogression.challenge.ChallengeData
import org.rsmod.content.pvmprogression.mutation.MutationStats
import org.rsmod.content.pvmprogression.record.BossRecord
import org.rsmod.content.pvmprogression.record.BossRecordOverall
import org.rsmod.content.pvmprogression.streak.StreakData

/**
 * The full persisted state for one character. Created lazily - only when a player first engages.
 */
data class PvmProgressionData(
    val bounty: BountyData = BountyData.EMPTY,
    val records: Map<Int, BossRecord> = emptyMap(),
    val recordOverall: BossRecordOverall = BossRecordOverall.EMPTY,
    val mutations: MutationStats = MutationStats.EMPTY,
    val challenges: ChallengeData = ChallengeData.EMPTY,
    val streak: StreakData = StreakData.EMPTY,
) {
    companion object {
        val EMPTY = PvmProgressionData()
    }
}
