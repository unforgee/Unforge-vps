package org.rsmod.content.interfaces.journal.tab

import com.google.inject.AbstractModule
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.player.perk.PerkVarps
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.journal.tab.configs.points_components
import org.rsmod.content.interfaces.journal.tab.configs.points_interfaces
import org.rsmod.content.interfaces.journal.tab.configs.points_varps
import org.rsmod.content.interfaces.journal.tab.scripts.PointsJournalScript
import org.rsmod.content.skills.core.skilling_point_varps
import org.rsmod.game.cheat.CheatCommandMap

class PointsTestDeps
@Inject
constructor(
    val points: PlayerPoints,
    val cheats: CheatCommandMap,
    val companions: CompanionService,
)

/**
 * `CompanionService` has no `@Inject` constructor - production binds it through `CompanionModule`,
 * which the test injector does not install. Bind a plain instance here; its default
 * contract/persistence sources are exactly what the tests need.
 */
private object PointsTestModule : AbstractModule() {
    override fun configure() {
        bind(CompanionService::class.java).toInstance(CompanionService())
    }
}

/**
 * The `Points & Progress` journal page and its `::points` entry point.
 *
 * The registry assertions cover the ledger itself: deterministic order, the total being the exact
 * sum of *active* balances, inactive systems reporting `0`, no double counting, and reads staying
 * scoped to the requesting player. The command assertions prove `::points` opens the page inside
 * the journal tab container and mutates nothing.
 */
class PointsJournalScriptTest {
    private fun GameTestScope.seedBalances() {
        player.setVarp(points_varps.questPoints, 7)
        player.setVarp(points_varps.slayerPoints, 3)
        player.setVarp(points_varps.wildSlayerPoints, 5)
        player.setVarp(points_varps.donatorPoints, 9)
        player.setVarp(PerkVarps.points, 4)
        player.setVarp(skilling_point_varps.points, 6)
    }

    @Test
    fun GameTestState.`registry returns every source in deterministic order`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            assertEquals(
                listOf(
                    "quest_points",
                    "achievement_points",
                    "skill_points",
                    "companion_talent",
                    "perk_points",
                    "pvm_points",
                    "boss_points",
                    "slayer_points",
                    "wild_slayer_points",
                    "slayer_task",
                    "support_points",
                    "donator_points",
                    "donator_rank",
                ),
                deps.points.entries(player).map { it.key },
            )
        }

    @Test
    fun GameTestState.`total is the exact sum of active balances`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            seedBalances()
            player.characterId = 7
            deps.companions.recruit(
                7,
                1,
                "Rex",
                CompanionClass.TANK,
                npcId = 0,
                maximumHitpoints = 100,
            )

            val entries = deps.points.entries(player)
            // 7+6+3(companion)+4+3+5+9 = 37; inactive systems contribute nothing.
            assertEquals(37, deps.points.total(entries))
            assertEquals(
                entries.filter { it.active }.sumOf { it.value },
                deps.points.total(entries),
            )
        }

    @Test
    fun GameTestState.`inactive systems report zero and stay out of the total`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            val inactive = deps.points.entries(player).filter { !it.active }
            assertEquals(
                setOf("achievement_points", "boss_points", "support_points"),
                inactive.map { it.key }.toSet(),
            )
            assertTrue(inactive.all { it.value == 0 })
            assertEquals(
                deps.points.total(player),
                deps.points.entries(player).filter { it.active }.sumOf { it.value },
            )
        }

    @Test
    fun GameTestState.`no source key appears twice`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            val keys = deps.points.entries(player).map { it.key }
            assertEquals(keys.size, keys.distinct().size)
        }

    @Test
    fun GameTestState.`points command opens the page and mutates nothing`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            seedBalances()
            val before =
                listOf(
                    player.vars[points_varps.questPoints],
                    player.vars[points_varps.slayerPoints],
                    player.vars[points_varps.wildSlayerPoints],
                    player.vars[points_varps.donatorPoints],
                    player.vars[PerkVarps.points],
                    player.vars[skilling_point_varps.points],
                )

            assertTrue(deps.cheats.execute(player, "points", emptyList()))

            // The open and its row writes are synchronous; `advance()` would clear the
            // capture buffer before the packets could be inspected.
            assertTrue(player.ui.containsOverlay(points_interfaces.unforge_points))
            assertEquals(
                before,
                listOf(
                    player.vars[points_varps.questPoints],
                    player.vars[points_varps.slayerPoints],
                    player.vars[points_varps.wildSlayerPoints],
                    player.vars[points_varps.donatorPoints],
                    player.vars[PerkVarps.points],
                    player.vars[skilling_point_varps.points],
                ),
            )

            // Static chrome (title/dividers) lives in the interface definition - only the
            // dynamic rows and the pinned total arrive as IfSetText packets.
            val texts = client.filterIsInstance<IfSetText>()
            assertTrue(texts.any { it.text == "Quest" })
            assertTrue(texts.any { it.text == "Quests" })
            // Total row is pinned outside the scroll layer: 7+6+4+3+5+9 = 34.
            assertTrue(
                texts.any {
                    it.interfaceId == points_components.totalValue.interfaceId &&
                        it.componentId == points_components.totalValue.component &&
                        it.text == "34"
                }
            )
            // Inactive rows render honestly instead of inventing balances.
            assertTrue(texts.any { it.text == "Inactive" })
        }

    @Test
    fun GameTestState.`the report only ever reads the requesting player's balances`() =
        runInjectedGameTest(PointsTestDeps::class, PointsTestModule, PointsJournalScript::class) {
            deps ->
            val other = registerPlayer()
            player.setVarp(points_varps.slayerPoints, 11)
            other.setVarp(points_varps.slayerPoints, 99)

            val own = deps.points.entries(player).first { it.key == "slayer_points" }
            val theirs = deps.points.entries(other).first { it.key == "slayer_points" }
            assertEquals(11, own.value)
            assertEquals(99, theirs.value)
        }
}
