package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.varp.VarpLifetime

class UnforgeSlayerTestDeps @Inject constructor()

/**
 * The full slayer kill path, exercised through the real [NpcKilledEvent] subscription registered by
 * [UnforgeSlayer]: the same event `NpcDeath.spawnDeathDrops` publishes on every attributed kill
 * is what the tests emit.
 *
 * Task/npc data is read straight from the generated resources the implementation itself loads, so a
 * port-tool regeneration that breaks the task<->npc links fails these tests rather than shipping
 * silently.
 */
class UnforgeSlayerScriptTest {
    /**
     * `task|name|types|level|min|max|weight|disabled|unlock|ext|main|wild` -> name to row index.
     */
    private fun taskIndexByName(): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        resourceLines("unforge_slayer_tasks.txt").forEach { line ->
            val p = line.split("|")
            if (p[0] == "task") {
                out[p[1]] = out.size
            }
        }
        return out
    }

    /** `npc|id` / `task|name` rows -> npc type id to credited task names. */
    private fun npcTaskNames(): Map<Int, Set<String>> {
        val out = mutableMapOf<Int, MutableSet<String>>()
        var npcId = -1
        resourceLines("unforge_slayer_npcs.txt").forEach { line ->
            val p = line.split("|")
            when (p[0]) {
                "npc" -> npcId = p[1].toInt()
                "task" -> if (npcId >= 0) out.getOrPut(npcId) { mutableSetOf() } += p[1]
            }
        }
        return out
    }

    private fun resourceLines(name: String): List<String> =
        checkNotNull(javaClass.getResourceAsStream("$RES_DIR/$name")) { "Missing $name" }
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun GameTestScope.npcForTask(name: String): Npc {
        val id =
            npcTaskNames().entries.firstOrNull { name in it.value }?.key
                ?: error("No npc counts towards task '$name'")
        return spawnNpc(player.coords, npcTypes.getValue(id))
    }

    private fun GameTestScope.nonTaskNpc(): Npc {
        val taskNpcs = npcTaskNames().keys
        val type =
            npcTypes.values.firstOrNull { it.id !in taskNpcs }
                ?: error("Every npc is a slayer task npc?")
        return spawnNpc(player.coords, type)
    }

    private fun GameTestScope.assignTask(name: String, remaining: Int, dangerous: Boolean) {
        val index = taskIndexByName()[name] ?: error("No task named '$name'")
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.task, index + 1)
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.remaining, remaining)
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.assigned, remaining)
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.dangerous, if (dangerous) 1 else 0)
    }

    @Test
    fun GameTestState.`task kill decrements remaining`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            assignTask("Zombies", remaining = 3, dangerous = false)

            eventBus.publish(NpcKilledEvent(npcForTask("Zombies"), player))

            assertEquals(2, player.vars[UnforgeSlayerVarps.remaining])
            assertEquals(0, player.vars[UnforgeSlayerVarps.points])
            assertEquals(0, player.vars[UnforgeSlayerVarps.completed])
            assertMessageNotSent("Your slayer task is now complete.")
        }

    @Test
    fun GameTestState.`npc outside the task does not decrement`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            assignTask("Zombies", remaining = 3, dangerous = false)

            // A banshee counts towards a different task, so it must not credit Zombies.
            eventBus.publish(NpcKilledEvent(npcForTask("Banshees"), player))
            // An npc linked to no task at all must not credit anything either.
            eventBus.publish(NpcKilledEvent(nonTaskNpc(), player))

            assertEquals(3, player.vars[UnforgeSlayerVarps.remaining])
        }

    @Test
    fun GameTestState.`final kill completes regular task and pays points`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            // Zombies: EASY,MEDIUM -> highest type MEDIUM -> reward = (30 * 2/3) = 20.
            assignTask("Zombies", remaining = 1, dangerous = false)

            eventBus.publish(NpcKilledEvent(npcForTask("Zombies"), player))
            // The completion path is synchronous: the protected-access coroutine starts eagerly
            // inside the event handler, so all effects land before any `advance`. Assert before
            // advancing - `advance` clears the capture client each cycle.
            assertEquals(20, player.vars[UnforgeSlayerVarps.points])
            assertEquals(1, player.vars[UnforgeSlayerVarps.completed])
            // Task state fully reset.
            assertEquals(0, player.vars[UnforgeSlayerVarps.task])
            assertEquals(0, player.vars[UnforgeSlayerVarps.remaining])
            assertEquals(0, player.vars[UnforgeSlayerVarps.assigned])
            assertEquals(0, player.vars[UnforgeSlayerVarps.dangerous])
            // Wilderness totals untouched.
            assertEquals(0, player.vars[UnforgeSlayerVarps.wildPoints])
            assertEquals(0, player.vars[UnforgeSlayerVarps.wildCompleted])
            assertMessageSent("Your slayer task is now complete.")
        }

    @Test
    fun GameTestState.`dangerous task pays wilderness totals only`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            assignTask("Zombies", remaining = 1, dangerous = true)

            eventBus.publish(NpcKilledEvent(npcForTask("Zombies"), player))

            assertEquals(20, player.vars[UnforgeSlayerVarps.wildPoints])
            assertEquals(1, player.vars[UnforgeSlayerVarps.wildCompleted])
            assertEquals(0, player.vars[UnforgeSlayerVarps.points])
            assertEquals(0, player.vars[UnforgeSlayerVarps.completed])
            assertEquals(0, player.vars[UnforgeSlayerVarps.task])
            assertMessageSent("Your slayer task is now complete.")
        }

    @Test
    fun GameTestState.`kills with no task or zero remaining are ignored`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            // No task assigned at all.
            eventBus.publish(NpcKilledEvent(npcForTask("Zombies"), player))

            assertEquals(0, player.vars[UnforgeSlayerVarps.remaining])
            assertEquals(0, player.vars[UnforgeSlayerVarps.points])
            assertEquals(0, player.vars[UnforgeSlayerVarps.completed])

            // Task assigned but already finished - an extra kill must not double-pay.
            assignTask("Zombies", remaining = 0, dangerous = false)
            eventBus.publish(NpcKilledEvent(npcForTask("Zombies"), player))

            assertEquals(0, player.vars[UnforgeSlayerVarps.points])
            assertEquals(0, player.vars[UnforgeSlayerVarps.completed])
            assertMessageNotSent("Your slayer task is now complete.")
        }

    @Test
    fun GameTestState.`slayer varps persist across logout`() =
        runInjectedGameTest(UnforgeSlayerTestDeps::class, null, UnforgeSlayer::class) {
            // Task, progress, points and unlock state must all survive a relog: every varp the
            // loop relies on is declared with the default Perm lifetime. A non-perm regression
            // here would silently wipe tasks and balances on logout.
            val varps =
                UnforgeSlayerVarps.blocks +
                    listOf(
                        UnforgeSlayerVarps.task,
                        UnforgeSlayerVarps.remaining,
                        UnforgeSlayerVarps.assigned,
                        UnforgeSlayerVarps.dangerous,
                        UnforgeSlayerVarps.points,
                        UnforgeSlayerVarps.wildPoints,
                        UnforgeSlayerVarps.completed,
                        UnforgeSlayerVarps.wildCompleted,
                        UnforgeSlayerVarps.unlocksA,
                        UnforgeSlayerVarps.unlocksB,
                    )
            assertEquals(varps.size, varps.count { it.scope == VarpLifetime.Perm })
        }

    private companion object {
        private const val RES_DIR = "/org/rsmod/content/areas/unforge"
    }
}
