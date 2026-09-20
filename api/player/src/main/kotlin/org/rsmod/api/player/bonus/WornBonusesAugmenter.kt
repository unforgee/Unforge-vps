package org.rsmod.api.player.bonus

import org.rsmod.game.entity.Player

/**
 * Extension point for systems that need to adjust a player's resolved [WornBonuses.Bonuses] before
 * combat formulas consume them.
 *
 * Augmenters are applied inside [WornBonuses.calculate] after all standard equipment and
 * set-specific modifiers have been resolved, so implementations always see the player's final
 * own-equipment bonuses. Because [WornBonuses.calculate] recomputes on every call, augmenters are
 * evaluated against live equipment state and must never cache stale values themselves.
 */
public fun interface WornBonusesAugmenter {
    /**
     * Returns the bonuses that combat should use for [player], usually [bonuses] merged with
     * externally sourced values. Implementations that do not apply to [player] must return
     * [bonuses] unchanged.
     */
    public fun augment(player: Player, bonuses: WornBonuses.Bonuses): WornBonuses.Bonuses
}
