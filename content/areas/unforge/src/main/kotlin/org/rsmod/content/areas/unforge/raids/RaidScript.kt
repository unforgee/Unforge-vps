package org.rsmod.content.areas.unforge.raids

import jakarta.inject.Inject
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Wires the shared [RaidService] engine to commands, interface buttons and game events.
 *
 * `::tob` starts a raid immediately, `::cox` teleports to the public Chambers entrance, `::raid`
 * opens the lobby interface, and the dev party commands (`::raidinvite`, `::raidjoin`,
 * `::raidleave`, `::raidstart`, `::raidparty`, `::raidstatus`) manage lobbies.
 */
class RaidScript
@Inject
constructor(
    private val service: RaidService,
    private val launcher: ProtectedAccessLauncher,
    private val pvmPoints: PvmPoints,
    private val eventBus: EventBus,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("tob") {
            desc = "Enter the Theatre of Blood (optional: expert)"
            cheat { startFromArgs(this, RaidKind.THEATRE) }
        }
        onCommand("cox") {
            desc = "Teleport to the Chambers of Xeric entrance"
            cheat {
                // The old command started a generated raid instance. If its map template was not
                // loaded, players appeared in an empty area. Keep instance creation behind the
                // explicit raid commands and make ::cox a reliable entrance teleport.
                PathingEntityCommon.telejump(player, collision, COX_ENTRANCE)
            }
        }
        onCommand("raid") {
            desc = "Raid lobby: ::raid [tob|cox] [expert], or no args for the menu"
            cheat { raidCommand(this) }
        }
        onCommand("raidstart") {
            desc = "Start a raid with your party using the lobby selection"
            cheat {
                launcher.launch(player) {
                    val sel = service.lobbySelection(player)
                    service.run { startRaid(sel.kind, sel.difficulty) }
                }
            }
        }
        onCommand("raidparty") {
            desc = "Form a raid party"
            cheat { service.createParty(player) }
        }
        onCommand("raidinvite") {
            desc = "Invite a player to your raid party: ::raidinvite <name>"
            invalidArgs = "Usage: ::raidinvite <player name>"
            cheat {
                args.firstOrNull()?.let { service.invite(player, it) }
                    ?: player.mes("<col=ff3333>Usage: ::raidinvite <player name></col>")
            }
        }
        onCommand("raidjoin") {
            desc = "Join a raid party you were invited to: ::raidjoin <leader>"
            invalidArgs = "Usage: ::raidjoin <leader name>"
            cheat {
                args.firstOrNull()?.let { service.joinParty(player, it) }
                    ?: player.mes("<col=ff3333>Usage: ::raidjoin <leader name></col>")
            }
        }
        onCommand("raidleave") {
            desc = "Leave your raid party or exit the raid"
            cheat {
                if (service.runOf(player) != null) {
                    service.leaveRaid(player)
                } else {
                    service.leaveParty(player)
                }
            }
        }
        onCommand("raidstatus") {
            desc = "Show your raid status"
            cheat {
                val run = service.runOf(player)
                if (run != null) {
                    player.mes(service.statusText(run))
                } else {
                    val party = service.partyOf(player)
                    if (party != null) {
                        player.mes(
                            "<col=ffb84d>Raid party (${party.size()}/${RaidParty.MAX_SIZE}): " +
                                party.members.joinToString(", ") { m -> m.username } +
                                "</col>"
                        )
                    } else {
                        player.mes("<col=ffb84d>You are not in a raid or raid party.</col>")
                    }
                }
            }
        }

        onEvent<NpcKilledEvent> { service.onNpcKilled(this) }
        onEvent<SessionStateEvent.Logout> { service.onLogout(player) }

        // Exit portals are attackable - accept the first ops so a click always leaves.
        onOpNpc1(RaidNpcs.exitPortal) { service.leaveRaid(player) }
        onOpNpc2(RaidNpcs.exitPortal) { service.leaveRaid(player) }
        onOpNpc3(RaidNpcs.exitPortal) { service.leaveRaid(player) }

        onOpLoc1(RaidLocs.tobChestClosed) { service.run { claimChest(it.loc.coords) } }
        onOpLoc1(RaidLocs.coxChestClosed) { service.run { claimChest(it.loc.coords) } }

        // The Great Olm's head resists damage while its hands live.
        onModifyNpcHit(RaidNpcs.olmHead) { hit.damage = service.modifyNpcHit(npc, hit.damage) }

        onIfOpen(raid_lobby_interfaces.lobby) { onLobbyOpen(player) }
        onIfClose(raid_lobby_interfaces.lobby) {}
        onIfModalButton(raid_lobby_components.tob) { select(RaidKind.THEATRE, null) }
        onIfModalButton(raid_lobby_components.cox) { select(RaidKind.CHAMBERS, null) }
        onIfModalButton(raid_lobby_components.normal) { select(null, RaidDifficulty.NORMAL) }
        onIfModalButton(raid_lobby_components.expert) { select(null, RaidDifficulty.EXPERT) }
        onIfModalButton(raid_lobby_components.bank) { player.openBank(eventBus) }
        onIfModalButton(raid_lobby_components.close) { ifClose() }
        onIfModalButton(raid_lobby_components.start) {
            ifClose()
            val sel = service.lobbySelection(player)
            service.run { startRaid(sel.kind, sel.difficulty) }
        }
    }

    private companion object {
        val COX_ENTRANCE = CoordGrid(1254, 3558, 0)
    }

    private fun startFromArgs(cheat: Cheat, kind: RaidKind) {
        val difficulty =
            if (cheat.args.firstOrNull()?.equals("expert", ignoreCase = true) == true) {
                RaidDifficulty.EXPERT
            } else {
                RaidDifficulty.NORMAL
            }
        service.lobbySelection(cheat.player).kind = kind
        service.lobbySelection(cheat.player).difficulty = difficulty
        launcher.launch(cheat.player) { service.run { startRaid(kind, difficulty) } }
    }

    private fun raidCommand(cheat: Cheat) {
        var kind: RaidKind? = null
        var difficulty: RaidDifficulty? = null
        for (arg in cheat.args) {
            when (arg.lowercase()) {
                "tob" -> kind = RaidKind.THEATRE
                "cox" -> kind = RaidKind.CHAMBERS
                "expert" -> difficulty = RaidDifficulty.EXPERT
                "normal" -> difficulty = RaidDifficulty.NORMAL
            }
        }
        val sel = service.lobbySelection(cheat.player)
        kind?.let { sel.kind = it }
        difficulty?.let { sel.difficulty = it }
        if (kind == null && difficulty == null) {
            launcher.launch(cheat.player) { ifOpenMainModal(raid_lobby_interfaces.lobby) }
        } else if (kind != null) {
            launcher.launch(cheat.player) { service.run { startRaid(sel.kind, sel.difficulty) } }
        } else {
            // Difficulty-only arg: open the lobby so the player can pick the raid.
            launcher.launch(cheat.player) { ifOpenMainModal(raid_lobby_interfaces.lobby) }
        }
    }

    private fun onLobbyOpen(player: Player) {
        player.ifSetEvents(raid_lobby_components.tob, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.cox, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.normal, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.expert, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.start, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.bank, -1..-1, IfEvent.Op1)
        player.ifSetEvents(raid_lobby_components.close, -1..-1, IfEvent.Op1)
        refreshLobby(player)
    }

    private fun ProtectedAccess.select(kind: RaidKind?, difficulty: RaidDifficulty?) {
        val sel = service.lobbySelection(player)
        kind?.let { sel.kind = it }
        difficulty?.let { sel.difficulty = it }
        refreshLobby(player)
    }

    private fun refreshLobby(player: Player) {
        val sel = service.lobbySelection(player)
        player.ifSetText(
            raid_lobby_components.selection,
            "${sel.kind.displayName} - ${sel.difficulty.displayName}",
        )
        val party = service.partyOf(player)
        val partyText =
            if (party == null) {
                "Solo | PvM points: ${pvmPoints.balance(player)}"
            } else {
                "Party ${party.size()}/${RaidParty.MAX_SIZE}: " +
                    party.members.joinToString(", ") { it.username } +
                    " | PvM points: ${pvmPoints.balance(player)}"
            }
        player.ifSetText(raid_lobby_components.partyStatus, partyText)
    }
}
