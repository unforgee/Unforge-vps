package org.rsmod.content.pvmprogression.record

/**
 * Per-boss record sheet for one character.
 *
 * Created lazily - only once a player actually engages the boss - so a fresh account has no
 * thousands of empty rows. All time fields are tenths of a second (one game cycle = 0.6s = 6
 * tenths), matching [BossEncounter.elapsedTenths].
 */
data class BossRecord(
    val bossId: Int,
    val bossName: String,
    val totalKills: Int = 0,
    val fastestKillTenths: Int = Int.MAX_VALUE,
    val slowestKillTenths: Int = 0,
    val highestHit: Int = 0,
    val highestSpecialHit: Int = 0,
    val damageDealt: Int = 0,
    val damageTaken: Int = 0,
    val lowestHpBossFinish: Int = 0,
    val lowestHpPercent: Int = 0,
    val foodConsumed: Int = 0,
    val prayerConsumed: Int = 0,
    val killsWithoutFood: Int = 0,
    val killsWithoutPrayerRestore: Int = 0,
    val deaths: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val meleeKills: Int = 0,
    val rangedKills: Int = 0,
    val magicKills: Int = 0,
    val companionAssistedKills: Int = 0,
    val soloKills: Int = 0,
    val groupKills: Int = 0,
    val lastKillEpoch: Long = 0L,
    val luckyKills: Int = 0,
    val lastStandKills: Int = 0,
    val lowestFinishingHp: Int = Int.MAX_VALUE,
    val lowestFinishingHpPercentBps: Int = Int.MAX_VALUE,
    val specialRecordBoss: String? = null,
) {
    companion object {
        val EMPTY = BossRecord(bossId = -1, bossName = "")
    }
}
