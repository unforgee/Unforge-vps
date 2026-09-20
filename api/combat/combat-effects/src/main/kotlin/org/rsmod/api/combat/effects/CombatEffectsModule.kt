package org.rsmod.api.combat.effects

import org.rsmod.plugin.module.PluginModule

public class CombatEffectsModule : PluginModule() {
    override fun bind() {
        bindInstance<CombatEffects>()
        bindInstance<StatusService>()
    }
}
