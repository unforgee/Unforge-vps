package org.rsmod.api.companion

import org.rsmod.plugin.module.PluginModule

public class CompanionModule : PluginModule() {
    override fun bind() {
        bindSingleton<CompanionContractSource>(NoopCompanionContractSource)
        bindSingleton<CompanionPersistence>(NoopCompanionPersistence)
        bindInstance<CompanionFrameLayoutStore>()
        bindInstance<CompanionFrameLayoutApplier>()
        bindInstance<CompanionCharacterApplier>()
        addSetBinding<org.rsmod.api.account.character.CharacterDataStage.Pipeline>(
            CompanionCharacterPipeline::class.java
        )
        addSetBinding<org.rsmod.api.account.character.CharacterDataStage.Pipeline>(
            CompanionFrameLayoutPipeline::class.java
        )
        bindBaseInstance<CompanionPlayerRegistry>(DefaultCompanionPlayerRegistry::class.java)
        bindInstance<CompanionService>()
        bindInstance<CompanionSkillService>()
        bindInstance<CompanionTelemetryService>()
        addSetBinding<org.rsmod.api.player.bonus.WornBonusesAugmenter>(
            CompanionOwnerBonusAugmenter::class.java
        )
        addSetBinding<org.rsmod.api.death.PlayerDeathHook>(CompanionDeathHook::class.java)
    }
}
