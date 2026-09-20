package org.rsmod.api.player.support

import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.game.entity.PathingEntity

/**
 * Extension point that lets the combat status system apply worn-instance ability riders (ailments,
 * area-of-effect splash) to hit targets without a module dependency cycle.
 *
 * `api:npc` and `api:combat:combat-scripts` cannot depend on `api:combat:combat-effects` (the
 * status service transitively depends on `api:npc` via `api:combat:combat-commons`), so the hit
 * processors invoke this delegate instead. `combat-effects` installs the real implementation at
 * startup; the default is a no-op.
 */
public object AbilityProcProviders {
    /**
     * Invoked when a worn-instance ability proc lands on [target]. [proc] is the capped aggregate
     * of every ability that rolled this hit; [damageDealt] is the damage actually applied to the
     * target (post-cap), used to scale AoE splash. `combat-effects` applies ailment riders through
     * `StatusService` and splashes AoE damage onto nearby npcs.
     */
    public var onHit:
        (
            source: PathingEntity, target: PathingEntity, proc: AbilityProc, damageDealt: Int,
        ) -> Unit =
        { _, _, _, _ ->
        }
}
