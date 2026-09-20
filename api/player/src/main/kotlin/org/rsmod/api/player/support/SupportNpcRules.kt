package org.rsmod.api.player.support

import org.rsmod.api.config.refs.params
import org.rsmod.game.type.npc.UnpackedNpcType

/**
 * Per-NPC rules for the PvM support spellbook.
 *
 * Every value has a "full support" default, so the existing npc roster behaves exactly as before
 * and a boss has to explicitly opt out. There is deliberately no global "all bosses resist support"
 * rule - gating is per npc via cache params, set with an `NpcEditor`/`NpcBuilder` param map.
 *
 * Multipliers are per-mille: `1000` = 100 %, `0` = the effect is blocked for that npc. The
 * `special` multiplier is reserved for dedicated special-mechanic damage so it can be tuned
 * independently of ordinary auto-attacks.
 */
public object SupportNpcRules {
    public const val FULL_MULTIPLIER: Int = 1_000

    /**
     * Support can never be *amplified* above 100 %: the per-npc knob only ever scales it down, so a
     * typo of `100000` cannot become a 100x boss multiplier.
     */
    private const val MAX_MULTIPLIER: Int = FULL_MULTIPLIER

    /** Scales ordinary support damage dealt to (and support mitigation taken from) this npc. */
    public fun supportMultiplier(type: UnpackedNpcType): Int =
        (type.paramOrNull(params.npc_support_multiplier) ?: FULL_MULTIPLIER).coerceIn(
            0,
            MAX_MULTIPLIER,
        )

    /** Scales supportive healing while this npc is the player's opponent. */
    public fun healingMultiplier(type: UnpackedNpcType): Int =
        (type.paramOrNull(params.npc_support_healing_multiplier) ?: FULL_MULTIPLIER).coerceIn(
            0,
            MAX_MULTIPLIER,
        )

    /** Reserved for scripted special mechanics, so they can be tuned apart from auto-attacks. */
    public fun specialMultiplier(type: UnpackedNpcType): Int =
        (type.paramOrNull(params.npc_support_special_multiplier) ?: FULL_MULTIPLIER).coerceIn(
            0,
            MAX_MULTIPLIER,
        )

    /** `0` = ordinary npc, `1` = elite, `2` = boss, `3` = raid boss. Informational. */
    public fun tier(type: UnpackedNpcType): Int = type.paramOrNull(params.npc_pvm_tier) ?: 0

    /** Scales [bps] by [type]'s support multiplier. */
    public fun scale(bps: Int, type: UnpackedNpcType): Int =
        bps * supportMultiplier(type) / FULL_MULTIPLIER
}
