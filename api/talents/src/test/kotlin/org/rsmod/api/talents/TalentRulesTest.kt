package org.rsmod.api.talents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.talents.TalentCatalog.definition

/**
 * Pure-rule coverage for the shared talent-tree validation: tier gates, prerequisite branches,
 * max-rank, point costs, tree separation and the company permission flags. These run without any
 * game harness because [TalentRules] is deliberately player/company-agnostic.
 */
class TalentRulesTest {
    private fun def(id: String) = definition(id)

    private fun check(
        id: String,
        tree: TalentTree,
        balance: Int = 0,
        spent: Int = 0,
        ranks: Map<String, Int> = emptyMap(),
        role: CompanyRole? = null,
    ) = TalentRules.checkTrain(def(id), tree, balance, spent, ranks, role)

    @Test
    fun `catalog shapes are stable`() {
        assertEquals(17, TalentCatalog.player.size)
        assertEquals(13, TalentCatalog.company.size)
        for (tree in TalentTree.entries) {
            val defs = TalentCatalog.definitions(tree)
            assertTrue(defs.all { it.tree == tree })
            assertTrue((1..TalentDefinition.MAX_TIERS).all { t -> defs.any { it.tier == t } })
            assertTrue(defs.any { it.maxRank > 1 })
        }
        // At least one branching prereq per tree and one leader-only company talent.
        assertTrue(TalentCatalog.player.any { it.requires != null })
        assertTrue(TalentCatalog.company.any { it.requires != null })
        assertTrue(TalentCatalog.company.any { it.leaderOnly })
    }

    @Test
    fun `tier one is open and spends a point`() {
        val result = check("sharp-strikes", TalentTree.PLAYER, balance = 1, spent = 0)
        assertEquals(TrainResult.Ok, result)
    }

    @Test
    fun `insufficient points blocks the spend`() {
        val result = check("sharp-strikes", TalentTree.PLAYER, balance = 0)
        assertEquals(TrainResult.InsufficientPoints(0, 1), result)
    }

    @Test
    fun `later tiers stay locked until the gate is spent`() {
        // Player tier 2 gate is 5 spent.
        val locked = check("battle-focus", TalentTree.PLAYER, balance = 10, spent = 4)
        assertEquals(TrainResult.TierLocked(2, 5, 4), locked)
        val open = check("battle-focus", TalentTree.PLAYER, balance = 10, spent = 5)
        assertEquals(TrainResult.Ok, open)
    }

    @Test
    fun `company tier gates use the tighter company table`() {
        // Company tier 2 gate is 4 spent.
        val locked =
            check(
                "supply-lines",
                TalentTree.COMPANY,
                balance = 10,
                spent = 3,
                role = CompanyRole.MEMBER,
            )
        assertEquals(TrainResult.TierLocked(2, 4, 3), locked)
        val open =
            check(
                "supply-lines",
                TalentTree.COMPANY,
                balance = 10,
                spent = 4,
                role = CompanyRole.MEMBER,
            )
        assertEquals(TrainResult.Ok, open)
    }

    @Test
    fun `prereq branches enforce the required rank`() {
        // momentum requires sharp-strikes rank 3 (and tier 2's 5 spent).
        val missing =
            check(
                "momentum",
                TalentTree.PLAYER,
                balance = 10,
                spent = 5,
                ranks = mapOf("sharp-strikes" to 2),
            )
        assertEquals(TrainResult.PrereqMissing("sharp-strikes", 3), missing)
        val met =
            check(
                "momentum",
                TalentTree.PLAYER,
                balance = 10,
                spent = 5,
                ranks = mapOf("sharp-strikes" to 3),
            )
        assertEquals(TrainResult.Ok, met)
    }

    @Test
    fun `max rank blocks further training`() {
        val result =
            check(
                "hoarder",
                TalentTree.PLAYER,
                balance = 10,
                spent = 12,
                ranks = mapOf("hoarder" to 1),
            )
        assertEquals(TrainResult.MaxRank, result)
    }

    @Test
    fun `company talents reject the player tree and vice versa`() {
        assertEquals(
            TrainResult.WrongTree,
            check("war-chest-fund", TalentTree.PLAYER, balance = 10),
        )
        assertEquals(
            TrainResult.WrongTree,
            check("point-runner", TalentTree.COMPANY, balance = 10, role = CompanyRole.LEADER),
        )
    }

    @Test
    fun `company tree requires membership`() {
        assertEquals(
            TrainResult.NotMember,
            check("shared-knowledge", TalentTree.COMPANY, balance = 10, role = null),
        )
    }

    @Test
    fun `leaderOnly talents require the leader role`() {
        assertEquals(
            TrainResult.LeaderRequired,
            check(
                "war-council",
                TalentTree.COMPANY,
                balance = 20,
                spent = 10,
                role = CompanyRole.MEMBER,
            ),
        )
        assertEquals(
            TrainResult.Ok,
            check(
                "war-council",
                TalentTree.COMPANY,
                balance = 20,
                spent = 10,
                role = CompanyRole.LEADER,
            ),
        )
    }

    @Test
    fun `spent accumulates per-rank costs`() {
        val ranks = mapOf("sharp-strikes" to 3, "point-runner" to 1, "momentum" to 1)
        // sharp-strikes 3x1 + point-runner 1x1 + momentum 1x2 = 6
        assertEquals(6, TalentRules.spent(TalentTree.PLAYER, ranks))
        assertTrue(TalentRules.tierUnlocked(TalentTree.PLAYER, 2, 6))
        assertFalse(TalentRules.tierUnlocked(TalentTree.PLAYER, 3, 6))
    }
}
