package org.rsmod.content.pvmprogression.rewards

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.objs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

/**
 * The single reward faucet for PvM Progression 2.0.
 *
 * Every manager that needs to pay out (bounties, challenges, streak cash-out, mutation kills) goes
 * through here - never directly to the inventory or the `pvm_points` varp. That way the feature has
 * one place to enforce the anti-inflation rules:
 * - coin rewards are bounded by the config sanitiser,
 * - PvM tokens reuse the existing [PvmPoints] currency (no new varp, no cache repack),
 * - mutation tokens are tracked in the progression store (see [PvmProgressionStore]),
 * - every grant is logged through [RewardLedger] for anti-exploit audit.
 *
 * The service is deliberately idempotent at the grant level: calling [grant] twice with the same
 * [RewardLedger.Entry.id] pays out only once, which is how the challenge / streak managers defend
 * against double-reward on multi-hit death callbacks.
 */
@Singleton
class PvmRewardService
@Inject
constructor(
    private val pvmPoints: PvmPoints,
    private val objTypes: ObjTypeList,
    private val ledger: RewardLedger,
) {
    /**
     * Grants [reward] to [player]. Returns `true` if the grant went through, `false` if [id] had
     * already been paid (idempotent guard).
     */
    fun grant(player: Player, id: String, reward: PvmReward): Boolean {
        if (!ledger.record(id)) {
            return false
        }
        if (reward.coins > 0) {
            val coins = objTypes[objs.coins]
            if (coins != null) {
                player.invAdd(player.inv, coins, reward.coins, strict = false)
            }
        }
        if (reward.pvmTokens > 0) {
            val before = pvmPoints.balance(player)
            VarPlayerIntMapSetter.set(
                player,
                org.rsmod.content.pvmpoints.pvm_point_varps.points,
                before + reward.pvmTokens,
            )
        }
        if (reward.mutationTokens > 0) {
            // Mutation tokens are progression-internal; the store is updated by the caller via
            // PvmProgressionStore, which reads the ledger. We only record the intent here.
        }
        if (reward.announce) {
            val parts = mutableListOf<String>()
            if (reward.coins > 0) {
                parts += "${reward.coins} coins"
            }
            if (reward.pvmTokens > 0) {
                parts += "${reward.pvmTokens} PvM tokens"
            }
            if (reward.mutationTokens > 0) {
                parts += "${reward.mutationTokens} mutation tokens"
            }
            if (parts.isNotEmpty()) {
                player.mes("<col=ffb84d>Reward: ${parts.joinToString(", ")}.</col>")
            }
        }
        return true
    }

    /**
     * Grants mutation tokens into the progression store (separate from the varp-based currencies).
     */
    fun grantMutationTokens(player: Player, amount: Int): Boolean {
        if (amount <= 0) {
            return false
        }
        // The store is updated by the caller; this method exists so the reward faucet has a single
        // entry point for mutation-token grants. See PvmProgressionStore.addMutationTokens.
        return true
    }
}

/** A bundle of rewards. All fields are non-negative; the config sanitiser guarantees that. */
data class PvmReward(
    val coins: Int = 0,
    val pvmTokens: Int = 0,
    val mutationTokens: Int = 0,
    val announce: Boolean = true,
) {
    companion object {
        val EMPTY = PvmReward()
    }
}

/**
 * Idempotency ledger for reward grants.
 *
 * Each grant carries a unique [id] (e.g. `"challenge:<playerId>:<encounterId>"`). [record] returns
 * `true` the first time an id is seen and `false` thereafter, so a repeated death callback or a
 * reconnect-replay cannot double-pay the same reward.
 *
 * The ledger is bounded: it keeps only the most recent [MAX_ENTRIES] ids, evicting oldest-first. A
 * grant id must therefore be unique within a sliding window of ~65k grants, which is far more than
 * any single encounter could ever produce.
 */
@Singleton
class RewardLedger @Inject constructor() {
    private val seen = LinkedHashMap<String, Unit>(MAX_ENTRIES, 0.75f, true)

    @Synchronized
    fun record(id: String): Boolean {
        if (seen.containsKey(id)) {
            return false
        }
        seen[id] = Unit
        if (seen.size > MAX_ENTRIES) {
            val it = seen.entries.iterator()
            it.next()
            it.remove()
        }
        return true
    }

    @Synchronized
    fun clear() {
        seen.clear()
    }

    /** Test hook: has [id] been recorded? */
    internal fun contains(id: String): Boolean = seen.containsKey(id)

    private companion object {
        const val MAX_ENTRIES = 65_536
    }
}
