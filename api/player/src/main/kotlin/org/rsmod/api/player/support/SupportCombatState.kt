package org.rsmod.api.player.support

import java.util.WeakHashMap
import org.rsmod.game.entity.Player

/**
 * Server-authoritative PvM support state for a player: the temporary buffs and the absorption
 * shield granted by the PvM support spellbook.
 *
 * This lives in `api:player` (not in the content module) because the two hit processors that apply
 * it - [org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor] for outgoing PvM damage and
 * [org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor] for incoming PvM damage - sit at
 * or below this module and cannot depend on content.
 *
 * **PvM-only by construction.** Every reader here is called exclusively from an npc-targeted
 * (offence) or npc-originated (defence) code path, so a support buff can never leak into PvP. The
 * values are also purely additive: they are folded into the existing `totalBps` / `reductionBps`
 * pools instead of multiplying, so they cannot compound with gear, prayers or perks.
 *
 * All state is kept on the game thread; nothing here is thread-safe by design.
 */
public object SupportCombatState {
    /** Expiry is an absolute `currentMapClock` value; `0` means "not active". */
    private class Buffs {
        var damageBps = 0
        var damageExpiry = 0
        var accuracyBps = 0
        var accuracyExpiry = 0
        var incomingReductionBps = 0
        var reductionExpiry = 0
        var shieldPoints = 0
        var shieldExpiry = 0
    }

    private val buffs = WeakHashMap<Player, Buffs>()

    /**
     * `Battle Hymn` on the caster/party member: additive PvM outgoing-damage bonus for [ticks].
     * Re-applying replaces the value and refreshes the duration rather than stacking.
     */
    public fun applyDamageBuff(player: Player, bps: Int, ticks: Int) {
        val state = buffs.getOrPut(player) { Buffs() }
        state.damageBps = bps
        state.damageExpiry = player.currentMapClock + ticks
    }

    /** `Battle Hymn` accuracy component. */
    public fun applyAccuracyBuff(player: Player, bps: Int, ticks: Int) {
        val state = buffs.getOrPut(player) { Buffs() }
        state.accuracyBps = bps
        state.accuracyExpiry = player.currentMapClock + ticks
    }

    /**
     * `Iron Sanctuary`: additive PvM incoming-damage reduction for [ticks]. Re-applying replaces
     * the value and refreshes the duration, so two casts of the same spell never stack.
     */
    public fun applyIncomingReduction(player: Player, bps: Int, ticks: Int) {
        val state = buffs.getOrPut(player) { Buffs() }
        state.incomingReductionBps = bps
        state.reductionExpiry = player.currentMapClock + ticks
    }

    /**
     * `Guardian's Grace` shield: absorbs up to [points] PvM damage for [ticks].
     *
     * A new shield never stacks on an existing one - the larger pool wins and the duration is
     * refreshed.
     */
    public fun grantShield(player: Player, points: Int, ticks: Int) {
        val state = buffs.getOrPut(player) { Buffs() }
        state.shieldPoints = maxOf(state.shieldPoints, points)
        state.shieldExpiry = player.currentMapClock + ticks
    }

    public fun outgoingDamageBps(player: Player): Int = active(player)?.damageBps ?: 0

    public fun outgoingAccuracyBps(player: Player): Int = active(player)?.accuracyBps ?: 0

    public fun incomingReductionBps(player: Player): Int = active(player)?.incomingReductionBps ?: 0

    /** Remaining shield points, or `0` when there is no live shield. */
    public fun shieldPoints(player: Player): Int {
        val state = active(player) ?: return 0
        return if (player.currentMapClock < state.shieldExpiry) state.shieldPoints else 0
    }

    /**
     * Consumes up to [damage] shield points and returns the amount absorbed. Callers must subtract
     * the result from the incoming damage. Only ever called for npc-originated hits.
     */
    public fun absorb(player: Player, damage: Int): Int {
        if (damage <= 0) {
            return 0
        }
        val state = active(player) ?: return 0
        if (player.currentMapClock >= state.shieldExpiry || state.shieldPoints <= 0) {
            return 0
        }
        val absorbed = minOf(state.shieldPoints, damage)
        state.shieldPoints -= absorbed
        if (state.shieldPoints == 0) {
            state.shieldExpiry = 0
        }
        return absorbed
    }

    /** Drops every buff and shield - used on death and logout. */
    public fun clear(player: Player) {
        buffs.remove(player)
    }

    /** Milliseconds/ticks remaining for the HUD readout; `0` when inactive. */
    public fun remainingTicks(player: Player, kind: SupportBuffKind): Int {
        val state = buffs[player] ?: return 0
        val expiry =
            when (kind) {
                SupportBuffKind.BattleHymn -> maxOf(state.damageExpiry, state.accuracyExpiry)
                SupportBuffKind.IronSanctuary -> state.reductionExpiry
                SupportBuffKind.GuardiansGrace -> state.shieldExpiry
            }
        return (expiry - player.currentMapClock).coerceAtLeast(0)
    }

    private fun active(player: Player): Buffs? {
        val state = buffs[player] ?: return null
        val clock = player.currentMapClock
        if (state.damageExpiry <= clock) {
            state.damageBps = 0
        }
        if (state.accuracyExpiry <= clock) {
            state.accuracyBps = 0
        }
        if (state.reductionExpiry <= clock) {
            state.incomingReductionBps = 0
        }
        if (state.shieldExpiry <= clock) {
            state.shieldPoints = 0
        }
        if (state.damageBps == 0 && state.accuracyBps == 0 && state.incomingReductionBps == 0) {
            // Shield state is intentionally kept so `shieldPoints`/`absorb` can still report it.
            if (state.shieldPoints == 0) {
                return null
            }
        }
        return state
    }
}

/** The three timed PvM support effects, for HUD/readout purposes. */
public enum class SupportBuffKind {
    BattleHymn,
    IronSanctuary,
    GuardiansGrace,
}
