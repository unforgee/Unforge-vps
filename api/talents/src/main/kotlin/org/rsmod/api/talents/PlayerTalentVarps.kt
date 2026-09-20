package org.rsmod.api.talents

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

public typealias talent_varps = PlayerTalentVarps

/**
 * The player talent-tree's persistent state.
 *
 * [points] is the unspent talent-point balance - the same `Perm` + `transmitNever` shape as
 * `perk_points`/`skill_points`: it never leaves the server, so the client cannot forge it. Each
 * player-tree definition owns one `talent_rk_<id>` varp holding its current rank (0..maxRank); the
 * varp names are generated from [TalentCatalog.player], so a new catalog row automatically gets its
 * own persistent slot - the matching `.data/symbols/varp.sym` lines are generated alongside.
 */
public object PlayerTalentVarps : VarpReferences() {
    public val points: VarpType = find("talent_points")
    public val ranks: Map<String, VarpType> =
        TalentCatalog.player.associate { it.id to find(rankVarpName(it.id)) }

    /** Varp name backing [id]'s rank (sym names cannot contain `-`). */
    public fun rankVarpName(id: String): String = "talent_rk_${id.replace('-', '_')}"
}

internal object PlayerTalentVarpBuilder : VarpBuilder() {
    init {
        build("talent_points") {
            permanent = true
            transmitNever = true
        }
        for (def in TalentCatalog.player) {
            build(PlayerTalentVarps.rankVarpName(def.id)) {
                permanent = true
                transmitNever = true
            }
        }
    }
}
