package org.rsmod.api.ironman

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.plugin.module.PluginModule

public class IronmanModule : PluginModule() {
    override fun bind() {
        bindInstance<GameModeService>()
        bindInstance<GroupIronmanService>()
        bindInstance<IronmanDataPipeline>()
        addSetBinding<CharacterDataStage.Pipeline>(IronmanDataPipeline::class.java)
    }
}
