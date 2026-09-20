package org.rsmod.content.other.earlygame

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** Small integration contract for systems that are being merged in parallel. */
public sealed class EarlyGameHookEvent(public val player: Player) : UnboundEvent {
    public class Milestone(player: Player, public val milestone: AdventureMilestone) :
        EarlyGameHookEvent(player)

    public class Discovery(player: Player, public val discoveryId: String) :
        EarlyGameHookEvent(player)

    public class WorldFind(
        player: Player,
        public val kind: String,
        public val rare: Boolean = false,
    ) : EarlyGameHookEvent(player)

    public class Counter(
        player: Player,
        public val milestone: AdventureMilestone,
        public val amount: Int = 1,
    ) : EarlyGameHookEvent(player)

    public class CompanionAction(player: Player, public val action: String) :
        EarlyGameHookEvent(player)
}
