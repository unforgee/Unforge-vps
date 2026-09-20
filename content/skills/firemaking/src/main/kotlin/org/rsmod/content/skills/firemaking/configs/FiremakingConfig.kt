package org.rsmod.content.skills.firemaking.configs

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType

typealias firemaking_objs = FiremakingObjs

typealias firemaking_locs = FiremakingLocs

typealias firemaking_seqs = FiremakingSeqs

object FiremakingObjs : ObjReferences() {
    val tinderbox = find("tinderbox")
    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val willow_logs = find("willow_logs")
    val teak_logs = find("teak_logs")
    val maple_logs = find("maple_logs")
    val mahogany_logs = find("mahogany_logs")
    val yew_logs = find("yew_logs")
    val magic_logs = find("magic_logs")
    val redwood_logs = find("redwood_logs")
}

/** The fire a lit log leaves behind; the loc the module places and the fire Cooking cooks on. */
object FiremakingLocs : LocReferences() {
    val fire = find("fire")
}

object FiremakingSeqs : SeqReferences() {
    val create_fire = find("human_createfire")
}

/**
 * @param level the Firemaking level required.
 * @param xp awarded when the log catches.
 * @param burnTicks how long the fire stays lit, in ticks. The tick counts are the game's own, read
 *   off the fire loc's despawn rather than invented: a normal log burns for 100 ticks, and the
 *   duration climbs with the log tier because the loc is the same one in every case.
 */
data class BurnableLog(val log: ObjType, val level: Int, val xp: Double, val burnTicks: Int = 100)

object FiremakingRecipes {
    val all: List<BurnableLog> =
        listOf(
            BurnableLog(firemaking_objs.logs, 1, 40.0),
            BurnableLog(firemaking_objs.oak_logs, 15, 60.0),
            BurnableLog(firemaking_objs.willow_logs, 30, 90.0),
            BurnableLog(firemaking_objs.teak_logs, 35, 105.0),
            BurnableLog(firemaking_objs.maple_logs, 45, 135.0),
            BurnableLog(firemaking_objs.mahogany_logs, 50, 157.5),
            BurnableLog(firemaking_objs.yew_logs, 60, 202.5),
            BurnableLog(firemaking_objs.magic_logs, 75, 303.8),
            BurnableLog(firemaking_objs.redwood_logs, 90, 350.0),
        )

    /** Keyed by log obj id, which is how the tinderbox finds the log it was used on. */
    val byLog: Map<Int, BurnableLog>
        get() = all.associateBy { it.log.id }
}

/** Ticks between the tinderbox being used and the fire appearing. */
const val FIREMAKING_CYCLES: Int = 2
