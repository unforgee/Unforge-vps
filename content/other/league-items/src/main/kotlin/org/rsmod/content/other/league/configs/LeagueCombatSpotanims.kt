package org.rsmod.content.other.league.configs

import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias league_combat_spots = LeagueCombatSpotanims

object LeagueCombatSpotanims : SpotanimReferences() {
    val cleave = find("sp_attack_cleave_spotanim", 13013927)
    val lightning = find("saradomin_lightning", 11448129)
}
