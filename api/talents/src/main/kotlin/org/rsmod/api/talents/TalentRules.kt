package org.rsmod.api.talents

/**
 * Pure unlock/validation rules shared by both trees.
 *
 * Everything here is a function of `(definition, balance, spent, ranks, role)` - no player or
 * company object leaks in, so the same checks run identically for the varp-backed player service
 * and the db-backed company service, and unit tests need no game harness.
 */
public object TalentRules {
    /**
     * Spent points required to unlock each tier (1-based index). Player tiers cost more to reach
     * because the player's pool grows per character; company gates are tighter because the pool is
     * shared across members.
     */
    private val PLAYER_TIER_GATES: IntArray = intArrayOf(0, 5, 12, 20)
    private val COMPANY_TIER_GATES: IntArray = intArrayOf(0, 4, 10, 16)

    /** Minimum cumulative spent points that unlock [tier] (1-based) for [tree]. */
    public fun tierGate(tree: TalentTree, tier: Int): Int {
        require(tier in 1..TalentDefinition.MAX_TIERS)
        return if (tree == TalentTree.PLAYER) PLAYER_TIER_GATES[tier - 1]
        else COMPANY_TIER_GATES[tier - 1]
    }

    public fun tierUnlocked(tree: TalentTree, tier: Int, spent: Int): Boolean =
        spent >= tierGate(tree, tier)

    /** Total points spent across [ranks] (`ranks` = definition id -> current rank). */
    public fun spent(tree: TalentTree, ranks: Map<String, Int>): Int =
        ranks.entries.sumOf { (id, rank) ->
            TalentCatalog.definition(id).also { require(it.tree == tree) }.pointsPerRank * rank
        }

    /**
     * Validates one rank purchase.
     *
     * Check order matters: ownership and permission run before any economy check so a misrouted or
     * unauthorised request fails closed rather than leaking state.
     *
     * @param role the actor's company role, or `null` for the player tree / a non-member.
     */
    public fun checkTrain(
        definition: TalentDefinition,
        tree: TalentTree,
        balance: Int,
        spent: Int,
        ranks: Map<String, Int>,
        role: CompanyRole?,
    ): TrainResult {
        if (definition.tree != tree) return TrainResult.WrongTree
        if (tree == TalentTree.COMPANY) {
            if (role == null) return TrainResult.NotMember
            if (definition.leaderOnly && role != CompanyRole.LEADER) {
                return TrainResult.LeaderRequired
            }
        }
        val rank = ranks[definition.id] ?: 0
        if (rank >= definition.maxRank) return TrainResult.MaxRank
        val gate = tierGate(tree, definition.tier)
        if (spent < gate) {
            return TrainResult.TierLocked(definition.tier, gate, spent)
        }
        val prereq = definition.requires
        if (prereq != null && (ranks[prereq] ?: 0) < definition.requiresRank) {
            return TrainResult.PrereqMissing(prereq, definition.requiresRank)
        }
        if (balance < definition.pointsPerRank) {
            return TrainResult.InsufficientPoints(balance, definition.pointsPerRank)
        }
        return TrainResult.Ok
    }
}
