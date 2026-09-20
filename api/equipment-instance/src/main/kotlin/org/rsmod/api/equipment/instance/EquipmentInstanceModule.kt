package org.rsmod.api.equipment.instance

import org.rsmod.plugin.module.PluginModule

public class EquipmentInstanceModule : PluginModule() {
    override fun bind() {
        bindInstance<EquipmentInstanceRepository>()
        bindInstance<EquipmentInstanceRegistry>()
        bindInstance<EquipmentInstanceMutationService>()
        bindInstance<EquipmentInstanceService>()
        bindInstance<WornSkillBonuses>()
        bindInstance<ReforgeService>()
        bindInstance<ForgeService>()
    }
}
