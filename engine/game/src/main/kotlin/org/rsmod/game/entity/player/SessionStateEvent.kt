package org.rsmod.game.entity.player

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class SessionStateEvent {
    /** Fired when a player is registered to the player list. */
    public data class Initialize(val player: Player) : UnboundEvent

    /** Fired after [Initialize] during the player login sequence. */
    public data class Login(val player: Player) : UnboundEvent

    /** Fired after [Login] during the player login sequence. */
    public data class EngineLogin(val player: Player, override val id: Long = 0L) : KeyedEvent

    /**
     * Fired during [EngineLogin] after high-priority login packets (varp reset / var transmit) and
     * before low-priority login scripts. Used to open the gameframe so overlay onLoad CS2 (notably
     * magic spellbook 2262) runs with authoritative vars, matching live client login order.
     */
    public data class EngineLoginUi(val player: Player) : UnboundEvent

    /**
     * Fired after the login gameframe (toplevel + overlays) has been opened during [EngineLoginUi].
     * Tutorial Island and other content that needs a live toplevel target (modals, helper overlay)
     * should listen here instead of [EngineLogin].
     */
    public data class EngineLoginReady(val player: Player) : UnboundEvent

    /** Fired before the player's account data is queued for saving. */
    public data class Logout(val player: Player) : UnboundEvent

    /** Fired when a player is unregistered from the player list. */
    public data class Delete(val player: Player) : UnboundEvent
}
