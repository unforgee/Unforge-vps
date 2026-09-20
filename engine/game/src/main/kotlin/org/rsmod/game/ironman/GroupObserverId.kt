package org.rsmod.game.ironman

import org.rsmod.game.obj.Obj

/**
 * Group ironman members share a group-scoped [org.rsmod.game.entity.Player.observerUUID] so that
 * private/owned objs (drops, death piles) are visible to and owned by the whole group.
 *
 * Group observer ids occupy the negative `Long` range; character-based observer ids are always
 * positive, so the two namespaces can never collide. [Obj.NULL_OBSERVER_ID] is excluded.
 */
public fun groupObserverId(groupId: Int): Long {
    require(groupId > 0) { "`groupId` must be positive. (groupId=$groupId)" }
    return -groupId.toLong()
}

public fun isGroupObserverId(observerId: Long): Boolean =
    observerId < 0 && observerId != Obj.NULL_OBSERVER_ID
