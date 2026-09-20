package org.rsmod.content.areas.unforge.donator

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.type.mod.ModLevelTypeList

class UnforgeRankIconTestDeps
@Inject
constructor(val cheats: CheatCommandMap, val modLevels: ModLevelTypeList) {
    /** Runs the real `::donor` command - requires [player]'s modLevel to be admin first. */
    fun setDonatorTierViaCommand(player: Player, tierId: Int) {
        check(cheats.execute(player, "donor", listOf(tierId.toString()))) {
            "::donor command was not registered."
        }
    }
}

class UnforgeRankIconsTest {
    @Test
    fun GameTestState.`normal player has no rank icons`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) {
            assertNull(player.appearance.namePrefix)
            assertEquals(-1, player.chatBadge)
        }

    @Test
    fun GameTestState.`donator badge shows after donor command`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) {
            player.setVarp(UnforgeDonatorVarps.tier, DonatorTier.DIAMOND.id)
            RankIconRenderer.sync(player)
            assertEquals("<img=63>", player.appearance.namePrefix)
            assertEquals(63, player.chatBadge)
        }

    @Test
    fun GameTestState.`staff only shows crown`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) { deps ->
            player.modLevel = deps.modLevels[modlevels.admin]
            RankIconRenderer.sync(player)
            assertEquals("<img=2>", player.appearance.namePrefix)
            assertEquals(-1, player.chatBadge)
        }

    @Test
    fun GameTestState.`moderator plus donator shows crown then badge`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) { deps ->
            player.modLevel = deps.modLevels[modlevels.moderator]
            player.setVarp(UnforgeDonatorVarps.tier, DonatorTier.SAPPHIRE.id)
            RankIconRenderer.sync(player)
            assertEquals("<img=1><img=60>", player.appearance.namePrefix)
            assertEquals(60, player.chatBadge)
        }

    @Test
    fun GameTestState.`admin plus highest donator shows crown then badge`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) { deps ->
            player.modLevel = deps.modLevels[modlevels.admin]
            deps.setDonatorTierViaCommand(player, DonatorTier.ZENYTE.id)
            assertEquals("<img=2><img=66>", player.appearance.namePrefix)
            assertEquals(66, player.chatBadge)
        }

    @Test
    fun GameTestState.`clearing donator keeps only the staff crown`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) { deps ->
            player.modLevel = deps.modLevels[modlevels.admin]
            deps.setDonatorTierViaCommand(player, DonatorTier.RUBY.id)
            deps.setDonatorTierViaCommand(player, DonatorTier.NONE.id)
            assertEquals("<img=2>", player.appearance.namePrefix)
            assertEquals(-1, player.chatBadge)
        }

    @Test
    fun GameTestState.`appearance rebuild is flagged when badges change`() =
        runInjectedGameTest(
            UnforgeRankIconTestDeps::class,
            null,
            UnforgeRankIcons::class,
            UnforgeDonator::class,
        ) {
            player.appearance.clearRebuildFlag()
            player.setVarp(UnforgeDonatorVarps.tier, DonatorTier.EMERALD.id)
            RankIconRenderer.sync(player)
            assertEquals(true, player.appearance.rebuild)
            assertEquals("<img=61>", player.appearance.namePrefix)
        }
}
