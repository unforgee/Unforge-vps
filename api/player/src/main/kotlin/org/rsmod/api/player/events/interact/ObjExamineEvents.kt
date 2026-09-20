package org.rsmod.api.player.events.interact

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType

public class ObjExamineEvents {
    /**
     * Published after the standard examine output (chat message + instance data) whenever a player
     * examines an inventory-held or worn obj. Subscribers get the resolved [type] and the raw [obj]
     * so they can look up instance data themselves.
     */
    public data class Examine(
        public val player: Player,
        public val obj: InvObj,
        public val type: UnpackedObjType,
    ) : UnboundEvent
}
