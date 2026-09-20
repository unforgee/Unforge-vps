package org.rsmod.api.ironman

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Game mode commands.
 *
 * `::gamemode` is a read-only report for normal players. `::setgamemode` is the only path that can
 * change an existing mode and is gated behind [modlevels.admin] with an audit row written by
 * [GameModeService]. The `::group*`/`::g*` commands drive Group Ironman membership and the shared
 * group storage and are available to all players (the service validates modes).
 */
public class IronmanCommandScript
@Inject
constructor(
    private val gameModeService: GameModeService,
    private val groupIronman: GroupIronmanService,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("gamemode") {
            desc = "Show your game mode and status"
            cheat { player.mes(IronmanPolicy.describe(player)) }
        }

        onCommand("setgamemode") {
            desc = "Change a player's game mode (audited)"
            modLevel = modlevels.admin
            invalidArgs = "Use as ::setgamemode playerName mode [reason]"
            cheat(::setGameMode)
        }

        onCommand("groupcreate") {
            desc = "Create an ironman group"
            invalidArgs = "Use as ::groupcreate groupName"
            cheat(::groupCreate)
        }
        onCommand("groupinvite") {
            desc = "Invite a player to your group"
            invalidArgs = "Use as ::groupinvite playerName"
            cheat(::groupInvite)
        }
        onCommand("groupaccept") {
            desc = "Accept a pending group invitation"
            cheat(::groupAccept)
        }
        onCommand("groupleave") {
            desc = "Leave your ironman group"
            cheat(::groupLeave)
        }
        onCommand("groupkick") {
            desc = "Remove a member from your group"
            invalidArgs = "Use as ::groupkick playerName"
            cheat(::groupKick)
        }
        onCommand("gstorage") {
            desc = "List group storage contents"
            cheat { groupIronman.listStorage(player) }
        }
        onCommand("gdeposit") {
            desc = "Deposit an inventory item into group storage"
            invalidArgs = "Use as ::gdeposit invSlot count"
            cheat(::gDeposit)
        }
        onCommand("gwithdraw") {
            desc = "Withdraw an item from group storage"
            invalidArgs = "Use as ::gwithdraw storageSlot count"
            cheat(::gWithdraw)
        }
    }

    private fun setGameMode(cheat: Cheat) =
        with(cheat) {
            val mode = GameMode.fromDbName(args[1].uppercase())
            if (mode == null) {
                player.mes(
                    "Unknown mode '${args[1]}'. Valid: " +
                        GameMode.entries.joinToString { it.dbName }
                )
                return
            }
            val target = playerList.firstOrNull { it.username.equals(args[0], ignoreCase = true) }
            if (target == null) {
                player.mes("Player '${args[0]}' is not online.")
                return
            }
            val reason = args.drop(2).joinToString(" ").ifBlank { "admin command" }
            gameModeService.adminChangeGameMode(player, target, mode, reason)
            player.mes("Changed ${target.username}'s game mode to ${mode.displayName} (audited).")
            target.mes(
                "Your game mode has been changed to ${mode.displayName} by an administrator."
            )
        }

    private fun groupCreate(cheat: Cheat) =
        with(cheat) {
            val name = args.joinToString(" ").take(MAX_GROUP_NAME)
            groupIronman.createGroup(player, name) { player.mes(it) }
        }

    private fun groupInvite(cheat: Cheat) =
        with(cheat) {
            val target = playerList.firstOrNull { it.username.equals(args[0], ignoreCase = true) }
            if (target == null) {
                player.mes("Player '${args[0]}' is not online.")
                return
            }
            groupIronman.invite(player, target) { player.mes(it) }
        }

    private fun groupAccept(cheat: Cheat) =
        with(cheat) { groupIronman.acceptInvite(player) { player.mes(it) } }

    private fun groupLeave(cheat: Cheat) =
        with(cheat) { groupIronman.leaveGroup(player) { player.mes(it) } }

    private fun groupKick(cheat: Cheat) =
        with(cheat) {
            val target = playerList.firstOrNull { it.username.equals(args[0], ignoreCase = true) }
            if (target == null) {
                player.mes("Player '${args[0]}' is not online.")
                return
            }
            groupIronman.kickMember(player, target) { player.mes(it) }
        }

    private fun gDeposit(cheat: Cheat) =
        with(cheat) {
            groupIronman.deposit(player, args[0].toInt(), args[1].toInt()) { player.mes(it) }
        }

    private fun gWithdraw(cheat: Cheat) =
        with(cheat) {
            groupIronman.withdraw(player, args[0].toInt(), args[1].toInt()) { player.mes(it) }
        }

    private companion object {
        private const val MAX_GROUP_NAME = 32
    }
}
