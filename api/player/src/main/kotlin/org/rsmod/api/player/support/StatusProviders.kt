package org.rsmod.api.player.support

import org.rsmod.game.entity.PathingEntity

/**
 * Extension points that let the combat status system feed damage modifiers into the hit processors
 * without introducing a module dependency cycle.
 *
 * `api:npc` and `api:player` cannot depend on `api:combat:combat-effects` (the status service
 * transitively depends on `api:npc` via `api:combat:combat-commons`), so the hit processors query
 * these delegates instead. `combat-effects` installs the real implementations at startup; the
 * defaults are no-ops.
 */
public object StatusProviders {
    /**
     * Additional incoming-damage basis points applied to hits taken by the entity (e.g. the
     * `VULNERABLE` status contributes `500` bps per stack).
     */
    public var incomingDamageBps: (PathingEntity) -> Int = { 0 }

    /**
     * Damage reduction in basis points applied to hits dealt by the entity (e.g. the `WEAKEN`
     * status contributes `500` bps per stack).
     */
    public var outgoingDamageReductionBps: (PathingEntity) -> Int = { 0 }
}
