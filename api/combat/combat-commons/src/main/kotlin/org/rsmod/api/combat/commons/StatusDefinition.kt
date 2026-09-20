package org.rsmod.api.combat.commons

/**
 * Static rules describing how a [StatusEffect] behaves once applied to an entity.
 *
 * @property effect The status this definition governs.
 * @property tickInterval Cycles between periodic procs (e.g. poison damage). `0` marks a purely
 *   passive effect that only expires over time.
 * @property stackPolicy How repeated applications of the same effect are resolved.
 * @property stackCap Maximum stack count reachable through [StackPolicy.INCREMENT_STACKS].
 * @property clearOnDeath Whether the status is removed when the entity dies.
 * @property clearOnLogout Whether the status is removed when the entity logs out.
 * @property potencyDecay Potency reduction applied after each periodic proc (e.g. poison weakening
 *   by 1 damage per tick). `0` keeps potency constant.
 * @property potencyGrowth Potency increase applied after each periodic proc (e.g. venom growing
 *   stronger every tick). Applied after [potencyDecay].
 * @property maxPotency Upper bound for potency reached through [potencyGrowth]; `0` means uncapped.
 */
public data class StatusDefinition(
    public val effect: StatusEffect,
    public val tickInterval: Int,
    public val stackPolicy: StackPolicy,
    public val stackCap: Int = 1,
    public val clearOnDeath: Boolean = true,
    public val clearOnLogout: Boolean = false,
    public val potencyDecay: Int = 0,
    public val potencyGrowth: Int = 0,
    public val maxPotency: Int = 0,
) {
    init {
        require(tickInterval >= 0) { "Status tick interval must not be negative" }
        require(stackCap > 0) { "Status stack cap must be positive" }
        require(potencyDecay >= 0) { "Status potency decay must not be negative" }
        require(potencyGrowth >= 0) { "Status potency growth must not be negative" }
        require(maxPotency >= 0) { "Status max potency must not be negative" }
    }
}
