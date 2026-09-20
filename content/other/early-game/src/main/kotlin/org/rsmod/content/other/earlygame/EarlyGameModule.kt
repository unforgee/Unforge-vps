package org.rsmod.content.other.earlygame

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.plugin.module.PluginModule

/** Registers only the persistence pipeline; gameplay is discovered through PluginScript. */
public class EarlyGameModule : PluginModule() {
    override fun bind() {
        addSetBinding<CharacterDataStage.Pipeline>(EarlyGameCharacterPipeline::class.java)
        bindInstance<EarlyGameProgressionService>()
        bindInstance<EarlyGameCompanionBridge>()
        bindInstance<EarlyGameTrialManager>()
    }
}
