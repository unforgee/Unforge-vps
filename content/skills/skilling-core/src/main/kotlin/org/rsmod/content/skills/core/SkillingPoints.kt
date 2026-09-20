package org.rsmod.content.skills.core

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

typealias skilling_point_varps = SkillingPointVarps

/**
 * The skill-point balance behind the Skill Quartermaster - the PvM + skilling POC's skilling
 * currency.
 *
 * **Why a varp and not an item token:** `skill_points` mirrors `perk_points` exactly - a
 * persistent, server-only balance that is never transmitted to the client (the shop header renders
 * it with `ifSetText` instead). Points are minted only by successful skilling actions and spent
 * only at the skill shop - never convertible to gp or perk points. A varp cannot be dropped,
 * banked, traded or lost on death, and a fresh or migrated player defaults to `0` for free. The
 * earlier `star_fragment` item-token prototype was a stopgap for a `Shops`-engine shop, which the
 * custom `unforge_skillshop` modal does not use.
 *
 * The builder lives in this module rather than the shop module so that the producers of the
 * currency - skill scripts like mining - can resolve the varp on their own module classpath.
 * Consumers such as `skillshop_varps` resolve the same type by name.
 */
object SkillingPointVarps : VarpReferences() {
    val points: VarpType = find("skill_points")
}

internal object SkillingPointVarpBuilder : VarpBuilder() {
    init {
        build("skill_points") {
            permanent = true
            transmitNever = true
        }
    }
}

/**
 * One grant of skilling points, validated at construction.
 *
 * The cap is a guard rail against a mangled config handing out absurd balances in one action, not a
 * balance decision: a normal POC action awards `1`.
 *
 * Idempotency is deliberately **not** modelled here. A value object can be applied twice, which is
 * what "duplicate-kutsu" in the plan's test row pins down: the *caller* - the skill script - grants
 * once per successful action, and the server-side guarantee lives there. Silently deduplicating
 * inside this model would need per-player state this POC does not have.
 */
data class SkillingPointAward(val amount: Int) {
    init {
        require(amount in 1..MAX_PER_ACTION) {
            "A skilling point award must be in 1..$MAX_PER_ACTION per action: $amount"
        }
    }

    companion object {
        const val MAX_PER_ACTION: Int = 100
    }
}

/** Balance after granting [award]. Pure so the unit tests run without the game harness. */
fun applySkillingPointAward(balance: Int, award: SkillingPointAward): Int = balance + award.amount

/**
 * Balance after spending [cost], or `null` when the spend cannot happen - a non-positive cost or
 * insufficient funds. `null` is the atomic contract in miniature: either the whole cost comes off
 * or nothing does; there is no partial spend.
 */
fun applySkillingPointSpend(balance: Int, cost: Int): Int? =
    if (cost in 1..balance) balance - cost else null

/**
 * The player-facing half of the skilling point model: grants awards into the `skill_points` varp,
 * spends from it, and reads the current balance.
 *
 * Small and typed on purpose - this is the plan's "pieni typed helper", not a generic reward
 * engine. No event bus, no per-skill registry, no ledger history: the first vertical slice only
 * needs "give N points for one action" and "take N points for one purchase".
 *
 * Both writes are single-threaded read-modify-write on the game thread, so a spend is atomic by
 * construction: the balance is checked and deducted in one [applySkillingPointSpend] step before
 * the varp is written back.
 */
@Singleton
class SkillingPoints @Inject constructor() {
    /** How many points the player has right now; `0` for players missing the varp. */
    fun balance(player: Player): Int = player.vars[SkillingPointVarps.points]

    /**
     * Grants [award] to the player and returns the new balance.
     *
     * One call = one grant: per-action idempotency is the caller's contract, not this model's. A
     * skill script that invokes this once per successful action can never double-mint, and a caller
     * bug shows up as a doubled balance instead of a silent no-op.
     */
    fun grant(access: ProtectedAccess, award: SkillingPointAward): Int {
        val updated = applySkillingPointAward(balance(access.player), award)
        VarPlayerIntMapSetter.set(access.player, SkillingPointVarps.points, updated)
        return updated
    }

    /**
     * Atomically deducts [cost] from the player's balance.
     *
     * @return `true` when [cost] was positive and covered by the balance and the new balance was
     *   written; `false` otherwise - nothing was deducted in that case, and the caller decides what
     *   "insufficient funds" means for the player.
     */
    fun spend(access: ProtectedAccess, cost: Int): Boolean {
        val updated = applySkillingPointSpend(balance(access.player), cost) ?: return false
        VarPlayerIntMapSetter.set(access.player, SkillingPointVarps.points, updated)
        return true
    }
}
