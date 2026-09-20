package org.rsmod.api.death

import org.rsmod.plugin.module.PluginModule

public class DeathModule : PluginModule() {
    override fun bind() {
        newSetBinding<PlayerDeathHook>()
    }
}
