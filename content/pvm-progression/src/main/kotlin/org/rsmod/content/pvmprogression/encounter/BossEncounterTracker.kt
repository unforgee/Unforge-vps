package org.rsmod.content.pvmprogression.encounter

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** The authoritative, server-side boss-encounter tracker. See package docs for the full design. */
@Singleton
class BossEncounterTracker
@Inject
constructor(private val mapClock: MapClock, private val configSource: PvmProgressionConfigSource) {
    private val encounters = WeakHashMap<Player, BossEncounter>()
    private val encounterIdSeq = AtomicLong(1L)

    /** The live encounter for [player], or `null` if they are not currently fighting a boss. */
    fun current(player: Player): BossEncounter? = encounters[player]

    /** Snapshot of all open encounters, for the per-cycle sampler. */
    fun snapshot(): Map<Player, BossEncounter> = encounters.toMap()

    /** Opens (or refreshes) an encounter for [player] against [boss]. */
    fun touch(player: Player, boss: Npc): BossEncounter {
        val cycle = mapClock.cycle
        val existing = encounters[player]
        if (existing != null && existing.bossId == boss.visType.id && boss.hitpoints > 0) {
            existing.lastActivityCycle = cycle
            existing.bossRef = boss
            return existing
        }
        if (existing != null) {
            abandon(player, reason = AbandonReason.SWITCHED_TARGET)
        }
        val encounter =
            BossEncounter(
                id = encounterIdSeq.getAndIncrement(),
                player = player,
                bossId = boss.visType.id,
                bossName = boss.visType.name,
                bossRef = boss,
                bossMaxHp = boss.baseHitpointsLvl,
                startCycle = cycle,
                lastActivityCycle = cycle,
                startCoords = player.coords,
                startHitpoints = player.hitpoints,
                startPrayer = player.prayerLvl,
            )
        encounters[player] = encounter
        return encounter
    }

    /** Ends the encounter for [player] with [reason]. Returns the abandoned encounter or `null`. */
    fun abandon(player: Player, reason: AbandonReason): BossEncounter? {
        val encounter = encounters.remove(player) ?: return null
        encounter.endCycle = mapClock.cycle
        encounter.endReason = reason
        return encounter
    }

    /**
     * Per-cycle maintenance: abandons encounters that have gone idle / out-of-range /
     * over-duration.
     */
    fun tick() {
        val config = configSource().encounter
        val cycle = mapClock.cycle
        val toAbandon = mutableListOf<Pair<Player, AbandonReason>>()
        for ((player, encounter) in encounters) {
            val boss = encounter.bossRef
            val bossDead = boss == null || !boss.isSlotAssigned || boss.hitpoints <= 0
            if (bossDead) {
                encounter.lastActivityCycle = cycle
                continue
            }
            if (chebyshev(player.coords, encounter.startCoords) > config.abandonDistance) {
                toAbandon += player to AbandonReason.TELEPORTED
                continue
            }
            if (cycle - encounter.startCycle > config.maxDurationCycles) {
                toAbandon += player to AbandonReason.TIMEOUT
                continue
            }
            if (cycle - encounter.lastActivityCycle > config.idleTimeoutCycles) {
                toAbandon += player to AbandonReason.IDLE
            }
        }
        for ((player, reason) in toAbandon) {
            abandon(player, reason)
        }
    }

    /** Refreshes `lastActivityCycle` when the player takes damage during an open encounter. */
    fun notePlayerTookDamage(player: Player) {
        encounters[player]?.lastActivityCycle = mapClock.cycle
    }

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int {
        if (a.level != b.level) {
            return Int.MAX_VALUE
        }
        return maxOf(abs(a.x - b.x), abs(a.z - b.z))
    }
}
