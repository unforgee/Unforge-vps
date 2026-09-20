package org.rsmod.api.commons.gameplay

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * Claim-once reward helpers backed by perm varp bits, so rewards survive logout/reconnect and can
 * never be granted twice (diaries, one-off chests, unlock flags).
 *
 * Use a dedicated bitmask varp per reward set; `bit` selects the flag within it.
 */
public fun Player.rewardClaimed(varp: VarpType, bit: Int): Boolean =
    (vars[varp] ushr bit) and 1 == 1

public fun Player.markRewardClaimed(varp: VarpType, bit: Int) {
    VarPlayerIntMapSetter.set(this, varp, vars[varp] or (1 shl bit))
}

/**
 * Runs [reward] exactly once per player: sets the claim bit first (closing the double-click /
 * duplicate-interaction race) and returns `false` without running it if already claimed.
 */
public inline fun Player.claimRewardOnce(
    varp: VarpType,
    bit: Int,
    reward: Player.() -> Unit,
): Boolean {
    if (rewardClaimed(varp, bit)) {
        return false
    }
    markRewardClaimed(varp, bit)
    reward()
    return true
}
