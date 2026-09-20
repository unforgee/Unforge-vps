package org.rsmod.api.combat.commons

import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.UnpackedNpcType

/**
 * Registry for the active slayer-task check used by combat formulas.
 *
 * Combat formulas sit below content modules in the dependency graph, so the "is this npc the
 * player's current slayer task" resolution cannot live there. The slayer content module installs a
 * provider here at startup; until one is installed, every npc reports "not a task" (the previous
 * behaviour of the `isSlayerTask` TODO stub).
 */
public object SlayerTaskProviders {
    @Volatile
    public var isTaskNpc: (npc: UnpackedNpcType, player: Player) -> Boolean = { _, _ -> false }
}
