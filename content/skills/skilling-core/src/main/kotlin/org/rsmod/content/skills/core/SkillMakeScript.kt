package org.rsmod.content.skills.core

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drives every make loop that shares [SkillingQueues.make], one iteration per firing.
 *
 * Registered exactly **once** for the whole server: `onProtectedEvent` keys handlers by queue id,
 * so a second subscription for the same queue would silently replace this one - which is why the
 * skill modules that use it carry recipes and not queue handlers.
 */
class SkillMakeScript @Inject constructor(private val actions: SkillMakeActions) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs<MakeJob>(SkillingQueues.make) { actions.continueJob(this, it.args) }
    }
}
