package org.rsmod.content.pvmprogression.bounty

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.game.hit.HitType

/** Pure tests for [BountyObjectiveMatcher] - no game thread, only value-type facts. */
class BountyObjectiveMatcherTest {
    private fun task(type: BountyObjectiveType, target: String? = null, required: Int = 1) =
        BountyTask(
            bountyId = "test",
            name = "test",
            description = "test",
            objectiveType = type,
            target = target,
            requiredAmount = required,
            currentAmount = 0,
            difficulty = BountyDifficulty.NORMAL,
        )

    private fun facts(
        npcId: Int = 1,
        npcName: String = "Goblin",
        isBoss: Boolean = false,
        damageDealt: Int = 0,
        killTimeTenths: Int = 0,
        noDeath: Boolean = true,
        style: HitType? = null,
        isMutated: Boolean = false,
        slayerKill: Boolean = false,
        healed: Int = 0,
        damageTaken: Int = 0,
        challengeCompleted: ChallengeType? = null,
    ) =
        BountyObjectiveMatcher.KillFacts(
            npcId,
            npcName,
            10,
            isBoss,
            damageDealt,
            killTimeTenths,
            noDeath,
            style,
            isMutated,
            emptyList<MutationKind>(),
            slayerKill,
            0,
            healed,
            damageTaken,
            challengeCompleted,
        )

    @Test
    fun `KILL_NPC matches by id`() {
        val t = task(BountyObjectiveType.KILL_NPC, target = "1", required = 5)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(npcId = 1)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(npcId = 2)))
    }

    @Test
    fun `KILL_NPC matches by name case-insensitive`() {
        val t = task(BountyObjectiveType.KILL_NPC, target = "goblin")
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(npcName = "Goblin")))
    }

    @Test
    fun `KILL_NPC with null target matches any npc`() {
        val t = task(BountyObjectiveType.KILL_NPC, target = null)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(npcId = 999)))
    }

    @Test
    fun `KILL_BOSS only matches bosses`() {
        val t = task(BountyObjectiveType.KILL_BOSS, target = "1")
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(npcId = 1, isBoss = true)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(npcId = 1, isBoss = false)))
    }

    @Test
    fun `KILL_DIFFERENT_BOSSES grants one per boss kill`() {
        val t = task(BountyObjectiveType.KILL_DIFFERENT_BOSSES, required = 3)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(isBoss = true)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(isBoss = false)))
    }

    @Test
    fun `DEAL_DAMAGE accumulates damage against target`() {
        val t = task(BountyObjectiveType.DEAL_DAMAGE, target = "1", required = 500)
        assertEquals(150, BountyObjectiveMatcher.progress(t, facts(npcId = 1, damageDealt = 150)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(npcId = 2, damageDealt = 150)))
    }

    @Test
    fun `DEAL_STYLE_DAMAGE only counts the locked style`() {
        val t = task(BountyObjectiveType.DEAL_STYLE_DAMAGE, target = "melee", required = 100)
        assertEquals(
            50,
            BountyObjectiveMatcher.progress(
                t,
                facts(npcName = "melee", style = HitType.Melee, damageDealt = 50),
            ),
        )
        assertEquals(
            0,
            BountyObjectiveMatcher.progress(
                t,
                facts(npcName = "melee", style = HitType.Ranged, damageDealt = 50),
            ),
        )
    }

    @Test
    fun `TAKE_DAMAGE grants the damage taken amount`() {
        val t = task(BountyObjectiveType.TAKE_DAMAGE, required = 200)
        assertEquals(80, BountyObjectiveMatcher.progress(t, facts(damageTaken = 80)))
    }

    @Test
    fun `HEAL grants the healing amount`() {
        val t = task(BountyObjectiveType.HEAL, required = 100)
        assertEquals(40, BountyObjectiveMatcher.progress(t, facts(healed = 40)))
    }

    @Test
    fun `SLAYER_KILLS only counts slayer kills`() {
        val t = task(BountyObjectiveType.SLAYER_KILLS, required = 5)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(slayerKill = true)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(slayerKill = false)))
    }

    @Test
    fun `FAST_BOSS_KILL grants one when under threshold`() {
        // target is the kill-time threshold in tenths of a second; 600 = 60s.
        val t = task(BountyObjectiveType.FAST_BOSS_KILL, target = "600", required = 1)
        assertEquals(
            1,
            BountyObjectiveMatcher.progress(t, facts(isBoss = true, killTimeTenths = 300)),
        )
        assertEquals(
            0,
            BountyObjectiveMatcher.progress(t, facts(isBoss = true, killTimeTenths = 700)),
        )
        assertEquals(
            0,
            BountyObjectiveMatcher.progress(t, facts(isBoss = false, killTimeTenths = 100)),
        )
    }

    @Test
    fun `NO_DEATH_BOSS grants one on a no-death boss kill`() {
        val t = task(BountyObjectiveType.NO_DEATH_BOSS, required = 1)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(isBoss = true, noDeath = true)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(isBoss = true, noDeath = false)))
    }

    @Test
    fun `MUTANT_KILL grants one for mutated kills`() {
        val t = task(BountyObjectiveType.MUTANT_KILL, required = 1)
        assertEquals(1, BountyObjectiveMatcher.progress(t, facts(isMutated = true)))
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(isMutated = false)))
    }

    @Test
    fun `CHALLENGE_COMPLETE grants one when a challenge completed`() {
        val t = task(BountyObjectiveType.CHALLENGE_COMPLETE, required = 1)
        assertEquals(
            1,
            BountyObjectiveMatcher.progress(t, facts(challengeCompleted = ChallengeType.NO_FOOD)),
        )
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(challengeCompleted = null)))
    }

    @Test
    fun `completed task grants no further progress`() {
        val t = task(BountyObjectiveType.KILL_NPC, required = 1)
        t.currentAmount = 1
        assertTrue(t.isComplete)
        assertEquals(0, BountyObjectiveMatcher.progress(t, facts(npcId = 1)))
    }

    @Test
    fun `matches convenience returns true when progress is positive`() {
        val t = task(BountyObjectiveType.KILL_NPC, target = "1")
        assertTrue(BountyObjectiveMatcher.matches(t, facts(npcId = 1)))
        assertFalse(BountyObjectiveMatcher.matches(t, facts(npcId = 2)))
    }
}
