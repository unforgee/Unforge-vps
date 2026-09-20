package org.rsmod.content.skills.core

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.SkillMulti
import org.rsmod.api.player.ui.SkillMultiOption
import org.rsmod.api.player.ui.SkillMultiVerb

/**
 * A picked [recipe] and how many the player asked for, in `1..SkillMulti.MAX_COUNT`.
 *
 * The dialog reports `0` for verbs whose quantity row is disabled; that is normalised to `1` before
 * this is handed back, so `count` is always a usable iteration count.
 */
data class MakeSelection(val recipe: MakeRecipe, val count: Int)

/**
 * The engine every production skill outside Smithing drives: open the make menu, then run one
 * iteration per firing of [SkillingQueues.make] until the inputs run out or the player interrupts.
 *
 * Skill-agnostic on purpose. What varies between Cooking, Crafting, Fletching, Herblore, Firemaking
 * and Runecraft is the recipe table and which locs or objs open the menu - not the consume → roll →
 * grant → advance → re-queue cycle. Keeping that cycle in one place is what makes the interruption
 * behaviour identical across all six, and what makes it testable once.
 */
@Singleton
class SkillMakeActions @Inject constructor(private val skillingRewards: SkillingRewards) {
    /**
     * Opens interface 270 with [recipes] and suspends until the player picks one, closes the dialog
     * or walks away.
     *
     * Returns `null` for a closed dialog rather than throwing: walking out of a make-menu is
     * ordinary, not a lost-access error.
     */
    suspend fun openMakeMenu(
        access: ProtectedAccess,
        title: String,
        recipes: List<MakeRecipe>,
        verb: SkillMultiVerb = SkillMultiVerb.Make,
        selectedCount: Int = 1,
    ): MakeSelection? {
        if (recipes.isEmpty()) {
            return null
        }
        val selection =
            access.skillMultiDialog(
                title = title,
                options = recipes.map { SkillMultiOption(it.product, it.label) },
                verb = verb,
                maxCount = SkillMulti.MAX_COUNT,
                selectedCount = selectedCount,
            ) ?: return null
        val recipe = recipes.getOrNull(selection.index) ?: return null
        return MakeSelection(recipe, selection.count.coerceAtLeast(1))
    }

    /**
     * Plays the first cycle and hands the rest to the weak queue. Nothing is produced yet: the
     * first item lands when the queue fires, so the animation always precedes its output.
     */
    fun start(access: ProtectedAccess, request: MakeRequest, count: Int) {
        if (count <= 0 || !access.hasInputs(request)) {
            return
        }
        animate(access, request)
        access.weakQueue(SkillingQueues.make, request.cycles, MakeJob(request, count))
    }

    /**
     * One iteration, then re-queues itself while anything is still owed.
     *
     * Deliberately silent when it stops early: running out of inputs is the normal way a "make all"
     * ends and the player can see their own inventory.
     */
    suspend fun continueJob(access: ProtectedAccess, job: MakeJob) {
        val request = job.request
        if (job.remaining <= 0 || !access.hasInputs(request)) {
            return
        }
        // There is no suspension between validation, deletion and delivery.
        val inputs = request.consumed.entries.toList()
        val saved =
            request.saveObj?.takeIf {
                request.saveAffix != null &&
                    skillingRewards.rollChance(access, request.skill, request.saveAffix)
            }
        val consumed = inputs.filter { it.key != saved }
        val deleted =
            when (consumed.size) {
                0 -> true
                1 -> access.invDel(access.inv, consumed[0].key, consumed[0].value).success
                2 ->
                    access
                        .invDel(
                            access.inv,
                            consumed[0].key,
                            consumed[0].value,
                            consumed[1].key,
                            consumed[1].value,
                        )
                        .success
                3 ->
                    access
                        .invDel(
                            access.inv,
                            consumed[0].key,
                            consumed[0].value,
                            consumed[1].key,
                            consumed[1].value,
                            consumed[2].key,
                            consumed[2].value,
                        )
                        .success
                else -> error("A make recipe supports at most three ingredients")
            }
        if (!deleted) return
        var delivered = true

        val failureProduct = request.failureProduct
        val failureBps = request.failureChanceBps?.invoke(access.stat(request.stat)) ?: 0
        val failed =
            failureProduct != null &&
                failureBps > 0 &&
                access.random.of(SkillingRewards.ROLL_BOUND) < failureBps
        if (failed) {
            access.invAdd(access.inv, failureProduct, request.produced)
            val message = request.failureMessage
            if (message != null) {
                access.mes(message)
            }
        } else {
            val product = request.product
            if (product != null) {
                // False can mean ground delivery, so it must never trigger a material refund.
                delivered = skillingRewards.grant(access, request.skill, product, request.produced)
            }
            access.statAdvance(request.stat, request.xp)
            val message = request.successMessage
            if (message != null) {
                access.mes(message)
            }
        }
        if (!failed) request.onMade?.invoke(access)

        val remaining = job.remaining - 1
        if (delivered && remaining > 0 && access.hasInputs(request)) {
            animate(access, request)
            access.weakQueue(SkillingQueues.make, request.cycles, MakeJob(request, remaining))
        }
    }

    private fun animate(access: ProtectedAccess, request: MakeRequest) {
        val anim = request.anim
        if (anim != null) {
            access.anim(anim)
        }
        val sound = request.sound
        if (sound != null) {
            access.soundSynth(sound)
        }
    }
}
