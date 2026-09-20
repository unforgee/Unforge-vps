package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

typealias points_varps = PointsVarps

/**
 * Point balances owned by other modules, resolved here by name for the `Points & Progress` report.
 * Each varp is the single authoritative balance for its system - this object only reads them;
 * nothing here mints or mutates points.
 * - `qp` is the vanilla quest-points varp (no custom quest system writes it yet, so it reads `0`
 *   until quests land).
 * - `cw_slayer_points`/`cw_slayer_wild_points` are two genuinely separate economies (regular vs
 *   wilderness slayer rewards) and are reported as distinct rows.
 * - `cw_donator_points` is the donator-shop balance shown by `::donorstatus`.
 */
object PointsVarps : VarpReferences() {
    val questPoints: VarpType = find("qp")
    val pvmPoints: VarpType = find("pvm_points")
    val slayerPoints: VarpType = find("cw_slayer_points")
    val wildSlayerPoints: VarpType = find("cw_slayer_wild_points")
    val donatorPoints: VarpType = find("cw_donator_points")
    val donatorTier: VarpType = find("cw_donator_tier")
    val slayerTask: VarpType = find("cw_slayer_task")
    val slayerRemaining: VarpType = find("cw_slayer_remaining")
}
