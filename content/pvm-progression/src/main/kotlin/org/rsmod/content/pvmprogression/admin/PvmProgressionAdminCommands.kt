package org.rsmod.content.pvmprogression.admin

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.pvmprogression.boss.BossModifierManager
import org.rsmod.content.pvmprogression.boss.BossSystemsManager
import org.rsmod.content.pvmprogression.bounty.PvMBountyManager
import org.rsmod.content.pvmprogression.challenge.CombatChallengeManager
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounterTracker
import org.rsmod.content.pvmprogression.events.BountyCategory
import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.content.pvmprogression.mutation.MutationManager
import org.rsmod.content.pvmprogression.record.BossRecordManager
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.content.pvmprogression.streak.PvMStreakManager
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Admin commands for PvM Progression 2.0.
 *
 * Every command is admin-gated and delegates to the managers - it never mutates state directly, so
 * the same anti-exploit guards (idempotent reward ids, streak double-increment block, mutation
 * eligibility) apply to admin actions as to live gameplay. Commands that take an argument read it
 * off `cheat.args` via the standard `Cheat` data class.
 */
class PvmProgressionAdminCommands
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val bounty: PvMBountyManager,
    private val records: BossRecordManager,
    private val mutations: MutationManager,
    private val challenges: CombatChallengeManager,
    private val streak: PvMStreakManager,
    private val tracker: BossEncounterTracker,
    private val cache: PvmProgressionCache,
    private val bossSystems: BossSystemsManager,
    private val bossModifiers: BossModifierManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registerCommand("rerollbounties", "Reroll a bounty category [daily|weekly|elite]") {
            ::rerollBounties
        }
        registerCommand("completebounty", "Force-complete a bounty by id") { ::completeBounty }
        registerCommand("spawnmutation", "Force a mutation <type> on the nearest npc") {
            ::spawnMutation
        }
        registerCommand("mutationchance", "Set the mutation chance denominator <n>") {
            ::mutationChance
        }
        registerCommand("resetbossrecord", "Reset your record for <bossId>") { ::resetBossRecord }
        registerCommand("challenge", "Force a challenge offer <type>") { ::forceChallenge }
        registerCommand("challengecomplete", "Force-complete the active challenge") {
            ::challengeComplete
        }
        registerCommand("challengefail", "Force-fail the active challenge") { ::challengeFail }
        registerCommand("setstreak", "Set your PvM streak <amount>") { ::setStreak }
        registerCommand("resetstreak", "Reset your PvM streak") { ::resetStreak }
        registerCommand("pvmconfig", "Print the live PvM Progression config") { ::pvmConfig }
        registerCommand("pvmprogression", "Debug PvM Progression state") { ::pvmProgression }
        registerCommand("luckyboss", "Force your next eligible boss kill to be Lucky") {
            ::luckyBoss
        }
        registerCommand("laststandtest", "Show Last Stand eligibility") { ::lastStandTest }
        registerCommand("bossmodifier", "Force the next eligible boss spawn modifier") {
            ::bossModifier
        }
    }

    private fun rerollBounties(cheat: Cheat) {
        val player = cheat.player
        val categoryArg = cheat.args.firstOrNull() ?: "daily"
        val category =
            when (categoryArg.lowercase()) {
                "daily" -> BountyCategory.DAILY
                "weekly" -> BountyCategory.WEEKLY
                "elite" -> BountyCategory.ELITE
                else -> BountyCategory.DAILY
            }
        bounty.reroll(player, category)
        player.mes("Rerolled ${category.name.lowercase()} bounties.")
    }

    private fun completeBounty(cheat: Cheat) {
        val player = cheat.player
        val id =
            cheat.args.firstOrNull()
                ?: run {
                    player.mes("Usage: ::completebounty <bountyId>")
                    return
                }
        if (bounty.completeBounty(player, id)) {
            player.mes("Bounty $id force-completed.")
        } else {
            player.mes("No bounty with id $id found.")
        }
    }

    private fun spawnMutation(cheat: Cheat) {
        val player = cheat.player
        val typeArg = cheat.args.firstOrNull() ?: "GIANT"
        val kind =
            runCatching { MutationKind.valueOf(typeArg.uppercase()) }.getOrNull()
                ?: run {
                    player.mes("Unknown mutation type: $typeArg")
                    return
                }
        val encounter = tracker.current(player)
        val npc = encounter?.bossRef
        if (npc != null) {
            mutations.forceMutate(npc, kind)
            player.mes("Forced $kind mutation on ${npc.visType.name}.")
        } else {
            player.mes("No active boss encounter to mutate.")
        }
    }

    private fun mutationChance(cheat: Cheat) {
        val n =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    cheat.player.mes("Usage: ::mutationchance <n>")
                    return
                }
        configSource.update { it.copy(mutation = it.mutation.copy(chanceDenominator = n)) }
        cheat.player.mes("Mutation chance denominator set to $n.")
    }

    private fun resetBossRecord(cheat: Cheat) {
        val player = cheat.player
        val bossId =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    player.mes("Usage: ::resetbossrecord <bossId>")
                    return
                }
        records.resetRecord(player, bossId)
        player.mes("Reset record for boss $bossId.")
    }

    private fun forceChallenge(cheat: Cheat) {
        val player = cheat.player
        val typeArg = cheat.args.firstOrNull() ?: "NO_FOOD"
        val type =
            runCatching { ChallengeType.valueOf(typeArg.uppercase()) }.getOrNull()
                ?: run {
                    player.mes("Unknown challenge type: $typeArg")
                    return
                }
        val encounter =
            tracker.current(player)
                ?: run {
                    player.mes("No active boss encounter.")
                    return
                }
        val offer = challenges.forceOffer(player, encounter, type)
        player.mes("Forced challenge: ${offer.type} vs ${offer.bossName}.")
    }

    private fun challengeComplete(cheat: Cheat) {
        val player = cheat.player
        val encounter =
            tracker.current(player)
                ?: run {
                    player.mes("No active boss encounter.")
                    return
                }
        challenges.forceComplete(player, encounter)
        player.mes("Challenge force-completed.")
    }

    private fun challengeFail(cheat: Cheat) {
        val player = cheat.player
        val encounter =
            tracker.current(player)
                ?: run {
                    player.mes("No active boss encounter.")
                    return
                }
        challenges.forceFail(player, encounter)
        player.mes("Challenge force-failed.")
    }

    private fun setStreak(cheat: Cheat) {
        val player = cheat.player
        val amount =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    player.mes("Usage: ::setstreak <amount>")
                    return
                }
        streak.setStreak(player, amount)
        player.mes("Streak set to $amount.")
    }

    private fun resetStreak(cheat: Cheat) {
        streak.resetStreak(cheat.player)
        cheat.player.mes("Streak reset.")
    }

    private fun pvmConfig(cheat: Cheat) {
        val player = cheat.player
        val config = configSource()
        player.mes("Mutation chance: 1/${config.mutation.chanceDenominator}")
        player.mes("Bounty daily count: ${config.bounty.dailyCount}")
        player.mes("Challenge offer bps: ${config.challenge.offerChanceBps}")
        player.mes("Streak increment: ${config.streak.incrementPerKill}")
        player.mes("Boss combat level: ${config.record.bossCombatLevel}")
    }

    private fun pvmProgression(cheat: Cheat) {
        val player = cheat.player
        val encounter = tracker.current(player)
        val data = cache.getOrCreate(player.characterId)
        if (encounter != null) {
            player.mes(
                "Encounter: id=${encounter.id} boss=${encounter.bossName}(${encounter.bossId})"
            )
            player.mes("  damageDealt=${encounter.damageDealt} taken=${encounter.damageTaken}")
            val offer = challenges.activeOffer(player, encounter)
            player.mes("  challenge=${offer?.type ?: "none"} accepted=${offer?.accepted ?: false}")
        } else {
            player.mes("No active encounter.")
        }
        player.mes("Streak: ${data.streak.currentStreak} (best ${data.streak.bestStreak})")
        player.mes("Mutations total kills: ${data.mutations.totalKills}")
        player.mes("Challenges completed: ${data.challenges.totalCompleted}")
    }

    private fun luckyBoss(cheat: Cheat) {
        bossSystems.forceLucky(cheat.player)
        cheat.player.mes("Your next eligible boss kill will be Lucky.")
    }

    private fun lastStandTest(cheat: Cheat) {
        cheat.player.mes(bossSystems.lastStandDebug(cheat.player))
    }

    private fun bossModifier(cheat: Cheat) {
        val arg = cheat.args.firstOrNull()?.uppercase()
        if (arg == "MYTHIC") {
            bossModifiers.forceNext(null)
            cheat.player.mes("The next eligible boss spawn will be Mythic.")
            return
        }
        val modifier =
            arg?.let {
                runCatching { org.rsmod.content.pvmprogression.events.BossModifier.valueOf(it) }
                    .getOrNull()
            }
        if (modifier == null) {
            cheat.player.mes("Usage: ::bossmodifier <enraged|armored|vampiric|quick|cursed|mythic>")
            return
        }
        bossModifiers.forceNext(modifier)
        cheat.player.mes("The next eligible boss spawn will be ${modifier.name}.")
    }

    private fun ScriptContext.registerCommand(
        command: String,
        desc: String,
        cheat: Cheat.() -> Unit,
    ) {
        onCommand(command) {
            this.modLevel = modlevels.admin
            this.desc = desc
            this.cheat(cheat)
        }
    }
}
