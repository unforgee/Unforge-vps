package org.rsmod.content.pvmprogression.bounty

/**
 * The objective category a [BountyTask] tracks. [BountyObjectiveMatcher] maps each gameplay event
 * to one of these; the matcher is the only place that knows how a kill / damage event advances an
 * objective, so it can be unit-tested without a game thread.
 */
enum class BountyObjectiveType {
    KILL_NPC,
    KILL_NPC_CATEGORY,
    KILL_BOSS,
    KILL_DIFFERENT_BOSSES,
    DEAL_DAMAGE,
    DEAL_STYLE_DAMAGE,
    TAKE_DAMAGE,
    HEAL,
    SLAYER_KILLS,
    FAST_BOSS_KILL,
    NO_DEATH_BOSS,
    MUTANT_KILL,
    CHALLENGE_COMPLETE,
}

/**
 * Difficulty bucket; drives the coin / token payout table in [PvmProgressionConfig.BountyConfig].
 */
enum class BountyDifficulty {
    EASY,
    NORMAL,
    HARD,
    ELITE,
}

/**
 * One bounty board entry. Mutable only via [currentAmount], which the matcher advances; every other
 * field is fixed at generation time. `expiry` is an epoch-millis cutoff so the manager can drop a
 * stale task without waiting for the reset window.
 */
data class BountyTask(
    val bountyId: String,
    val name: String,
    val description: String,
    val objectiveType: BountyObjectiveType,
    val target: String?,
    val requiredAmount: Int,
    var currentAmount: Int = 0,
    val difficulty: BountyDifficulty,
    val rewardTier: Int = difficulty.ordinal,
    val expiry: Long = Long.MAX_VALUE,
) {
    /** `true` once [currentAmount] has reached [requiredAmount]. */
    val isComplete: Boolean
        get() = currentAmount >= requiredAmount

    /** Progress in basis points (0-10_000), for hub display. */
    val progressBps: Int
        get() =
            if (requiredAmount <= 0) 10_000
            else (currentAmount * 10_000 / requiredAmount).coerceIn(0, 10_000)
}
