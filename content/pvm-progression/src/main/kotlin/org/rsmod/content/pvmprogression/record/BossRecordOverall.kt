package org.rsmod.content.pvmprogression.record

/**
 * Cross-boss aggregate stats for one character: the numbers the PvM hub overview shows. Derived
 * from the per-boss [BossRecord] map by [BossRecordManager] on each kill, so it never drifts.
 */
data class BossRecordOverall(
    val totalBossKills: Int = 0,
    val totalBossDeaths: Int = 0,
    val favouriteBoss: String? = null,
    val mostKilledBoss: String? = null,
    val mostKilledCount: Int = 0,
    val bestStreak: Int = 0,
    val totalBossDamage: Int = 0,
    val fastestKillTenths: Int = Int.MAX_VALUE,
    val fastestKillBoss: String? = null,
) {
    companion object {
        val EMPTY = BossRecordOverall()
    }
}
