package org.rsmod.content.pvmprogression

import com.google.inject.Scopes.SINGLETON
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.content.pvmprogression.boss.BossModifierManager
import org.rsmod.content.pvmprogression.boss.BossSystemsManager
import org.rsmod.content.pvmprogression.bounty.PvMBountyManager
import org.rsmod.content.pvmprogression.challenge.CombatChallengeManager
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounterTracker
import org.rsmod.content.pvmprogression.events.PvmAchievementHooks
import org.rsmod.content.pvmprogression.mutation.MutationManager
import org.rsmod.content.pvmprogression.record.BossRecordManager
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.rewards.RewardLedger
import org.rsmod.content.pvmprogression.store.InMemoryPvmProgressionStore
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.content.pvmprogression.store.PvmProgressionSavePipeline
import org.rsmod.content.pvmprogression.store.PvmProgressionStore
import org.rsmod.content.pvmprogression.streak.PvMStreakManager
import org.rsmod.plugin.module.PluginModule

/**
 * Guice module for PvM Progression 2.0.
 *
 * Binds every manager as a singleton (they hold only process-local, player-keyed state), the config
 * source, the reward faucet + ledger, the cache + in-memory store, the save pipeline, the death
 * hook, and the achievement-hook surface. The save pipeline and death hook are registered as set
 * bindings so the engine's `AccountSavingService` and death sequence discover them alongside every
 * other module's contributions - this is the merge-safe way to extend those pipelines without
 * editing core files.
 */
class PvmProgressionModule : PluginModule() {
    override fun bind() {
        bindInstance<PvmProgressionConfigSource>()
        bindInstance<PvmRewardService>()
        bindInstance<RewardLedger>()
        bindInstance<PvmProgressionCache>()
        bindInstance<BossEncounterTracker>()
        bindInstance<PvMBountyManager>()
        bindInstance<MutationManager>()
        bindInstance<BossRecordManager>()
        bindInstance<BossModifierManager>()
        bindInstance<BossSystemsManager>()
        bindInstance<CombatChallengeManager>()
        bindInstance<PvMStreakManager>()
        bindInstance<PvmAchievementHooks>()
        bindInstance<PvmProgressionSavePipeline>()
        addSetBinding<CharacterDataStage.Pipeline>(PvmProgressionSavePipeline::class.java)
        addSetBinding<PlayerDeathHook>(PvmProgressionDeathHook::class.java)
        bind(PvmProgressionStore::class.java)
            .to(InMemoryPvmProgressionStore::class.java)
            .`in`(SINGLETON)
    }
}
