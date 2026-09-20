package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

/** Global server-side seams for effects that apply to every player hit processor. */
public object PlayerHitEvents {
    /** Published immediately before the standard player hit processor applies damage. */
    public data class GlobalImpact(
        public val player: Player,
        public val hit: Hit,
        public var damage: Int = hit.damage,
    ) : UnboundEvent
}
