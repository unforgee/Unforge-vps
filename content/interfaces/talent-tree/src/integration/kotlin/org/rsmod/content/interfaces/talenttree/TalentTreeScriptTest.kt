package org.rsmod.content.interfaces.talenttree

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.talents.CompanyMembership
import org.rsmod.api.talents.CompanyRole
import org.rsmod.api.talents.CompanyService
import org.rsmod.api.talents.CompanyState
import org.rsmod.api.talents.PlayerTalentService
import org.rsmod.api.talents.TalentCatalog
import org.rsmod.api.talents.TalentEarnScript
import org.rsmod.api.talents.TalentEffectKey
import org.rsmod.api.talents.TalentTree
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.talenttree.configs.talenttree_components
import org.rsmod.content.interfaces.talenttree.configs.talenttree_interfaces
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.npc.UnpackedNpcType

class TalentTreeTestDeps
@Inject
constructor(
    val talents: PlayerTalentService,
    val companies: CompanyService,
    val cheats: CheatCommandMap,
)

/**
 * `TalentModule` is a plugin module, which the test injector does not install - bind the two
 * services as singletons here so the test scope and the script share one authoritative instance.
 */
private object TalentTreeTestModule : AbstractModule() {
    override fun configure() {
        bind(PlayerTalentService::class.java).`in`(Scopes.SINGLETON)
        bind(CompanyService::class.java).`in`(Scopes.SINGLETON)
    }
}

/**
 * The shared `unforge_talenttree` window end-to-end: `::talents`/`::companytree` entry points, card
 * clicks with the two-step confirm dialog, rendered tags/tooltips, and the atomicity contract that
 * a re-click can never spend a point the rules would deny.
 *
 * Card index = `(tier - 1) * 5 + slot`, matching the builder's grid order.
 */
class TalentTreeScriptTest {
    private fun cardIndex(defId: String): Int {
        val def = TalentCatalog.definition(defId)
        return (def.tier - 1) * 5 + def.slot
    }

    private fun GameTestScope.openTree(command: String) {
        // The command launches a protected coroutine eagerly - the modal is open and its
        // IfSetText packets are already captured without an advance.
        assertTrue(deps.cheats.execute(player, command, emptyList()))
        assertModalOpen(talenttree_interfaces.unforge_talenttree)
    }

    /** Clicks a card, lets the handler run, then answers the confirm dialog. */
    private fun GameTestScope.clickCard(defId: String, accept: Boolean) {
        player.ifButton(talenttree_components.cardHits[cardIndex(defId)])
        advance()
        // The resume runs the coroutine to completion eagerly - its messages are captured
        // immediately; a following `advance()` would wipe the capture buffer.
        player.resumeActiveCoroutine(
            ResumePauseButtonInput(components.chatmenu_pbutton, subcomponent = if (accept) 1 else 2)
        )
    }

    private fun GameTestScope.spawnBoss(): Npc {
        val type: UnpackedNpcType =
            npcTypes.values.firstOrNull { it.vislevel >= 100 }
                ?: error("No npc type with vislevel >= 100")
        return spawnNpc(player.coords, type)
    }

    private fun GameTestScope.renderedTexts(): List<String> =
        client.filterIsInstance<IfSetText>().map { it.text }

    private fun GameTestScope.makeCompany(
        role: CompanyRole,
        points: Int = 0,
        ranks: Map<String, Int> = emptyMap(),
        member: Player = player,
    ): CompanyState {
        val state = CompanyState(COMPANY_ID, "Test Co")
        state.points = points
        state.ranks.putAll(ranks)
        deps.companies.restore(
            member,
            CompanyMembership(member.characterId, COMPANY_ID, role),
            state,
        )
        return state
    }

    private lateinit var deps: TalentTreeTestDeps

