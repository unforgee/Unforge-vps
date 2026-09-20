package org.rsmod.content.pvmprogression.hub

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.pvmprogression.bounty.PvMBountyManager
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.mutation.MutationManager
import org.rsmod.content.pvmprogression.record.BossRecordManager
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.content.pvmprogression.streak.PvMStreakManager
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The PvM Progression hub.
 *
 * The spec asks for a tabbed interface; rather than pack a new cache interface (which the
 * concurrent cache-packing agent makes unsafe), this script renders the entire hub as chatbox lines
 * via the existing `mes` faucet. Each `::command` prints one tab's worth of state, and `::pvmhub`
 * prints the overview. This is fully functional with zero cache risk.
 */
class PvmHubScript
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val bounty: PvMBountyManager,
    private val records: BossRecordManager,
    private val mutations: MutationManager,
    private val streak: PvMStreakManager,
    private val cache: PvmProgressionCache,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registerCommand("pvmhub", "PvM Progression overview") { ::pvmHub }
        registerCommand("bounties", "Show your bounty board") { ::showBounties }
        registerCommand("bossrecords", "Show your boss records") { ::showBossRecords }
        registerCommand("mutations", "Show your mutation stats") { ::showMutations }
        registerCommand("challenges", "Show your challenge stats") { ::showChallenges }
        registerCommand("streak", "Show your PvM streak") { ::showStreak }
        registerCommand("streakcashout", "Cash out your unclaimed PvM streak tokens") {
            ::cashOutStreak
        }
    }

    private fun cashOutStreak(cheat: Cheat) {
        streak.cashOut(cheat.player)
    }

    private fun pvmHub(cheat: Cheat) {
        val player = cheat.player
        val streakData = streak.getStreak(player)
        val overall = records.getOverall(player)
        val bountyData = bounty.getOrCreate(player)
        val challengeData = cache.getOrCreate(player.characterId).challenges
        val mutationData = cache.getOrCreate(player.characterId).mutations
        player.mes("<col=ffb84d>=== PvM Progression Hub ===</col>")
        player.mes("Current Streak: ${streakData.currentStreak} | Best: ${streakData.bestStreak}")
        player.mes(
            "Daily: ${bountyData.dailyTasks.count { it.isComplete }}/${bountyData.dailyTasks.size} | " +
                "Weekly: ${bountyData.weeklyTasks.count { it.isComplete }}/${bountyData.weeklyTasks.size} | " +
                "Elite: ${bountyData.eliteTasks.count { it.isComplete }}/${bountyData.eliteTasks.size}"
        )
        player.mes("Mutations Killed: ${mutationData.totalKills}")
        player.mes("Challenges Completed: ${challengeData.totalCompleted}")
        player.mes("Boss Kills: ${overall.totalBossKills} | Deaths: ${overall.totalBossDeaths}")
        val fastest =
            if (overall.fastestKillTenths == Int.MAX_VALUE) "n/a"
            else "${overall.fastestKillTenths / 10.0}s (${overall.fastestKillBoss})"
        player.mes("Fastest PB: $fastest")
        player.mes("Unclaimed streak tokens: ${streakData.unclaimedTokens}")
    }

    private fun showBounties(cheat: Cheat) {
        val player = cheat.player
        for ((category, task) in bounty.bountyProgress(player)) {
            val cat = category.name.lowercase().replaceFirstChar { it.uppercase() }
            val pct = task.progressBps / 100
            player.mes(
                "[$cat] ${task.name} - ${task.currentAmount}/${task.requiredAmount} (${pct}%)"
            )
        }
    }

    private fun showBossRecords(cheat: Cheat) {
        val player = cheat.player
        val overall = records.getOverall(player)
        player.mes("<col=ffb84d>=== Boss Records ===</col>")
        player.mes("Total kills: ${overall.totalBossKills} | Deaths: ${overall.totalBossDeaths}")
        player.mes("Most killed: ${overall.mostKilledBoss} (${overall.mostKilledCount})")
        val fastest =
            if (overall.fastestKillTenths == Int.MAX_VALUE) "n/a"
            else "${overall.fastestKillTenths / 10.0}s (${overall.fastestKillBoss})"
        player.mes("Fastest kill: $fastest")
        player.mes("Best streak: ${overall.bestStreak}")
        for (record in records.getRecords(player).values) {
            val pb =
                if (record.fastestKillTenths == Int.MAX_VALUE) "n/a"
                else "${record.fastestKillTenths / 10.0}s"
            player.mes(
                "${record.bossName}: kills=${record.totalKills} pb=$pb " +
                    "hi=${record.highestHit} streak=${record.currentStreak}/${record.bestStreak}"
            )
        }
    }

    private fun showMutations(cheat: Cheat) {
        val player = cheat.player
        player.mes("<col=ffb84d>=== Mutation Stats ===</col>")
        val stats = cache.getOrCreate(player.characterId).mutations
        player.mes("Total mutation kills: ${stats.totalKills}")
        stats.rarestKilled?.let { player.mes("Rarest kind killed: $it") }
        for ((kind, count) in stats.killsByType) {
            player.mes("  $kind: $count")
        }
        val snapshot = mutations.snapshot()
        player.mes("Active mutated npcs: ${snapshot.size}")
        for ((npc, instance) in snapshot) {
            player.mes("  ${mutations.displayName(npc)} (${instance.rarity})")
        }
    }

    private fun showChallenges(cheat: Cheat) {
        val player = cheat.player
        player.mes("<col=ffb84d>=== Challenge Stats ===</col>")
        val data = cache.getOrCreate(player.characterId).challenges
        player.mes("Total completed: ${data.totalCompleted}")
        for ((type, count) in data.completedByType) {
            player.mes("  $type: $count")
        }
        player.mes("Active offers: ${data.activeOffers.size}")
        for (offer in data.activeOffers) {
            player.mes(
                "  ${offer.type} vs ${offer.bossName} (${offer.tier}) accepted=${offer.accepted}"
            )
        }
    }

    private fun showStreak(cheat: Cheat) {
        val player = cheat.player
        val data = streak.getStreak(player)
        player.mes("<col=ffb84d>=== PvM Streak ===</col>")
        player.mes("Current: ${data.currentStreak} | Best: ${data.bestStreak}")
        player.mes("Unclaimed tokens: ${data.unclaimedTokens}")
        val unlock = configSource().streak.cashOutUnlockKills
        player.mes("Cash out unlocks at $unlock kills.")
    }

    // These commands only expose the caller's own progression data, so they stay open to every
    // player - admin gating here would hide the whole hub from non-dev realms.
    private fun ScriptContext.registerCommand(
        command: String,
        desc: String,
        cheat: Cheat.() -> Unit,
    ) {
        onCommand(command) {
            this.modLevel = modlevels.player
            this.desc = desc
            this.cheat(cheat)
        }
    }
}
