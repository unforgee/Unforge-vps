package org.rsmod.content.areas.unforge

import jakarta.inject.Inject
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class UnforgeScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        // Unforge port: handlers registered by feature plugins in this module.
    }
}
