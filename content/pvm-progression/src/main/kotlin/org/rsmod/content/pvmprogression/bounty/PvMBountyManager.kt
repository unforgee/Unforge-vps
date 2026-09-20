package org.rsmod.content.pvmprogression.bounty

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.events.BountyCategory
import org.rsmod.content.pvmprogression.events.BountyCategoryCompleteEvent
import org.rsmod.content.pvmprogression.events.BountyCompleteEvent
import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.content.pvmprogression.rewards.PvmReward
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * The bounty-board manager.
 *
 * Each character has three category lists (daily / weekly / elite) plus two reset deadlines. The
 * manager regenerates a category only when the wall-clock has crossed its deadline, so a player who
 * logs in mid-day keeps their morning's tasks. Generation draws objectives and targets from
 * [BOSS_TARGETS] / [BountyObjectiveType] using the player-seeded [GameRandom], so two players do
 * not get the same board.
 *
 * Kill / damage / challenge events flow through [onNpcKill] / [onMutationKill] /
 * [onChallengeComplete] and are matched by [BountyObjectiveMatcher]; a completed task pays out
 * through the idempotent [PvmRewardService] and publishes a [BountyCompleteEvent]. When all tasks
 * in a category complete, a [BountyCategoryCompleteEvent] fires.
 */
