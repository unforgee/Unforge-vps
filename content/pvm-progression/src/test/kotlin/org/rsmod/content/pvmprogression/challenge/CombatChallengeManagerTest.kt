package org.rsmod.content.pvmprogression.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.random.GameRandom
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.encounter.BossEncounter
import org.rsmod.content.pvmprogression.events.ChallengeTier
import org.rsmod.content.pvmprogression.events.ChallengeType
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.rewards.RewardLedger
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.content.pvmprogression.test.FakeRandom
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid

/**
 * Unit tests for [CombatChallengeManager] using real, lightweight components and a [FakeRandom].
 *
 * These tests exercise the offer / accept / ignore lifecycle and the eligibility wiring without
 * ever calling [PvmRewardService.grant] (which needs a live inventory): [forceOffer] builds an
 * already- accepted challenge without paying out, and [ignore] / [activeOffer] only touch the
 * cache.
 */
class CombatChallengeManagerTest {
    private val configSource = PvmProgressionConfigSource()
    private val cache = PvmProgressionCache()
    private val ledger = RewardLedger()
    private val eventBus = EventBus()
    private val mapClock = MapClock(cycle = 100)
    private val rewards = PvmRewardService(PvmPoints(), ObjTypeList(mutableMapOf()), ledger)

    // FakeRandom returns values cycling through the queue; forceOffer calls randomStyle()
    // (random.of(1, 3)) twice per offer, so the queue has enough values.
    private val random = FakeRandom(1, 2, 1, 2, 1, 2, 1, 2)
    private val manager =
        CombatChallengeManager(configSource, random, cache, rewards, eventBus, mapClock)

    private fun freshManager(
        random: GameRandom,
        cache: PvmProgressionCache = PvmProgressionCache(),
        mapClock: MapClock = MapClock(cycle = 5000),
        configSource: PvmProgressionConfigSource = PvmProgressionConfigSource(),
    ): CombatChallengeManager =
        CombatChallengeManager(configSource, random, cache, rewards, eventBus, mapClock)

    private fun newPlayer(): Player {
        val player = Player()
        player.characterId = 1
        return player
    }

    private fun newEncounter(player: Player, bossMaxHp: Int = 200): BossEncounter =
        BossEncounter(
            id = 1000L,
            player = player,
            bossId = 42,
            bossName = "King Black Dragon",
            bossRef = null,
            bossMaxHp = bossMaxHp,
            startCycle = 0,
            lastActivityCycle = 0,
            startCoords = CoordGrid(x = 0, z = 0, level = 0),
            startHitpoints = 99,
            startPrayer = 99,
        )

    @Test
    fun `forceOffer creates an accepted pending challenge`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        val offer = manager.forceOffer(player, encounter, ChallengeType.NO_FOOD)
        assertEquals(encounter.id, offer.encounterId)
        assertEquals(ChallengeType.NO_FOOD, offer.type)
        assertTrue(offer.accepted)
        assertEquals(ChallengeTier.SILVER, offer.tier)
    }

    @Test
    fun `forceOffer tier scales with boss max hp`() {
        val player = newPlayer()
        val bronze =
            manager.forceOffer(player, newEncounter(player, bossMaxHp = 100), ChallengeType.NO_FOOD)
        assertEquals(ChallengeTier.BRONZE, bronze.tier)
        val gold =
            manager.forceOffer(player, newEncounter(player, bossMaxHp = 500), ChallengeType.NO_FOOD)
        assertEquals(ChallengeTier.GOLD, gold.tier)
        val elite =
            manager.forceOffer(player, newEncounter(player, bossMaxHp = 900), ChallengeType.NO_FOOD)
        assertEquals(ChallengeTier.ELITE, elite.tier)
    }

    @Test
    fun `forceOffer stores the offer in the active offer pool`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        val offer = manager.forceOffer(player, encounter, ChallengeType.NO_FOOD)
        val active = manager.activeOffer(player, encounter)
        assertNotNull(active)
        assertEquals(offer.encounterId, active.encounterId)
    }

    @Test
    fun `activeOffer returns null when no offer exists for the encounter`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        assertNull(manager.activeOffer(player, encounter))
    }

    @Test
    fun `ignore removes the offer from the active pool`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        val offer = manager.forceOffer(player, encounter, ChallengeType.NO_FOOD)
        assertNotNull(manager.activeOffer(player, encounter))
        manager.ignore(player, offer)
        assertNull(manager.activeOffer(player, encounter))
    }

    @Test
    fun `accept marks the offer as accepted`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        val offer = manager.forceOffer(player, encounter, ChallengeType.NO_FOOD)
        // forceOffer already accepts; reset to test accept explicitly.
        offer.accepted = false
        manager.accept(player, offer)
        assertTrue(offer.accepted)
    }

    @Test
    fun `maybeOffer returns null when the roll exceeds the chance bps`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        // Default offerChanceBps = 2000 (20%). A roll of 5000 >= 2000 -> no offer.
        val mgr = freshManager(FakeRandom(5000))
        assertNull(mgr.maybeOffer(player, encounter))
    }

    @Test
    fun `maybeOffer returns an offer when the roll is under the chance bps`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        val cache = PvmProgressionCache()
        // Roll 0 (< 2000) -> offer. Then random.of(eligible.size) picks the type, and two
        // randomStyle() calls follow. Supply a generous queue.
        val mgr = freshManager(FakeRandom(0, 0, 1, 2, 1, 2), cache = cache)
        val offer = mgr.maybeOffer(player, encounter)
        assertNotNull(offer)
        assertFalse(offer.accepted)
        assertNotNull(mgr.activeOffer(player, encounter))
    }

    @Test
    fun `maybeOffer respects the cooldown between offers`() {
        val player = newPlayer()
        val encounter = newEncounter(player)
        // First offer succeeds; default offerCooldownCycles = 400, so a second offer at the
        // same cycle is blocked even though the first was ignored.
        val mgr = freshManager(FakeRandom(0, 0, 1, 2, 1, 2, 0, 0, 1, 2, 1, 2))
        val first = mgr.maybeOffer(player, encounter)
        assertNotNull(first)
        mgr.ignore(player, first)
        assertNull(mgr.maybeOffer(player, encounter))
    }

    @Test
    fun `maybeOffer returns null when challenges are disabled`() {
        val disabledConfig = PvmProgressionConfigSource()
        disabledConfig.update { it.copy(challenge = it.challenge.copy(enabled = false)) }
        val player = newPlayer()
        val encounter = newEncounter(player)
        val mgr = freshManager(FakeRandom(0), configSource = disabledConfig)
        assertNull(mgr.maybeOffer(player, encounter))
    }
}
