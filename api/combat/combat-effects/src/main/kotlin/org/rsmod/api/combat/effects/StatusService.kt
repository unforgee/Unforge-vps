package org.rsmod.api.combat.effects

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.combat.commons.StatusDefinition
import org.rsmod.api.combat.commons.StatusDefinitions
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.combat.commons.StatusInstance
import org.rsmod.api.config.refs.timers
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Centralized lifecycle service for combat status effects ([StatusEffect]).
 *
 * Active statuses are tracked per entity in a [WeakHashMap], so dead and despawned entities are
 * released automatically. The full lifecycle is unified: [apply] resolves stacking through the
 * effect's [StatusDefinition], [tick]/[tickEffect] advance durations and fire periodic procs, and
 * [clearOnDeath]/[clearOnLogout] perform cleanup.
 *
 * Player poison additionally mirrors its state onto the legacy [Player.poisonDamage] and
 * [Player.poisonExpiry] fields, and keeps the `toxins` timer armed, so pre-existing callers keep
 * working while all reads and writes route through this service.
 */
@Singleton
public class StatusService @Inject constructor() {
    private val active = WeakHashMap<PathingEntity, MutableMap<StatusEffect, StatusInstance>>()

    /**
     * Applies [effect] to [target] for [duration] cycles.
     *
     * The effect's [StatusDefinition.stackPolicy] decides how the application merges with an
     * already-active instance. [potency] carries the effect magnitude (e.g. poison damage per
     * tick). `FROZEN` also locks the target's movement for the resolved duration.
     *
     * Returns the resolved instance now active on the target.
     */
    public fun apply(
        target: PathingEntity,
        effect: StatusEffect,
        duration: Int,
        stacks: Int = 1,
        potency: Int = 0,
    ): StatusInstance {
        val def = StatusDefinitions[effect]
        val incoming =
            StatusInstance(
                effect = effect,
                duration = duration,
                stacks = stacks,
                stackCap = def.stackCap,
                potency = potency,
                nextTickDelay = def.tickInterval,
            )
        val statuses = active.getOrPut(target) { mutableMapOf() }
        val resolved = StatusDefinitions.resolve(statuses[effect], incoming, def)
        statuses[effect] = resolved
        if (effect == StatusEffect.FROZEN) {
            target.lockMovement(resolved.duration)
        }
        syncLegacyPoison(target, resolved)
        return resolved
    }

    /** `true` while [effect] is active on [target]. */
    public fun has(target: PathingEntity, effect: StatusEffect): Boolean =
        get(target, effect) != null

    /** Returns the active [StatusInstance] for [effect] on [target], or `null`. */
    public fun get(target: PathingEntity, effect: StatusEffect): StatusInstance? =
        active[target]?.get(effect)

    /** Current stack count of [effect] on [target]; `0` when inactive. */
    public fun getStacks(target: PathingEntity, effect: StatusEffect): Int =
        get(target, effect)?.stacks ?: 0

    /** Removes [effect] from [target], releasing any movement lock it imposed. */
    public fun remove(target: PathingEntity, effect: StatusEffect) {
        val statuses = active[target] ?: return
        if (statuses.remove(effect) == null) {
            return
        }
        if (statuses.isEmpty()) {
            active.remove(target)
        }
        if (effect == StatusEffect.FROZEN) {
            target.clearMovementLock()
        }
        if (effect == StatusEffect.POISON) {
            clearLegacyPoison(target)
        }
    }

    /** Removes every status whose definition marks [StatusDefinition.clearOnDeath]. */
    public fun clearOnDeath(target: PathingEntity) {
        clearMatching(target) { StatusDefinitions[it].clearOnDeath }
    }

    /** Removes every status whose definition marks [StatusDefinition.clearOnLogout]. */
    public fun clearOnLogout(target: PathingEntity) {
        clearMatching(target) { StatusDefinitions[it].clearOnLogout }
    }

    /**
     * Advances every status on [target] by a single cycle.
     *
     * [onDamage] fires for each periodic proc with the effect and its current potency. [exclude]
     * can skip timer-driven effects (e.g. player poison is advanced by the `toxins` timer via
     * [tickEffect] instead, to keep legacy timer semantics).
     */
    public fun tick(
        target: PathingEntity,
        exclude: StatusEffect? = null,
        onDamage: (PathingEntity, StatusEffect, Int) -> Unit = { _, _, _ -> },
    ) {
        val statuses = active[target] ?: return
        val effects = statuses.keys.toList()
        for (effect in effects) {
            if (effect == exclude) {
                continue
            }
            advanceOnce(target, statuses, effect, onDamage)
        }
    }

    /**
     * Advances a single [effect] on [target] by [cycles] cycles.
     *
     * Periodic procs fire at their [StatusDefinition.tickInterval] boundaries, matching a
     * timer-driven scheduler such as the `toxins` player timer.
     */
    public fun tickEffect(
        target: PathingEntity,
        effect: StatusEffect,
        cycles: Int,
        onDamage: (PathingEntity, StatusEffect, Int) -> Unit = { _, _, _ -> },
    ) {
        val statuses = active[target] ?: return
        repeat(cycles) {
            if (statuses[effect] == null) {
                return
            }
            advanceOnce(target, statuses, effect, onDamage)
        }
    }

    private fun advanceOnce(
        target: PathingEntity,
        statuses: MutableMap<StatusEffect, StatusInstance>,
        effect: StatusEffect,
        onDamage: (PathingEntity, StatusEffect, Int) -> Unit,
    ) {
        val instance = statuses[effect] ?: return
        val result = StatusDefinitions.advance(instance, StatusDefinitions[effect])
        if (result.damage > 0) {
            onDamage(target, effect, result.damage)
        }
        val updated = result.instance
        if (updated == null) {
            statuses.remove(effect)
            if (statuses.isEmpty()) {
                active.remove(target)
            }
            if (effect == StatusEffect.POISON) {
                clearLegacyPoison(target)
            }
        } else {
            statuses[effect] = updated
            syncLegacyPoison(target, updated)
        }
    }

    private fun clearMatching(target: PathingEntity, predicate: (StatusEffect) -> Boolean) {
        val statuses = active[target] ?: return
        val effects = statuses.keys.filter(predicate)
        for (effect in effects) {
            remove(target, effect)
        }
    }

    private fun syncLegacyPoison(target: PathingEntity, instance: StatusInstance) {
        if (instance.effect != StatusEffect.POISON || target !is Player) {
            return
        }
        target.poisonDamage = instance.potency
        target.poisonExpiry = target.currentMapClock + instance.duration
        if (target.timerMap[timers.toxins.id.toShort()] == null) {
            target.timer(timers.toxins, CombatEffects.POISON_INTERVAL)
        }
    }

    private fun clearLegacyPoison(target: PathingEntity) {
        when (target) {
            is Player -> {
                target.poisonDamage = 0
                target.poisonExpiry = -1
                target.timerMap.remove(timers.toxins)
            }
            is Npc -> target.timerMap.remove(timers.toxins)
        }
    }
}
