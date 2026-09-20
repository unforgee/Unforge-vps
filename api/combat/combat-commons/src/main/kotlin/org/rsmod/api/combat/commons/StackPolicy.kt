package org.rsmod.api.combat.commons

/** Determines how multiple applications of the same status effect to a target are resolved. */
public enum class StackPolicy {
    /**
     * Retains the stronger potency (e.g. poison damage). If the new application has higher potency,
     * it replaces potency and refreshes duration; if equal, it extends or keeps the longest
     * duration; if weaker, the existing stronger status is kept.
     */
    KEEP_STRONGEST,

    /** Refreshes the active duration without increasing stacks (capped at 1 or definition cap). */
    REFRESH_DURATION,

    /**
     * Adds stacks up to the definition stack cap and refreshes duration (e.g. Bleed, Burn,
     * Vulnerable).
     */
    INCREMENT_STACKS,

    /** Adds the incoming duration to the existing remaining duration without changing stacks. */
    EXTEND_DURATION,

    /** Replaces the existing instance completely with the new application. */
    REPLACE,
}
