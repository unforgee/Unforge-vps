package org.rsmod.api.companion

/**
 * Server-side combat meter contract. Producers may be the authoritative hit processors or a
 * protocol adapter.
 */
public data class CombatTelemetryEvent(
    public val encounterId: Long,
    public val sourceId: Long,
    public val ownerId: Long,
    public val sourceName: String,
    public val targetId: Long,
    public val ability: String,
    public val damage: Int = 0,
    public val effectiveHealing: Int = 0,
    public val overhealing: Int = 0,
    public val damageTaken: Int = 0,
    public val targetIsBoss: Boolean = false,
    public val targetIsPlayer: Boolean = false,
    public val timestampMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(encounterId >= 0)
        require(sourceId >= 0)
        require(ownerId >= 0)
        require(damage >= 0 && effectiveHealing >= 0 && overhealing >= 0 && damageTaken >= 0)
        require(ability.length <= 64)
    }
}

/** Compact aggregate used by both server serializers and client-side protocol adapters. */
public class CombatTelemetryAccumulator {
    private val values = linkedMapOf<Long, CombatTelemetryTotals>()

    public fun record(event: CombatTelemetryEvent) {
        val current =
            values.getOrPut(event.sourceId) {
                CombatTelemetryTotals(event.sourceId, event.ownerId, event.sourceName)
            }
        current.damage += event.damage
        current.effectiveHealing += event.effectiveHealing
        current.overhealing += event.overhealing
        current.damageTaken += event.damageTaken
        current.hitCount += if (event.damage > 0) 1 else 0
        current.maxHit = maxOf(current.maxHit, event.damage)
        if (event.ability.isNotBlank()) {
            current.abilityBreakdown[event.ability] =
                (current.abilityBreakdown[event.ability] ?: 0) + event.damage
        }
    }

    public fun snapshot(): List<CombatTelemetryTotals> =
        values.values.map { it.copy(abilityBreakdown = it.abilityBreakdown.toMutableMap()) }

    public fun reset() {
        values.clear()
    }
}

public data class CombatTelemetryTotals(
    public val sourceId: Long,
    public val ownerId: Long,
    public val sourceName: String,
    public var damage: Int = 0,
    public var effectiveHealing: Int = 0,
    public var overhealing: Int = 0,
    public var damageTaken: Int = 0,
    public var hitCount: Int = 0,
    public var maxHit: Int = 0,
    public var abilityBreakdown: MutableMap<String, Int> = mutableMapOf(),
)
