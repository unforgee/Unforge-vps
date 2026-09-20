package org.rsmod.api.npc.threat

/**
 * Centralized balance constants for the PvM threat/aggro and support systems. Every tunable lives
 * here - combat classes must not scatter their own threat numbers.
 */
public object ThreatConfig {
    /** Verbose `[THREAT]`/`[AGGRO]` logging, toggled at runtime by `::threatdebug`. */
    public var DEBUG_THREAT: Boolean = false

    // --- threat generation ---

    /** 1 point of damage = this much base threat (scaled by role and gear). */
    public const val DAMAGE_THREAT_BPS: Int = 10_000

    /** Threat multiplier for damage dealt by a [CombatRole.TANK]. */
    public const val TANK_THREAT_BPS: Int = 25_000

    /** Threat multiplier for damage dealt by a [CombatRole.HYBRID]. */
    public const val HYBRID_THREAT_BPS: Int = 12_500

    /** Threat multiplier for damage dealt by DAMAGE/SUPPORT roles. */
    public const val NORMAL_THREAT_BPS: Int = 10_000

    /** Threat per effective hitpoint healed (bps of the healed amount). */
    public const val HEAL_THREAT_BPS: Int = 5_000

    /** Threat per point of shield/protection granted to an ally (bps). */
    public const val SHIELD_THREAT_BPS: Int = 5_000

    // --- taunt ---

    /** Forced-target duration of a taunt, in game cycles (~0.6s each). 8 cycles ≈ 4.8s. */
    public const val TAUNT_DURATION_CYCLES: Int = 8

    /** Flat threat injected on top of the current top entry when a taunt lands. */
    public const val TAUNT_THREAT_BONUS: Long = 250

    // --- target switching ---

    /**
     * A challenger adjacent to the npc must exceed the current target's threat by this percentage
     * (bps) to pull aggro. Proximity protection against ping-ponging.
     */
    public const val SWITCH_THRESHOLD_MELEE_BPS: Int = 11_000

    /** Same as [SWITCH_THRESHOLD_MELEE_BPS] for challengers outside melee range. */
    public const val SWITCH_THRESHOLD_RANGED_BPS: Int = 12_000

    /** Maximum chebyshev distance (tiles) at which a threat holder remains a valid target. */
    public const val TARGET_MAX_DISTANCE: Int = 24

    // --- decay ---

    /**
     * Per-entry threat decay in basis points per cycle of inactivity, applied lazily when an entry
     * is touched or evaluated. `10` ≈ 0.1%/cycle; a player who stops generating threat fades out of
     * contention over a few minutes instead of instantly.
     */
    public const val DECAY_BPS_PER_CYCLE: Int = 10

    // --- survivability ---

    /**
     * Hard cap for equipment-driven incoming damage reduction (bps). Lives on
     * `EquipmentCombatStats` (api:player) because the player hit processor reads it there.
     */
    public val MAX_DAMAGE_REDUCTION_BPS: Int
        get() = org.rsmod.api.player.bonus.EquipmentCombatStats.MAX_DAMAGE_REDUCTION_BPS

    // --- support AI thresholds (hitpoints per-mille of max) ---

    /** Below this hp ratio a heal is an emergency that pre-empts everything else. */
    public const val SUPPORT_CRITICAL_HP_PERMILLE: Int = 350

    /** The party tank is healed once it falls below this ratio. */
    public const val SUPPORT_TANK_HEAL_PERMILLE: Int = 450

    /** Any ally below this ratio qualifies for a normal heal. */
    public const val SUPPORT_HEAL_PERMILLE: Int = 800

    // --- support heal scaling (style contribution, bps of style power) ---

    /** Melee support: share of `meleePower` added to `healingPower`. */
    public const val MELEE_SUPPORT_HEAL_SCALING_BPS: Int = 2_500

    /** Ranged support: share of `rangedPower` added to `healingPower`. */
    public const val RANGED_SUPPORT_HEAL_SCALING_BPS: Int = 2_500

    /** Magic support: share of `magicPower` added to `healingPower`. */
    public const val MAGIC_SUPPORT_HEAL_SCALING_BPS: Int = 5_000
}
