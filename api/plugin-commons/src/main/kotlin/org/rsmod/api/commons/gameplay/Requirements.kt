package org.rsmod.api.commons.gameplay

import org.rsmod.api.player.stat.statBase
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList

/**
 * A single skill requirement expressed as a [StatType] + minimum **base** level.
 *
 * Base level (not the boosted/drained current level) is what equipment, shortcuts and reward gates
 * should check, matching the semantics used by `HeldEquipOp` stat requirements.
 */
public data class StatRequirement(public val stat: StatType, public val level: Int) {
    public fun metBy(player: Player): Boolean = player.statBase(stat) >= level
}

/** Requirements whose base-level check the player does not currently satisfy. */
public fun Player.missingRequirements(
    requirements: Iterable<StatRequirement>
): List<StatRequirement> = requirements.filterNot { it.metBy(this) }

public fun Player.meetsRequirements(requirements: Iterable<StatRequirement>): Boolean =
    requirements.all { it.metBy(this) }

/** Stats whose base level is below [StatType.maxLevel]. Used by all-99 gates (e.g. max cape). */
public fun Player.missingMaxedStats(stats: Iterable<StatType>): List<StatType> =
    stats.filter { statBase(it) < it.maxLevel }

public fun Player.hasAllStatsMaxed(statTypes: StatTypeList): Boolean =
    statTypes.values.all { statBase(it) >= it.maxLevel }
