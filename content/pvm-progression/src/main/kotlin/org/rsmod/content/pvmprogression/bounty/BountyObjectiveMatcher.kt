package org.rsmod.content.pvmprogression.bounty

import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.game.hit.HitType

/**
 * Pure, game-thread-free matching of bounty objectives to gameplay events.
 *
 * The manager extracts the minimal, value-type facts from each live event (npc id, name, damage,
 * style, kill time, etc.) and hands them to [matches] here. Keeping the translation in one pure
 * object means the entire objective table can be pinned down by unit tests without a `Player`,
 * `Npc`, or game cycle.
 *
 * Each matcher branch returns both whether the event applies to the task and how much progress it
 * grants (usually 1 for a kill, the damage amount for damage objectives, 0 if the event does not
 * match the task's target filter).
 */
object BountyObjectiveMatcher {
    /** The facts the matcher needs from a kill event (npc or boss). */
    data class KillFacts(
        val npcId: Int,
        val npcName: String,
        val visLevel: Int,
        val isBoss: Boolean,
        val damageDealt: Int,
        val killTimeTenths: Int,
        val noDeath: Boolean,
        val style: HitType?,
        val isMutated: Boolean,
        val mutationKinds: List<MutationKind>,
        val slayerKill: Boolean,
        val differentBossesKilledThisSession: Int,
        val healed: Int,
        val damageTaken: Int,
        val challengeCompleted: ChallengeType?,
    )

    /**
     * Returns the progress amount [event] grants towards [task], or `0` when the event does not
     * match the task's objective / target filter. The caller adds the result to
     * `task.currentAmount` and checks `isComplete`.
     */
    fun progress(task: BountyTask, event: KillFacts): Int {
        if (task.isComplete) {
            return 0
        }
        return when (task.objectiveType) {
            BountyObjectiveType.KILL_NPC -> {
                if (matchesTarget(task, event.npcId, event.npcName)) 1 else 0
            }
            BountyObjectiveType.KILL_NPC_CATEGORY -> {
                if (matchesTarget(task, event.npcId, event.npcName)) 1 else 0
            }
            BountyObjectiveType.KILL_BOSS -> {
                if (event.isBoss && matchesTarget(task, event.npcId, event.npcName)) 1 else 0
            }
            BountyObjectiveType.KILL_DIFFERENT_BOSSES -> {
                if (event.isBoss) 1 else 0
            }
            BountyObjectiveType.DEAL_DAMAGE -> {
                if (matchesTarget(task, event.npcId, event.npcName)) event.damageDealt else 0
            }
            BountyObjectiveType.DEAL_STYLE_DAMAGE -> {
                val targetStyle = parseStyle(task.target)
                if (
                    targetStyle != null &&
                        event.style == targetStyle &&
                        matchesTargetByName(task, event.npcName)
                ) {
                    event.damageDealt
                } else {
                    0
                }
            }
            BountyObjectiveType.TAKE_DAMAGE -> event.damageTaken.coerceAtLeast(0)
            BountyObjectiveType.HEAL -> event.healed.coerceAtLeast(0)
            BountyObjectiveType.SLAYER_KILLS -> if (event.slayerKill) 1 else 0
            BountyObjectiveType.FAST_BOSS_KILL -> {
                if (!event.isBoss) return 0
                val threshold = task.target?.toIntOrNull() ?: Int.MAX_VALUE
                if (event.killTimeTenths in 1..threshold) 1 else 0
            }
            BountyObjectiveType.NO_DEATH_BOSS -> {
                if (event.isBoss && event.noDeath) 1 else 0
            }
            BountyObjectiveType.MUTANT_KILL -> {
                if (event.isMutated) 1 else 0
            }
            BountyObjectiveType.CHALLENGE_COMPLETE -> {
                if (event.challengeCompleted != null) 1 else 0
            }
        }
    }

    /** Convenience: `true` when [progress] > 0. */
    fun matches(task: BountyTask, event: KillFacts): Boolean = progress(task, event) > 0

    private fun matchesTarget(task: BountyTask, npcId: Int, npcName: String): Boolean {
        val target = task.target ?: return true
        if (target.toIntOrNull() == npcId) {
            return true
        }
        return npcName.equals(target, ignoreCase = true)
    }

    private fun matchesTargetByName(task: BountyTask, npcName: String): Boolean {
        val target = task.target ?: return true
        return target.toIntOrNull() == null && npcName.equals(target, ignoreCase = true)
    }

    private fun parseStyle(target: String?): HitType? =
        when (target?.lowercase()?.trim()) {
            "melee" -> HitType.Melee
            "ranged",
            "range" -> HitType.Ranged
            "magic",
            "mage" -> HitType.Magic
            "typeless" -> HitType.Typeless
            else -> null
        }
}
