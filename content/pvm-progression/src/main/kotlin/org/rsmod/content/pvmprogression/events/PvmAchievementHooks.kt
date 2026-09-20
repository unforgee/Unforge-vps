package org.rsmod.content.pvmprogression.events

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.script.onEvent
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Achievement hook surface for PvM Progression 2.0.
 *
 * The spec asks the feature to "prepare" a fixed set of achievement hooks (BOUNTY_COMPLETE,
 * DAILY_BOUNTIES_COMPLETE, ... BOSS_STREAK_100). Rather than wire each one into a not-yet-existing
 * achievement engine (which the other agent may also be touching), this layer defines a single
 * [PvmAchievementHook] extension point and fires it for every event the spec lists. Any future
 * achievement system - or a debug listener - just implements the hook and registers it via the
 * module; the wiring here is the entire bridge.
 *
 * This keeps the feature decoupled: managers publish events, this script translates them into
 * achievement signals, and no manager knows achievements exist.
 */
@Singleton
class PvmAchievementHooks @Inject constructor() {
    /**
     * Implement and bind via `addSetBinding<PvmAchievementHook>` to receive progression signals.
     */
    fun interface PvmAchievementHook {
        fun on(signal: PvmAchievementSignal)
    }

    private val hooks = mutableListOf<PvmAchievementHook>()

    /** Registers a hook. Called by the module / tests; safe to call at any time. */
    fun register(hook: PvmAchievementHook) {
        hooks += hook
    }

    internal fun fire(signal: PvmAchievementSignal) {
        for (hook in hooks) {
            hook.on(signal)
        }
    }
}

/** The fixed achievement signal set requested by the spec. */
enum class PvmAchievementSignal {
    BOUNTY_COMPLETE,
    DAILY_BOUNTIES_COMPLETE,
    WEEKLY_BOUNTIES_COMPLETE,
    ELITE_BOUNTY_COMPLETE,
    MUTATION_KILL,
    EPIC_MUTATION_KILL,
    TREASURE_MUTATION_KILL,
    COMBAT_CHALLENGE_COMPLETE,
    ELITE_CHALLENGE_COMPLETE,
    BOSS_PERSONAL_BEST,
    BOSS_STREAK_10,
    BOSS_STREAK_25,
    BOSS_STREAK_50,
    BOSS_STREAK_100,
    LUCKY_KILL,
    LAST_STAND_KILL,
    MODIFIED_BOSS_KILL,
    MYTHIC_BOSS_KILL,
    MYSTERY_ENCHANT_ROLLED,
    LEGENDARY_ENCHANT_ROLLED,
}

/**
 * Script that translates PvM Progression events into [PvmAchievementSignal]s.
 *
 * It subscribes to every event the managers publish and maps each one to the corresponding signal
 * from the spec. Streak milestones are mapped by threshold so 10/25/50/100 each fire their own
 * signal (and a 100-streak also implies the lower ones have already fired).
 */
class PvmAchievementHookScript
@Inject
constructor(private val hooks: PvmAchievementHooks, private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<BountyCompleteEvent> {
            hooks.fire(PvmAchievementSignal.BOUNTY_COMPLETE)
            when (category) {
                BountyCategory.DAILY -> hooks.fire(PvmAchievementSignal.DAILY_BOUNTIES_COMPLETE)
                BountyCategory.WEEKLY -> hooks.fire(PvmAchievementSignal.WEEKLY_BOUNTIES_COMPLETE)
                BountyCategory.ELITE -> hooks.fire(PvmAchievementSignal.ELITE_BOUNTY_COMPLETE)
            }
        }
        onEvent<MutationKillEvent> {
            hooks.fire(PvmAchievementSignal.MUTATION_KILL)
            if (rarity == MutationRarity.EPIC) {
                hooks.fire(PvmAchievementSignal.EPIC_MUTATION_KILL)
            }
            if (MutationKind.TREASURE in mutations) {
                hooks.fire(PvmAchievementSignal.TREASURE_MUTATION_KILL)
            }
        }
        onEvent<CombatChallengeCompleteEvent> {
            hooks.fire(PvmAchievementSignal.COMBAT_CHALLENGE_COMPLETE)
            if (tier == ChallengeTier.ELITE) {
                hooks.fire(PvmAchievementSignal.ELITE_CHALLENGE_COMPLETE)
            }
        }
        onEvent<BossPersonalBestEvent> { hooks.fire(PvmAchievementSignal.BOSS_PERSONAL_BEST) }
        onEvent<StreakMilestoneEvent> {
            when (milestone) {
                10 -> hooks.fire(PvmAchievementSignal.BOSS_STREAK_10)
                25 -> hooks.fire(PvmAchievementSignal.BOSS_STREAK_25)
                50 -> hooks.fire(PvmAchievementSignal.BOSS_STREAK_50)
                100 -> hooks.fire(PvmAchievementSignal.BOSS_STREAK_100)
            }
        }
        onEvent<LuckyKillEvent> { hooks.fire(PvmAchievementSignal.LUCKY_KILL) }
        onEvent<LastStandKillEvent> { hooks.fire(PvmAchievementSignal.LAST_STAND_KILL) }
        onEvent<ModifiedBossKillEvent> { hooks.fire(PvmAchievementSignal.MODIFIED_BOSS_KILL) }
        onEvent<MythicBossKillEvent> { hooks.fire(PvmAchievementSignal.MYTHIC_BOSS_KILL) }
        onEvent<MysteryEnchantRolledEvent> {
            hooks.fire(PvmAchievementSignal.MYSTERY_ENCHANT_ROLLED)
        }
        onEvent<LegendaryEnchantRolledEvent> {
            hooks.fire(PvmAchievementSignal.LEGENDARY_ENCHANT_ROLLED)
        }
    }
}
