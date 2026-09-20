package org.rsmod.api.death

import org.rsmod.api.player.protect.ProtectedAccess

/**
 * Extension point invoked when a player reaches zero hitpoints, before the standard death sequence
 * runs.
 *
 * Hooks are consulted in registration order; the first hook that returns `true` consumes the death
 * and the standard sequence (respawn, hardcore demotion, item loss) is skipped entirely. This is
 * how non-player-owned player entities such as companion bots implement their own death lifecycle
 * instead of being teleported to the respawn point.
 */
public fun interface PlayerDeathHook {
    /**
     * Handles the death of [ProtectedAccess.player].
     *
     * @return `true` when the death was consumed and the standard sequence must not run; `false` to
     *   let the next hook (or the standard sequence) handle it.
     */
    public suspend fun death(access: ProtectedAccess): Boolean
}
