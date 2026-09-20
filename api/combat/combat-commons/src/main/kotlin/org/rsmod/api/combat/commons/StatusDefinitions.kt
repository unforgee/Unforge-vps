package org.rsmod.api.combat.commons

/**
 * Result of advancing a [StatusInstance] by a single game cycle.
 *
 * @property instance The updated instance, or `null` when the status expired.
 * @property damage Potency damage to apply this cycle; `0` when no periodic proc fired.
 */
public data class StatusAdvance(public val instance: StatusInstance?, public val damage: Int)

/** Built-in [StatusDefinition]s and the pure status lifecycle rules used by the status service. */
public object StatusDefinitions {
    private val byEffect: Map<StatusEffect, StatusDefinition> =
        listOf(
                StatusDefinition(
                    effect = StatusEffect.POISON,
                    tickInterval = 30,
                    stackPolicy = StackPolicy.KEEP_STRONGEST,
                    potencyDecay = 1,
                ),
                StatusDefinition(
                    effect = StatusEffect.VENOM,
                    tickInterval = 30,
                    stackPolicy = StackPolicy.KEEP_STRONGEST,
                    potencyGrowth = 2,
                    maxPotency = 20,
                ),
                StatusDefinition(
                    effect = StatusEffect.BLEED,
                    tickInterval = 5,
                    stackPolicy = StackPolicy.INCREMENT_STACKS,
                    stackCap = 5,
                ),
                StatusDefinition(
                    effect = StatusEffect.BURN,
                    tickInterval = 5,
                    stackPolicy = StackPolicy.REFRESH_DURATION,
                ),
                StatusDefinition(
                    effect = StatusEffect.CHILL,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.REFRESH_DURATION,
                ),
                StatusDefinition(
                    effect = StatusEffect.SLOW,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.KEEP_STRONGEST,
                ),
                StatusDefinition(
                    effect = StatusEffect.FROZEN,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.REFRESH_DURATION,
                ),
                StatusDefinition(
                    effect = StatusEffect.VULNERABLE,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.INCREMENT_STACKS,
                    stackCap = 5,
                ),
                StatusDefinition(
                    effect = StatusEffect.WEAKEN,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.INCREMENT_STACKS,
                    stackCap = 5,
                ),
                StatusDefinition(
                    effect = StatusEffect.BLIND,
                    tickInterval = 0,
                    stackPolicy = StackPolicy.REFRESH_DURATION,
                ),
            )
            .associateBy { it.effect }

    init {
        check(byEffect.size == StatusEffect.entries.size) {
            "Every StatusEffect must have a StatusDefinition"
        }
    }

    public operator fun get(effect: StatusEffect): StatusDefinition =
        requireNotNull(byEffect[effect]) { "Missing StatusDefinition for $effect" }

    /**
     * Resolves how an [incoming] application interacts with an [existing] active status of the same
     * effect, following the definition's [StatusDefinition.stackPolicy].
     */
    public fun resolve(
        existing: StatusInstance?,
        incoming: StatusInstance,
        def: StatusDefinition,
    ): StatusInstance {
        if (existing == null) {
            return incoming.copy(stacks = incoming.stacks.coerceIn(1, def.stackCap))
        }
        return when (def.stackPolicy) {
            StackPolicy.KEEP_STRONGEST ->
                when {
                    incoming.potency > existing.potency -> incoming
                    incoming.potency == existing.potency ->
                        existing.copy(
                            duration = maxOf(existing.duration, incoming.duration),
                            nextTickDelay = minNextTickDelay(existing, incoming),
                        )
                    else -> existing
                }
            StackPolicy.REFRESH_DURATION ->
                existing.copy(
                    duration = maxOf(existing.duration, incoming.duration),
                    potency = maxOf(existing.potency, incoming.potency),
                    nextTickDelay = minNextTickDelay(existing, incoming),
                )
            StackPolicy.INCREMENT_STACKS ->
                existing.copy(
                    duration = maxOf(existing.duration, incoming.duration),
                    stacks = (existing.stacks + incoming.stacks).coerceAtMost(def.stackCap),
                    potency = maxOf(existing.potency, incoming.potency),
                    nextTickDelay = minNextTickDelay(existing, incoming),
                )
            StackPolicy.EXTEND_DURATION ->
                existing.copy(duration = existing.duration + incoming.duration)
            StackPolicy.REPLACE -> incoming.copy(stacks = incoming.stacks.coerceIn(1, def.stackCap))
        }
    }

    /**
     * Advances [instance] by a single cycle: decrements the remaining duration, fires a periodic
     * proc when [StatusInstance.nextTickDelay] reaches zero, and applies
     * [StatusDefinition.potencyDecay] to fired procs.
     *
     * Potency never decays below `1` for damage statuses.
     */
    public fun advance(instance: StatusInstance, def: StatusDefinition): StatusAdvance {
        var updated = instance
        var damage = 0
        if (def.tickInterval > 0) {
            val delay = updated.nextTickDelay - 1
            if (delay <= 0) {
                damage = updated.potency
                var potency = updated.potency - def.potencyDecay + def.potencyGrowth
                potency = potency.coerceAtLeast(if (def.potencyDecay > 0) 1 else 0)
                if (def.maxPotency > 0) {
                    potency = potency.coerceAtMost(def.maxPotency)
                }
                updated = updated.copy(potency = potency, nextTickDelay = def.tickInterval)
            } else {
                updated = updated.copy(nextTickDelay = delay)
            }
        }
        // `StatusInstance` requires a positive duration, so the expiry check must happen
        // before decrementing. A proc on the final cycle still emits its damage.
        if (instance.duration <= 1) {
            return StatusAdvance(instance = null, damage = damage)
        }
        return StatusAdvance(
            instance = updated.copy(duration = instance.duration - 1),
            damage = damage,
        )
    }

    private fun minNextTickDelay(existing: StatusInstance, incoming: StatusInstance): Int =
        minOf(existing.nextTickDelay, incoming.nextTickDelay)
}
