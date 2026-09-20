package org.rsmod.content.pvmprogression.challenge

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounter
import org.rsmod.content.pvmprogression.events.ChallengeTier
import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.events.CombatChallengeCompleteEvent
import org.rsmod.content.pvmprogression.rewards.PvmReward
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * The random combat-challenge engine.
 *
 * When a boss encounter opens ([maybeOffer]), a config-driven chance rolls whether a challenge is
 * offered; the type is drawn from [ChallengeEligibility.eligibleTypes] for that boss. The player
 * may accept or ignore the offer; ignoring (or letting it time out) simply discards it. Accepting
 * arms the challenge: each per-cycle tick ([onCycleTick]) samples the encounter for fail conditions
 * (used food, changed prayer, used potions, exceeded the speed window, moved, etc.). The challenge
 * never ends the boss fight - it only sets a `failed` flag and announces the reason.
 *
 * On boss kill ([onBossKill]) an accepted, non-failed challenge pays out a tier-scaled reward
 * through [PvmRewardService] with an idempotent id keyed by player + encounter, so a multi-hit
 * death callback cannot double-pay. A failed challenge only prints the fail reason.
 *
 * Tier scaling: BRONZE / SILVER / GOLD / ELITE correspond to the boss's combat level band, so a
 * harder boss's challenge is worth more.
 */
