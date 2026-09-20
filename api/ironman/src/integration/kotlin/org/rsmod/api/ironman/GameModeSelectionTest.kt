package org.rsmod.api.ironman

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.HardcoreStatus
import org.rsmod.game.ironman.IronmanPolicy

class GameModeSelectionTest {
    private fun GameTestScope.loginReady(player: Player) {
        eventBus.publish(SessionStateEvent.EngineLoginReady(player))
        advance(2)
    }

    /**
     * Drives the full selection flow: click the mode card, then press the Confirm button. The
     * wrap + holder pattern emits clicks on the holder with `comsub == 0`.
     */
    private fun GameTestScope.selectMode(player: Player, index: Int) {
        player.ifButton(gamemode_components.cardHits[index], comsub = 0)
        advance(1)
        player.ifButton(gamemode_components.confirm, comsub = 0)
        advance(2)
    }

    @Test
    fun GameTestState.`every game mode can be selected`() =
        runGameTest(GameModeSelectionScript::class) {
            for (mode in GameMode.entries) {
                val newPlayer = registerPlayer()
                assertFalse(newPlayer.gameModeSelected)

                loginReady(newPlayer)
                assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)

                selectMode(newPlayer, mode.ordinal)

                assertEquals(mode, newPlayer.gameMode)
                assertTrue(newPlayer.gameModeSelected)
                assertEquals(
                    if (mode.isHardcore) HardcoreStatus.ACTIVE else HardcoreStatus.DISABLED,
                    newPlayer.hardcoreStatus,
                )
            }
        }

    @Test
    fun GameTestState.`new account cannot reach gated gameplay before selecting`() =
        runGameTest(GameModeSelectionScript::class) {
            val newPlayer = registerPlayer()
            loginReady(newPlayer)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)
            // While unselected, every gated action is denied by IronmanPolicy.
            assertFalse(IronmanPolicy.canUseBank(newPlayer).allowed)
        }

    @Test
    fun GameTestState.`confirm without a selection does nothing`() =
        runGameTest(GameModeSelectionScript::class) {
            val newPlayer = registerPlayer()
            loginReady(newPlayer)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)

            // Pressing Confirm before picking a card must not apply a mode.
            newPlayer.ifButton(gamemode_components.confirm, comsub = 0)
            advance(2)
            assertFalse(newPlayer.gameModeSelected)
            assertNull(newPlayer.gameMode)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)
        }

    @Test
    fun GameTestState.`re-clicking cards switches the pending selection`() =
        runGameTest(GameModeSelectionScript::class) {
            val newPlayer = registerPlayer()
            loginReady(newPlayer)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)

            // Pick Ironman, change to Group Ironman, then confirm - the last pick wins.
            newPlayer.ifButton(gamemode_components.cardHits[GameMode.IRONMAN.ordinal], comsub = 0)
            advance(1)
            selectMode(newPlayer, GameMode.GROUP_IRONMAN.ordinal)

            assertEquals(GameMode.GROUP_IRONMAN, newPlayer.gameMode)
            assertTrue(newPlayer.gameModeSelected)
        }

    @Test
    fun GameTestState.`closing the modal reopens selection via watchdog`() =
        runGameTest(GameModeSelectionScript::class) {
            val newPlayer = registerPlayer()
            loginReady(newPlayer)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)

            // Simulate escaping the modal; the soft-timer watchdog relaunches the flow.
            newPlayer.ifClose()
            advance(RECHECK_TICKS + 2)

            assertFalse(newPlayer.gameModeSelected)
            assertModalOpen(gamemode_interfaces.unforgeGamemode, newPlayer)
        }

    @Test
    fun GameTestState.`legacy selected account skips the selection flow`() =
        runGameTest(GameModeSelectionScript::class) {
            val legacy = registerPlayer()
            legacy.gameMode = GameMode.REGULAR
            legacy.gameModeSelected = true
            loginReady(legacy)
            assertModalNotOpen(gamemode_interfaces.unforgeGamemode, legacy)
        }

    private companion object {
        private const val RECHECK_TICKS = 10
    }
}
