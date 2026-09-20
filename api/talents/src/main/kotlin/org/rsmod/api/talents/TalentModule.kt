package org.rsmod.api.talents

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.plugin.module.PluginModule

/** Dependency bindings for the shared player/company talent-tree module. */
public class TalentModule : PluginModule() {
    override fun bind() {
        bindInstance<CompanyCharacterApplier>()
        bindInstance<PlayerTalentService>()
        bindInstance<CompanyService>()
        addSetBinding<CharacterDataStage.Pipeline>(CompanyCharacterPipeline::class.java)
    }
}
