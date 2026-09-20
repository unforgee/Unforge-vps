package org.rsmod.api.player.output

import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.equipment.instance.EquipmentAbilityCatalog
import org.rsmod.game.entity.Player

/**
 * Visual/audio-style feedback for successfully rolled item-instance abilities and armour-set procs:
 * a red attack-glow spotanim on the attacker plus an overhead "<Ability name>!" line so procs are
 * visible to the owner and to nearby players. Works for real players and companion bots alike -
 * both are [Player] entities.
 */
public object AbilityProcFx {
    /**
     * Announces every ability in [abilityIds] that rolled successfully: one shared spotanim and a
     * single overhead line joining the display names (e.g. `Arazzor Cleave! Venomous Rend!`).
     * `set-*` armour-set ids render as the set name (`set-guthans` -> `Guthans!`).
     */
    public fun announce(player: Player, abilityIds: List<String>) {
        if (abilityIds.isEmpty()) {
            return
        }
        player.spotanim(spotanims.sp_attackglow_red, slot = constants.spotanim_slot_combat)
        player.say(abilityIds.joinToString(" ") { "${displayName(it)}!" })
    }

    private fun displayName(abilityId: String): String =
        if (abilityId.startsWith("set-")) {
            abilityId.removePrefix("set-").replaceFirstChar { it.uppercase() }
        } else {
            EquipmentAbilityCatalog.displayName(abilityId)
        }
}
