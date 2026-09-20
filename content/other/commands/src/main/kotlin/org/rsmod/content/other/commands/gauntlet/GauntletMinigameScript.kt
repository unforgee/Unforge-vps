package org.rsmod.content.other.commands.gauntlet

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.commands.playerCommand
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Lightweight, server-authoritative wave state for the Gauntlet Arena. */
@Singleton
public class GauntletMinigameScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
) : PluginScript() {
    private val runs = ConcurrentHashMap<Int, GauntletRun>()
    private val cooldownUntil = ConcurrentHashMap<Int, Int>()
    private val ownedNpcs = ConcurrentHashMap<Npc, Int>()

    override fun ScriptContext.startup() {
        playerCommand("gauntlet", "Enter The Gauntlet Arena", cheat = { startRun(player) })
        playerCommand("arena", "Enter The Gauntlet Arena", cheat = { startRun(player) })
        onEvent<NpcKilledEvent> { onNpcKilled(npc, killer) }
        onEvent<NpcStateEvents.Delete> { ownedNpcs.remove(npc) }
        onEvent<SessionStateEvent.Logout> { cleanup(player, applyCooldown = false) }
        onEvent<SessionStateEvent.Delete> { cleanup(player, applyCooldown = false) }
    }

    public fun startRun(player: Player) {
        val id = player.characterId
        if (runs.containsKey(id)) {
            player.mes("You are already in The Gauntlet Arena.")
            return
        }
        val readyAt = cooldownUntil[id]
        if (readyAt != null && readyAt > mapClock.cycle) {
            player.mes("The arena is still on cooldown for you.")
            return
        }
        if (player.combatLevel < GauntletConfig.MIN_COMBAT_LEVEL) {
            player.mes(
                "You need combat level ${GauntletConfig.MIN_COMBAT_LEVEL} to enter the arena."
            )
            return
        }
        val run = GauntletRun(id)
        runs[id] = run
        player.mes(
            "<col=ff981f>Welcome to The Gauntlet Arena.</col> Defeat five waves for points and a guaranteed reward."
        )
        spawnWave(player, run)
    }

    private fun spawnWave(player: Player, run: GauntletRun) {
        val wave = run.beginWave() ?: return onVictory(player, run)
        var spawned = 0
        for ((index, id) in wave.npcIds.withIndex()) {
            val type = npcTypes[id] ?: continue
            val coords = player.coords.translateX((index % 3) - 1).translateZ((index / 3) + 2)
            val npc = Npc(type, CoordGrid(coords.packed))
            npcRepo.add(npc, Int.MAX_VALUE)
            ownedNpcs[npc] = run.playerId
            spawned++
        }
        run.activeNpcs = spawned
        if (spawned == 0) {
            player.mes(
                "The arena could not load this wave because its NPC definitions are unavailable."
            )
            cleanup(player, applyCooldown = false)
            return
        }
        player.mes("Wave ${run.wave + 1} begins! Defeat ${run.activeNpcs} enemies.")
    }

    private fun onNpcKilled(npc: Npc, killer: Player) {
        val owner = ownedNpcs.remove(npc) ?: return
        if (owner != killer.characterId) return
        val run = runs[owner] ?: return
        if (!run.registerKill()) return
        run.finishWave()
        killer.mes(
            "Wave cleared! +${GauntletConfig.WAVE_POINTS[run.wave - 1]} points. Total: ${run.points}."
        )
        if (run.wave >= GauntletWave.entries.size) onVictory(killer, run)
        else spawnWave(killer, run)
    }

    private fun onVictory(player: Player, run: GauntletRun) {
        if (run.rewardGranted) return
        run.rewardGranted = true
        player.invAdd(player.inv, GauntletConfig.REWARD_ITEM_ID, GauntletConfig.REWARD_ITEM_AMOUNT)
        player.mes(
            "<col=00ff00>Victory!</col> You earned ${run.points} points and a guaranteed reward."
        )
        cleanup(player, applyCooldown = true)
    }

    private fun cleanup(player: Player, applyCooldown: Boolean) {
        val run = runs.remove(player.characterId) ?: return
        ownedNpcs.entries
            .filter { it.value == run.playerId }
            .forEach { entry ->
                npcRepo.del(entry.key, Int.MAX_VALUE)
                ownedNpcs.remove(entry.key)
            }
        if (applyCooldown)
            cooldownUntil[player.characterId] = mapClock + GauntletConfig.COOLDOWN_CYCLES
    }
}
