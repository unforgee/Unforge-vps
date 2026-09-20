package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.NpcHintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.ResetHintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.TileHintArrow
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow.TileHintArrow.HintArrowTilePosition
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Clears any active yellow hint arrow (world + minimap). */
public fun Player.clearHintArrow() {
    client.write(HintArrow(ResetHintArrow))
}

public object HintArrows {
    /** Points the yellow downward arrow at [npc] (also flashes on the minimap). */
    public fun setNpc(player: Player, npc: Npc) {
        player.client.write(HintArrow(NpcHintArrow(npc.slotId)))
    }

    public fun setNpc(player: Player, npcSlotId: Int) {
        player.client.write(HintArrow(NpcHintArrow(npcSlotId)))
    }

    /** Points the yellow arrow at a tile (used for locs/doors when no NPC target). */
    public fun setTile(
        player: Player,
        coords: CoordGrid,
        position: HintArrowTilePosition = HintArrowTilePosition.CENTER,
        height: Int = 0,
    ) {
        setTile(player, coords.x, coords.z, height, position)
    }

    public fun setTile(
        player: Player,
        x: Int,
        z: Int,
        height: Int = 0,
        position: HintArrowTilePosition = HintArrowTilePosition.CENTER,
    ) {
        player.client.write(HintArrow(TileHintArrow(x, z, height, position)))
    }

    public fun clear(player: Player) {
        player.clearHintArrow()
    }
}
