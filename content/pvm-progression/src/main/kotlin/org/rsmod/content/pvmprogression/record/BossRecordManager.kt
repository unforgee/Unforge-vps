package org.rsmod.content.pvmprogression.record

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounter
import org.rsmod.content.pvmprogression.events.BossPersonalBestEvent
import org.rsmod.content.pvmprogression.events.BossRecordEvent
import org.rsmod.content.pvmprogression.events.RecordField
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * The per-boss and overall record keeper.
 *
 * On every attributed boss kill ([onBossKill]), the encounter's sampled facts fold into the boss's
 * [BossRecord]. A new personal best is only announced when the new time beats the old best by at
 * least [PvmProgressionConfig.RecordConfig.personalBestMarginTenths], so the chatbox is not spammed
 * by trivial 0.1s improvements. The overall sheet is recomputed from the full record map each kill.
 *
 * Records are created lazily: a boss gets a row the first time a player kills it, not before.
 */
@Singleton
class BossRecordManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val cache: PvmProgressionCache,
    private val eventBus: EventBus,
) {
    /**
     * Fold [encounter] into the killer's boss record and overall sheet; publish PB / record events.
     */
    fun onBossKill(encounter: BossEncounter, player: Player) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        val records = data.records.toMutableMap()
        val existing = records[encounter.bossId] ?: BossRecord(encounter.bossId, encounter.bossName)
        val config = configSource().record
        val previousBest = existing.fastestKillTenths
        val updated =
            existing.copy(
                totalKills = existing.totalKills + 1,
                fastestKillTenths = minOf(existing.fastestKillTenths, encounter.elapsedTenths),
                slowestKillTenths = maxOf(existing.slowestKillTenths, encounter.elapsedTenths),
                highestHit = maxOf(existing.highestHit, encounter.highestHit),
                highestSpecialHit = maxOf(existing.highestSpecialHit, encounter.highestSpecialHit),
                damageDealt = existing.damageDealt + encounter.damageDealt,
                damageTaken = existing.damageTaken + encounter.damageTaken,
                lowestHpBossFinish = maxOf(existing.lowestHpBossFinish, encounter.playerHpAtKill),
                lowestHpPercent = maxOf(existing.lowestHpPercent, encounter.playerHpPercentAtKill),
                foodConsumed = existing.foodConsumed + encounter.foodConsumed,
                prayerConsumed = existing.prayerConsumed + encounter.prayerConsumed,
                killsWithoutFood =
                    if (encounter.foodConsumed == 0) existing.killsWithoutFood + 1 else 0,
                killsWithoutPrayerRestore =
                    if (encounter.prayerConsumed == 0) existing.killsWithoutPrayerRestore + 1
                    else 0,
                currentStreak = existing.currentStreak + 1,
                bestStreak = maxOf(existing.bestStreak, existing.currentStreak + 1),
                meleeKills = existing.meleeKills + if (encounter.finalHitStyle == 1) 1 else 0,
                rangedKills = existing.rangedKills + if (encounter.finalHitStyle == 2) 1 else 0,
                magicKills = existing.magicKills + if (encounter.finalHitStyle == 3) 1 else 0,
                companionAssistedKills =
                    existing.companionAssistedKills + if (encounter.companionAssisted) 1 else 0,
                soloKills = existing.soloKills + if (!encounter.companionAssisted) 1 else 0,
                groupKills = existing.groupKills + if (encounter.companionAssisted) 1 else 0,
                lastKillEpoch = System.currentTimeMillis(),
            )
        records[encounter.bossId] = updated
        maybeAnnouncePersonalBest(player, encounter, previousBest, config.personalBestMarginTenths)
        maybeAnnounceHighestHit(player, encounter, existing, updated)
        val overall = recomputeOverall(records)
        cache.put(cid, data.copy(records = records, recordOverall = overall))
    }

    /** Increment the death counter for [bossId] and reset its current streak. */
    fun onBossDeath(player: Player, bossId: Int) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        val records = data.records.toMutableMap()
        val existing = records[bossId] ?: return
        records[bossId] = existing.copy(deaths = existing.deaths + 1, currentStreak = 0)
        val overall = recomputeOverall(records)
        cache.put(cid, data.copy(records = records, recordOverall = overall))
    }

    /** Records server-authoritative Lucky Kill / Last Stand statistics. */
    fun recordSpecialKill(
        player: Player,
        bossId: Int,
        bossName: String,
        lucky: Boolean,
        lastStand: Boolean,
        finishingHp: Int,
        finishingHpPercentBps: Int,
    ) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        val records = data.records.toMutableMap()
        val existing = records[bossId] ?: BossRecord(bossId, bossName)
        val newHp = minOf(existing.lowestFinishingHp, finishingHp)
        val newPercent = minOf(existing.lowestFinishingHpPercentBps, finishingHpPercentBps)
        records[bossId] =
            existing.copy(
                bossName = bossName,
                luckyKills = existing.luckyKills + if (lucky) 1 else 0,
                lastStandKills = existing.lastStandKills + if (lastStand) 1 else 0,
                lowestFinishingHp = newHp,
                lowestFinishingHpPercentBps = newPercent,
                specialRecordBoss =
                    if (
                        newHp < existing.lowestFinishingHp ||
                            newPercent < existing.lowestFinishingHpPercentBps
                    )
                        bossName
                    else existing.specialRecordBoss,
            )
        cache.put(cid, data.copy(records = records))
    }

    /** All per-boss records for [player]. */
    fun getRecords(player: Player): Map<Int, BossRecord> =
        cache.getOrCreate(player.characterId).records

    /** The cross-boss overall sheet for [player]. */
    fun getOverall(player: Player): BossRecordOverall =
        cache.getOrCreate(player.characterId).recordOverall

    /** Admin: wipes the record for [bossId]. */
    fun resetRecord(player: Player, bossId: Int) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        val records = data.records.toMutableMap()
        records.remove(bossId)
        val overall = recomputeOverall(records)
        cache.put(cid, data.copy(records = records, recordOverall = overall))
    }

    private fun maybeAnnouncePersonalBest(
        player: Player,
        encounter: BossEncounter,
        previousBest: Int,
        marginTenths: Int,
    ) {
        if (
            previousBest != Int.MAX_VALUE && encounter.elapsedTenths <= previousBest - marginTenths
        ) {
            eventBus.publish(
                BossPersonalBestEvent(
                    player = player,
                    bossId = encounter.bossId,
                    bossName = encounter.bossName,
                    timeTenths = encounter.elapsedTenths,
                    previousTenths = previousBest,
                )
            )
        }
    }

    private fun maybeAnnounceHighestHit(
        player: Player,
        encounter: BossEncounter,
        existing: BossRecord,
        updated: BossRecord,
    ) {
        if (updated.highestHit > existing.highestHit && updated.highestHit > 0) {
            eventBus.publish(
                BossRecordEvent(
                    player = player,
                    bossId = encounter.bossId,
                    bossName = encounter.bossName,
                    field = RecordField.HIGHEST_HIT,
                    value = updated.highestHit.toLong(),
                )
            )
        }
    }

    private fun recomputeOverall(records: Map<Int, BossRecord>): BossRecordOverall {
        if (records.isEmpty()) {
            return BossRecordOverall.EMPTY
        }
        val totalKills = records.values.sumOf { it.totalKills }
        val totalDeaths = records.values.sumOf { it.deaths }
        val mostKilled = records.values.maxByOrNull { it.totalKills } ?: BossRecord.EMPTY
        val fastest =
            records.values
                .filter { it.fastestKillTenths != Int.MAX_VALUE }
                .minByOrNull { it.fastestKillTenths }
        val bestStreak = records.values.maxOf { it.bestStreak }
        val totalDamage = records.values.sumOf { it.damageDealt }
        val favourite =
            records.values.filter { it.totalKills > 0 }.maxByOrNull { it.totalKills }?.bossName
        return BossRecordOverall(
            totalBossKills = totalKills,
            totalBossDeaths = totalDeaths,
            favouriteBoss = favourite,
            mostKilledBoss = mostKilled.bossName,
            mostKilledCount = mostKilled.totalKills,
            bestStreak = bestStreak,
            totalBossDamage = totalDamage,
            fastestKillTenths = fastest?.fastestKillTenths ?: Int.MAX_VALUE,
            fastestKillBoss = fastest?.bossName,
        )
    }
}
