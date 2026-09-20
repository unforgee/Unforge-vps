package org.rsmod.api.death

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Published once per npc death that has an attributed player killer, after normal drops have been
 * rolled. Used by systems like the solo boss arena to advance rounds and award perks.
 */
public class NpcKilledEvent(
    public val npc: Npc,
    public val killer: Player,
    /** True only for the canonical attributed death path that has a combat reward. */
    public val validCombatKill: Boolean = true,
) : UnboundEvent
