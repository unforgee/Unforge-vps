package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import org.rsmod.game.entity.Player

/** Aggregates only skilling affixes from item instances currently worn by [Player]. */
public class WornSkillBonuses
@Inject
constructor(private val instances: EquipmentInstanceRegistry) {
    public fun value(player: Player, skill: String, effect: String): Int =
        player.worn.objs
            .asSequence()
            .filterNotNull()
            .mapNotNull { obj -> obj.instanceId.takeIf { it != 0L }?.let(instances::get) }
            .flatMap { it.skillAffixes.asSequence() }
            .filter {
                it.skill.equals("ALL", ignoreCase = true) ||
                    it.skill.equals(skill, ignoreCase = true)
            }
            .filter { it.effect.equals(effect, ignoreCase = true) }
            .sumOf { it.magnitude }

    public fun has(player: Player, skill: String, effect: String): Boolean =
        value(player, skill, effect) > 0
}
