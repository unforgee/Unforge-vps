package org.rsmod.content.skills.smithing

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/**
 * Published once per completed smelt or smith, after the objs have changed hands.
 *
 * Exists so content that cares about a *particular* product - Tutorial Island waiting on its first
 * bronze bar and bronze dagger - does not have to bind a second handler to interface 312's slots.
 * Two `onIfModalButton` subscriptions for one component would silently replace each other.
 *
 * @param count how many [product] objs were added, which for dart tips and arrowtips is more than
 *   one per iteration.
 */
data class SmithingProductMade(val player: Player, val product: ObjType, val count: Int) :
    UnboundEvent
