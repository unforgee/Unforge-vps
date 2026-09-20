package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class PlayerClientEvents {
    /** Fired when the client reports that the map scene has finished building. */
    public data class MapBuildComplete(val player: Player) : UnboundEvent
}
