package org.rsmod.api.talents

/**
 * Which talent tree a definition or balance belongs to. Player ranks live on permanent
 * `talent_rk_*` varps; company ranks live in the `company_talents` table shared by every approved
 * member of the company.
 */
public enum class TalentTree {
    PLAYER,
    COMPANY,
}

/**
 * The effect a talent rank contributes to. The numeric value is always expressed in basis points so
 * a single resolver can serve every effect - `bps = ranks * effectBpsPerRank`. Which systems
 * actually consume a key is a separate decision per key; the catalog never promises more than the
 * resolver exposes.
 */
public enum class TalentEffectKey {
    /** Outgoing damage vs npcs, +bps per rank (player tree). */
    NPC_DAMAGE_BPS,

    /** Bonus PvM points per kill, +bps per rank (player tree). */
    PVM_POINTS_BPS,

    /** Bonus non-combat xp, +bps per rank (player tree). */
    SKILL_XP_BPS,

    /** Extra drop-roll chance, +bps per rank (player tree). */
    DROP_ROLL_BPS,

    /** Bonus slayer points on task completion, +bps per rank (player tree). */
    SLAYER_POINTS_BPS,

    /** Company passive: bonus non-combat xp for every approved member, +bps per rank. */
    COMPANY_XP_BPS,

    /** Company passive: bonus PvM points per kill for every approved member, +bps per rank. */
    COMPANY_PVM_POINTS_BPS,

    /** Company passive: outgoing damage vs npcs for every approved member, +bps per rank. */
    COMPANY_DAMAGE_BPS,
}

/** Membership role inside a company. `LEADER` may also spend `leaderOnly` talents. */
public enum class CompanyRole(public val dbName: String) {
    LEADER("LEADER"),
    MEMBER("MEMBER");

    public companion object {
        public fun fromDb(name: String?): CompanyRole? = entries.firstOrNull { it.dbName == name }
    }
}

/**
 * One row of a talent catalog - data-driven so new talents never touch UI code.
 *
 * @property id stable key; for the player tree it also names the `talent_rk_<id>` varp.
 * @property tree which tree this talent belongs to - a definition can never cross trees.
 * @property tier 1-based tier; the tier gate requires [TalentRules.tierGate] spent points.
 * @property slot column index inside the tier grid (0-based), purely presentational.
 * @property pointsPerRank cost of a single rank; mirrors the reference layout where higher tiers
 *   cost more per rank.
 * @property requires definition id that must hold at least [requiresRank] before this talent can be
 *   trained - the branching-dependency mechanism.
 * @property requiresRank minimum rank of [requires]; `0` means the prereq is ignored.
 * @property leaderOnly company-only flag: spending this talent requires [CompanyRole.LEADER].
 * @property effectBpsPerRank basis points of [effectKey] granted per rank; `0` for talents that
 *   only gate other talents.
 */
public data class TalentDefinition(
    public val id: String,
    public val tree: TalentTree,
    public val tier: Int,
    public val slot: Int,
    public val name: String,
    public val description: String,
    public val iconObj: Int,
    public val maxRank: Int,
    public val pointsPerRank: Int,
    public val effectKey: TalentEffectKey,
    public val effectBpsPerRank: Int,
    public val requires: String? = null,
    public val requiresRank: Int = 0,
    public val leaderOnly: Boolean = false,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{2,48}"))) { "Invalid talent id: $id" }
        require(tier in 1..MAX_TIERS) { "Invalid tier $tier for $id" }
        require(slot in 0 until SLOTS_PER_TIER) { "Invalid slot $slot for $id" }
        require(maxRank in 1..MAX_RANK) { "Invalid maxRank $maxRank for $id" }
        require(pointsPerRank >= 1) { "Invalid pointsPerRank for $id" }
        require(effectBpsPerRank >= 0) { "Negative effect for $id" }
        require(requires == null || requiresRank >= 1) { "Prereq rank must be >= 1 for $id" }
        require(!leaderOnly || tree == TalentTree.COMPANY) { "leaderOnly is company-only: $id" }
    }

    public companion object {
        public const val MAX_TIERS: Int = 4
        public const val SLOTS_PER_TIER: Int = 5
        public const val MAX_RANK: Int = 10
    }
}

/**
 * The result of a train attempt. Every failure is a distinct case so the script layer can translate
 * it into a player-readable message and tests can assert the exact gate that fired. No path here
 * mutates state - mutation only happens after [Ok] inside the owning service's atomic write.
 */
public sealed interface TrainResult {
    public data object Ok : TrainResult

    public data class InsufficientPoints(val balance: Int, val cost: Int) : TrainResult

    public data class TierLocked(val tier: Int, val requiredSpent: Int, val spent: Int) :
        TrainResult

    public data class PrereqMissing(val required: String, val requiredRank: Int) : TrainResult

    public data object MaxRank : TrainResult

    /** Company tree: the talent is `leaderOnly` and the actor is not a [CompanyRole.LEADER]. */
    public data object LeaderRequired : TrainResult

    /** Company tree: the actor is not an approved member of the target company. */
    public data object NotMember : TrainResult

    /** Wrong tree - a company talent was submitted against the player tree, or vice versa. */
    public data object WrongTree : TrainResult
}
