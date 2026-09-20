package org.rsmod.game.ironman

import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj

/**
 * Central, server-authoritative policy for all game-mode restrictions.
 *
 * This object is intentionally a set of pure functions so every system (obj take, bank, shops,
 * item-on-player, future trade/GE/reward handlers) can call it without extra dependencies. Nothing
 * client-side is ever trusted: all checks read server-side [Player] and [Obj] state.
 */
public object IronmanPolicy {
    /** A player that has not selected a mode may not perform any gated gameplay action. */
    public fun isSelectionPending(player: Player): Boolean = !player.gameModeSelected

    /**
     * The mode used for restriction checks. Unselected accounts are treated as fully restricted
     * until the server-side selection is persisted.
     */
    public fun effectiveMode(player: Player): GameMode? =
        if (player.gameModeSelected) player.gameMode else null

    private fun isRestricted(player: Player): Boolean =
        effectiveMode(player)?.isIronmanRestricted ?: true

    private fun isUltimate(player: Player): Boolean = effectiveMode(player)?.isUltimate == true

    /** True when both players share the same group-scoped observer id (same ironman group). */
    public fun sameGroup(a: Player, b: Player): Boolean {
        val modeA = effectiveMode(a)
        val modeB = effectiveMode(b)
        if (modeA != GameMode.GROUP_IRONMAN || modeB != GameMode.GROUP_IRONMAN) {
            return false
        }
        val groupA = a.groupId ?: return false
        val groupB = b.groupId ?: return false
        return groupA == groupB
    }

    public fun canTradeWith(player: Player, target: Player): PolicyResult {
        if (isSelectionPending(player) || isSelectionPending(target)) {
            return PolicyResult.Deny(MSG_SELECTION_PENDING)
        }
        val mode = player.gameMode ?: return PolicyResult.Deny(MSG_SELECTION_PENDING)
        return when (mode) {
            GameMode.REGULAR -> PolicyResult.Allow
            GameMode.GROUP_IRONMAN ->
                if (sameGroup(player, target)) {
                    PolicyResult.Allow
                } else {
                    PolicyResult.Deny(MSG_GROUP_EXTERNAL_TRADE)
                }
            else -> PolicyResult.Deny(MSG_IRONMAN_TRADE)
        }
    }

    /** Using an item on another player is a potential transfer path; irons may not do it. */
    public fun canUseItemOn(player: Player, target: Player): PolicyResult =
        canTradeWith(player, target)

    /**
     * Ground-obj take rule:
     * - unowned objs (world spawns, `fromServer`) are takeable by everyone;
     * - owned objs are only takeable by their original owner. For group ironman, `ownerId` equals
     *   the group-scoped `observerUUID`, so group members share each other's drops.
     *
     * Applies to player-dropped items and other players' NPC kill drops alike.
     */
    public fun canTakeObj(player: Player, obj: Obj): PolicyResult {
        if (isSelectionPending(player)) {
            return PolicyResult.Deny(MSG_SELECTION_PENDING)
        }
        val owner = obj.nullableOwnerId ?: return PolicyResult.Allow
        if (!isRestricted(player)) {
            return PolicyResult.Allow
        }
        return if (owner == player.observerUUID) {
            PolicyResult.Allow
        } else {
            PolicyResult.Deny(MSG_OTHER_PLAYER_DROP)
        }
    }

    public fun canUseBank(player: Player): PolicyResult {
        if (isSelectionPending(player)) {
            return PolicyResult.Deny(MSG_SELECTION_PENDING)
        }
        return if (isUltimate(player)) {
            PolicyResult.Deny(MSG_ULTIMATE_BANK)
        } else {
            PolicyResult.Allow
        }
    }

    public fun canUseGroupStorage(player: Player): PolicyResult {
        if (isSelectionPending(player)) {
            return PolicyResult.Deny(MSG_SELECTION_PENDING)
        }
        return if (effectiveMode(player) == GameMode.GROUP_IRONMAN && player.groupId != null) {
            PolicyResult.Allow
        } else {
            PolicyResult.Deny(MSG_GROUP_STORAGE_DENIED)
        }
    }

