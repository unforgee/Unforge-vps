package org.rsmod.content.other.commands.relicrush

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

/** Server-authoritative Relic Rush defense run. */
@Singleton
public class RelicRushScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
) : PluginScript() {
    private val runs = ConcurrentHashMap<Int, RelicRushRun>()
    private val cooldownUntil = ConcurrentHashMap<Int, Int>()
    private val ownedNpcs = ConcurrentHashMap<Npc, Int>()

    override fun ScriptContext.startup() {
        // Note: `::relic` is owned by EarlyGameScript (starter relic selection); the
        // dedicated `relicrush` name stays this minigame's entry point.
        playerCommand("relicrush", "Defend the ancient relic", cheat = { startRun(player) })
        onEvent<NpcKilledEvent> { onNpcKilled(npc, killer) }
        onEvent<NpcStateEvents.Delete> { ownedNpcs.remove(npc) }
        onEvent<SessionStateEvent.Logout> { cleanup(player) }
        onEvent<SessionStateEvent.Delete> { cleanup(player) }
    }

    public fun startRun(player: Player) {
        val id = player.characterId
        if (runs.containsKey(id)) {
            player.mes("You are already defending a relic.")
            return
        }
        if ((cooldownUntil[id] ?: 0) > mapClock.cycle) {
            player.mes("The relic chamber is still on cooldown for you.")
            return
        }
        if (player.combatLevel < RelicRushConfig.MIN_COMBAT_LEVEL) {
            player.mes(
                "You need combat level ${RelicRushConfig.MIN_COMBAT_LEVEL} to enter Relic Rush."
            )
            return
        }
        val run = RelicRushRun(id)
        runs[id] = run
        player.mes(
            "<col=ff981f>Relic Rush begins!</col> Keep the relic alive and collect pillar energy."
        )
        spawnWave(player, run)
    }

    private fun spawnWave(player: Player, run: RelicRushRun) {
        val wave = run.beginWave() ?: return spawnBoss(player, run)
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
            player.mes("Relic Rush could not load its NPC definitions.")
            cleanup(player)
            return
        }
        player.mes("Wave ${run.wave + 1} begins. Relic integrity: ${relicPercent(run)}%.")
    }

    private fun spawnBoss(player: Player, run: RelicRushRun) {
        if (run.bossAlive) return
        val type = npcTypes[2025]
        if (type == null) {
            player.mes("The Relic Guardian definition is unavailable.")
            finish(player, run)
            return
        }
        val boss = Npc(type, CoordGrid(player.coords.packed))
        npcRepo.add(boss, Int.MAX_VALUE)
        ownedNpcs[boss] = run.playerId
        run.bossAlive = true
        player.mes("<col=ff0000>The Relic Guardian has appeared!</col>")
    }

    private fun onNpcKilled(npc: Npc, killer: Player) {
        val owner = ownedNpcs.remove(npc) ?: return
        if (owner != killer.characterId) return
        val run = runs[owner] ?: return
        if (run.bossAlive) {
            run.bossAlive = false
            run.points += 250
            finish(killer, run)
            return
        }
        if (!run.registerKill()) return
        run.finishWave()
        killer.mes("Wave cleared! Total points: ${run.points}.")
        spawnWave(killer, run)
    }

    public fun collectEnergy(player: Player): Boolean {
        val run = runs[player.characterId] ?: return false
        if (!run.collectEnergy()) return false
        player.mes("You collect relic energy. Pillar charge: ${run.pillarEnergy}%.")
        return true
    }

    public fun repairRelic(player: Player): Boolean {
        val run = runs[player.characterId] ?: return false
        run.repairRelic()
        player.mes("The relic is repaired to ${relicPercent(run)}% integrity.")
        return true
    }

    public fun damageRelic(player: Player, amount: Int) {
        val run = runs[player.characterId] ?: return
        run.damageRelic(amount)
        player.mes("The relic is damaged! Integrity: ${relicPercent(run)}%.")
        if (run.relicHealth == 0) finish(player, run)
    }

    private fun finish(player: Player, run: RelicRushRun) {
        if (run.rewardGranted) return
        run.rewardGranted = true
        val tier = run.rewardTier()
        val amount =
            when (tier) {
                RelicRushRewardTier.PERFECT -> RelicRushConfig.PERFECT_REWARD_AMOUNT
                RelicRushRewardTier.STABLE -> RelicRushConfig.STABLE_REWARD_AMOUNT
                RelicRushRewardTier.PARTICIPATION -> RelicRushConfig.PARTICIPATION_REWARD_AMOUNT
            }
        run.points += run.pillarEnergy
        player.invAdd(player.inv, RelicRushConfig.PARTICIPATION_REWARD_ID, amount)
        player.mes("<col=00ff00>Relic Rush complete!</col> Tier: $tier. Points: ${run.points}.")
        cleanup(player)
        cooldownUntil[player.characterId] = mapClock + RelicRushConfig.COOLDOWN_CYCLES
    }

    private fun cleanup(player: Player) {
        val run = runs.remove(player.characterId) ?: return
        ownedNpcs.entries
            .filter { it.value == run.playerId }
            .forEach { entry ->
                npcRepo.del(entry.key, Int.MAX_VALUE)
                ownedNpcs.remove(entry.key)
            }
    }

    private fun relicPercent(run: RelicRushRun): Int =
        run.relicHealth * 100 / RelicRushConfig.STARTING_RELIC_HEALTH
}