    @Test
    fun GameTestState.`talents opens the personal tree and renders its state`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 3)
            openTree("talents")

            val rendered = renderedTexts()
            assertTrue(rendered.contains("Player Talent Tree"))
            assertTrue(rendered.contains("Available: 3 pts"))
            assertTrue(rendered.contains("0 / $maxSpend"))
            assertTrue(rendered.contains("Sharpened Strikes"))
            assertTrue(rendered.contains("Tier 2 - spend 5 points to unlock (0/5)"))
            // An empty, affordable tier-1 card offers Train while later tiers stay Locked.
            assertTrue(rendered.contains("Train"))
            assertTrue(rendered.contains("Locked"))
        }

    @Test
    fun GameTestState.`accepted train spends exactly one point`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 3)
            openTree("talents")
            clickCard("sharp-strikes", accept = true)

            assertEquals(1, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertEquals(2, deps.talents.points(player))
            assertMessageSent("<col=c8a2ff>Trained Sharpened Strikes to rank 1.</col>")
        }

    @Test
    fun GameTestState.`cancelled train spends nothing`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 3)
            openTree("talents")
            clickCard("sharp-strikes", accept = false)

            assertEquals(0, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertEquals(3, deps.talents.points(player))
            assertMessageSent("Talent training cancelled.")
        }

    @Test
    fun GameTestState.`insufficient balance refuses before the dialog`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            openTree("talents")
            player.ifButton(talenttree_components.cardHits[cardIndex("sharp-strikes")])
            advance()

            assertEquals(0, deps.talents.points(player))
            assertEquals(0, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertMessageSent("<col=c8a2ff>You need 1 talent point(s) for that - you have 0.</col>")
        }

    @Test
    fun GameTestState.`locked tier refuses before the dialog`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 10)
            openTree("talents")
            player.ifButton(talenttree_components.cardHits[cardIndex("battle-focus")])
            advance()

            assertEquals(10, deps.talents.points(player))
            assertEquals(0, deps.talents.rank(player, TalentCatalog.definition("battle-focus")))
            assertMessageSent("<col=c8a2ff>Tier 2 requires 5 spent points - you have 0.</col>")
        }

    @Test
    fun GameTestState.`missing prereq refuses before the dialog`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            // 5 spent unlocks tier 2, but momentum still needs sharp-strikes rank 3.
            deps.talents.grant(player, 10)
            repeat(2) { deps.talents.train(player, TalentCatalog.definition("sharp-strikes")) }
            repeat(3) { deps.talents.train(player, TalentCatalog.definition("point-runner")) }
            openTree("talents")
            player.ifButton(talenttree_components.cardHits[cardIndex("momentum")])
            advance()

            assertEquals(0, deps.talents.rank(player, TalentCatalog.definition("momentum")))
            assertMessageSent("<col=c8a2ff>Requires Sharpened Strikes rank 3 first.</col>")
        }

    @Test
    fun GameTestState.`a repeated request cannot spend the same point twice`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 1)
            openTree("talents")
            clickCard("sharp-strikes", accept = true)
            assertEquals(1, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertEquals(0, deps.talents.points(player))

            // Same card again: the re-validated attempt sees balance 0 and refuses.
            player.ifButton(talenttree_components.cardHits[cardIndex("sharp-strikes")])
            advance()
            assertEquals(1, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertMessageSent("<col=c8a2ff>You need 1 talent point(s) for that - you have 0.</col>")
        }

    @Test
    fun GameTestState.`max rank renders Max and refuses further training`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            // 5+5+2 = 12 spent unlocks tier 3, then hoarder goes straight to its only rank.
            deps.talents.grant(player, 50)
            repeat(5) { deps.talents.train(player, TalentCatalog.definition("sharp-strikes")) }
            repeat(5) { deps.talents.train(player, TalentCatalog.definition("point-runner")) }
            repeat(2) { deps.talents.train(player, TalentCatalog.definition("keen-eye")) }
            deps.talents.train(player, TalentCatalog.definition("hoarder"))
            openTree("talents")

            assertTrue(renderedTexts().contains("Max"))
            player.ifButton(talenttree_components.cardHits[cardIndex("hoarder")])
            advance()
            assertMessageSent("<col=c8a2ff>Hoarder is already at max rank.</col>")
        }

    @Test
    fun GameTestState.`info op sends the talent tooltip to chat`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            openTree("talents")
            player.ifButton(
                talenttree_components.cardHits[cardIndex("momentum")],
                op = IfButtonOp.Op2,
            )
            advance()

            assertMessageSent(
                "<col=c8a2ff>Momentum</col> - +4% damage against monsters per rank. " +
                    "Rank cost: 2 pts. Max rank: 2. " +
                    "Requires Sharpened Strikes rank 3."
            )
        }

    @Test
    fun GameTestState.`reset refunds every spent point after confirmation`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 5)
            repeat(3) { deps.talents.train(player, TalentCatalog.definition("sharp-strikes")) }
            assertEquals(2, deps.talents.points(player))
            openTree("talents")

            player.ifButton(talenttree_components.reset)
            advance()
            player.resumeActiveCoroutine(
                ResumePauseButtonInput(components.chatmenu_pbutton, subcomponent = 1)
            )

            assertEquals(0, deps.talents.rank(player, TalentCatalog.definition("sharp-strikes")))
            assertEquals(5, deps.talents.points(player))
            assertMessageSent("<col=c8a2ff>Talent tree reset - refunded 3 point(s).</col>")
        }

    @Test
    fun GameTestState.`companytree refuses players without a membership`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            assertTrue(deps.cheats.execute(player, "companytree", emptyList()))
            assertModalNotOpen(talenttree_interfaces.unforge_talenttree)
            assertMessageSent(
                "<col=c8a2ff>You are not in a company. Use ::company create <name>.</col>"
            )
        }

    @Test
    fun GameTestState.`member views but cannot train the leader-only talent`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            // 10 spent across tier-1 ranks unlocks tier 3 where war-council sits.
            val state =
                makeCompany(
                    CompanyRole.MEMBER,
                    points = 20,
                    ranks = mapOf("shared-knowledge" to 5, "war-chest-fund" to 5),
                )
            openTree("companytree")
            assertTrue(renderedTexts().contains("Company Talent Tree"))

            player.ifButton(talenttree_components.cardHits[cardIndex("war-council")])
            advance()
            assertMessageSent("<col=c8a2ff>Only the company leader can train that talent.</col>")
            assertEquals(0, state.ranks["war-council"] ?: 0)
        }

    @Test
    fun GameTestState.`leader trains and the passive applies to every member`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            val state =
                makeCompany(
                    CompanyRole.LEADER,
                    points = 20,
                    ranks = mapOf("shared-knowledge" to 5, "war-chest-fund" to 5),
                )
            val member = registerPlayer()
            deps.companies.restore(
                member,
                CompanyMembership(member.characterId, COMPANY_ID, CompanyRole.MEMBER),
                state,
            )

            openTree("companytree")
            clickCard("war-council", accept = true)

            assertEquals(1, state.ranks["war-council"])
            // The company damage passive now applies to leader and member alike...
            assertEquals(500, deps.companies.effectBps(player, TalentEffectKey.COMPANY_DAMAGE_BPS))
            assertEquals(500, deps.companies.effectBps(member, TalentEffectKey.COMPANY_DAMAGE_BPS))
            // ...but never leaks to company-less players or the personal tree.
            val outsider = registerPlayer()
            assertEquals(0, deps.companies.effectBps(outsider, TalentEffectKey.COMPANY_DAMAGE_BPS))
            assertEquals(0, deps.talents.effectBps(player, TalentEffectKey.NPC_DAMAGE_BPS))
        }

    @Test
    fun GameTestState.`member ranks stay off the personal tree and vice versa`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            deps.talents.grant(player, 5)
            deps.talents.train(player, TalentCatalog.definition("sharp-strikes"))
            val state =
                makeCompany(CompanyRole.LEADER, points = 5, ranks = mapOf("shared-knowledge" to 2))

            // Personal ranks only contain player-tree ids; the company state only company ids.
            assertTrue(deps.talents.ranks(player).keys.all { it in playerTalentIds })
            assertTrue(state.ranks.keys.all { it in companyTalentIds })
            assertTrue("shared-knowledge" !in deps.talents.ranks(player))
            // And a company train does not touch the player's personal balance.
            deps.companies.train(player, TalentCatalog.definition("banner-of-unity"))
            assertEquals(4, deps.talents.points(player))
        }

    @Test
    fun GameTestState.`boss kill awards a personal and a company talent point`() =
        runInjectedGameTest(
            TalentTreeTestDeps::class,
            TalentTreeTestModule,
            TalentTreeScript::class,
            TalentEarnScript::class,
        ) { deps ->
            this@TalentTreeScriptTest.deps = deps
            val state = makeCompany(CompanyRole.MEMBER)
            eventBus.publish(NpcKilledEvent(spawnBoss(), player))

            assertEquals(1, deps.talents.points(player))
            assertEquals(1, synchronized(state) { state.points })
        }

    private val maxSpend: Int =
        TalentCatalog.definitions(TalentTree.PLAYER).sumOf { it.pointsPerRank * it.maxRank }

    private companion object {
        private const val COMPANY_ID = 7
        private val playerTalentIds = TalentCatalog.player.map { it.id }.toSet()
        private val companyTalentIds = TalentCatalog.company.map { it.id }.toSet()
    }
}
