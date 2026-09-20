package org.rsmod.content.other.fragments

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.plugin.module.PluginModule

public class FragmentModule : PluginModule() {
    override fun bind() {
        addSetBinding<CharacterDataStage.Pipeline>(FragmentCharacterPipeline::class.java)
        bindInstance<FragmentService>()
        addSetBinding<org.rsmod.api.companion.CompanionStatAugmenter>(
            FragmentCompanionAugmenter::class.java
        )
        addSetBinding<org.rsmod.api.equipment.instance.EquipmentRollModifier>(
            FragmentEquipmentRollModifier::class.java
        )
    }
}
