package org.rsmod.api.combat.commons.types

/**
 * The combat attack style an npc uses while engaging a player.
 *
 * The value is stored on the npc type through the `npc_attack_style` param, where the raw int is
 * mapped to an entry via [from]. Npcs without the param (or with an unknown value) default to
 * [Melee], preserving the original melee-only behavior.
 */
public enum class NpcAttackStyle(public val id: Int) {
    Melee(0),
    Ranged(1),
    Magic(2);

    public companion object {
        public fun from(id: Int): NpcAttackStyle = entries.firstOrNull { it.id == id } ?: Melee
    }
}