@Singleton
class PvMBountyManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val random: GameRandom,
    private val cache: PvmProgressionCache,
    private val rewards: PvmRewardService,
    private val eventBus: EventBus,
) {
    private val idSeq = AtomicLong(1L)

    /** The persisted bounty board for [player], generating each category lazily on first access. */
    fun getOrCreate(player: Player): BountyData {
        val data = cache.getOrCreate(player.characterId)
        var bounty = data.bounty
        bounty = dailyResetIfNeeded(player, bounty)
        bounty = weeklyResetIfNeeded(player, bounty)
        if (bounty !== data.bounty) {
            cache.put(player.characterId, data.copy(bounty = bounty))
        }
        return bounty
    }

    /** Force-regenerate the daily list for [player] (admin / reroll). */
    fun generateDaily(player: Player): BountyData {
        val data = cache.getOrCreate(player.characterId)
        val config = configSource().bounty
        val tasks =
            generateTasks(player, BountyCategory.DAILY, BountyDifficulty.EASY, config.dailyCount)
        val bounty = data.bounty.copy(dailyTasks = tasks, dailyResetEpoch = nextDailyReset())
        cache.put(player.characterId, data.copy(bounty = bounty))
        return bounty
    }

    /** Force-regenerate the weekly list for [player]. */
    fun generateWeekly(player: Player): BountyData {
        val data = cache.getOrCreate(player.characterId)
        val config = configSource().bounty
        val tasks =
            generateTasks(
                player,
                BountyCategory.WEEKLY,
                BountyDifficulty.NORMAL,
                config.weeklyCount,
            )
        val bounty = data.bounty.copy(weeklyTasks = tasks, weeklyResetEpoch = nextWeeklyReset())
        cache.put(player.characterId, data.copy(bounty = bounty))
        return bounty
    }

    /** Reroll the [category] list for [player]. */
    fun reroll(player: Player, category: BountyCategory): BountyData =
        when (category) {
            BountyCategory.DAILY -> generateDaily(player)
            BountyCategory.WEEKLY -> generateWeekly(player)
            BountyCategory.ELITE -> generateElite(player)
        }

    /** Advance bounty progress on an npc kill. [facts] is the matcher's value-type input. */
    fun onNpcKill(player: Player, facts: BountyObjectiveMatcher.KillFacts) {
        val bounty = getOrCreate(player)
        val daily = advance(player, bounty.dailyTasks, BountyCategory.DAILY, facts)
        val weekly = advance(player, bounty.weeklyTasks, BountyCategory.WEEKLY, facts)
        val elite = advance(player, bounty.eliteTasks, BountyCategory.ELITE, facts)
        val data = cache.getOrCreate(player.characterId)
        cache.put(
            player.characterId,
            data.copy(
                bounty = bounty.copy(dailyTasks = daily, weeklyTasks = weekly, eliteTasks = elite)
            ),
        )
    }

    /** Advance bounty progress on a mutation kill. */
    fun onMutationKill(player: Player, npcId: Int, npcName: String, kinds: List<MutationKind>) {
        val facts =
            BountyObjectiveMatcher.KillFacts(
                npcId = npcId,
                npcName = npcName,
                visLevel = 0,
                isBoss = false,
                damageDealt = 0,
                killTimeTenths = 0,
                noDeath = true,
                style = null,
                isMutated = true,
                mutationKinds = kinds,
                slayerKill = false,
                differentBossesKilledThisSession = 0,
                healed = 0,
                damageTaken = 0,
                challengeCompleted = null,
            )
        onNpcKill(player, facts)
    }

    /** Advance bounty progress on a challenge completion. */
    fun onChallengeComplete(player: Player, type: ChallengeType) {
        val facts =
            BountyObjectiveMatcher.KillFacts(
                npcId = -1,
                npcName = "",
                visLevel = 0,
                isBoss = false,
                damageDealt = 0,
                killTimeTenths = 0,
                noDeath = true,
                style = null,
                isMutated = false,
                mutationKinds = emptyList(),
                slayerKill = false,
                differentBossesKilledThisSession = 0,
                healed = 0,
                damageTaken = 0,
                challengeCompleted = type,
            )
        onNpcKill(player, facts)
    }

    /** Admin: force-complete [bountyId] for [player]. */
    fun completeBounty(player: Player, bountyId: String): Boolean {
        val bounty = getOrCreate(player)
        val allTasks = bounty.dailyTasks + bounty.weeklyTasks + bounty.eliteTasks
        val task = allTasks.firstOrNull { it.bountyId == bountyId } ?: return false
        val category = categoryOf(bounty, task)
        task.currentAmount = task.requiredAmount
        finalizeCompletion(player, task, category)
        val data = cache.getOrCreate(player.characterId)
        cache.put(player.characterId, data.copy(bounty = bounty))
        return true
    }

    /** Regenerate the daily board if the reset deadline has passed. */
    fun dailyResetIfNeeded(player: Player, bounty: BountyData): BountyData {
        val now = System.currentTimeMillis()
        if (bounty.dailyTasks.isEmpty() || now >= bounty.dailyResetEpoch) {
            val config = configSource().bounty
            val tasks =
                generateTasks(
                    player,
                    BountyCategory.DAILY,
                    BountyDifficulty.EASY,
                    config.dailyCount,
                )
            return bounty.copy(dailyTasks = tasks, dailyResetEpoch = nextDailyReset())
        }
        return bounty
    }

    /** Regenerate the weekly board if the reset deadline has passed. */
    fun weeklyResetIfNeeded(player: Player, bounty: BountyData): BountyData {
        val now = System.currentTimeMillis()
        if (bounty.weeklyTasks.isEmpty() || now >= bounty.weeklyResetEpoch) {
            val config = configSource().bounty
            val tasks =
                generateTasks(
                    player,
                    BountyCategory.WEEKLY,
                    BountyDifficulty.NORMAL,
                    config.weeklyCount,
                )
            return bounty.copy(weeklyTasks = tasks, weeklyResetEpoch = nextWeeklyReset())
        }
        return bounty
    }

    /** All tasks with their progress, for hub display. */
    fun bountyProgress(player: Player): List<Pair<BountyCategory, BountyTask>> {
        val bounty = getOrCreate(player)
        return bounty.dailyTasks.map { BountyCategory.DAILY to it } +
            bounty.weeklyTasks.map { BountyCategory.WEEKLY to it } +
            bounty.eliteTasks.map { BountyCategory.ELITE to it }
    }

    /** Force-regenerate the elite list for [player]. */
    fun generateElite(player: Player): BountyData {
        val data = cache.getOrCreate(player.characterId)
        val config = configSource().bounty
        val tasks =
            generateTasks(player, BountyCategory.ELITE, BountyDifficulty.ELITE, config.eliteCount)
        val bounty = data.bounty.copy(eliteTasks = tasks)
        cache.put(player.characterId, data.copy(bounty = bounty))
        return bounty
    }

    private fun advance(
        player: Player,
        tasks: List<BountyTask>,
        category: BountyCategory,
        facts: BountyObjectiveMatcher.KillFacts,
    ): List<BountyTask> {
        if (tasks.isEmpty()) {
            return tasks
        }
        val updated =
            tasks.map { task ->
                if (task.isComplete) {
                    return@map task
                }
                val delta = BountyObjectiveMatcher.progress(task, facts)
                if (delta <= 0) {
                    return@map task
                }
                task.currentAmount = (task.currentAmount + delta).coerceAtMost(task.requiredAmount)
                if (task.isComplete) {
                    finalizeCompletion(player, task, category)
                }
                task
            }
        if (updated.all { it.isComplete } && updated.isNotEmpty()) {
            eventBus.publish(BountyCategoryCompleteEvent(player, category))
        }
        return updated
    }

    private fun finalizeCompletion(player: Player, task: BountyTask, category: BountyCategory) {
        val reward = rewardForDifficulty(task.difficulty)
        val id = "bounty:${player.characterId}:${task.bountyId}"
        if (rewards.grant(player, id, reward)) {
            eventBus.publish(BountyCompleteEvent(player, category, task.bountyId, task.name))
            val notifyConfig = configSource().notify
            if (notifyConfig.bountyAnnounce) {
                player.mes("<col=2ecc71>Bounty complete: ${task.name}!</col>")
            }
        }
    }

    private fun rewardForDifficulty(difficulty: BountyDifficulty): PvmReward {
        val config = configSource().bounty
        return when (difficulty) {
            BountyDifficulty.EASY -> PvmReward(config.coinsEasy, config.tokensEasy)
            BountyDifficulty.NORMAL -> PvmReward(config.coinsNormal, config.tokensNormal)
            BountyDifficulty.HARD -> PvmReward(config.coinsHard, config.tokensHard)
            BountyDifficulty.ELITE -> PvmReward(config.coinsElite, config.tokensElite)
        }
    }

    private fun categoryOf(bounty: BountyData, task: BountyTask): BountyCategory =
        when (task) {
            in bounty.dailyTasks -> BountyCategory.DAILY
            in bounty.weeklyTasks -> BountyCategory.WEEKLY
            else -> BountyCategory.ELITE
        }

    private fun generateTasks(
        player: Player,
        category: BountyCategory,
        baseDifficulty: BountyDifficulty,
        count: Int,
    ): List<BountyTask> {
        if (count <= 0) {
            return emptyList()
        }
        val config = configSource().bounty
        val maxOpen = config.maxOpenPerCategory
        val n = count.coerceAtMost(maxOpen)
        val objectives = pickObjectives(category)
        val tasks = mutableListOf<BountyTask>()
        for (i in 0 until n) {
            val objective = objectives[random.of(objectives.size)]
            val difficulty = scaleDifficulty(baseDifficulty, i, n)
            val target = pickTarget(objective)
            val required = requiredAmount(objective, difficulty)
            val id = "bounty-${category.name.lowercase()}-${idSeq.getAndIncrement()}"
            val name = describeTask(objective, target, required)
            tasks +=
                BountyTask(
                    bountyId = id,
                    name = name,
                    description = name,
                    objectiveType = objective,
                    target = target,
                    requiredAmount = required,
                    currentAmount = 0,
                    difficulty = difficulty,
                    rewardTier = difficulty.ordinal,
                    expiry = expiryForCategory(category),
                )
        }
        return tasks
    }

    private fun pickObjectives(category: BountyCategory): List<BountyObjectiveType> =
        when (category) {
            BountyCategory.DAILY ->
                listOf(
                    BountyObjectiveType.KILL_NPC,
                    BountyObjectiveType.KILL_BOSS,
                    BountyObjectiveType.DEAL_DAMAGE,
                    BountyObjectiveType.HEAL,
                )
            BountyCategory.WEEKLY ->
                listOf(
                    BountyObjectiveType.KILL_BOSS,
                    BountyObjectiveType.KILL_DIFFERENT_BOSSES,
                    BountyObjectiveType.DEAL_DAMAGE,
                    BountyObjectiveType.NO_DEATH_BOSS,
                    BountyObjectiveType.FAST_BOSS_KILL,
                    BountyObjectiveType.MUTANT_KILL,
                )
            BountyCategory.ELITE ->
                listOf(
                    BountyObjectiveType.KILL_DIFFERENT_BOSSES,
                    BountyObjectiveType.NO_DEATH_BOSS,
                    BountyObjectiveType.FAST_BOSS_KILL,
                    BountyObjectiveType.CHALLENGE_COMPLETE,
                    BountyObjectiveType.MUTANT_KILL,
                )
        }

    private fun pickTarget(objective: BountyObjectiveType): String? =
        when (objective) {
            BountyObjectiveType.KILL_NPC,
            BountyObjectiveType.KILL_BOSS,
            BountyObjectiveType.DEAL_DAMAGE -> BOSS_TARGETS[random.of(BOSS_TARGETS.size)]
            BountyObjectiveType.DEAL_STYLE_DAMAGE -> STYLES[random.of(STYLES.size)]
            BountyObjectiveType.FAST_BOSS_KILL -> (random.of(60, 180)).toString()
            else -> null
        }

    private fun requiredAmount(objective: BountyObjectiveType, difficulty: BountyDifficulty): Int {
        val scale = 1 + difficulty.ordinal
        return when (objective) {
            BountyObjectiveType.KILL_NPC,
            BountyObjectiveType.KILL_NPC_CATEGORY,
            BountyObjectiveType.KILL_BOSS,
            BountyObjectiveType.SLAYER_KILLS,
            BountyObjectiveType.NO_DEATH_BOSS,
            BountyObjectiveType.MUTANT_KILL,
            BountyObjectiveType.CHALLENGE_COMPLETE -> scale
            BountyObjectiveType.KILL_DIFFERENT_BOSSES -> 3 + difficulty.ordinal
            BountyObjectiveType.DEAL_DAMAGE,
            BountyObjectiveType.DEAL_STYLE_DAMAGE -> 500 * scale
            BountyObjectiveType.TAKE_DAMAGE -> 200 * scale
            BountyObjectiveType.HEAL -> 100 * scale
            BountyObjectiveType.FAST_BOSS_KILL -> 1
        }
    }

    private fun describeTask(
        objective: BountyObjectiveType,
        target: String?,
        required: Int,
    ): String {
        val targetDesc = target?.let { " $it" } ?: ""
        return when (objective) {
            BountyObjectiveType.KILL_NPC -> "Kill $required$targetDesc"
            BountyObjectiveType.KILL_NPC_CATEGORY -> "Kill $required$targetDesc category npcs"
            BountyObjectiveType.KILL_BOSS -> "Defeat $required$targetDesc"
            BountyObjectiveType.KILL_DIFFERENT_BOSSES -> "Defeat $required different bosses"
            BountyObjectiveType.DEAL_DAMAGE -> "Deal $required damage$targetDesc"
            BountyObjectiveType.DEAL_STYLE_DAMAGE -> "Deal $required $target damage"
            BountyObjectiveType.TAKE_DAMAGE -> "Take $required damage"
            BountyObjectiveType.HEAL -> "Restore $required hitpoints"
            BountyObjectiveType.SLAYER_KILLS -> "Kill $required slayer targets"
            BountyObjectiveType.FAST_BOSS_KILL -> "Kill a boss in under $target seconds"
            BountyObjectiveType.NO_DEATH_BOSS -> "Defeat $required$targetDesc without dying"
            BountyObjectiveType.MUTANT_KILL -> "Defeat $required mutated monsters"
            BountyObjectiveType.CHALLENGE_COMPLETE -> "Complete $required combat challenges"
        }
    }

    private fun scaleDifficulty(base: BountyDifficulty, index: Int, total: Int): BountyDifficulty {
        if (total <= 1) {
            return base
        }
        val bump = index * 2 / total
        val ordinal = (base.ordinal + bump).coerceAtMost(BountyDifficulty.ELITE.ordinal)
        return BountyDifficulty.entries[ordinal]
    }

    private fun expiryForCategory(category: BountyCategory): Long =
        when (category) {
            BountyCategory.DAILY -> nextDailyReset()
            BountyCategory.WEEKLY -> nextWeeklyReset()
            BountyCategory.ELITE -> nextWeeklyReset()
        }

    private fun nextDailyReset(): Long {
        val config = configSource().bounty
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, config.dailyResetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun nextWeeklyReset(): Long {
        val config = configSource().bounty
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, config.dailyResetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (
            cal.get(Calendar.DAY_OF_WEEK) != config.weeklyResetDay ||
                cal.timeInMillis <= System.currentTimeMillis()
        ) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private companion object {
        /** A small pool of known boss names used as bounty targets. */
        val BOSS_TARGETS =
            listOf(
                "giant",
                "ogre",
                "hill giant",
                "moss giant",
                "ice giant",
                "lesser demon",
                "greater demon",
                "black demon",
                "green dragon",
                "blue dragon",
                "red dragon",
                "bronze dragon",
                "king black dragon",
                "kalphite queen",
                "giant mole",
                "chaos elemental",
                "dagannoth rex",
                "dagannoth prime",
                "dagannoth supreme",
            )

        val STYLES = listOf("melee", "ranged", "magic")
    }
}
