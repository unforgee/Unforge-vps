package org.rsmod.api.net.rsprot.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.config.constants
import org.rsmod.game.entity.Player
import org.rsmod.game.type.mod.UnpackedModLevelType

class ChatModIconTest {
    private fun modLevel(clientCode: Int) =
        UnpackedModLevelType(
            clientCode = clientCode,
            accessFlags = 0L,
            internalId = 0,
            internalName = "test",
        )

    @Test
    fun `player without mod level or badge resolves to the plain code`() {
        val player = Player()
        assertEquals(0, player.chatModIcon())
    }

    @Test
    fun `staff without badge keeps the vanilla client code`() {
        val player = Player()
        player.modLevel = modLevel(constants.mod_clientcode_pmod)
        assertEquals(1, player.chatModIcon())
        player.modLevel = modLevel(constants.mod_clientcode_jmod)
        assertEquals(2, player.chatModIcon())
    }

    @Test
    fun `donator badge is sent for non-staff players`() {
        val player = Player()
        player.modLevel = modLevel(constants.mod_clientcode_player)
        player.chatBadge = 63
        assertEquals(63, player.chatModIcon())
    }

    @Test
    fun `staff and donator collapse into the combined sprite range`() {
        val player = Player()
        player.chatBadge = 60
        player.modLevel = modLevel(constants.mod_clientcode_pmod)
        assertEquals(67, player.chatModIcon())
        player.chatBadge = 66
        assertEquals(73, player.chatModIcon())
        player.modLevel = modLevel(constants.mod_clientcode_jmod)
        assertEquals(80, player.chatModIcon())
        player.chatBadge = 63
        assertEquals(77, player.chatModIcon())
    }

    @Test
    fun `out of range badge never overrides the staff icon`() {
        val player = Player()
        player.modLevel = modLevel(constants.mod_clientcode_jmod)
        player.chatBadge = 42
        assertEquals(2, player.chatModIcon())
    }
}
