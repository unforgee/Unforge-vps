package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc

class UnforgeSuperiorsTestDeps @Inject constructor()

/**
 * Superior-creature behaviour end-to-end through the real [NpcKilledEvent] subscriptions: the spawn
 * roll lives in [UnforgeSuperiors], kill credit in [UnforgeSlayer]. `random.next` pins the
 * 1/200 roll so the spawn path is deterministic.
 */
class UnforgeSuperiorsScriptTest {
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

    /** npc type id -> credited task names, from the same generated resource the script reads. */
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

    private fun GameTestScope.assignTask(name: String, remaining: Int) {
        val index = taskIndexByName()[name] ?: error("No task named '$name'")
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.task, index + 1)
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.remaining, remaining)
        VarPlayerIntMapSetter.set(player, UnforgeSlayerVarps.assigned, remaining)
    }

    /** All tests use Banshees, so the banshee superior id covers every spawned superior. */
    private fun GameTestScope.superiorsNear(): List<Npc> {
        val superiorId = npcTypes[UnforgeSuperiorNpcs.banshee].id
        return npcRepo.findAll(player.coords).filter { it.type.id == superiorId }.toList()
    }

    @Test
    fun GameTestState.`superior kill credits its base task`() =
        runInjectedGameTest(
            UnforgeSuperiorsTestDeps::class,
            null,
            UnforgeSlayer::class,
            UnforgeSuperiors::class,
        ) {
            assignTask("Banshees", remaining = 3)
            val superior = spawnNpc(player.coords, npcTypes[UnforgeSuperiorNpcs.banshee])

            eventBus.publish(NpcKilledEvent(superior, player))

            assertEquals(2, player.vars[UnforgeSlayerVarps.remaining])
        }

    @Test
    fun GameTestState.`lucky roll with unlock spawns the superior`() =
        runInjectedGameTest(
            UnforgeSuperiorsTestDeps::class,
            null,
            UnforgeSlayer::class,
            UnforgeSuperiors::class,
        ) {
            assignTask("Banshees", remaining = 5)
            player.setSlayerUnlock(CwSlayerUnlock.BIGGER_AND_BADDER, true)
            random.next = 0 // forces `of(200)` -> 0: the superior roll succeeds

            eventBus.publish(NpcKilledEvent(npcForTask("Banshees"), player))

            assertEquals(1, superiorsNear().size)
            // The task kill still counted normally.
            assertEquals(4, player.vars[UnforgeSlayerVarps.remaining])
        }

    @Test
    fun GameTestState.`no spawn without the unlock even on a lucky roll`() =
        runInjectedGameTest(
            UnforgeSuperiorsTestDeps::class,
            null,
            UnforgeSlayer::class,
            UnforgeSuperiors::class,
        ) {
            assignTask("Banshees", remaining = 5)
            random.next = 0

            eventBus.publish(NpcKilledEvent(npcForTask("Banshees"), player))

            assertTrue(superiorsNear().isEmpty())
            assertEquals(4, player.vars[UnforgeSlayerVarps.remaining])
        }

    @Test
    fun GameTestState.`one superior per player and kills never chain`() =
        runInjectedGameTest(
            UnforgeSuperiorsTestDeps::class,
            null,
            UnforgeSlayer::class,
            UnforgeSuperiors::class,
        ) {
            assignTask("Banshees", remaining = 10)
            player.setSlayerUnlock(CwSlayerUnlock.BIGGER_AND_BADDER, true)

            random.next = 0
            eventBus.publish(NpcKilledEvent(npcForTask("Banshees"), player))
            val first = superiorsNear().single()

            // While a superior is alive another credited kill must not spawn a second.
            random.next = 0
            eventBus.publish(NpcKilledEvent(npcForTask("Banshees"), player))
            assertEquals(1, superiorsNear().size)

            // Killing the superior itself must not chain another roll - it is not in the
            // generated task-npc links.
            random.next = 0
            eventBus.publish(NpcKilledEvent(first, player))
            assertEquals(1, superiorsNear().size)
            // Its kill still counted towards the task (10 - 2 regular - 1 superior).
            assertEquals(7, player.vars[UnforgeSlayerVarps.remaining])
        }

    private companion object {
        private const val RES_DIR = "/org/rsmod/content/areas/unforge"
    }
}
