package org.rsmod.content.pvmprogression.streak

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounter
import org.rsmod.content.pvmprogression.events.StreakCashOutEvent
import org.rsmod.content.pvmprogression.events.StreakMilestoneEvent
import org.rsmod.content.pvmprogression.rewards.PvmReward
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * The PvM hot-streak engine.
 *
 * Each boss kill increments the streak and adds tokens to the unclaimed pool (capped by config). A
 * boss death resets the streak and forfeits the configured fraction of the pool - only the pool,
 * never the player's own items. Milestones (5/10/25/50/100/250/500) publish a
 * [StreakMilestoneEvent] the first time they are crossed in a streak.
 *
 * Anti-exploit: the streak refuses to increment twice for the same encounter id, so a multi-hit
 * death callback that re-enters the kill path cannot double-count.
 */
@Singleton
class PvMStreakManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val cache: PvmProgressionCache,
    private val rewards: PvmRewardService,
    private val eventBus: EventBus,
) {
    /** Increment the streak for [player] on the boss kill in [encounter]. */
    fun onBossKill(player: Player, encounter: BossEncounter) {
        val cid = player.characterId
        val config = configSource().streak
        val data = cache.getOrCreate(cid)
        if (data.streak.lastKillEncounterId == encounter.id) {
            return
        }
        val newStreak = data.streak.currentStreak + config.incrementPerKill
        val newPool =
            (data.streak.unclaimedTokens + config.tokensPerKill).coerceAtMost(
                config.maxUnclaimedTokens
            )
        val newBest = maxOf(data.streak.bestStreak, newStreak)
        val claimed = data.streak.milestonesClaimed.toMutableSet()
        val newMilestones = mutableListOf<Int>()
        for (milestone in StreakData.MILESTONES) {
            if (newStreak >= milestone && milestone !in claimed) {
                claimed += milestone
                newMilestones += milestone
            }
        }
        val updated =
            data.streak.copy(
                currentStreak = newStreak,
                bestStreak = newBest,
                unclaimedTokens = newPool,
                lastKillEpoch = System.currentTimeMillis(),
                milestonesClaimed = claimed,
                lastKillEncounterId = encounter.id,
            )
        cache.put(cid, data.copy(streak = updated))
        for (milestone in newMilestones) {
            eventBus.publish(StreakMilestoneEvent(player, milestone, newStreak))
        }
        val notifyConfig = configSource().notify
        if (notifyConfig.streakAnnounce && newMilestones.isNotEmpty()) {
            player.mes("<col=f1c40f>PvM streak milestone: $newStreak kills!</col>")
        }
    }

    /**
     * Reset the streak and forfeit the configured fraction of the unclaimed pool on a boss death.
     */
    fun onBossDeath(player: Player) {
        val cid = player.characterId
        val config = configSource().streak
        val data = cache.getOrCreate(cid)
        if (data.streak.currentStreak == 0 && data.streak.unclaimedTokens == 0) {
            return
        }
        val lost = data.streak.unclaimedTokens * config.deathPoolLossBps / 10_000
        val newPool = (data.streak.unclaimedTokens - lost).coerceAtLeast(0)
        val updated =
            data.streak.copy(currentStreak = 0, unclaimedTokens = newPool, lastKillEncounterId = 0L)
        cache.put(cid, data.copy(streak = updated))
        if (lost > 0) {
            player.mes("<col=e74c3c>PvM streak lost: forfeited $lost unclaimed tokens.</col>")
        }
    }

    /** Cash out the unclaimed pool, if the streak is high enough to unlock it. */
    fun cashOut(player: Player): Boolean {
        val cid = player.characterId
        val config = configSource().streak
        val data = cache.getOrCreate(cid)
        if (data.streak.currentStreak < config.cashOutUnlockKills) {
            player.mes(
                "<col=e74c3c>Cash out unlocks at ${config.cashOutUnlockKills} kills " +
                    "(current: ${data.streak.currentStreak}).</col>"
            )
            return false
        }
        if (data.streak.unclaimedTokens <= 0) {
            player.mes("<col=e74c3c>No unclaimed tokens to cash out.</col>")
            return false
        }
        val amount = data.streak.unclaimedTokens
        val reward = PvmReward(pvmTokens = amount)
        val id = "streak:$cid:cashout:${data.streak.currentStreak}"
        if (!rewards.grant(player, id, reward)) {
            player.mes("<col=e74c3c>Cash-out already processed for this streak.</col>")
            return false
        }
        val updated = data.streak.copy(unclaimedTokens = 0)
        cache.put(cid, data.copy(streak = updated))
        eventBus.publish(StreakCashOutEvent(player, data.streak.currentStreak, amount))
        player.mes("<col=2ecc71>Cashed out $amount PvM tokens from your streak!</col>")
        return true
    }

    /** The live streak state for [player]. */
    fun getStreak(player: Player): StreakData = cache.getOrCreate(player.characterId).streak

    /** Admin: set the streak to [amount]. */
    fun setStreak(player: Player, amount: Int) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        val updated =
            data.streak.copy(
                currentStreak = amount.coerceAtLeast(0),
                bestStreak = maxOf(data.streak.bestStreak, amount.coerceAtLeast(0)),
            )
        cache.put(cid, data.copy(streak = updated))
    }

    /** Admin: reset the streak and pool. */
    fun resetStreak(player: Player) {
        val cid = player.characterId
        val data = cache.getOrCreate(cid)
        cache.put(cid, data.copy(streak = StreakData.EMPTY))
    }
}
