package org.rsmod.api.ironman

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.factory.player.TestPlayerFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.HardcoreStatus
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.game.ironman.groupObserverId
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.map.CoordGrid

class IronmanPolicyTest {
    private fun player(
        mode: GameMode?,
        selected: Boolean = true,
        uuid: Long = 1L,
        groupId: Int? = null,
    ): Player =
        TestPlayerFactory().create {
            this.gameMode = mode
            this.gameModeSelected = selected
            this.uuid = uuid
            this.groupId = groupId
            this.observerUUID = groupId?.let(::groupObserverId) ?: uuid
            if (mode?.isHardcore == true) {
                this.hardcoreStatus = HardcoreStatus.ACTIVE
            }
        }

    private fun ownedObj(ownerId: Long): Obj =
        Obj(
            coords = CoordGrid.ZERO,
            entity = ObjEntity(4151, 1, ObjScope.Private.id),
            creationCycle = 0,
            receiverId = ownerId,
            ownerId = ownerId,
        )

    private fun unownedObj(): Obj =
        Obj(
            coords = CoordGrid.ZERO,
            entity = ObjEntity(4151, 1, ObjScope.Perm.id),
            creationCycle = 0,
            receiverId = Obj.NULL_OBSERVER_ID,
            ownerId = Obj.NULL_OBSERVER_ID,
        )

    @Test
    fun `unselected account is denied every gated action`() {
        val unselected = player(mode = null, selected = false)
        val regular = player(GameMode.REGULAR, uuid = 2)
        assertTrue(IronmanPolicy.isSelectionPending(unselected))
        assertFalse(IronmanPolicy.canTradeWith(unselected, regular).allowed)
        assertFalse(IronmanPolicy.canTradeWith(regular, unselected).allowed)
        assertFalse(IronmanPolicy.canUseBank(unselected).allowed)
        assertFalse(IronmanPolicy.canTakeObj(unselected, unownedObj()).allowed)
        assertFalse(IronmanPolicy.canUseGroupStorage(unselected).allowed)
        assertFalse(IronmanPolicy.canUseItemOn(unselected, regular).allowed)
    }

    @Test
    fun `regular players can trade and use all systems`() {
        val regular = player(GameMode.REGULAR)
        val other = player(GameMode.REGULAR, uuid = 2)
        assertTrue(IronmanPolicy.canTradeWith(regular, other).allowed)
        assertTrue(IronmanPolicy.canUseBank(regular).allowed)
        assertTrue(IronmanPolicy.canTakeObj(regular, ownedObj(ownerId = 99)).allowed)
        assertTrue(IronmanPolicy.canUseGrandExchange(regular).allowed)
    }

    @Test
    fun `ironman cannot trade or take other players' drops`() {
        val ironman = player(GameMode.IRONMAN)
        val regular = player(GameMode.REGULAR, uuid = 2)
        assertFalse(IronmanPolicy.canTradeWith(ironman, regular).allowed)
        assertFalse(IronmanPolicy.canUseItemOn(ironman, regular).allowed)
        assertFalse(IronmanPolicy.canTakeObj(ironman, ownedObj(ownerId = 2)).allowed)
        assertTrue(IronmanPolicy.canTakeObj(ironman, ownedObj(ownerId = 1)).allowed)
        assertTrue(IronmanPolicy.canTakeObj(ironman, unownedObj()).allowed)
        assertTrue(IronmanPolicy.canUseBank(ironman).allowed)
    }

    @Test
    fun `ultimate ironman cannot use the bank`() {
        val ultimate = player(GameMode.ULTIMATE_IRONMAN)
        assertFalse(IronmanPolicy.canUseBank(ultimate).allowed)
        assertEquals(
            IronmanPolicy.MSG_ULTIMATE_BANK,
            IronmanPolicy.canUseBank(ultimate).denialMessage,
        )
    }

    @Test
    fun `demoted hardcore ultimate keeps ultimate restrictions`() {
        val demoted = player(GameMode.ULTIMATE_IRONMAN)
        demoted.hardcoreStatus = HardcoreStatus.DEMOTED
        demoted.hardcoreDeathCount = 1
        assertFalse(IronmanPolicy.canUseBank(demoted).allowed)
        assertFalse(demoted.hardcoreRemaining)
    }

    @Test
    fun `group ironman can only trade within the group`() {
        val memberA = player(GameMode.GROUP_IRONMAN, uuid = 10, groupId = 5)
        val memberB = player(GameMode.GROUP_IRONMAN, uuid = 11, groupId = 5)
        val outsider = player(GameMode.GROUP_IRONMAN, uuid = 12, groupId = 6)
        val regular = player(GameMode.REGULAR, uuid = 13)
        assertTrue(IronmanPolicy.canTradeWith(memberA, memberB).allowed)
        assertTrue(IronmanPolicy.canUseGroupStorage(memberA).allowed)
        assertFalse(IronmanPolicy.canTradeWith(memberA, outsider).allowed)
        assertFalse(IronmanPolicy.canTradeWith(memberA, regular).allowed)
        // Group members can take each other's drops via the shared group observer id.
        assertTrue(IronmanPolicy.canTakeObj(memberA, ownedObj(ownerId = -5)).allowed)
        // ... but not drops owned by players outside the group.
        assertFalse(IronmanPolicy.canTakeObj(memberA, ownedObj(ownerId = 99)).allowed)
    }

    @Test
    fun `restricted modes cannot buy overstock sold by other players`() {
        val ironman = player(GameMode.IRONMAN)
        val regular = player(GameMode.REGULAR)
        // Shop has 20 stock, only 10 from default stock; ironman is capped at 10.
        assertEquals(
            10,
            IronmanPolicy.shopBuyCap(ironman, currentStock = 20, initialStock = 10, request = 50),
        )
        assertEquals(
            20,
            IronmanPolicy.shopBuyCap(regular, currentStock = 20, initialStock = 10, request = 50),
        )
    }

    @Test
    fun `invalid client-sent mode names cannot map to a game mode`() {
        assertNull(GameMode.fromDbName("bogus"))
        assertNull(GameMode.fromDbName(""))
        assertNull(GameMode.fromDbName(null))
        assertNull(GameMode.fromDbName("HARDCORE")) // partial name is not a valid mode
        assertEquals(GameMode.HARDCORE_IRONMAN, GameMode.fromDbName("hardcore_ironman"))
    }

    @Test
    fun `hardcore modes demote to their non-hardcore base mode`() {
        assertEquals(GameMode.IRONMAN, GameMode.HARDCORE_IRONMAN.demotedMode)
        assertEquals(GameMode.ULTIMATE_IRONMAN, GameMode.HARDCORE_ULTIMATE_IRONMAN.demotedMode)
        assertEquals(GameMode.IRONMAN, GameMode.IRONMAN.demotedMode)
        assertTrue(GameMode.HARDCORE_IRONMAN.isHardcore)
        assertTrue(GameMode.HARDCORE_ULTIMATE_IRONMAN.isHardcore)
        assertTrue(GameMode.HARDCORE_ULTIMATE_IRONMAN.isUltimate)
    }
}
