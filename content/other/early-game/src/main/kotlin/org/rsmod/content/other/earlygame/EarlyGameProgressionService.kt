package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

/**
 * The server-authoritative state machine for the first-play experience.
 *
 * It intentionally has no dependency on Forge, Slayer, Bounty or Mutation implementations. Those
 * systems can publish the small hook methods below when available; the developer commands provide a
 * deterministic smoke-test path while those systems are being merged.
 */
@Singleton
public class EarlyGameProgressionService
@Inject
constructor(
    private val companionBridge: EarlyGameCompanionBridge,
    private val trackerSync: EarlyGameTrackerSync,
) {
    private val states = ConcurrentHashMap<Int, EarlyGameState>()

    /**
     * Writes to this property persist the state and immediately push the tracker snapshot to the
     * client, so every mutation path (commands, modal interfaces, hooks) keeps the Adventure Path
     * overlay in sync without each caller remembering to push.
     */
    private var Player.syncedState: EarlyGameState
        get() = ensure(this)
        set(value) {
            states[characterId] = value
            trackerSync.pushStatus(this, value)
        }

    public fun restore(characterId: Int, state: EarlyGameState) {
        states[characterId] = state
    }

    public fun state(characterId: Int): EarlyGameState? = states[characterId]

    public fun ensure(player: Player): EarlyGameState =
        states.computeIfAbsent(player.characterId) {
            // Legacy detection is deliberately conservative. Existing accounts can opt into the
            // optional path; the normal login pipeline must never block a progressed character.
            EarlyGameState()
        }

    public fun chooseRelic(player: Player, relic: StarterRelic): Boolean {
        val current = ensure(player)
        val now = System.currentTimeMillis()
        if (current.relicSelected && current.starterRelic == relic) {
            player.mes("${relic.displayName} is already selected.")
            return false
        }
        val updated =
            current.copy(starterRelic = relic, relicSelected = true, lastRelicChangeEpoch = now)
        player.syncedState = updated
        completeMilestone(player, AdventureMilestone.CHOOSE_RELIC, grantReward = false)
        player.mes(
            "<col=ffb84d>${relic.displayName}</col> selected. Recommended Companion: ${relic.recommendedCompanion}."
        )
        return true
    }

    public fun changeRelic(player: Player, relic: StarterRelic): Boolean =
        chooseRelic(player, relic)

    public fun completeMilestone(
        player: Player,
        milestone: AdventureMilestone,
        grantReward: Boolean = true,
    ): Boolean {
        val current = ensure(player)
        if (milestone.name in current.completedMilestones) return false
        val completed = current.completedMilestones + milestone.name
        val claimed =
            if (grantReward) current.claimedMilestoneRewards + milestone.name
            else current.claimedMilestoneRewards
        var updated =
            current.copy(completedMilestones = completed, claimedMilestoneRewards = claimed)
        if (milestone == AdventureMilestone.TRIAL)
            updated = updated.copy(firstBondCompleted = true, trialActive = false)
        player.syncedState = updated
        if (grantReward) grantMilestoneReward(player, milestone)
        player.mes("<col=33b532>✓ ${milestone.title}</col> — ${milestone.reward}")
        if (milestone == AdventureMilestone.DISCOVER_TEN) unlockFirstBond(player)
        refreshBondUnlock(player)
        return true
    }

    public fun refreshBondUnlock(player: Player) {
        val current = ensure(player)
        if (
            current.firstBondUnlocked ||
                current.firstBondCompleted ||
                current.starterCompanionSelected
        )
            return
        val required =
            listOf(
                AdventureMilestone.FIRST_BOSS,
                AdventureMilestone.COMBAT_CHALLENGE,
                AdventureMilestone.THREE_BOUNTIES,
                AdventureMilestone.FIVE_SLAYER,
                AdventureMilestone.DISCOVER_TEN,
            )
        if (required.all { it.name in current.completedMilestones }) unlockFirstBond(player)
    }

    public fun unlockFirstBond(player: Player): Boolean {
        val current = ensure(player)
        if (current.firstBondUnlocked || current.firstBondCompleted) return false
        player.syncedState = current.copy(firstBondUnlocked = true)
        player.mes("<col=ffb84d>ADVENTURE PATH — FINAL CHAPTER</col>")
        player.mes("<col=ff981f>FIRST BOND</col>: Prove yourself and earn your first Companion.")
        return true
    }

    public fun startTrial(player: Player): Boolean {
        val current = ensure(player)
        if (current.firstBondCompleted || current.starterCompanionSelected) {
            player.mes("Your First Bond is already complete.")
            return false
        }
        if (!current.firstBondUnlocked) {
            player.mes(
                "Complete the First Real Challenge chapter before starting Trial of Bonding."
            )
            return false
        }
        if (current.trialActive) {
            player.mes("The Bond Guardian is already waiting for you. Defeat it to continue.")
            return false
        }
        player.syncedState =
            current.copy(trialActive = true, trialAttempts = current.trialAttempts + 1)
        player.mes("<col=ff981f>TRIAL OF BONDING</col>")
        player.mes("Defeat the Bond Guardian. A failed attempt can always be retried.")
        return true
    }

    public fun failTrial(player: Player) {
        val current = ensure(player)
        if (!current.trialActive) return
        player.syncedState = current.copy(trialActive = false)
        player.mes("The trial was not completed. Return to the Adventure Hub to try again.")
    }

    public fun completeTrial(player: Player): Boolean {
        val current = ensure(player)
        if (!current.trialActive || current.firstBondCompleted) return false
        player.syncedState = current.copy(trialActive = false)
        completeMilestone(player, AdventureMilestone.TRIAL)
        player.mes("<col=33b532>FIRST BOND COMPLETE</col>. Companion Selection is now open.")
        return true
    }

    public fun selectFirstCompanion(player: Player, id: String): Boolean {
        val current = ensure(player)
        if (!current.firstBondCompleted) {
            player.mes("Complete Trial of Bonding before choosing a Companion.")
            return false
        }
        if (current.starterCompanionSelected) {
            player.mes("Your first Companion has already been selected.")
            return false
        }
        val definition = StarterCompanion.entries.firstOrNull { it.id == id.lowercase() }
        if (definition == null) {
            player.mes("Choose vanguard, hunter or mystic.")
            return false
        }
        val companion = companionBridge.unlockCompanion(player, definition)
        player.syncedState =
            current.copy(starterCompanionSelected = true, starterCompanionId = definition.id)
        recordDiscovery(player, "first-companion")
        completeMilestone(player, AdventureMilestone.CHOOSE_COMPANION)
        player.mes("<col=ffb84d>FIRST COMPANION UNLOCKED</col>: ${companion.displayName}")
        player.mes("Role: ${companion.role}. Ability: ${companion.abilityName}.")
        player.mes("COMPANION BASICS: Summon your Companion, open the Hub, then use its ability.")
        return true
    }

    public fun companionAction(player: Player, action: String): Boolean {
        val current = ensure(player)
        if (!current.starterCompanionSelected) return false
        val normalized = action.lowercase()
        val basics = current.companionBasics + normalized
        player.syncedState = current.copy(companionBasics = basics)
        when (normalized) {
            "summon" -> completeMilestone(player, AdventureMilestone.SUMMON_COMPANION)
            "hub" -> completeMilestone(player, AdventureMilestone.OPEN_COMPANION_HUB)
            "ability" -> {
                completeMilestone(player, AdventureMilestone.USE_COMPANION_ABILITY)
                recordDiscovery(player, "first-companion-ability")
            }
        }
        val latest = ensure(player)
        if (setOf("summon", "hub", "ability").all { it in latest.companionBasics }) {
            player.syncedState = latest.copy(companionTutorialCompleted = true)
            player.mes("<col=33b532>ADVENTURE PATH COMPLETE</col>")
            player.mes(
                "You are ready to explore Unforge. Title unlocked: <col=ffb84d>THE ADVENTURER</col>"
            )
        }
        return true
    }

    public fun recordMonsterKill(player: Player, boss: Boolean = false, mutation: Boolean = false) {
        val current = ensure(player)
        val kills = current.monsterKills + 1
        player.syncedState =
            current.copy(monsterKills = kills, bossKills = current.bossKills + if (boss) 1 else 0)
        completeMilestone(player, AdventureMilestone.KILL_FIRST_MONSTER)
        if (kills >= 25) completeMilestone(player, AdventureMilestone.KILL_25)
        if (kills >= 100) completeMilestone(player, AdventureMilestone.KILL_100)
        if (boss) {
            completeMilestone(player, AdventureMilestone.FIRST_BOSS)
            recordDiscovery(player, "first-boss")
        }
        if (mutation) {
            completeMilestone(player, AdventureMilestone.FIRST_MUTATION)
            recordDiscovery(player, "first-mutation")
        }
        refreshBondUnlock(player)
    }

    public fun recordCounterMilestone(
        player: Player,
        milestone: AdventureMilestone,
        amount: Int = 1,
    ) {
        val current = ensure(player)
        val updated =
            when (milestone) {
                AdventureMilestone.THREE_BOUNTIES ->
                    current.copy(bountyCount = current.bountyCount + amount)
                AdventureMilestone.FIVE_SLAYER ->
                    current.copy(slayerTaskCount = current.slayerTaskCount + amount)
                else -> current
            }
        player.syncedState = updated
        val count =
            when (milestone) {
                AdventureMilestone.THREE_BOUNTIES -> updated.bountyCount
                AdventureMilestone.FIVE_SLAYER -> updated.slayerTaskCount
                else -> amount
            }
        val target = if (milestone == AdventureMilestone.THREE_BOUNTIES) 3 else 5
        if (count >= target) completeMilestone(player, milestone)
        refreshBondUnlock(player)
    }

    public fun recordDiscovery(player: Player, id: String): Boolean {
        val current = ensure(player)
        if (id in current.discoveries) return false
        val definition = EarlyGameDiscoveries.byId[id] ?: return false
        val discoveries = current.discoveries + id
        val points = current.discoveryPoints + definition.rarity.points
        val thresholds = current.discoveryRewardMilestones.toMutableSet()
        EarlyGameConfig.discoveryRewardThresholds
            .filter { points >= it }
            .forEach { thresholds += it.toString() }
        player.syncedState =
            current.copy(
                discoveries = discoveries,
                discoveryPoints = points,
                discoveryRewardMilestones = thresholds,
            )
        player.mes(
            "<col=ffb84d>NEW DISCOVERY</col>: ${definition.title} (+${definition.rarity.points} Discovery Points)"
        )
        if (discoveries.size >= 10) completeMilestone(player, AdventureMilestone.DISCOVER_TEN)
        return true
    }

    public fun registerWorldFind(player: Player, kind: String, rare: Boolean = false) {
        val current = ensure(player)
        val now = System.currentTimeMillis()
        if (now < current.worldFindAvailableAtEpoch) {
            val minutes = ((current.worldFindAvailableAtEpoch - now) / 60_000L).coerceAtLeast(1L)
            player.mes("Your next World Find is available in $minutes minute(s).")
            return
        }
        val stats = current.worldFindStatistics.toMutableMap()
        stats[kind] = (stats[kind] ?: 0) + 1
        player.syncedState =
            current.copy(
                worldFindStatistics = stats,
                worldFindAvailableAtEpoch =
                    now + EarlyGameConfig.WORLD_FIND_COOLDOWN_MINUTES * 60_000L,
            )
        player.mes(
            "<col=ffb84d>WORLD FIND</col>: ${kind.replace('-', ' ').replaceFirstChar { it.uppercase() }}"
        )
        if (rare) player.mes("A rare find! Discovery Points awarded.")
        recordDiscovery(player, "world-find-$kind")
    }

    public fun setTrackerPreferences(
        player: Player,
        minimized: Boolean? = null,
        hidden: Boolean? = null,
    ) {
        val current = ensure(player)
        player.syncedState =
            current.copy(
                trackerMinimized = minimized ?: current.trackerMinimized,
                trackerHidden = hidden ?: current.trackerHidden,
            )
    }

    public fun resetRelic(player: Player) {
        val current = ensure(player)
        player.syncedState =
            current.copy(starterRelic = null, relicSelected = false, lastRelicChangeEpoch = null)
        player.mes("Starter Relic selection reset.")
    }

    public fun resetAdventure(player: Player) {
        player.syncedState = EarlyGameState()
        player.mes("Adventure Path reset. This is a developer operation.")
    }

    public fun resetFirstBond(player: Player) {
        val current = ensure(player)
        player.syncedState =
            current.copy(
                firstBondUnlocked = false,
                firstBondCompleted = false,
                trialActive = false,
                trialAttempts = 0,
            )
        player.mes("First Bond reset.")
    }

    public fun resetStarterCompanion(player: Player) {
        val current = ensure(player)
        player.syncedState =
            current.copy(
                starterCompanionSelected = false,
                starterCompanionId = null,
                companionTutorialCompleted = false,
                companionBasics = emptySet(),
            )
        player.mes(
            "Starter Companion onboarding flag reset. Existing Companion data was preserved."
        )
    }

    public fun resetDiscoveries(player: Player) {
        val current = ensure(player)
        player.syncedState =
            current.copy(
                discoveries = emptySet(),
                discoveryPoints = 0,
                discoveryRewardMilestones = emptySet(),
            )
        player.mes("Discoveries reset.")
    }

    public fun setDiscoveryPoints(player: Player, amount: Int) {
        require(amount >= 0)
        val current = ensure(player)
        player.syncedState = current.copy(discoveryPoints = amount)
        player.mes("Discovery Points: $amount")
    }

    public fun summary(player: Player): String {
        val current = ensure(player)
        val completed = current.completedMilestones.size
        val total = EarlyGameConfig.starterPath.size
        val companion = current.starterCompanionId?.replaceFirstChar { it.uppercase() } ?: "LOCKED"
        return buildString {
            append("Adventure Progress $completed / $total | Relic ")
            append(current.starterRelic?.displayName ?: "Choose")
            append(" | Discoveries ${current.discoveries.size} (${current.discoveryPoints} pts)")
            append(" | First Companion $companion")
        }
    }

    private fun grantMilestoneReward(player: Player, milestone: AdventureMilestone) {
        // Conservative early-game bundle. Rewards are claimed atomically with the milestone state;
        // a reconnect cannot grant them twice. Concrete drop tables can replace this later.
        player.invAddSafe(995, 25)
    }

    private fun Player.invAddSafe(obj: Int, count: Int) {
        runCatching { invAdd(this.inv, obj, count, strict = false) }
    }

    public data class CompanionUnlockResult(
        val displayName: String,
        val role: String,
        val abilityName: String,
    )

    public enum class StarterCompanion(
        public val id: String,
        public val displayName: String,
        public val role: String,
        public val abilityName: String,
        public val companionClass: CompanionClass,
        public val starterTalent: String,
        public val starterAbility: String,
    ) {
        VANGUARD(
            "vanguard",
            "Vanguard",
            "Tank / Melee",
            "Challenging Shout",
            CompanionClass.TANK,
            "challenging-shout",
            "challenging-shout",
        ),
        HUNTER("hunter", "Hunter", "Ranged DPS", "Frenzy", CompanionClass.DPS, "frenzy", "frenzy"),
        MYSTIC(
            "mystic",
            "Mystic",
            "Support / Magic",
            "Mending Wave",
            CompanionClass.SUPPORT,
            "mending-wave",
            "mending-wave",
        ),
    }
}
