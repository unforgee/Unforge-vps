package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.game.ironman.groupObserverId
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.map.CoordGrid

/**
 * Companion kill drops must belong to the owner, not the virtual companion player. The bot shares
 * the owner's [Player.observerUUID] so `Obj.fromOwner(bot)` stamps `receiverId`/`ownerId` with an
 * id the owner passes in [IronmanPolicy.canTakeObj] and [Obj.isVisibleTo].
 */
class CompanionDropOwnershipTest {
    private fun owner(mode: GameMode = GameMode.IRONMAN, observer: Long? = 7L): Player =
        Player().apply {
            uuid = 7L
            observerUUID = observer
            gameMode = mode
            gameModeSelected = true
        }

    /** Mirrors the `Obj.fromOwner(bot, ...)` fields used by every drop path. */
    private fun companionDrop(observerId: Long): Obj =
        Obj(
            coords = CoordGrid.ZERO,
            entity = ObjEntity(4151, 1, ObjScope.Private.id),
            creationCycle = 0,
            receiverId = observerId,
            ownerId = observerId,
        )

    @Test
    fun `companion drop is visible to and takeable by a restricted owner`() {
        val owner = owner(mode = GameMode.IRONMAN)
        val drop = companionDrop(companionObserverId(owner, companionId = 42L))

        assertTrue(drop.isVisibleTo(owner))
        assertTrue(drop.isOriginalOwner(owner))
        assertTrue(IronmanPolicy.canTakeObj(owner, drop).allowed)
    }

    @Test
    fun `companion drop stays private from other restricted players`() {
        val owner = owner()
        val stranger =
            Player().apply {
                uuid = 99L
                observerUUID = 99L
                gameMode = GameMode.IRONMAN
                gameModeSelected = true
            }
        val drop = companionDrop(companionObserverId(owner, companionId = 42L))

        assertFalse(drop.isVisibleTo(stranger))
        assertEquals(
            IronmanPolicy.MSG_OTHER_PLAYER_DROP,
            IronmanPolicy.canTakeObj(stranger, drop).denialMessage,
        )
    }

    @Test
    fun `group ironman companion drops are shared with the group`() {
        val owner = owner(mode = GameMode.GROUP_IRONMAN, observer = groupObserverId(5))
        val mate =
            Player().apply {
                uuid = 8L
                observerUUID = groupObserverId(5)
                gameMode = GameMode.GROUP_IRONMAN
                gameModeSelected = true
                groupId = 5
            }
        val drop = companionDrop(companionObserverId(owner, companionId = 42L))

        assertTrue(drop.isVisibleTo(mate))
        assertTrue(IronmanPolicy.canTakeObj(mate, drop).allowed)
    }

    @Test
    fun `missing owner observer id falls back to the isolated companion id`() {
        val owner = owner(observer = null)
        val observerId = companionObserverId(owner, companionId = 42L)
        assertEquals(-42L, observerId)
        val drop = companionDrop(observerId)
        assertFalse(drop.isVisibleTo(owner))
    }
}
