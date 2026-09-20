package org.rsmod.api.combat.effects

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.hunt.PlayerSearch
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.hunt.HuntVis
import org.rsmod.map.CoordGrid

/**
 * Applies and queries the non-damage combat effects used by the spellbooks, such as freeze, bind,
 * teleblock and poison.
 *
 * All state is stored on the affected entity so it survives across script invocations and is
 * cleared automatically by the engine when it expires.
 */
public class CombatEffects
@Inject
constructor(
    private val npcSearch: NpcSearch,
    private val playerSearch: PlayerSearch,
    public val statusService: StatusService,
) {
    /** The radius (in tiles, per axis) covered by an area-of-effect spell such as a barrage. */
    public val areaRadius: Int = 1

    /**
     * Freezes [target] in place for [cycles], preventing it from starting or continuing movement.
     *
     * Freeze and bind share the same movement lock; a longer duration always wins.
     */
    public fun freeze(target: PathingEntity, cycles: Int) {
        target.lockMovement(cycles)
    }

    /** Binds [target] in place for [cycles]. @see [freeze] */
    public fun bind(target: PathingEntity, cycles: Int) {
        freeze(target, cycles)
    }

    /** `true` while [target] is frozen or bound. */
    public fun isMovementLocked(target: PathingEntity): Boolean = target.isMovementLocked

    /** Clears any active freeze or bind on [target]. */
    public fun clearMovementLock(target: PathingEntity) {
        target.clearMovementLock()
    }

    /**
     * Prevents [target] from casting teleport spells for [cycles].
     *
     * An existing teleblock is only extended, never shortened.
     */
    public fun teleblock(target: Player, cycles: Int) {
        val expiry = target.currentMapClock + cycles
        if (expiry > target.teleblockExpiry) {
            target.teleblockExpiry = expiry
        }
    }

    /** `true` while [target] is under the effects of a teleblock. */
    public fun isTeleblocked(target: Player): Boolean =
        target.teleblockExpiry > target.currentMapClock

    /** Clears any active teleblock on [target]. */
    public fun clearTeleblock(target: Player) {
        target.teleblockExpiry = -1
    }

    /**
     * Poisons [target] for [damage] per periodic tick, lasting [cycles].
     *
     * The application is routed through [statusService] ([StatusEffect.POISON] uses
     * [org.rsmod.api.combat.commons.StackPolicy.KEEP_STRONGEST]): a stronger poison replaces the
     * active one, an equal poison refreshes its duration and a weaker poison is discarded.
     *
     * Player targets keep the legacy `toxins` timer and the
     * [Player.poisonDamage]/[Player.poisonExpiry] fields synchronized; npc targets tick through the
     * same unified status lifecycle.
     */
    public fun poison(target: PathingEntity, damage: Int, cycles: Int) {
        if (damage <= 0 || cycles <= 0) {
            return
        }
        statusService.apply(
            target = target,
            effect = StatusEffect.POISON,
            duration = cycles,
            potency = damage,
        )
    }

    /** `true` while [target] is poisoned. */
    public fun isPoisoned(target: PathingEntity): Boolean =
        statusService.has(target, StatusEffect.POISON)

    /** Clears any active poison on [target]. */
    public fun clearPoison(target: PathingEntity) {
        statusService.remove(target, StatusEffect.POISON)
    }

    /**
     * Returns every npc within [radius] tiles (per axis) of [center], including [center] itself.
     *
     * Used to resolve the secondary targets of area-of-effect spells.
     */
    public fun npcsInArea(center: CoordGrid, radius: Int = areaRadius): Sequence<Npc> =
        npcSearch.findAllAny(center, radius, HuntVis.Off)

    /** Returns every player within [radius] tiles (per axis) of [center]. */
    public fun playersInArea(center: CoordGrid, radius: Int = areaRadius): Sequence<Player> =
        playerSearch.findAll(center, radius, HuntVis.Off)

    public companion object {
        /** Cycles between poison damage ticks. */
        public const val POISON_INTERVAL: Int = 30
    }
}
