package org.rsmod.api.death

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.isWilderness
import org.rsmod.api.combat.effects.CombatEffects
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.jingles
import org.rsmod.api.config.refs.midis
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.varps
import org.rsmod.api.invtx.invClear
import org.rsmod.api.ironman.GameModeService
import org.rsmod.api.player.deathResetTimers
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.map.CoordGrid

@Singleton
public class PlayerDeath
@Inject
constructor(
    private val statTypes: StatTypeList,
    private val objRepo: ObjRepository,
    private val gameModeService: GameModeService,
    private val effects: CombatEffects,
    private val deathHooks: Set<PlayerDeathHook>,
) {
    private var Player.specialAttackType by intVarp(varps.sa_attack)

    public suspend fun death(access: ProtectedAccess) {
        for (hook in deathHooks) {
            if (hook.death(access)) {
                return
            }
        }
        access.deathSequence()
    }

    private suspend fun ProtectedAccess.deathSequence() {
        val deathCoords = player.coords
        val inWilderness = deathCoords.isWilderness()
        val respawn = CoordGrid(0, 50, 50, 21, 18)
        val randomRespawn = mapFindSquareLineOfWalk(respawn, minRadius = 0, maxRadius = 2)
        stopAction()
        delay(2)
        anim(seqs.human_death)
        delay(4)
        combatClearQueue()
        clearQueue(queues.death)
        midiSong(midis.stop_music)
        midiJingle(jingles.death_jingle_2)
        mes("Oh dear, you are dead!")

        // Hardcore demotion: every death that reaches `queues.death` is a genuine lethal death
        // (this server has no scripted/minigame-safe deaths that enqueue it). The demotion is
        // applied and persisted before the respawn so a disconnect cannot skip it. Hardcore
        // Ultimate Ironman keeps UIM restrictions via `demotedMode`.
        val demotion = gameModeService.demoteHardcore(player)
        if (demotion != null) {
            while (!demotion.isDone) {
                delay(1)
            }
            mes(
                "You have fallen as a Hardcore Ironman. Your hardcore status is lost, " +
                    "and your game mode is now ${player.gameMode?.displayName}."
            )
        }

        if (inWilderness) {
            val duration = 1500 // 15 minutes
            var droppedCount = 0
            for (item in player.inv) {
                if (item != null && item.count > 0) {
                    objRepo.add(item, deathCoords, duration, player)
                    droppedCount++
                }
            }
            for (item in player.worn) {
                if (item != null && item.count > 0) {
                    objRepo.add(item, deathCoords, duration, player)
                    droppedCount++
                }
            }
            player.invClear(player.inv)
            player.invClear(player.worn)
            if (droppedCount > 0) {
                mes(
                    "A death pile has been created at (${deathCoords.x}, ${deathCoords.z}) for 15 minutes."
                )
            }
        }

        telejump(randomRespawn ?: respawn)
        resetAnim()
        resetPlayerState(statTypes)
        restoreToplevelTabs(
            components.toplevel_target_pvp_icons,
            components.toplevel_target_side1,
            components.toplevel_target_side2,
            components.toplevel_target_side4,
            components.toplevel_target_side5,
            components.toplevel_target_side6,
            components.toplevel_target_side9,
            components.toplevel_target_side8,
            components.toplevel_target_side7,
            components.toplevel_target_side10,
            components.toplevel_target_side11,
            components.toplevel_target_side12,
            components.toplevel_target_side13,
        )
    }

    private fun ProtectedAccess.resetPlayerState(stats: StatTypeList) {
        player.disablePrayers()
        player.deathResetTimers()

        // Unified status cleanup: removes every status flagged `clearOnDeath` (poison, bleed,
        // freeze locks, ...) and syncs the legacy poison fields.
        effects.statusService.clearOnDeath(player)

        player.specialAttackType = 0
        player.skullIcon = null

        rebuildAppearance()

        camReset()
        statRestoreAll(stats.values)
        minimapReset()
    }
}
