package org.rsmod.content.areas.unforge.slayer

import com.github.michaelbull.logging.InlineLogger
import kotlin.random.Random
import org.rsmod.api.player.stat.baseDefenceLvl
import org.rsmod.api.player.stat.baseSlayerLvl
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * Shared slayer task state - extracted so both the dialogue flows ([UnforgeSlayer]) and the
 * slayer hub UI (`slayer.hub`) operate on one implementation instead of drifting copies.
 *
 * Task definitions and npc->task links are generated from `content/kronos-data` by
 * `work/port-tools/port_slayer.py` into `unforge_slayer_tasks.txt` and
 * `unforge_slayer_npcs.txt`; both are loaded once here.
 */
internal object CwSlayerData {
    private val logger = InlineLogger()

    /** Task index -> task; index+1 is stored in `cw_slayer_task` (0 = no task). */
    val tasks: List<CwSlayerTask> by lazy { loadTasks() }

    /** npc type id -> slayer task names the npc counts towards. */
    val npcTasks: Map<Int, Set<String>> by lazy { loadNpcTasks() }

    /** First npc id that counts towards the given task name - used for the hub head model. */
    fun npcIdForTask(name: String): Int? = npcTasks.entries.firstOrNull { name in it.value }?.key

    private fun loadTasks(): List<CwSlayerTask> {
        val res = "/org/rsmod/content/areas/unforge/unforge_slayer_tasks.txt"
        val stream = javaClass.getResourceAsStream(res)
        if (stream == null) {
            logger.warn { "Unforge slayer task resource missing: $res" }
            return emptyList()
        }
        val out = mutableListOf<CwSlayerTask>()
        var teleports = mutableListOf<CwSlayerTeleport>()
        stream.bufferedReader().useLines { seq ->
            for (raw in seq) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split("|")
                when (p[0]) {
                    "task" -> {
                        teleports = mutableListOf()
                        out +=
                            CwSlayerTask(
                                key = out.size,
                                name = p[1],
                                types =
                                    p[2]
                                        .split(",")
                                        .mapNotNull { t ->
                                            CwSlayerTaskType.entries.firstOrNull { it.name == t }
                                        }
                                        .toSet(),
                                level = p[3].toInt(),
                                min = p[4].toInt(),
                                max = p[5].toInt(),
                                weight = p[6].toInt(),
                                disabled = p[7] == "1",
                                unlock = unlockOf(p[8]),
                                extension = unlockOf(p[9]),
                                mainSpawns = p[10].toInt(),
                                wildSpawns = p[11].toInt(),
                                teleports = teleports,
                            )
                    }
                    "tp" ->
                        teleports +=
                            CwSlayerTeleport(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4])
                }
            }
        }
        return out
    }

    private fun unlockOf(name: String): CwSlayerUnlock? {
        if (name == "-") return null
        val match =
            CwSlayerUnlock.entries.firstOrNull { it.name == name }
                ?: CwSlayerUnlock.entries.firstOrNull {
                    it.name.replace("_", "") == name.replace("_", "")
                }
        if (match == null) {
            logger.warn { "Unknown slayer unlock flag in task data: $name" }
        }
        return match
    }

    private fun loadNpcTasks(): Map<Int, Set<String>> {
        val res = "/org/rsmod/content/areas/unforge/unforge_slayer_npcs.txt"
        val stream = javaClass.getResourceAsStream(res)
        if (stream == null) {
            logger.warn { "Unforge slayer npc resource missing: $res" }
            return emptyMap()
        }
        val out = mutableMapOf<Int, MutableSet<String>>()
        var npcId = -1
        stream.bufferedReader().useLines { seq ->
            for (raw in seq) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split("|")
                when (p[0]) {
                    "npc" -> npcId = p[1].toInt()
                    "task" -> if (npcId >= 0) out.getOrPut(npcId) { mutableSetOf() } += p[1]
                }
            }
        }
        return out
    }
}

internal fun cwSetVarp(player: Player, varp: VarpType, value: Int) {
    player.vars.backing[varp.id] = value
    player.resyncVar(varp)
}

internal fun Player.cwCurrentTask(): CwSlayerTask? {
    val idx = vars[UnforgeSlayerVarps.task]
    return if (idx > 0) CwSlayerData.tasks.getOrNull(idx - 1) else null
}

internal fun Player.cwRemaining(): Int = vars[UnforgeSlayerVarps.remaining]

internal fun Player.cwIsDangerous(): Boolean = vars[UnforgeSlayerVarps.dangerous] == 1

internal fun Player.cwResetTask() {
    cwSetVarp(this, UnforgeSlayerVarps.task, 0)
    cwSetVarp(this, UnforgeSlayerVarps.remaining, 0)
    cwSetVarp(this, UnforgeSlayerVarps.assigned, 0)
    cwSetVarp(this, UnforgeSlayerVarps.dangerous, 0)
}

internal fun Player.cwBlockedTasks(): List<CwSlayerTask> =
    UnforgeSlayerVarps.blocks.mapNotNull { CwSlayerData.tasks.getOrNull(vars[it] - 1) }

internal fun cwPossibleTasks(
    player: Player,
    type: CwSlayerTaskType,
    preferWilderness: Boolean,
): List<CwSlayerTask> {
    val blocked = player.cwBlockedTasks().toSet()
    return CwSlayerData.tasks.filter { task ->
        type in task.types &&
            !task.disabled &&
            player.baseSlayerLvl >= task.level &&
            // Basilisks/Cockatrice require defence 20 (mirror shield), like Kronos.
            ((task.name != "Basilisks" && task.name != "Cockatrice") ||
                player.baseDefenceLvl >= 20) &&
            (task.unlock == null || player.hasSlayerUnlock(task.unlock)) &&
            (if (preferWilderness) task.wildSpawns > 0 else task.mainSpawns > 0) &&
            task !in blocked
    }
}

/** Mirrors `Slayer.set` - returns the task assigned, or null if no candidates. */
internal fun cwAssignTask(
    player: Player,
    type: CwSlayerTaskType,
    preferWilderness: Boolean,
): CwSlayerTask? {
    val candidates = cwPossibleTasks(player, type, preferWilderness)
    if (candidates.isEmpty()) {
        return null
    }
    var roll = Random.nextInt(candidates.sumOf { it.weight })
    var task = candidates.last()
    for (t in candidates) {
        roll -= t.weight
        if (roll < 0) {
            task = t
            break
        }
    }
    cwSetVarp(player, UnforgeSlayerVarps.task, task.key + 1)
    var amount =
        if (CwSlayerTaskType.BOSS in task.types) -1 else Random.nextInt(task.min, task.max + 1)
    if (task.extension != null && player.hasSlayerUnlock(task.extension)) {
        amount = (amount * 1.35).toInt()
    }
    cwSetVarp(player, UnforgeSlayerVarps.remaining, amount)
    cwSetVarp(player, UnforgeSlayerVarps.assigned, amount)
    cwSetVarp(player, UnforgeSlayerVarps.dangerous, if (preferWilderness) 1 else 0)
    return task
}
