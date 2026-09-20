package org.rsmod.content.pvmprogression.boss

import jakarta.inject.Inject
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BossSystemsScript @Inject constructor(private val systems: BossSystemsManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> { systems.onKill(this) }
        onEvent<NpcHitEvents.GlobalModify> { systems.onNpcHitModify(this) }
        onEvent<NpcHitEvents.GlobalImpact> { systems.onNpcHitImpact(this) }
        onEvent<PlayerHitEvents.GlobalImpact> { systems.onPlayerHitImpact(this) }
        onEvent<NpcStateEvents.Respawn> { systems.onNpcLifecycleReset(npc) }
        onEvent<NpcStateEvents.Delete> { systems.onNpcLifecycleReset(npc) }
    }
}
