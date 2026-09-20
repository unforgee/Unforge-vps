package org.rsmod.content.pvmprogression.encounter

import jakarta.inject.Inject
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.script.onEvent
import org.rsmod.content.pvmprogression.bounty.BountyObjectiveMatcher
import org.rsmod.content.pvmprogression.bounty.PvMBountyManager
import org.rsmod.content.pvmprogression.challenge.CombatChallengeManager
import org.rsmod.content.pvmprogression.mutation.MutationManager
import org.rsmod.content.pvmprogression.record.BossRecordManager
import org.rsmod.content.pvmprogression.streak.PvMStreakManager
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The encounter glue script.
 *
 * It is the single place that turns raw engine signals into manager calls. See class docs on each
 * subscriber below for the exact seam it rides. The script never touches core files: it only
 * subscribes to UnboundEvent seams that the engine already publishes, so it is fully merge-safe.
 */
class PvmProgressionEncounterScript
@Inject
constructor(
    private val tracker: BossEncounterTracker,
    private val perks: PerkService,
    private val bounty: PvMBountyManager,
    private val records: BossRecordManager,
    private val challenges: CombatChallengeManager,
    private val streak: PvMStreakManager,
    private val mutations: MutationManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> {
            val visLevel = npc.visType.vislevel
            if (!perks.isBoss(visLevel)) {
                mutations.onKill(killer, npc)
                return@onEvent
            }
            val encounter = tracker.current(killer) ?: return@onEvent
            if (encounter.bossId != npc.visType.id) {
                return@onEvent
            }
            finalizeBossKill(encounter, killer)
        }

        onEvent<GameLifecycle.LateCycle> { onLateCycle() }

        onEvent<SessionStateEvent.Logout> {
            val encounter = tracker.current(player) ?: return@onEvent
            if (encounter.endReason == null) {
                tracker.abandon(player, AbandonReason.LOGOUT)
                challenges.onBossKill(encounter)
            }
        }
    }

    private fun finalizeBossKill(encounter: BossEncounter, killer: Player) {
        encounter.bossHpAtDeath = 0
        encounter.playerHpAtKill = killer.hitpoints
        val maxHp = killer.baseHitpointsLvl.coerceAtLeast(1)
        encounter.playerHpPercentAtKill = (killer.hitpoints * 10_000 / maxHp).coerceIn(0, 10_000)
        records.onBossKill(encounter, killer)
        streak.onBossKill(killer, encounter)
        challenges.onBossKill(encounter)
        bounty.onNpcKill(
            killer,
            BountyObjectiveMatcher.KillFacts(
                npcId = encounter.bossId,
                npcName = encounter.bossName,
                visLevel = encounter.bossMaxHp,
                isBoss = true,
                damageDealt = encounter.damageDealt,
                killTimeTenths = encounter.elapsedTenths,
                noDeath = true,
                style = null,
                isMutated = false,
                mutationKinds = emptyList(),
                slayerKill = false,
                differentBossesKilledThisSession = 1,
                healed = encounter.healing,
                damageTaken = encounter.damageTaken,
                challengeCompleted = null,
            ),
        )
        tracker.abandon(killer, AbandonReason.BOSS_KILLED)
    }

    private fun onLateCycle() {
        tracker.tick()
        sampleOpenEncounters()
    }

    private fun sampleOpenEncounters() {
        for ((player, encounter) in tracker.snapshot()) {
            if (!player.isSlotAssigned || encounter.endReason != null) {
                continue
            }
            if (!encounter.challengeOffered) {
                challenges.maybeOffer(player, encounter)
                encounter.challengeOffered = true
            }
            samplePlayer(player, encounter)
            sampleBoss(encounter)
            challenges.onCycleTick(encounter)
        }
    }

    private fun samplePlayer(player: Player, encounter: BossEncounter) {
        val hp = player.hitpoints
        if (hp < encounter.lastSampledPlayerHp) {
            encounter.damageTaken += encounter.lastSampledPlayerHp - hp
            tracker.notePlayerTookDamage(player)
        }
        encounter.lastSampledPlayerHp = hp
        val prayer = player.prayerLvl
        if (prayer < encounter.lastSampledPrayer) {
            encounter.prayerConsumed += encounter.lastSampledPrayer - prayer
        }
        encounter.lastSampledPrayer = prayer
        if (player.coords != encounter.startCoords) {
            encounter.moved = true
        }
    }

    private fun sampleBoss(encounter: BossEncounter) {
        val boss = encounter.bossRef ?: return
        if (!boss.isSlotAssigned || boss.hitpoints <= 0) {
            return
        }
        val hp = boss.hitpoints
        if (hp < encounter.lastSampledBossHp) {
            encounter.damageDealt += encounter.lastSampledBossHp - hp
        }
        encounter.lastSampledBossHp = hp
    }
}
