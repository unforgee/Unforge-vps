package org.rsmod.content.pvmprogression.streak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.rewards.RewardLedger
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Unit tests for [PvMStreakManager] using real, lightweight components.
 *
 * These tests exercise the admin streak mutators ([setStreak], [resetStreak]) and the read
 * accessors ([getStreak]) - none of which call [PvmRewardService.grant], so no live inventory is
 * needed.
 */
class PvMStreakManagerTest {
    private val configSource = PvmProgressionConfigSource()
    private val cache = PvmProgressionCache()
    private val ledger = RewardLedger()
    private val eventBus = EventBus()
    private val rewards = PvmRewardService(PvmPoints(), ObjTypeList(mutableMapOf()), ledger)
    private val manager = PvMStreakManager(configSource, cache, rewards, eventBus)

    private fun newPlayer(id: Int = 1): Player {
        val player = Player()
        player.characterId = id
        return player
    }

    @Test
    fun `getStreak returns an empty streak for a fresh player`() {
        val player = newPlayer()
        val streak = manager.getStreak(player)
        assertEquals(0, streak.currentStreak)
        assertEquals(0, streak.bestStreak)
        assertEquals(0, streak.unclaimedTokens)
        assertEquals(StreakData.EMPTY, streak)
    }

    @Test
    fun `setStreak updates the current streak`() {
        val player = newPlayer()
        manager.setStreak(player, 5)
        assertEquals(5, manager.getStreak(player).currentStreak)
    }

    @Test
    fun `setStreak updates best streak when the new value is higher`() {
        val player = newPlayer()
        manager.setStreak(player, 5)
        assertEquals(5, manager.getStreak(player).bestStreak)
        // A lower subsequent streak does not lower the best.
        manager.setStreak(player, 3)
        assertEquals(3, manager.getStreak(player).currentStreak)
        assertEquals(5, manager.getStreak(player).bestStreak)
    }

    @Test
    fun `setStreak clamps negative amounts to zero`() {
        val player = newPlayer()
        manager.setStreak(player, -10)
        val streak = manager.getStreak(player)
        assertEquals(0, streak.currentStreak)
        assertTrue(streak.bestStreak >= 0)
    }

    @Test
    fun `resetStreak zeroes the streak and pool`() {
        val player = newPlayer()
        manager.setStreak(player, 12)
        // Manually add an unclaimed pool via the cache, since setStreak does not touch the pool.
        val data = cache.getOrCreate(player.characterId)
        cache.put(player.characterId, data.copy(streak = data.streak.copy(unclaimedTokens = 40)))
        manager.resetStreak(player)
        val streak = manager.getStreak(player)
        assertEquals(0, streak.currentStreak)
        assertEquals(0, streak.bestStreak)
        assertEquals(0, streak.unclaimedTokens)
        assertEquals(StreakData.EMPTY, streak)
    }

    @Test
    fun `streak state is isolated per character`() {
        val a = newPlayer(id = 1)
        val b = newPlayer(id = 2)
        manager.setStreak(a, 7)
        assertEquals(7, manager.getStreak(a).currentStreak)
        assertEquals(0, manager.getStreak(b).currentStreak)
    }
}
