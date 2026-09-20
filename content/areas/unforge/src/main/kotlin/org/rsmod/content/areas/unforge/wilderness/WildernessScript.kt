package org.rsmod.content.areas.unforge.wilderness

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class WildernessScript
@Inject
constructor(
    private val drops: WildernessDrops,
    private val bossManager: WildernessBossManager,
    private val patrolManager: WildernessPatrolManager,
) : PluginScript() {

    override fun ScriptContext.startup() {
        // Register Deadman & Bounty Hunter drop tables
        drops.registerAll()

        // Initialize roaming bosses (1 Apex + 3 Regular) & roaming revenant patrols
        bossManager.initialize()
        patrolManager.initialize()

        // Tick loop hooks
        onEvent<GameLifecycle.LateCycle> {
            bossManager.process()
            patrolManager.process()
        }
        onEvent<NpcKilledEvent> { bossManager.onNpcKilled(this) }

        // Admin debug / information command
        onCommand("wildinfo") {
            desc = "Show Wilderness PvE roaming boss status"
            cheat {
                player.mes("<col=ff9900>=== Wilderness PvE Danger Info ===</col>")
                val bosses = bossManager.getActiveBosses()
                for (boss in bosses) {
                    val apexTag = if (boss.type.isApex) " [APEX]" else ""
                    val coords = boss.npc?.coords?.let { "(${it.x}, ${it.z})" } ?: "None"
                    player.mes(
                        "<col=00ff00>${boss.type.bossName}$apexTag</col> - Hotspot: ${boss.currentHotspot.displayName} | State: ${boss.state} | Coords: $coords"
                    )
                }
            }
        }

        // Single admin entry point for the boss audit/E2E smoke-test loop. This intentionally
        // reports evidence and live state only; it never marks a boss COMPLETE automatically.
        onCommand("bossmaster") {
            desc = "Show boss coverage and live E2E smoke-test status"
            modLevel = modlevels.admin
            cheat {
                val mode = args.firstOrNull()?.lowercase() ?: "status"
                val bosses = bossManager.getActiveBosses()
                player.mes("<col=ff9900>=== Boss Master / E2E ===</col>")
                player.mes(
                    "Coverage: docs/BOSS_COVERAGE.md | machine data: data/boss_coverage.json"
                )
                player.mes(
                    "Rule: compile != PASS; COMPLETE requires an interactive full-loop test."
                )
                player.mes("Live Wilderness registrations: ${bosses.size} (1 apex target)")
                for (boss in bosses) {
                    val npc = boss.npc
                    val npcState =
                        when {
                            npc == null -> "missing"
                            !npc.isValidTarget() -> "invalid/respawning"
                            else -> "alive hp=${npc.hitpoints}"
                        }
                    val kc =
                        WildernessBossKillCounts.varp(boss.type)?.let { player.vars[it].toString() }
                            ?: "unmapped"
                    player.mes(
                        "${boss.type.bossName}: state=${boss.state}, hotspot=${boss.currentHotspot.displayName}, $npcState, KC=$kc"
                    )
                }
                when (mode) {
                    "status",
                    "check" -> {
                        player.mes(
                            "E2E checklist: ENTER=manual | TARGET=live state | ATTACK=hit pipeline | SPECIAL=damage queued"
                        )
                        player.mes(
                            "Remaining manual checks: PLAYER_DEATH, TELEPORT, LOGOUT, KILL, LOOT, KC, RESET, SECOND_RUN"
                        )
                    }
                    else -> player.mes("Usage: ::bossmaster [status|check]")
                }
            }
        }
    }
}
