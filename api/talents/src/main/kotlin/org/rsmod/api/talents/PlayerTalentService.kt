package org.rsmod.api.talents

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Server-authoritative player talent-tree service.
 *
 * Points and ranks live on permanent varps, so the whole tree survives logout/login and server
 * restarts with no extra persistence code - the same contract `PerkService` uses. [train] is the
 * only mutation of rank state: it runs [TalentRules.checkTrain] and then performs the debit + rank
 * write on the game thread, so a spend is atomic by construction (the client can never double-spend
 * a rank - a second request re-validates the already- updated state).
 */
@Singleton
public class PlayerTalentService @Inject constructor() {
    /** Unspent talent-point balance; `0` for fresh players. */
    public fun points(player: Player): Int = player.vars[PlayerTalentVarps.points]

    /** Current rank of [definition] for [player]; `0` when untrained. */
    public fun rank(player: Player, definition: TalentDefinition): Int {
        require(definition.tree == TalentTree.PLAYER) { "Not a player talent: ${definition.id}" }
        return player.vars[PlayerTalentVarps.ranks.getValue(definition.id)]
    }

    /** definition id -> current rank for every player talent. */
    public fun ranks(player: Player): Map<String, Int> =
        TalentCatalog.player.associate { it.id to rank(player, it) }

    /** Total points spent across the whole tree. */
    public fun spent(player: Player): Int = TalentRules.spent(TalentTree.PLAYER, ranks(player))

    /**
     * Attempts to buy one rank of [definition] for [player].
     *
     * @return the [TrainResult] - on [TrainResult.Ok] the point has been debited and the rank
     *   incremented; on every failure nothing was written.
     */
    public fun train(player: Player, definition: TalentDefinition): TrainResult {
        val ranks = ranks(player)
        val spent = TalentRules.spent(TalentTree.PLAYER, ranks)
        val result =
            TalentRules.checkTrain(
                definition = definition,
                tree = TalentTree.PLAYER,
                balance = points(player),
                spent = spent,
                ranks = ranks,
                role = null,
            )
        if (result != TrainResult.Ok) {
            return result
        }
        VarPlayerIntMapSetter.set(
            player,
            PlayerTalentVarps.points,
            points(player) - definition.pointsPerRank,
        )
        VarPlayerIntMapSetter.set(
            player,
            PlayerTalentVarps.ranks.getValue(definition.id),
            (ranks[definition.id] ?: 0) + 1,
        )
        return TrainResult.Ok
    }

    /**
     * Respec: refunds every spent point and zeroes all ranks. Returns the refunded amount.
     * Deliberately unconditional for the beta - any respec cost rule is a content decision on top
     * of this mechanical primitive.
     */
    public fun reset(player: Player): Int {
        val refund = spent(player)
        for (varp in PlayerTalentVarps.ranks.values) {
            VarPlayerIntMapSetter.set(player, varp, 0)
        }
        if (refund > 0) {
            VarPlayerIntMapSetter.set(player, PlayerTalentVarps.points, points(player) + refund)
        }
        return refund
    }

    /** Grants [amount] talent points (boss kills, admin tooling). */
    public fun grant(player: Player, amount: Int) {
        if (amount > 0) {
            VarPlayerIntMapSetter.set(player, PlayerTalentVarps.points, points(player) + amount)
        }
    }

    /**
     * Effective basis-point bonus of [key] from the player's trained ranks - e.g. rank 3 of a +200
     * bps/rank talent resolves to 600. Sums every player talent carrying [key]. Always
     * server-computed; never reads client-supplied state.
     */
    public fun effectBps(player: Player, key: TalentEffectKey): Int =
        TalentCatalog.player
            .filter { it.effectKey == key }
            .sumOf { rank(player, it) * it.effectBpsPerRank }
}
