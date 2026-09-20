package org.rsmod.content.pvmprogression.bounty

/**
 * The persisted bounty board for one character: the three category lists plus the two reset
 * deadlines. The manager regenerates a category list only when the wall-clock has crossed its
 * deadline, so a player who logs in mid-day keeps their morning's daily tasks.
 */
data class BountyData(
    val dailyTasks: List<BountyTask> = emptyList(),
    val weeklyTasks: List<BountyTask> = emptyList(),
    val eliteTasks: List<BountyTask> = emptyList(),
    val dailyResetEpoch: Long = 0L,
    val weeklyResetEpoch: Long = 0L,
) {
    companion object {
        val EMPTY = BountyData()
    }
}
