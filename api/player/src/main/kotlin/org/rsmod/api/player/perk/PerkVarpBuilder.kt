package org.rsmod.api.player.perk

import org.rsmod.api.type.builders.varp.VarpBuilder

internal object PerkVarpBuilder : VarpBuilder() {
    init {
        build("perk_points") {
            permanent = true
            transmitNever = true
        }
        build("perk_points_low") {
            permanent = true
            transmitNever = true
        }
        build("perk_points_high") {
            permanent = true
            transmitNever = true
        }
        build("perk_rank_format") {
            permanent = true
            transmitNever = true
        }
        // Each perk owns one persistent xp varp named `perk_xp_<enum name in lowercase>`.
        for (perk in Perk.entries) {
            build("perk_xp_${perk.name.lowercase()}") {
                permanent = true
                transmitNever = true
            }
        }
        for (name in listOf("damage", "attack_speed", "max_health")) {
            build("solo_boss_perk_$name") {
                permanent = true
                transmitNever = true
            }
        }
    }
}
