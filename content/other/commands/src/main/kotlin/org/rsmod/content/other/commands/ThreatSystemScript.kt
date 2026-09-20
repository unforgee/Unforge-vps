package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionPlayerRegistry
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.npc.threat.CombatRole
import org.rsmod.api.npc.threat.ThreatConfig
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.bonus.EquipmentCombatStats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wires the per-npc threat system into the world: role resolution for companion bots, cleanup on
 * npc death/despawn and player logout/death, plus the `::threat`, `::threatdebug`, `::role` and
 * `::taunt` developer commands.
 */
@Singleton
public class ThreatSystemScript
@Inject
constructor(
    private val threat: ThreatService,
    private val gearStats: EquipmentCombatStats,
    private val companions: CompanionService,
    private val companionRegistry: CompanionPlayerRegistry,
    private val npcList: NpcList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        threat.registerRoleResolver { player -> companionRoleOf(player) }

        onEvent<NpcKilledEvent> { threat.clearNpc(npc) }
        onEvent<NpcStateEvents.Delete> { threat.clearNpc(npc) }
        onEvent<SessionStateEvent.Logout> { threat.removePlayer(player) }
        onEvent<SessionStateEvent.Delete> { threat.removePlayer(player) }

        onCommand(
            "threat",
            "Show the threat table of the npc attacking you or your target",
            cheat = { dumpThreat() },
        )
        onCommand(
            "threatdebug",
            "Toggle [THREAT]/[AGGRO] debug logging",
            cheat = {
                ThreatConfig.DEBUG_THREAT = !ThreatConfig.DEBUG_THREAT
                val state = if (ThreatConfig.DEBUG_THREAT) "ENABLED" else "DISABLED"
                player.mes("Threat debug logging is now <col=ff981f>$state</col>.")
            },
        )
        onCommand(
            "role",
            "Set your PvM role: ::role tank|support|dps|hybrid|reset",
            cheat = { setRole() },
        )
        onCommand("taunt", "Taunt your current npc target onto you", cheat = { tauntTarget() })
    }

    private fun companionRoleOf(player: Player): CombatRole? {
        val companionId = companionRegistry.getCompanionId(player) ?: return null
        val ownerId = companionRegistry.getOwnerCharacterId(companionId) ?: return null
        val companion =
            companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return null
        return when (companion.companionClass) {
            CompanionClass.TANK -> CombatRole.TANK
            CompanionClass.SUPPORT -> CombatRole.SUPPORT
            CompanionClass.DPS -> CombatRole.DAMAGE
        }
    }

    /** The npc relevant to `::threat`: the player's own interaction target, else a nearby npc. */
    private fun targetNpc(player: Player): Npc? {
        (player.interaction as? InteractionNpc)?.let {
            return it.target
        }
        return npcList.firstOrNull { npc ->
            npc.isSlotAssigned &&
                npc.coords.level == player.coords.level &&
                npc.coords.chebyshevDistance(player.coords) <= 16 &&
                threat.currentTarget(npc) === player
        }
    }

    private fun org.rsmod.game.cheat.Cheat.dumpThreat() {
        val npc = targetNpc(player)
        if (npc == null) {
            player.mes("No npc is targeting you and you have no npc target.")
            return
        }
        val table = threat.describe(npc)
        player.mes(
            "<col=ff981f>THREAT</col> ${npc.visType.name} " +
                "(mode=${threat.tableMode(npc)} target=${threat.currentTarget(npc)?.username ?: "-"}" +
                " forced=${threat.forcedTarget(npc)?.username ?: "-"})"
        )
        if (table.isEmpty()) {
            player.mes("  <i>empty threat table</i>")
        }
        for ((holder, amount) in table) {
            player.mes("  ${holder.username} role=${threat.roleOf(holder)} threat=$amount")
        }
        val gear = gearStats.stats(player)
        player.mes(
            "  gear: melee=${gear.meleePower} ranged=${gear.rangedPower} magic=${gear.magicPower} " +
                "heal=${gear.healingPower} threatBonus=${gear.threatBonusBps}bps " +
                "dmgReduction=${gear.damageReductionBps}bps"
        )
    }

    private fun org.rsmod.game.cheat.Cheat.setRole() {
        val arg = args.getOrNull(0)?.lowercase()
        val role =
            when (arg) {
                "tank" -> CombatRole.TANK
                "support" -> CombatRole.SUPPORT
                "dps",
                "damage" -> CombatRole.DAMAGE
                "hybrid" -> CombatRole.HYBRID
                "reset",
                null -> null
                else -> {
                    player.mes("Usage: ::role tank|support|dps|hybrid|reset")
                    return
                }
            }
        threat.setRoleOverride(player, role)
        val shown = threat.roleOf(player)
        player.mes(
            "PvM role: <col=ff981f>$shown</col>" +
                if (role == null) " (override cleared)" else " (override)"
        )
    }

    private fun org.rsmod.game.cheat.Cheat.tauntTarget() {
        val npc = targetNpc(player)
        if (npc == null || !npc.isSlotAssigned) {
            player.mes("No npc to taunt.")
            return
        }
        if (threat.taunt(npc, player)) {
            player.mes("<col=ff981f>You taunt ${npc.visType.name}!</col>")
        } else {
            player.mes("${npc.visType.name} cannot be taunted right now.")
        }
    }
}

/** Removes a dead player's threat entries from every npc table. */
public class ThreatDeathHook @Inject constructor(private val threat: ThreatService) :
    PlayerDeathHook {
    override suspend fun death(access: ProtectedAccess): Boolean {
        threat.removePlayer(access.player)
        return false
    }
}

public class ThreatModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathHook>(ThreatDeathHook::class.java)
    }
}
