package org.rsmod.content.pvmprogression.encounter

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The live state of one player-vs-boss encounter. Mutable, but only ever touched by the owning
 * player's game-thread work, so no locking is required.
 *
 * Fields are `internal set` so the [BossEncounterTracker] and the encounter script can update them,
 * but downstream managers read them as val-like.
 */
class BossEncounter(
    val id: Long,
    val player: Player,
    val bossId: Int,
    val bossName: String,
    var bossRef: Npc?,
    val bossMaxHp: Int,
    val startCycle: Int,
    var lastActivityCycle: Int,
    val startCoords: CoordGrid,
    val startHitpoints: Int,
    val startPrayer: Int,
) {
    var endCycle: Int = -1
        internal set

    var endReason: AbandonReason? = null
        internal set

    /** Elapsed time in tenths of a second (one cycle = 0.6s). */
    val elapsedTenths: Int
        get() = ((endCycle - startCycle).coerceAtLeast(0)) * 6

    var damageDealt: Int = 0
        internal set

    var damageTaken: Int = 0
        internal set

    var healing: Int = 0
        internal set

    var foodConsumed: Int = 0
        internal set

    var potionsConsumed: Int = 0
        internal set

    var prayerConsumed: Int = 0
        internal set

    var highestHit: Int = 0
        internal set

    var highestSpecialHit: Int = 0
        internal set

    var usedSpecialAttack: Boolean = false
        internal set

    val stylesUsed: MutableSet<Int> = mutableSetOf()
    var moved: Boolean = false
        internal set

    var companionAssisted: Boolean = false
        internal set

    var companionDamage: Int = 0
        internal set

    var bossHpAtDeath: Int = 0
        internal set

    var playerHpAtKill: Int = 0
        internal set

    var playerHpPercentAtKill: Int = 0
        internal set

    var finalHitStyle: Int = -1
        internal set

    var finalHitSpecial: Boolean = false
        internal set

    /** Sampler state: last sampled player hitpoints, for damage-taken delta computation. */
    var lastSampledPlayerHp: Int = startHitpoints
        internal set

    /** Sampler state: last sampled prayer level, for prayer-consumed delta computation. */
    var lastSampledPrayer: Int = startPrayer
        internal set

    /** Sampler state: last sampled boss hitpoints, for damage-dealt delta computation. */
    var lastSampledBossHp: Int = bossMaxHp
        internal set

    /** Whether a challenge offer has already been generated for this encounter. */
    var challengeOffered: Boolean = false
        internal set
}
