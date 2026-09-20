package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.misc.client.MapBuildComplete
import org.rsmod.api.player.events.PlayerClientEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class MapBuildCompleteHandler @Inject constructor(private val eventBus: EventBus) :
    MessageHandler<MapBuildComplete> {
    override fun handle(player: Player, message: MapBuildComplete) {
        player.lastMapBuildComplete = player.currentMapClock
        eventBus.publish(PlayerClientEvents.MapBuildComplete(player))
    }
}
