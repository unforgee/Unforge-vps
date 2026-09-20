package org.rsmod.content.areas.unforge.donator

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Centralized renderer for the two independent rank badges shown before a player name: `STAFF ->
 * DONATOR -> NAME` (e.g. `<img=2><img=63>Jari`).
 *
 * Staff rank stays on the `modLevel`/`clientCode` system and donator rank on the `cw_donator_tier`
 * varp - neither system leaks into the other, and donator badges never grant staff permissions.
 *
 * Two delivery paths are kept in sync:
 * - [Player.appearance]`[namePrefix]` carries the `<img>` tags into overhead names and right-click
 *   menus ("Follow", "Trade with", ...), which the client renders verbatim.
 * - [Player.chatBadge] feeds the public-chat `modicon` byte (see `chatModIcon` in api:net), where a
 *   combined staff+donator sprite is sent when the player holds both ranks.
 *
 * The icon indices are part of the client contract documented on `constants.rankicon_donator_base`:
 * donator badges at 60..66, combined pmod/jmod badges at 67..80, spliced into the client's
 * `modIcons` array by `ChatIconManager.installUnforgeRankIcons`.
 */
object RankIconRenderer {
    /**
     * Mod-icons index of the staff crown, or `-1` for players without a staff rank.
     *
     * [Player.modLevel] is `lateinit` - it is assigned during account load before
     * `SessionStateEvent.Login` on live logins, but test harness players may not have one yet.
     */
    fun staffIcon(player: Player): Int {
        if (!player.isModLevelAssigned) {
            return -1
        }
        return player.modLevel.clientCode.takeIf { it != constants.mod_clientcode_player } ?: -1
    }

    /** Mod-icons index of the donator badge, or `-1` for non-donators. */
    fun donatorIcon(player: Player): Int =
        if (player.donatorTier.isDonator) {
            constants.rankicon_donator_base + player.donatorTier.ordinal - 1
        } else {
            -1
        }

    /** Ordered `<img>` tags for the name prefix: staff icon first, then the donator badge. */
    fun namePrefix(player: Player): String? {
        val prefix = buildString {
            staffIcon(player).takeIf { it >= 0 }?.let { append("<img=").append(it).append('>') }
            donatorIcon(player).takeIf { it >= 0 }?.let { append("<img=").append(it).append('>') }
        }
        return prefix.ifEmpty { null }
    }

    /**
     * Recomputes both badges and pushes them to the client. Call after login and after any
     * donator-tier or staff-rank change; assigning [namePrefix] flags the appearance for a rebuild
     * so nearby players pick the new icons up on the next player-info cycle.
     */
    fun sync(player: Player) {
        player.chatBadge = donatorIcon(player)
        val prefix = namePrefix(player)
        if (player.appearance.namePrefix != prefix) {
            player.appearance.namePrefix = prefix
        }
    }
}

class UnforgeRankIcons @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { RankIconRenderer.sync(player) }
    }
}
