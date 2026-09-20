package org.rsmod.api.companion

/**
 * Independent 1-99 progression tracks earned through role work, kept separate from the combat
 * level/experience pair on [Companion].
 * - [MELEE]/[RANGED]/[MAGIC] experience comes from damage dealt with the matching attack style.
 * - [SUPPORT] experience comes from effective healing, shielding, buffing and cleansing.
 * - [TANK] experience comes from holding aggro, taking hostile damage, taunting and protecting.
 *
 * Tracks are deliberately decoupled from [CompanionClass]: a DPS companion that ends up holding
 * aggro still earns TANK experience, and a tank healing through abilities earns SUPPORT experience.
 */
public enum class CompanionSkill {
    MELEE,
    RANGED,
    MAGIC,
    SUPPORT,
    TANK,
}

/** The progression track fed by dealing damage with [style]. */
public fun CompanionAttackStyle.toCompanionSkill(): CompanionSkill =
    when (this) {
        CompanionAttackStyle.MELEE -> CompanionSkill.MELEE
        CompanionAttackStyle.RANGED -> CompanionSkill.RANGED
        CompanionAttackStyle.MAGIC -> CompanionSkill.MAGIC
    }

/**
 * Experience economy and level curve for [CompanionSkill] tracks.
 *
 * The level curve reuses [CompanionRules.levelFromExperience] (the same `100 * level` per-level
 * steps as the combat track) but caps the result at [MAX_LEVEL]. Experience keeps accumulating past
 * the cap, bounded by [MAX_TRACK_EXPERIENCE] so persisted values stay small.
 */
public object CompanionSkillXp {
    public const val MAX_LEVEL: Int = 99

    /** Hard bound for stored per-track experience (~level 105 worth of xp; 99 needs 485,100). */
    public const val MAX_TRACK_EXPERIENCE: Long = 600_000L

    // --- SUPPORT rates ---
    /** Experience per effective (non-overheal) hitpoint restored. */
    public const val HEAL_XP_PER_HP: Long = 2

    /** Maximum experience a single heal event may grant. */
    public const val HEAL_XP_CAP_PER_EVENT: Long = 400

    /** Experience per point of shield/absorption granted to an ally. */
    public const val SHIELD_XP_PER_POINT: Long = 2

    /** Maximum experience a single shield event may grant. */
    public const val SHIELD_XP_CAP_PER_EVENT: Long = 400

    /** Flat experience for a buff that actually applied a new effect (never on refreshes). */
    public const val BUFF_XP: Long = 30

    /** Experience per harmful effect actually removed by a cleanse. */
    public const val CLEANSE_XP_PER_EFFECT: Long = 20

    /** Maximum experience a single cleanse event may grant. */
    public const val CLEANSE_XP_CAP_PER_EVENT: Long = 100

    // --- TANK rates ---
    /** Flat experience for a taunt that successfully takes over an npc's threat. */
    public const val TAUNT_XP: Long = 50

    /** Experience per hitpoint of hostile damage actually suffered by the companion. */
    public const val TANKED_XP_PER_DAMAGE: Long = 1

    /** Maximum experience a single hit may grant. */
    public const val TANKED_XP_CAP_PER_EVENT: Long = 250

    /** Experience per hitpoint of damage prevented/redirected by a protection effect. */
    public const val PROTECTION_XP_PER_DAMAGE: Long = 2

    /** Maximum experience a single protection event may grant. */
    public const val PROTECTION_XP_CAP_PER_EVENT: Long = 400

    // --- combat style tracks (MELEE/RANGED/MAGIC) ---
    /** Experience per hitpoint of damage dealt to a hostile npc with the matching style. */
    public const val STYLE_XP_PER_DAMAGE: Long = 1

    /** Maximum experience a single hit may grant on a style track. */
    public const val STYLE_XP_CAP_PER_EVENT: Long = 250

    /** Flat experience granted while the companion provably holds aggro (per interval). */
    public const val AGGRO_HOLD_XP: Long = 8

    /** Minimum interval between aggro-hold awards (~10 game cycles). */
    public const val AGGRO_HOLD_INTERVAL_MILLIS: Long = 6_000

    /** Minimum interval between taunt awards; the spell cooldown is the real limiter. */
    public const val TAUNT_MIN_INTERVAL_MILLIS: Long = 4_000

    /**
     * Minimum interval between flat-action awards (buffs/cleanses); deduplicates callers that
     * report the same logical action twice in one tick.
     */
    public const val FLAT_ACTION_MIN_INTERVAL_MILLIS: Long = 1_000

    /**
     * How long after the companion last left [CompanionState.COMBAT] its support actions still
     * count as combat support. Lets between-pull heals earn experience while keeping fully idle
     * companions at zero.
     */
    public const val COMBAT_GRACE_MILLIS: Long = 10_000

    /** 1-99 level for [experience], reusing the combat track's per-level steps. */
    public fun levelFor(experience: Long): Int =
        CompanionRules.levelFromExperience(experience.coerceAtLeast(0)).coerceAtMost(MAX_LEVEL)
}
