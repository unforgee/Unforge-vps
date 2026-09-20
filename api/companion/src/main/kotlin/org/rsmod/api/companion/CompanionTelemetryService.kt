package org.rsmod.api.companion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side companion combat meter. Producers (hit processors, role spells, xp observers) record
 * lightweight [CombatTelemetryEvent]s; consumers (debug commands, UI snapshots) read per-owner
 * aggregates. Events are tiny and recorded only on real combat outcomes - there is no per-tick
 * scanning.
 */
@Singleton
public class CompanionTelemetryService @Inject constructor() {
    private val meters = ConcurrentHashMap<Long, CombatTelemetryAccumulator>()

    public fun record(ownerCharacterId: Long, event: CombatTelemetryEvent) {
        meters.computeIfAbsent(ownerCharacterId) { CombatTelemetryAccumulator() }.record(event)
    }

    public fun snapshot(ownerCharacterId: Long): List<CombatTelemetryTotals> =
        meters[ownerCharacterId]?.snapshot().orEmpty()

    public fun reset(ownerCharacterId: Long) {
        meters.remove(ownerCharacterId)
    }

    public fun remove(ownerCharacterId: Long) {
        meters.remove(ownerCharacterId)
    }
}
