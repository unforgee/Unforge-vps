package org.rsmod.content.areas.tutorialisland

import org.rsmod.content.areas.tutorialisland.design.PlayerDesignKits
import org.rsmod.plugin.module.PluginModule

class TutorialIslandModule : PluginModule() {
    override fun bind() {
        bindInstance<PlayerDesignKits>()
    }
}
