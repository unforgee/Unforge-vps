package org.rsmod.api.npc.events

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder

public class NpcHitEvents {
    public data class Modify(
        public val npc: Npc,
        public val hit: HitBuilder,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    public data class Impact(
        public val npc: Npc,
        public val hit: Hit,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    /** Wildcard seams for systems that apply one server rule to every npc type. */
    public data class GlobalModify(public val npc: Npc, public val hit: HitBuilder) : UnboundEvent

    public data class GlobalImpact(
        public val npc: Npc,
        public val hit: Hit,
        /** Set by a listener to request one bounded non-recursive secondary hit. */
        public var secondaryDamage: Int = 0,
    ) : UnboundEvent
}
