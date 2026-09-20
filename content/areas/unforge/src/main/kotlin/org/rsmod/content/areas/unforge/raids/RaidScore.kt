package org.rsmod.content.areas.unforge.raids

/**
 * Deterministic raid score model. All functions are pure so the whole scoring pipeline can be
 * unit-tested without a live game.
 */
object RaidScore {
    /** Base points per room kind. */
    const val COMBAT_ROOM_BASE = 150
    const val WAVE_ROOM_BASE = 200
    const val PUZZLE_ROOM_BASE = 100
    const val MINIBOSS_BASE = 750
    const val FINAL_BOSS_BASE = 3_500

    /** Flat bonus per npc killed inside a room. */
    const val KILL_BONUS = 10

    /** Per-death score penalty in basis points (-8% per death). */
    const val DEATH_PENALTY_BPS = 800

    /** Total death penalty is capped at -40%. */
    const val MAX_DEATH_PENALTY_BPS = 4_000

    /** Completion-time bonus tiers (cycles: 1 cycle = 0.6s). */
    const val FAST_CLEAR_CYCLES = 3_000 // 30 minutes -> +5%
    const val SPEED_CLEAR_CYCLES = 4_500 // 45 minutes -> +2%
    const val FAST_BONUS_BPS = 500
    const val SPEED_BONUS_BPS = 200

    fun baseFor(kind: RaidRoomKind): Int =
        when (kind) {
            RaidRoomKind.COMBAT -> COMBAT_ROOM_BASE
            RaidRoomKind.WAVE -> WAVE_ROOM_BASE
            RaidRoomKind.PUZZLE -> PUZZLE_ROOM_BASE
            RaidRoomKind.MINIBOSS -> MINIBOSS_BASE
            RaidRoomKind.BOSS -> FINAL_BOSS_BASE
        }

    /** Points earned for clearing a room: base value plus a flat bonus per npc killed. */
    fun roomPoints(kind: RaidRoomKind, kills: Int): Int =
        baseFor(kind) + kills.coerceAtLeast(0) * KILL_BONUS

    /** Death penalty in basis points, capped so score can never be fully erased. */
    fun deathPenaltyBps(deaths: Int): Int =
        (deaths.coerceAtLeast(0) * DEATH_PENALTY_BPS).coerceAtMost(MAX_DEATH_PENALTY_BPS)

    /** Small bonus for fast completions. */
    fun timeBonusBps(elapsedCycles: Int): Int =
        when {
            elapsedCycles < 0 -> 0
            elapsedCycles <= FAST_CLEAR_CYCLES -> FAST_BONUS_BPS
            elapsedCycles <= SPEED_CLEAR_CYCLES -> SPEED_BONUS_BPS
            else -> 0
        }

    /**
     * Final raid score: `(roomScore + kill bonuses) * difficulty * (1 - deathPenalty) + timeBonus`.
     * Never negative.
     */
    fun finalScore(
        roomScore: Int,
        kills: Int,
        deaths: Int,
        difficulty: RaidDifficulty,
        elapsedCycles: Int,
    ): Int {
        val base = (roomScore + kills * KILL_BONUS).coerceAtLeast(0)
        val scaled = base * difficulty.scoreMultiplierBps / 10_000
        val penalised = scaled - scaled * deathPenaltyBps(deaths) / 10_000
        val withBonus = penalised + penalised * timeBonusBps(elapsedCycles) / 10_000
        return withBonus.coerceAtLeast(0)
    }
}