    /**
     * NPC shops are allowed for every mode, but restricted modes may only buy from the shop's
     * initial (default) stock - never overstock sold in by other players.
     *
     * @return the maximum count the player may buy in this transaction.
     */
    public fun shopBuyCap(player: Player, currentStock: Int, initialStock: Int, request: Int): Int {
        if (!isRestricted(player)) {
            return minOf(currentStock, request)
        }
        return minOf(currentStock, initialStock, request)
    }

    public fun canUseGrandExchange(player: Player): PolicyResult =
        if (isSelectionPending(player)) {
            PolicyResult.Deny(MSG_SELECTION_PENDING)
        } else {
            // No Grand Exchange exists on this server yet; this is the gate any future
            // implementation must call before accepting an offer from a restricted account.
            if (isRestricted(player)) PolicyResult.Deny(MSG_IRONMAN_GE) else PolicyResult.Allow
        }

    public fun canReceiveSharedReward(player: Player): PolicyResult =
        if (isRestricted(player)) {
            PolicyResult.Deny(MSG_SHARED_REWARD)
        } else {
            PolicyResult.Allow
        }

    public fun canJoinGroup(player: Player): PolicyResult =
        if (isSelectionPending(player)) {
            PolicyResult.Deny(MSG_SELECTION_PENDING)
        } else if (effectiveMode(player) != GameMode.GROUP_IRONMAN) {
            PolicyResult.Deny(MSG_GROUP_WRONG_MODE)
        } else {
            PolicyResult.Allow
        }

    /** Human-readable summary used by `::gamemode`. */
    public fun describe(player: Player): String {
        if (isSelectionPending(player)) {
            return "You have not chosen a game mode yet."
        }
        val mode = player.gameMode ?: return "You have not chosen a game mode yet."
        val sb = StringBuilder("Game mode: ${mode.displayName}")
        if (mode.isHardcore) {
            sb.append(
                " | Hardcore: ${player.hardcoreStatus.dbName} " +
                    "(deaths: ${player.hardcoreDeathCount})"
            )
        }
        if (mode.isGroup) {
            val group =
                player.groupId?.let { "#$it (${player.groupRank?.dbName ?: "?"})" } ?: "none"
            sb.append(" | Group: $group")
        }
        return sb.toString()
    }

    public sealed class PolicyResult {
        public data object Allow : PolicyResult()

        public data class Deny(val message: String) : PolicyResult()

        public val allowed: Boolean
            get() = this is Allow

        public val denialMessage: String?
            get() = (this as? Deny)?.message
    }

    public const val MSG_SELECTION_PENDING: String = "You must choose a game mode first."
    public const val MSG_IRONMAN_TRADE: String =
        "Ironman-tilassa et voi käydä kauppaa muiden pelaajien kanssa."
    public const val MSG_GROUP_EXTERNAL_TRADE: String =
        "Group Ironman voi jakaa tavaroita vain ryhmän jäsenten kanssa."
    public const val MSG_OTHER_PLAYER_DROP: String =
        "Et voi poimia toisen pelaajan pudottamaa tai tappamaa esinettä."
    public const val MSG_ULTIMATE_BANK: String =
        "Ultimate Ironman -tilassa pankki ei ole käytettävissä."
    public const val MSG_GROUP_STORAGE_DENIED: String =
        "Ryhmävarasto on vain Group Ironman -ryhmän jäsenille."
    public const val MSG_IRONMAN_GE: String = "Ironman-tilassa et voi käyttää Grand Exchangea."
    public const val MSG_SHARED_REWARD: String =
        "Tämä palkinto on jaettu tavalla, jota pelimuotosi ei salli."
    public const val MSG_GROUP_WRONG_MODE: String =
        "Vain Group Ironman -pelaajat voivat liittyä ryhmään."
    public const val MSG_SHOP_OVERSTOCK: String =
        "Ironman-tilassa et voi ostaa esineitä, jotka muut pelaajat ovat myyneet kauppaan."
}
