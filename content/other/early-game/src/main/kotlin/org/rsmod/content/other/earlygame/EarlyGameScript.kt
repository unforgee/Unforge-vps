@file:OptIn(InternalApi::class)

package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.cheat.CheatHandlerBuilder
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.league.configs.league_objs
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wires the early-game state machine to stable engine events and developer hooks. */
public class EarlyGameScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val progression: EarlyGameProgressionService,
    private val companionBridge: EarlyGameCompanionBridge,
    private val trialManager: EarlyGameTrialManager,
    private val trackerSync: EarlyGameTrackerSync,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> { onLogin(player) }
        onEvent<SessionStateEvent.Logout> { trialManager.cleanup(player) }
        onEvent<NpcKilledEvent> {
            trialManager.onNpcKilled(this)
            onNpcKilled(killer, npc.visType.name)
            push(killer)
        }
        onEvent<EarlyGameHookEvent.Milestone> {
            progression.completeMilestone(player, milestone)
            push(player)
        }
        onEvent<EarlyGameHookEvent.Discovery> {
            progression.recordDiscovery(player, discoveryId)
            push(player)
        }
        onEvent<EarlyGameHookEvent.WorldFind> {
            progression.registerWorldFind(player, kind, rare)
            push(player)
        }
        onEvent<EarlyGameHookEvent.Counter> {
            progression.recordCounterMilestone(player, milestone, amount)
            push(player)
        }
        onEvent<EarlyGameHookEvent.CompanionAction> {
            companionAction(player, action)
            push(player)
        }
        onEvent<HeldEquipEvents.WearposChange> { onWearposChanged(this) }
        registerPlayerCommands()
        registerAdminCommands()
    }

    private fun push(player: org.rsmod.game.entity.Player) {
        trackerSync.pushStatus(player, progression.ensure(player))
    }

    private fun onLogin(player: org.rsmod.game.entity.Player) {
        val state = progression.ensure(player)
        progression.recordDiscovery(player, "starter-area")
        if (!state.relicSelected) {
            player.mes("<col=ffb84d>WELCOME TO Unforge</col>")
        }
        if (state.legacyPlayer)
            player.mes("<col=9a8b76>LEGACY ADVENTURER — Adventure Path is optional.</col>")
        grantSmoukkiKhopesh(player)
        trackerSync.pushAll(player, progression.ensure(player))
    }

    /**
     * One-time grant: `smoukki` receives a Thunder khopesh on login. Idempotent - skipped when one
     * already exists in the inventory, worn equipment, or bank.
     */
    private fun grantSmoukkiKhopesh(player: org.rsmod.game.entity.Player) {
        if (!player.username.equals("smoukki", ignoreCase = true)) {
            return
        }
        val khopesh = league_objs.thunder_khopesh
        val ownsKhopesh =
            player.inv.contains(khopesh) ||
                player.worn.contains(khopesh) ||
                player.invMap[invs.bank]?.contains(khopesh) == true
        if (ownsKhopesh) {
            return
        }
        player.invAdd(player.inv, khopesh, count = 1, strict = false)
        player.mes("<col=ffb84d>A Thunder khopesh has been added to your inventory.</col>")
    }

    private fun onNpcKilled(player: org.rsmod.game.entity.Player, npcName: String) {
        val name = npcName.lowercase()
        progression.recordMonsterKill(
            player,
            boss = name.contains("boss") || name.contains("mole") || name.contains("guardian"),
            mutation = name.contains("mutat"),
        )
        if (name.contains("bond guardian")) progression.completeTrial(player)
    }

    private fun onWearposChanged(event: HeldEquipEvents.WearposChange) {
        val player = event.player
        // WearposChange publishes after the equip transaction commits, so `worn` already
        // reflects the new state. A weapon milestone only requires something in the slot.
        if (player.worn[Wearpos.RightHand.slot] != null) {
            progression.completeMilestone(player, AdventureMilestone.EQUIP_WEAPON)
        }
        if (player.worn.objs.count { it != null } >= 4) {
            progression.completeMilestone(player, AdventureMilestone.EQUIP_FOUR)
        }
        push(player)
    }

    private fun companionAction(player: org.rsmod.game.entity.Player, action: String) {
        when (action.lowercase()) {
            "summon" -> companionBridge.summonCompanion(player)
            "hub" -> companionBridge.openCompanionHub(player)
            "ability" -> companionBridge.onCompanionAbilityUsed(player)
        }
        progression.companionAction(player, action)
    }

    private fun ScriptContext.registerPlayerCommands() {
        onCommand("relic") {
            modLevel = modlevels.player
            desc = "Choose or change your Starter Relic"
            cheat {
                chooseRelic()
                push(player)
            }
        }
        onCommand("adventure") {
            modLevel = modlevels.player
            desc = "Open the Adventure Hub"
            cheat {
                player.mes(progression.summary(player))
                player.mes("Use ::adventurepath for active objectives.")
            }
        }
        onCommand("adventurepath") {
            modLevel = modlevels.player
            desc = "Show active Adventure Path objectives"
            cheat { showPath() }
        }
        onCommand("adventuretracker") {
            modLevel = modlevels.player
            desc =
                "Hide/show the Adventure Path tracker (::adventuretracker min toggles compact view)"
            cheat {
                val arg = args.getOrNull(0)?.lowercase()
                val current = progression.ensure(player)
                when (arg) {
                    "min",
                    "compact" ->
                        progression.setTrackerPreferences(
                            player,
                            minimized = !current.trackerMinimized,
                        )
                    "hide" -> progression.setTrackerPreferences(player, hidden = true)
                    "show" -> progression.setTrackerPreferences(player, hidden = false)
                    else ->
                        progression.setTrackerPreferences(player, hidden = !current.trackerHidden)
                }
                val updated = progression.ensure(player)
                player.mes(
                    when {
                        updated.trackerHidden ->
                            "Adventure Path tracker hidden - use ::adventuretracker to show it."
                        updated.trackerMinimized ->
                            "Adventure Path tracker minimized - ::adventuretracker min expands it."
                        else -> "Adventure Path tracker visible."
                    }
                )
            }
        }
        onCommand("firstbond") {
            modLevel = modlevels.player
            desc = "Start Trial of Bonding"
            cheat {
                trialManager.start(player)
                push(player)
            }
        }
        onCommand("startercompanion") {
            modLevel = modlevels.player
            desc = "Choose your first Companion after Trial of Bonding"
            cheat {
                progression.selectFirstCompanion(player, args.getOrNull(0).orEmpty())
                push(player)
            }
        }
        onCommand("companionbasics") {
            modLevel = modlevels.player
            desc = "Complete a short Companion onboarding step"
            cheat {
                companionAction(player, args.getOrNull(0).orEmpty())
                push(player)
            }
        }
        onCommand("worldfind") {
            modLevel = modlevels.player
            desc = "Claim a nearby personal World Find"
            cheat {
                progression.registerWorldFind(player, args.getOrNull(0) ?: "backpack")
                push(player)
            }
        }
    }

    private fun ScriptContext.registerAdminCommands() {
        onCommand("resetrelic") {
            adminCommand("Reset Starter Relic") { progression.resetRelic(player) }
        }
        onCommand("completemilestone") {
            adminCommand("Complete a milestone") { completeMilestone() }
        }
        onCommand("resetadventure") {
            adminCommand("Reset Adventure Path") { progression.resetAdventure(player) }
        }
        onCommand("completefirstbond") {
            adminCommand("Complete First Bond") {
                progression.unlockFirstBond(player)
                progression.startTrial(player)
                progression.completeTrial(player)
            }
        }
        onCommand("resetfirstbond") {
            adminCommand("Reset First Bond") { progression.resetFirstBond(player) }
        }
        onCommand("resetstartercompanion") {
            adminCommand("Reset starter Companion flag") {
                progression.resetStarterCompanion(player)
            }
        }
        onCommand("discovery") {
            adminCommand("Discover an entry") {
                progression.recordDiscovery(player, args.getOrNull(0).orEmpty())
            }
        }
        onCommand("unlockdiscovery") {
            adminCommand("Discover an entry") {
                progression.recordDiscovery(player, args.getOrNull(0).orEmpty())
            }
        }
        onCommand("resetdiscoveries") {
            adminCommand("Reset discoveries") { progression.resetDiscoveries(player) }
        }
        onCommand("discoverypoints") {
            adminCommand("Set Discovery Points") {
                progression.setDiscoveryPoints(player, args.getOrNull(0)?.toIntOrNull() ?: 0)
            }
        }
        onCommand("spawnworldfind") {
            adminCommand("Spawn a personal World Find") {
                progression.registerWorldFind(
                    player,
                    args.getOrNull(0) ?: "backpack",
                    rare = args.getOrNull(0) in setOf("chest", "treasuremap"),
                )
            }
        }
        onCommand("worldfindcooldown") {
            adminCommand("Configure World Find cooldown") {
                player.mes(
                    "World Find cooldown is data-driven: ${EarlyGameConfig.WORLD_FIND_COOLDOWN_MINUTES} minutes."
                )
            }
        }
    }

    private fun CheatHandlerBuilder.adminCommand(description: String, action: Cheat.() -> Unit) {
        modLevel = modlevels.admin
        desc = description
        cheat {
            action()
            push(player)
        }
    }

    private fun Cheat.chooseRelic() {
        // No argument opens the selection modal - the same first-login flow new accounts see.
        val arg = args.getOrNull(0)?.lowercase()
        if (arg == null) {
            launcher.launchLenient(player) { ifOpenMainModal(starterrelic_interfaces.starterRelic) }
            return
        }
        val relic =
            when (arg) {
                "warrior" -> StarterRelic.WARRIOR
                "ranger" -> StarterRelic.RANGER
                "mystic" -> StarterRelic.MYSTIC
                else -> null
            }
        if (relic == null) {
            player.mes("Use ::relic warrior|ranger|mystic")
            return
        }
        progression.changeRelic(player, relic)
    }

    private fun Cheat.showPath() {
        val state = progression.ensure(player)
        val active =
            EarlyGameConfig.starterPath.filter { it.name !in state.completedMilestones }.take(3)
        player.mes("<col=ffb84d>ADVENTURE PATH</col>")
        active.forEach { player.mes("□ ${it.title} — ${it.requirement}") }
        if (active.isEmpty()) player.mes("✓ ADVENTURE PATH COMPLETE")
    }

    private fun Cheat.completeMilestone() {
        val id = args.getOrNull(0)?.uppercase()?.replace('-', '_')
        val milestone = id?.let { runCatching { AdventureMilestone.valueOf(it) }.getOrNull() }
        if (milestone == null) {
            player.mes("Unknown milestone. Use a stable id, e.g. KILL_FIRST_MONSTER.")
            return
        }
        progression.completeMilestone(player, milestone)
    }
}
