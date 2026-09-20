package org.rsmod.api.commons.gameplay

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.type.seq.SeqType
import org.rsmod.map.CoordGrid

/**
 * A loc traversal definition: ladders, staircases, trapdoors, manholes and agility shortcuts.
 *
 * Register the destination for **both directions** (up+down / enter+exit) from the same route table
 * so a one-way traversal can never be shipped by accident. The executor mirrors the behaviour of
 * the existing generic ladder script: animate, one-tick delay, then [telejump].
 *
 * Skill [requirements] use **base** levels (see [StatRequirement]) - boosts do not let a player
 * squeeze through a shortcut they haven't earned.
 */
public data class TraversalRoute(
    /** Absolute destination coord. Compute relative targets via `loc.coords.translate(...)`. */
    public val destination: CoordGrid,
    /** Optional climb/crawl animation; `null` teleports without an anim (safe fallback). */
    public val animation: SeqType? = null,
    /** Ticks to wait before the move lands, matching ladder behaviour (1-2). */
    public val delay: Int = 1,
    public val requirements: List<StatRequirement> = emptyList(),
    public val failMessage: String = "You can't get past that.",
    public val successMessage: String? = null,
)

/**
 * Executes [route] against the interacting player: checks requirements, animates, delays and
 * teleports. Returns `false` when the requirements gate the player out (fail message sent).
 */
public suspend fun ProtectedAccess.traverse(route: TraversalRoute): Boolean {
    if (!player.meetsRequirements(route.requirements)) {
        mes(route.failMessage)
        return false
    }
    route.animation?.let(::anim)
    delay(route.delay)
    telejump(route.destination)
    route.successMessage?.let(::mes)
    return true
}
