package org.rsmod.content.skills.core

import org.rsmod.api.config.refs.queues
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.synth.SynthType

/**
 * One consumed ingredient: [count] objs of [obj] disappear per iteration.
 *
 * Tools (a knife, a chisel, a hammer) are deliberately **not** modelled here - they are never
 * consumed, so the script that opens the menu checks for them and the ingredient list stays a pure
 * description of what the recipe costs.
 */
data class MakeIngredient(val obj: ObjType, val count: Int)

/**
 * A single repeated action a production skill offers: what it costs, what it yields, and what it
 * pays.
 *
 * @param label the row shown in the `skillmulti` dialog. Must not contain `|`, which cs 632 uses to
 *   split the dialog's string argument.
 * @param level the skill level required. Not enforced here - the caller that owns the skill checks
 *   it so it can say *which* skill is too low.
 * @param produced how many [product] one iteration yields. Arrow shafts give 15 per log, darts and
 *   arrowtips stack differently per recipe.
 */
data class MakeRecipe(
    val product: ObjType,
    val label: String,
    val level: Int,
    val xp: Double,
    val ingredients: List<MakeIngredient>,
    val produced: Int = 1,
) {
    /** [ingredients] keyed by obj, which is the shape the make loop consumes from. */
    val consumed: Map<ObjType, Int> = ingredients.associate { it.obj to it.count }
}

/**
 * Everything the shared make loop needs to run one skill's iteration, and nothing about *which*
 * skill it is beyond the keys it is reported under.
 *
 * @param product `null` for actions that produce no obj at all - lighting a fire consumes a log and
 *   places a loc, so there is nothing to grant.
 * @param skill the [org.rsmod.api.commons.skilling.SkillingRewards] key, e.g. `COOKING`.
 * @param stat the stat to advance [xp] on.
 * @param cycles ticks between one iteration and the next. Doubles as the length of [anim].
 * @param sound the synth played on each iteration, when the cache names one.
 * @param saveObj ingredient refunded when [saveAffix] rolls, for worn skilling gear that preserves
 *   materials (Smithing's `SMITH_SAVE`, Herblore's secondary save).
 * @param failureProduct the obj produced when the action fails - burnt food. Failure consumes the
 *   ingredients and pays no xp, exactly like the game.
 * @param failureChanceBps failure chance **in basis points**, computed from the player's level by
 *   the owning skill (`250` = 2.5%). `null` means the action never fails.
 * @param onMade runs after a successful iteration, for side effects the loop does not own: placing
 *   a fire loc, publishing an event.
 */
data class MakeRequest(
    val product: ObjType?,
    val consumed: Map<ObjType, Int>,
    val skill: String,
    val stat: StatType,
    val xp: Double,
    val anim: SeqType? = null,
    val sound: SynthType? = null,
    val cycles: Int = 3,
    val produced: Int = 1,
    val saveObj: ObjType? = null,
    val saveAffix: String? = null,
    val failureProduct: ObjType? = null,
    val failureChanceBps: ((level: Int) -> Int)? = null,
    val successMessage: String? = null,
    val failureMessage: String? = null,
    val onMade: (suspend ProtectedAccess.() -> Unit)? = null,
    val level: Int = 1,
    val tools: Set<ObjType> = emptySet(),
    val valid: ProtectedAccess.() -> Boolean = { true },
) {
    init {
        require(cycles > 0 && produced > 0 && level >= 1)
        require(xp.isFinite() && xp >= 0)
        require(consumed.isNotEmpty() && consumed.values.all { it > 0 })
        require(saveObj == null || saveObj in consumed)
    }

    companion object {
        /** [recipe] with everything this loop cannot infer left to the caller. */
        fun of(
            recipe: MakeRecipe,
            skill: String,
            stat: StatType,
            anim: SeqType? = null,
            sound: SynthType? = null,
            cycles: Int = 3,
            successMessage: String? = null,
            onMade: (suspend ProtectedAccess.() -> Unit)? = null,
        ): MakeRequest =
            MakeRequest(
                product = recipe.product,
                consumed = recipe.consumed,
                skill = skill,
                stat = stat,
                xp = recipe.xp,
                level = recipe.level,
                anim = anim,
                sound = sound,
                cycles = cycles,
                produced = recipe.produced,
                successMessage = successMessage,
                onMade = onMade,
            )
    }
}

/**
 * A [MakeRequest] plus how many iterations are still owed, carried as the weak-queue argument.
 *
 * #### Why a queue and not a delay loop
 * `ProtectedAccess.delay` sets `player.delay`, and every input handler drops its packet while
 * `isDelayed` is true - a `repeat(count) { anim(); delay(cycles); make() }` loop locks the player
 * solid until it finishes. A **weak** queue that re-schedules itself is never delayed, so clicks
 * are handled normally and any of them calls `Player.clearPendingAction` → `ifClose` →
 * `weakQueueList.clear()`, which drops the rest of the run. That is the interruption model every
 * production skill in this server shares; `smithing_make` is the same pattern, kept in its own
 * module because it hands its products to the equipment-instance roll.
 */
data class MakeJob(val request: MakeRequest, val remaining: Int)

/**
 * One weak queue for every make loop outside Smithing.
 *
 * Queue types are cache-defined, so the number of make loops is bounded by the generic queues the
 * cache happens to carry rather than by how many skills exist. All six production skills share this
 * one: they are mutually exclusive on a single player (any new op clears the pending run) and a
 * single registration means no skill can silently replace another's handler.
 */
object SkillingQueues {
    val make = queues.generic_queue7
}

/** How many iterations the player has the inputs for right now, capped at [limit]. */
fun ProtectedAccess.affordableCount(request: MakeRequest, limit: Int): Int {
    val affordable =
        request.consumed.minOfOrNull { (obj, amount) -> invTotal(inv, obj) / amount } ?: 0
    return affordable.coerceAtMost(limit)
}

fun ProtectedAccess.hasInputs(request: MakeRequest): Boolean =
    stat(request.stat) >= request.level &&
        request.valid(this) &&
        request.tools.all { invTotal(inv, it) > 0 } &&
        request.consumed.all { (obj, amount) -> invTotal(inv, obj) >= amount }