@Singleton
class CombatChallengeManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val random: GameRandom,
    private val cache: PvmProgressionCache,
    private val rewards: PvmRewardService,
    private val eventBus: EventBus,
    private val mapClock: MapClock,
) {
    /** Roll an offer for [player] against [encounter]. Returns the offer if one was generated. */
    fun maybeOffer(player: Player, encounter: BossEncounter): PendingChallenge? {
        val config = configSource().challenge
        if (!config.enabled) {
            return null
        }
        val data = cache.getOrCreate(player.characterId)
        val challenges = data.challenges
        if (mapClock.cycle - challenges.lastOfferCycle < config.offerCooldownCycles) {
            return null
        }
        if (random.of(10_000) >= config.offerChanceBps) {
            return null
        }
        val eligible = ChallengeEligibility.eligibleTypes(encounter.bossId, encounter.bossName)
        if (eligible.isEmpty()) {
            return null
        }
        val type = eligible.elementAt(random.of(eligible.size))
        val tier = tierFor(encounter.bossMaxHp)
        val offer =
            PendingChallenge(
                encounterId = encounter.id,
                bossId = encounter.bossId,
                bossName = encounter.bossName,
                type = type,
                tier = tier,
                speedKillSeconds = scaledSpeedSeconds(encounter.bossMaxHp),
                lowDamageCap = scaledLowDamageCap(encounter.bossMaxHp),
                lockedStyle = randomStyle(),
                finishStyle = randomStyle(),
                companionPercentTarget = 0,
            )
        val updatedChallenges =
            challenges.copy(
                lastOfferCycle = mapClock.cycle,
                activeOffers = challenges.activeOffers + offer,
            )
        cache.put(player.characterId, data.copy(challenges = updatedChallenges))
        announceOffer(player, offer)
        return offer
    }

    /** Marks [offer] as accepted by the player. */
    fun accept(player: Player, offer: PendingChallenge) {
        offer.accepted = true
        player.mes("<col=3498db>Challenge accepted: ${describe(offer)}.</col>")
    }

    /** Per-cycle check of active, accepted challenges against the live encounter. */
    fun onCycleTick(encounter: BossEncounter) {
        if (!encounter.player.isSlotAssigned) {
            return
        }
        val data = cache.getOrCreate(encounter.player.characterId)
        for (offer in data.challenges.activeOffers) {
            if (offer.encounterId != encounter.id || !offer.accepted || offer.failed) {
                continue
            }
            checkFailConditions(offer, encounter)
        }
    }

    /**
     * Called on boss kill. An accepted, non-failed challenge succeeds and pays out; a failed one
     * prints the reason. Reward id is idempotent per player + encounter, so a multi-hit death
     * callback cannot double-pay.
     */
    fun onBossKill(encounter: BossEncounter) {
        val player = encounter.player
        val data = cache.getOrCreate(player.characterId)
        val offer =
            data.challenges.activeOffers.firstOrNull { it.encounterId == encounter.id } ?: return
        if (offer.failed) {
            player.mes(
                "<col=e74c3c>CHALLENGE FAILED: ${offer.failReason ?: "conditions not met"}.</col>"
            )
            removeOffer(player, offer)
            return
        }
        if (!offer.accepted) {
            removeOffer(player, offer)
            return
        }
        val reward = rewardForTier(offer.tier)
        val id = "challenge:${player.characterId}:${encounter.id}"
        if (rewards.grant(player, id, reward)) {
            eventBus.publish(
                CombatChallengeCompleteEvent(
                    player,
                    offer.bossId,
                    offer.bossName,
                    offer.type,
                    offer.tier,
                )
            )
            player.mes("<col=2ecc71>Challenge complete: ${describe(offer)}!</col>")
        }
        recordCompletion(player, offer)
        removeOffer(player, offer)
    }

    /** Admin: forces a challenge offer of [type] for [encounter]. */
    fun forceOffer(
        player: Player,
        encounter: BossEncounter,
        type: ChallengeType,
    ): PendingChallenge {
        val tier = tierFor(encounter.bossMaxHp)
        val offer =
            PendingChallenge(
                encounterId = encounter.id,
                bossId = encounter.bossId,
                bossName = encounter.bossName,
                type = type,
                tier = tier,
                speedKillSeconds = scaledSpeedSeconds(encounter.bossMaxHp),
                lowDamageCap = scaledLowDamageCap(encounter.bossMaxHp),
                lockedStyle = randomStyle(),
                finishStyle = randomStyle(),
            )
        offer.accepted = true
        val data = cache.getOrCreate(player.characterId)
        val updated = data.challenges.copy(activeOffers = data.challenges.activeOffers + offer)
        cache.put(player.characterId, data.copy(challenges = updated))
        return offer
    }

    /** Admin: forces the active challenge for [encounter] to succeed and pay out. */
    fun forceComplete(player: Player, encounter: BossEncounter) {
        val data = cache.getOrCreate(player.characterId)
        val offer =
            data.challenges.activeOffers.firstOrNull { it.encounterId == encounter.id } ?: return
        offer.failed = false
        offer.accepted = true
        onBossKill(encounter)
    }

    /** Admin: forces the active challenge for [encounter] to fail. */
    fun forceFail(player: Player, encounter: BossEncounter, reason: String = "admin override") {
        val data = cache.getOrCreate(player.characterId)
        val offer =
            data.challenges.activeOffers.firstOrNull { it.encounterId == encounter.id } ?: return
        offer.failed = true
        offer.failReason = reason
        player.mes("<col=e74c3c>CHALLENGE FAILED: $reason.</col>")
        removeOffer(player, offer)
    }

    /** The active offer for [encounter], if any. */
    fun activeOffer(player: Player, encounter: BossEncounter): PendingChallenge? =
        cache.getOrCreate(player.characterId).challenges.activeOffers.firstOrNull {
            it.encounterId == encounter.id
        }

    /** Removes [offer] from the player's offer pool (explicit ignore / completion). */
    fun ignore(player: Player, offer: PendingChallenge) {
        removeOffer(player, offer)
    }

    private fun removeOffer(player: Player, offer: PendingChallenge) {
        val data = cache.getOrCreate(player.characterId)
        val updated =
            data.challenges.copy(
                activeOffers = data.challenges.activeOffers.filter { it !== offer }
            )
        cache.put(player.characterId, data.copy(challenges = updated))
    }

    private fun checkFailConditions(offer: PendingChallenge, encounter: BossEncounter) {
        val config = configSource().challenge
        val reason: String? =
            when (offer.type) {
                ChallengeType.NO_FOOD -> if (encounter.foodConsumed > 0) "consumed food" else null
                ChallengeType.NO_PRAYER ->
                    if (encounter.prayerConsumed > 0) "prayer points changed" else null
                ChallengeType.NO_POTION ->
                    if (encounter.potionsConsumed > 0) "consumed a potion" else null
                ChallengeType.SPEED_KILL ->
                    if (encounter.elapsedTenths > offer.speedKillSeconds * 10) {
                        "exceeded the speed-kill window (${offer.speedKillSeconds}s)"
                    } else {
                        null
                    }
                ChallengeType.LOW_DAMAGE_TAKEN ->
                    if (encounter.damageTaken > offer.lowDamageCap) {
                        "took too much damage (cap ${offer.lowDamageCap})"
                    } else {
                        null
                    }
                ChallengeType.STYLE_LOCK -> {
                    val disallowed =
                        encounter.stylesUsed.any { it != offer.lockedStyle && it != -1 }
                    if (disallowed) "used a non-locked combat style" else null
                }
                ChallengeType.FINISH_WITH_STYLE ->
                    if (
                        encounter.finalHitStyle in 0..3 &&
                            encounter.finalHitStyle != offer.finishStyle
                    ) {
                        "did not finish with the required style"
                    } else {
                        null
                    }
                ChallengeType.NO_SPECIAL_ATTACK ->
                    if (encounter.usedSpecialAttack) "used a special attack" else null
                ChallengeType.SPECIAL_FINISH -> null
                ChallengeType.NO_COMPANION ->
                    if (encounter.companionAssisted) "had companion assistance" else null
                ChallengeType.COMPANION_ONLY_PHASE -> null
                ChallengeType.NO_MOVEMENT -> {
                    val bossHpFractionBps =
                        if (encounter.bossMaxHp > 0) {
                            val boss = encounter.bossRef
                            val hp = boss?.hitpoints ?: 0
                            hp * 10_000 / encounter.bossMaxHp
                        } else {
                            10_000
                        }
                    if (bossHpFractionBps < config.noMovementPhaseBps && encounter.moved) {
                        "moved during the final phase"
                    } else {
                        null
                    }
                }
                ChallengeType.LOW_HP_FINISH -> {
                    if (
                        encounter.playerHpPercentAtKill in 1..99 &&
                            encounter.playerHpPercentAtKill >= config.lowHpFinishBps
                    ) {
                        "finished above the low-HP threshold"
                    } else {
                        null
                    }
                }
            }
        if (reason != null) {
            offer.failed = true
            offer.failReason = reason
        }
    }

    private fun recordCompletion(player: Player, offer: PendingChallenge) {
        val data = cache.getOrCreate(player.characterId)
        val byType = data.challenges.completedByType.toMutableMap()
        byType.merge(offer.type, 1) { a, b -> a + b }
        val byTier = data.challenges.completedByTier.toMutableMap()
        byTier.merge(offer.tier, 1) { a, b -> a + b }
        val updated =
            data.challenges.copy(
                totalCompleted = data.challenges.totalCompleted + 1,
                completedByType = byType,
                completedByTier = byTier,
            )
        cache.put(player.characterId, data.copy(challenges = updated))
    }

    private fun announceOffer(player: Player, offer: PendingChallenge) {
        val notifyConfig = configSource().notify
        if (notifyConfig.challengeAnnounce) {
            player.mes("<col=3498db>Challenge offered (${offer.tier}): ${describe(offer)}.</col>")
        }
    }

    private fun describe(offer: PendingChallenge): String =
        "${offer.type} vs ${offer.bossName} (${offer.tier})"

    private fun tierFor(bossMaxHp: Int): ChallengeTier =
        when {
            bossMaxHp < 150 -> ChallengeTier.BRONZE
            bossMaxHp < 400 -> ChallengeTier.SILVER
            bossMaxHp < 800 -> ChallengeTier.GOLD
            else -> ChallengeTier.ELITE
        }

    private fun rewardForTier(tier: ChallengeTier): PvmReward {
        val config = configSource().challenge
        return when (tier) {
            ChallengeTier.BRONZE -> PvmReward(config.coinsBronze, config.tokensBronze)
            ChallengeTier.SILVER -> PvmReward(config.coinsSilver, config.tokensSilver)
            ChallengeTier.GOLD -> PvmReward(config.coinsGold, config.tokensGold)
            ChallengeTier.ELITE -> PvmReward(config.coinsElite, config.tokensElite)
        }
    }

    private fun scaledSpeedSeconds(bossMaxHp: Int): Int {
        val config = configSource().challenge
        val scale = (bossMaxHp / 100).coerceAtLeast(1)
        return (config.speedKillBaseSeconds * scale / 2).coerceAtLeast(config.speedKillBaseSeconds)
    }

    private fun scaledLowDamageCap(bossMaxHp: Int): Int {
        val config = configSource().challenge
        val scale = (bossMaxHp / 100).coerceAtLeast(1)
        return (config.lowDamageBaseAmount * scale).coerceAtLeast(config.lowDamageBaseAmount)
    }

    private fun randomStyle(): Int = random.of(1, 3)
}
