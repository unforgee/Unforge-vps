package org.rsmod.content.skills.smithing.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/**
 * One thing the player is making, repeatedly.
 *
 * @param consumed obj to count-per-iteration. Checked and deleted together so a partially-stocked
 *   inventory never loses ore or bars for nothing.
 * @param produced how many [product] a single iteration yields - dart tips and arrowtips give more
 *   than one per bar.
 * @param cycles ticks between one item appearing and the next.
 */
data class MakeRequest(
    val product: ObjType,
    val produced: Int,
    val consumed: Map<ObjType, Int>,
    val xp: Double,
    val anim: SeqType,
    val cycles: Int,
)

/**
 * A [MakeRequest] plus how many iterations are still owed, carried as the weak-queue argument.
 *
 * #### Why a queue and not a delay loop
 * The obvious shape - `repeat(count) { anim(); delay(cycles); make() }` - locks the player solid.
 * `ProtectedAccess.delay` sets `player.delay`, and every input handler (`OpLocHandler`,
 * `MoveGameClickHandler`, ...) drops its packet outright while `isDelayed` is true, so a "make 28"
 * ignored clicking away, clicking the anvil again, everything, until it finished.
 *
 * A **weak** queue that re-schedules itself has the behaviour the game actually wants: the player
 * is never delayed, so clicks are handled normally, and any of them calls
 * `Player.clearPendingAction` → `ifClose` → `weakQueueList.clear()`, which drops the rest of the
 * run. Same pattern the dairy cow uses for repeat milking.
 */
data class MakeJob(val request: MakeRequest, val remaining: Int)

/** How many iterations the player has the inputs for right now, capped at [limit]. */
fun ProtectedAccess.affordableCount(request: MakeRequest, limit: Int): Int {
    val affordable =
        request.consumed.minOfOrNull { (obj, amount) -> invTotal(inv, obj) / amount } ?: 0
    return affordable.coerceAtMost(limit)
}

fun ProtectedAccess.hasInputs(request: MakeRequest): Boolean =
    request.consumed.all { (obj, amount) -> invTotal(inv, obj) >= amount }
