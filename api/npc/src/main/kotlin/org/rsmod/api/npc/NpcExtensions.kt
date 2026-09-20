package org.rsmod.api.npc

import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.varns
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode

public fun Npc.clearInteractionRoute() {
    clearInteraction()
    abortRoute()
}

public fun Npc.queueDeath() {
    queue(queues.death, 1)
}

public fun Npc.isValidTarget(): Boolean {
    return isSlotAssigned && isVisible && isNotDelayed && hitpoints > 0
}

public fun Npc.isOutOfCombat(): Boolean = !isInCombat()

public fun Npc.isInCombat(): Boolean {
    if (vars[varns.lastattack] + constants.combat_activecombat_delay >= currentMapClock) {
        return true
    }
    return mode == NpcMode.OpPlayer2 || mode == NpcMode.ApPlayer2 || mode == NpcMode.PlayerEscape
}

/** @return `true` if the npc is **currently** in a multi-combat area. */
public fun Npc.mapMultiway(checker: AreaChecker): Boolean {
    return true
}

public fun Npc.hasAttackOp(): Boolean {
    return visType.op.any { it?.equals("attack", ignoreCase = true) == true } ||
        visType.hasOp(org.rsmod.game.interact.InteractionOp.Op2)
}

/**
 * @return `true` if the npc exposes a literal "attack" option - i.e. something a player could
 *   actually initiate combat with. Unlike [hasAttackOp] this does not treat any populated op2 (e.g.
 *   "Trade", "Bank") as attackable, so it is safe for automatic target selection.
 */
public fun Npc.isAttackable(): Boolean =
    visType.op.any { it?.equals("attack", ignoreCase = true) == true }

public fun Npc.attackOp(): org.rsmod.game.interact.InteractionOp {
    val attackSlot = visType.op.indexOfFirst { it?.equals("attack", ignoreCase = true) == true }
    return if (attackSlot in 0..4) {
        org.rsmod.game.interact.InteractionOp.entries[attackSlot]
    } else {
        org.rsmod.game.interact.InteractionOp.Op2
    }
}
