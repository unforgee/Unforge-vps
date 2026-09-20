package org.rsmod.api.combat.commons

/** Passive element metadata for a future attack pipeline. */
public data class AttackElement(public val element: Element = Element.NONE)

/**
 * A single active status on an entity.
 *
 * [duration] counts the remaining cycles until expiry; [potency] carries the effect magnitude (e.g.
 * poison damage per tick); [nextTickDelay] counts down to the next periodic proc.
 */
public data class StatusInstance(
    public val effect: StatusEffect,
    public val duration: Int,
    public val stacks: Int = 1,
    public val stackCap: Int = 1,
    public val potency: Int = 0,
    public val nextTickDelay: Int = 0,
) {
    init {
        require(duration > 0) { "Status duration must be positive" }
        require(stackCap > 0) { "Status stack cap must be positive" }
        require(stacks in 1..stackCap) { "Status stacks must be between 1 and stack cap" }
    }
}
