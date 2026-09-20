package org.rsmod.content.areas.tutorialisland

import jakarta.inject.Inject
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varps
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Tutorial Island is disabled for this standalone realm.
 *
 * Keep the legacy scripts in the build because their NPC and area content is still useful, but
 * never start the first-login flow. Marking the tutorial complete here also protects older/new
 * accounts from the tutorial rules and prevents the character creator from returning on reconnect.
 */
class TutorialIslandBootstrap @Inject constructor() : PluginScript() {
    private var Player.tutCompleted by boolVarp(tutorial_varps.tut2_completed)

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> { player.tutCompleted = true }
    }
}
