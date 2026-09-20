package org.rsmod.api.player.hit.processor

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.perk.PerkVarps
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class StandardPlayerHitProcessorTest {
    /**
     * Melee-based hits undergo special validation during processing (when the hitsplat is
     * displayed) to ensure that the hit's source is still alive.
     *
     * Currently, we have only confirmed this behavior for hits originating from an
     * [org.rsmod.game.entity.Npc]. If future evidence shows that the same applies to players, a
     * separate test should be added.
     */
    @Test
    fun GameTestState.`invalidate melee hits when npc source is dead`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Ensure hits are actually going through for test validity.
            advance(ticks = 1)
            check(player.hitpoints == 98) { "Unexpected player hitpoints: ${player.hitpoints}" }

            // Reset state and queue a new hit.
            player.stats[stats.hitpoints] = 99
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Set npc to 0 hitpoints, mimicking its death.
            npc.hitpoints = 0

            // Hit should have been invalidated due to npc being "dead."
            advance(ticks = 1)
            assertEquals(99, player.hitpoints)
        }

    /**
     * `Smithwright` is the POC's skill-flavoured PvM perk: -50 bps incoming melee damage from npcs
     * per level, 5% at level 10. Locked means zero xp, so the default outcome must be identical to
     * the pre-perk one.
     */
    @Test
    fun GameTestState.`smithwright reduces melee damage from npcs`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())

            // Locked (0 xp): the same result as before the perk existed - 50 damage stands.
            player.stats[stats.hitpoints] = 99
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 50)
            advance(ticks = 1)
            assertEquals(49, player.hitpoints)

            // Level 10 (55 xp): 500 bps off the npc melee hit -> 50 * 0.95 = 48 dealt.
            player.stats[stats.hitpoints] = 99
            player.setVarp(PerkVarps.xp_smithwright, 55)
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 50)
            advance(ticks = 1)
            assertEquals(51, player.hitpoints)

            // Mid-level (15 xp = level 5): 250 bps -> 50 - floor(50 * 0.025) = 49 dealt.
            player.stats[stats.hitpoints] = 99
            player.setVarp(PerkVarps.xp_smithwright, 15)
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 50)
            advance(ticks = 1)
            assertEquals(50, player.hitpoints)
        }

    /** The perk is melee-scoped: other npc hit styles never see it. */
    @Test
    fun GameTestState.`smithwright does not reduce other hit styles`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99
            player.setVarp(PerkVarps.xp_smithwright, 55)

            player.queueHit(npc, delay = 1, HitType.Magic, damage = 50)
            advance(ticks = 1)
            assertEquals(49, player.hitpoints)
        }

    /** The perk is PvM-scoped: a player-originated hit bypasses it entirely. */
    @Test
    fun GameTestState.`smithwright does not reduce player-originated hits`() =
        runGameTest(PlayerHitScript::class) {
            val attacker = registerPlayer()
            player.stats[stats.hitpoints] = 99
            player.setVarp(PerkVarps.xp_smithwright, 55)

            player.queueHit(attacker, delay = 1, HitType.Melee, damage = 50)
            advance(ticks = 1)
            assertEquals(49, player.hitpoints)
        }
}
