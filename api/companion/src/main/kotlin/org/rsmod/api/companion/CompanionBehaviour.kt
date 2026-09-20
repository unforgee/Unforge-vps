package org.rsmod.api.companion

/**
 * How a companion picks its enemy target within a combat mode. The combat mode
 * ([CompanionCombatMode]) decides *whether* the companion fights; the priority decides *which*
 * candidate it engages when several are available.
 */
public enum class CompanionTargetPriority {
    /** Assist: fight whatever the owner is fighting, falling back to the owner's attacker. */
    OWNER_TARGET,

    /** Protect: engage the npc currently attacking the owner before anything else. */
    OWNER_ATTACKER,

    /** Free: engage the nearest eligible hostile, regardless of what the owner is doing. */
    NEAREST_HOSTILE,

    /**
     * Support focus: enemy selection follows [OWNER_TARGET], but the companion's support pipeline
     * (heals) is tuned toward the lowest-health ally - the default heal behaviour. Kept explicit so
     * the Behaviour tab can express a heal-centric setup.
     */
    LOWEST_HEALTH_ALLY,
}

/**
 * Player-configured AI behaviour for a companion, persisted on the [Companion] record and applied
 * server-side on every tick - the UI is a view of this value, never the other way around.
 *
 * @param targetPriority which candidate the companion engages (see [CompanionTargetPriority]).
 * @param followDistance preferred spacing behind the owner while following, in tiles.
 * @param autoTaunt whether TANK companions may cast their automatic taunt spells.
 * @param autoDefend whether TANK companions may keep their defensive ward up automatically.
 * @param autoHeal whether SUPPORT companions may cast automatic heals.
 * @param healBelowPercent only heal allies at or below this hitpoints percentage.
 * @param autoBuff whether SUPPORT companions may cast automatic buffs.
 * @param catchUpTeleport whether the companion may catch-up teleport when left beyond the leash.
 */
public data class CompanionBehaviour(
    public val targetPriority: CompanionTargetPriority = CompanionTargetPriority.OWNER_TARGET,
    public val followDistance: Int = DEFAULT_FOLLOW_DISTANCE,
    public val autoTaunt: Boolean = true,
    public val autoDefend: Boolean = true,
    public val autoHeal: Boolean = true,
    public val healBelowPercent: Int = DEFAULT_HEAL_BELOW_PERCENT,
    public val autoBuff: Boolean = true,
    public val catchUpTeleport: Boolean = true,
) {
    init {
        require(followDistance in MIN_FOLLOW_DISTANCE..MAX_FOLLOW_DISTANCE)
        require(healBelowPercent in MIN_HEAL_BELOW_PERCENT..MAX_HEAL_BELOW_PERCENT)
    }

    /**
     * Stable single-column encoding: `PRIORITY;distance;taunt;defend;heal;below;buff;catchup`
     * (bools as 0/1).
     */
    public fun serialize(): String = buildString {
        append(targetPriority.name)
        append(FIELD_SEPARATOR)
        append(followDistance)
        append(FIELD_SEPARATOR)
        append(if (autoTaunt) 1 else 0)
        append(FIELD_SEPARATOR)
        append(if (autoDefend) 1 else 0)
        append(FIELD_SEPARATOR)
        append(if (autoHeal) 1 else 0)
        append(FIELD_SEPARATOR)
        append(healBelowPercent)
        append(FIELD_SEPARATOR)
        append(if (autoBuff) 1 else 0)
        append(FIELD_SEPARATOR)
        append(if (catchUpTeleport) 1 else 0)
    }

    public companion object {
        public const val MIN_FOLLOW_DISTANCE: Int = 1
        public const val MAX_FOLLOW_DISTANCE: Int = 8
        public const val DEFAULT_FOLLOW_DISTANCE: Int = 2
        public const val MIN_HEAL_BELOW_PERCENT: Int = 1
        public const val MAX_HEAL_BELOW_PERCENT: Int = 99
        public const val DEFAULT_HEAL_BELOW_PERCENT: Int = 80

        private const val FIELD_SEPARATOR: Char = ';'

        /** The encoding written by [serialize] when a row predates the behaviour column. */
        public val DEFAULT_SERIALIZED: String = CompanionBehaviour().serialize()

        /**
         * Parses a [serialize]d value. Anything unrecognized - `null`, old/short encodings or
         * out-of-range values - falls back to the safe defaults so a migration never loses the
         * companion row.
         */
        public fun deserialize(raw: String?): CompanionBehaviour {
            if (raw.isNullOrBlank()) return CompanionBehaviour()
            val fields = raw.split(FIELD_SEPARATOR)
            val priority =
                fields.getOrNull(0)?.let { name ->
                    CompanionTargetPriority.entries.firstOrNull { it.name == name }
                } ?: return CompanionBehaviour()
            return runCatching {
                    CompanionBehaviour(
                        targetPriority = priority,
                        followDistance = fields.getOrNull(1)?.toInt() ?: DEFAULT_FOLLOW_DISTANCE,
                        autoTaunt = fields.getOrNull(2)?.toInt() != 0,
                        autoDefend = fields.getOrNull(3)?.toInt() != 0,
                        autoHeal = fields.getOrNull(4)?.toInt() != 0,
                        healBelowPercent =
                            fields.getOrNull(5)?.toInt() ?: DEFAULT_HEAL_BELOW_PERCENT,
                        autoBuff = fields.getOrNull(6)?.toInt() != 0,
                        catchUpTeleport = fields.getOrNull(7)?.toInt() != 0,
                    )
                }
                .getOrDefault(CompanionBehaviour())
        }
    }
}
