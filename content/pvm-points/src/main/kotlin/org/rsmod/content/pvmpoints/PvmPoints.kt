package org.rsmod.content.pvmpoints

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

typealias pvm_point_varps = PvmPointVarps

/**
 * The PvM-point balance - the kill-side counterpart to `skill_points`.
 *
 * Same persistence shape as the other Unforge currencies: a permanent, server-only varp that is
 * never transmitted to the client (the Points & Progress journal page renders it with `ifSetText`).
 * Points are minted only by attributed npc kills, scaled by the npc's combat level, and are meant
 * to be spent on PvM-oriented rewards - never convertible to gp, skill points or perk points.
 */
object PvmPointVarps : VarpReferences() {
    val points: VarpType = find("pvm_points")
}

internal object PvmPointVarpBuilder : VarpBuilder() {
    init {
        build("pvm_points") {
            permanent = true
            transmitNever = true
        }
    }
}

/**
 * PvM points awarded for one kill of an npc with combat level [visLevel].
 *
 * Trivial mobs (`visLevel < 10`) award nothing so low-level spawn camping cannot mint the currency;
 * the tiers then scale with the effort a kill actually takes. Boss-tier npcs (`visLevel >= 100`,
 * the same threshold `PerkService.isBoss` uses) award meaningful amounts - the two currencies
 * coexist: perk points buy permanent perks, PvM points buy PvM-side rewards.
 */
fun pvmPointsForLevel(visLevel: Int): Int =
    when {
        visLevel < 10 -> 0
        visLevel < 50 -> 1
        visLevel < 100 -> 2
        visLevel < 200 -> 5
        visLevel < 350 -> 10
        else -> 20
    }

/**
 * The player-facing half of the PvM point model: reads the balance, grants kill awards into the
 * `pvm_points` varp, and deducts spends.
 *
 * Mirrors `SkillingPoints` deliberately - a small typed helper, not a generic reward engine. One
 * call to [award] = one kill's grant: per-kill idempotency is the caller's contract (the
 * `NpcKilledEvent` fires once per attributed kill), not something silently deduplicated here.
 */
@Singleton
class PvmPoints @Inject constructor() {
    /** How many points the player has right now; `0` for players missing the varp. */
    fun balance(player: Player): Int = player.vars[PvmPointVarps.points]

    /**
     * Grants the kill award for an npc of combat level [visLevel] and returns the amount granted
     * - `0` when the npc was too trivial to reward, in which case nothing was written.
     */
    /**
     * Grants the kill award for an npc of combat level [visLevel], scaled by [bonusBps]
     * (talent-tree PvM-point bonuses: `+bps` basis points of extra payout).
     *
     * @return the amount actually granted - `0` when the npc was too trivial to reward.
     */
    fun award(player: Player, visLevel: Int, bonusBps: Int = 0): Int {
        val base = pvmPointsForLevel(visLevel)
        if (base == 0) {
            return 0
        }
        val amount = base + base * bonusBps / 10_000
        VarPlayerIntMapSetter.set(player, PvmPointVarps.points, balance(player) + amount)
        return amount
    }

    /**
     * Atomically deducts [cost] from the player's balance.
     *
     * @return `true` when [cost] was positive and covered by the balance and the new balance was
     *   written; `false` otherwise - nothing was deducted in that case.
     */
    fun spend(player: Player, cost: Int): Boolean {
        if (cost <= 0 || cost > balance(player)) {
            return false
        }
        VarPlayerIntMapSetter.set(player, PvmPointVarps.points, balance(player) - cost)
        return true
    }
}
