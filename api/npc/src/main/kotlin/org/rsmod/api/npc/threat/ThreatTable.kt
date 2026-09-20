package org.rsmod.api.npc.threat

/** Threat accumulated by one player against one npc. */
public class ThreatEntry(
    public var threat: Long,
    /** Map cycle the entry was last touched; decay is computed lazily from this. */
    public var lastCycle: Int = 0,
)

/**
 * The per-npc threat table. Pure logic - engine entities never enter this class, so it is fully
 * unit-testable. Players are keyed by their unique `uuid` (companion bots carry negative uuids,
 * which is exactly what makes them distinguishable here).
 */
public class ThreatTable {
    public var mode: ThreatMode = ThreatMode.NORMAL

    /** player uuid -> accumulated threat. */
    public val entries: LinkedHashMap<Long, ThreatEntry> = LinkedHashMap()

    /** Uuid currently force-holding aggro (taunt or scripted override), `null` when inactive. */
    public var forcedKey: Long? = null
        private set

    /** Cycle at which [forcedKey] expires. */
    public var forcedUntilCycle: Int = 0
        private set

    /** Whether the current forced target came from a boss script rather than a taunt. */
    public var forcedScripted: Boolean = false
        private set

    public fun forcedTarget(cycle: Int): Long? =
        if (forcedKey != null && cycle < forcedUntilCycle) forcedKey else null

    /** Lazily applies inactivity decay to [entry], returning the effective threat. */
    private fun decayed(entry: ThreatEntry, cycle: Int, decayBps: Int): Long {
        val elapsed = cycle - entry.lastCycle
        if (elapsed > 0 && entry.threat > 0) {
            entry.threat =
                (entry.threat - entry.threat * decayBps * elapsed / 10_000).coerceAtLeast(0)
            entry.lastCycle = cycle
        }
        return entry.threat
    }

    /** Adds [amount] threat for [key] and returns the entry's new total. */
    public fun add(key: Long, amount: Long, cycle: Int, decayBps: Int): Long {
        if (amount <= 0) {
            return entries[key]?.let { decayed(it, cycle, decayBps) } ?: 0
        }
        val entry = entries.getOrPut(key) { ThreatEntry(0, cycle) }
        decayed(entry, cycle, decayBps)
        entry.threat += amount
        entry.lastCycle = cycle
        return entry.threat
    }

    public fun threatOf(key: Long, cycle: Int, decayBps: Int): Long =
        entries[key]?.let { decayed(it, cycle, decayBps) } ?: 0

    /**
     * Forces [key] to hold aggro until [untilCycle]. The holder is raised to at least `currentTop +
     * tauntBonus` threat so normal selection keeps favouring them afterwards.
     */
    public fun forceTarget(
        key: Long,
        untilCycle: Int,
        cycle: Int,
        tauntBonus: Long,
        decayBps: Int,
        scripted: Boolean = false,
    ) {
        if (!scripted) {
            val top = topEntry(cycle, decayBps)?.value?.threat ?: 0
            val entry = entries.getOrPut(key) { ThreatEntry(0, cycle) }
            decayed(entry, cycle, decayBps)
            entry.threat = maxOf(entry.threat, top + tauntBonus)
            entry.lastCycle = cycle
        }
        forcedKey = key
        forcedUntilCycle = untilCycle
        forcedScripted = scripted
    }

    /** Highest-threat valid key, or `null` when no valid entry exists. */
    public fun topKey(cycle: Int, decayBps: Int, valid: (Long) -> Boolean): Long? =
        topEntry(cycle, decayBps, valid)?.key

    private fun topEntry(
        cycle: Int,
        decayBps: Int,
        valid: (Long) -> Boolean = { true },
    ): Map.Entry<Long, ThreatEntry>? =
        entries.entries
            .filter { (key, entry) -> valid(key) && decayed(entry, cycle, decayBps) > 0 }
            .maxByOrNull { it.value.threat }

    /**
     * Picks the key the npc should attack under normal threat rules.
     * - If a forced target is active and still valid, it wins unconditionally.
     * - If the current target is invalid, the highest valid threat holder wins.
     * - Otherwise a challenger must exceed the current holder's threat by
     *   [ThreatConfig.SWITCH_THRESHOLD_MELEE_BPS] (challenger within [meleeDistance] tiles) or
     *   [ThreatConfig.SWITCH_THRESHOLD_RANGED_BPS] - proximity protection against ping-ponging.
     *
     * @return the key to engage, `null` to keep the current target, or [ThreatTable.DROP_TARGET]
     *   when the current target is invalid and nobody else qualifies.
     */
    public fun selectTarget(
        currentKey: Long?,
        cycle: Int,
        decayBps: Int,
        valid: (Long) -> Boolean,
        distance: (Long) -> Int,
        meleeDistance: Int = 1,
        meleeThresholdBps: Int = ThreatConfig.SWITCH_THRESHOLD_MELEE_BPS,
        rangedThresholdBps: Int = ThreatConfig.SWITCH_THRESHOLD_RANGED_BPS,
    ): Long? {
        val forced = forcedTarget(cycle)
        if (forced != null) {
            if (valid(forced)) {
                return forced
            }
            // Forced holder became invalid (died, logged out, left) - drop the force and let
            // normal threat selection run instead of sticking to a stale target.
            forcedKey = null
            forcedUntilCycle = 0
            forcedScripted = false
        }

        val challenger = topEntry(cycle, decayBps, valid)
        if (challenger == null) {
            return if (currentKey != null && !valid(currentKey)) DROP_TARGET else null
        }
        if (challenger.key == currentKey) {
            return null
        }
        if (currentKey == null || !valid(currentKey)) {
            return challenger.key
        }

        val currentThreat = entries[currentKey]?.let { decayed(it, cycle, decayBps) } ?: 0
        val thresholdBps =
            if (distance(challenger.key) <= meleeDistance) meleeThresholdBps else rangedThresholdBps
        return if (challenger.value.threat * 10_000 > currentThreat * thresholdBps) {
            challenger.key
        } else {
            null
        }
    }

    /** Removes [key] and clears a pending force held by it. */
    public fun remove(key: Long) {
        entries.remove(key)
        if (forcedKey == key) {
            forcedKey = null
            forcedUntilCycle = 0
            forcedScripted = false
        }
    }

    public companion object {
        /** Sentinel returned by [selectTarget] when the npc should drop its current target. */
        public const val DROP_TARGET: Long = Long.MIN_VALUE
    }
}
