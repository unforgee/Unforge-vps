package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Centralised free-tile selection for companion follow movement.
 *
 * Every companion that wants to move during a cycle resolves its destination through [resolveTile].
 * Candidates are produced deterministically by [followCandidates] — expanding chebyshev rings
 * around the owner, each ring ordered by the slot's preferred formation direction — and a tile is
 * only returned when its entire footprint is walkable, unoccupied and (for regular walking)
 * actually reachable by the route finder. Callers keep a per-cycle reservation set so two
 * companions can never be assigned the same tile within the same tick.
 */
@Singleton
public class CompanionFollowMovement
@Inject
constructor(private val collision: CollisionFlagMap, private val routeFactory: RouteFactory) {
    /**
     * Resolves the best free follow tile for [bot] near [playerCoords], or `null` when no usable
     * tile exists this cycle.
     *
     * @param occupied tiles taken by the owner, other companion bots and tiles already assigned to
     *   another companion this cycle. [bot]'s own tile must not be part of this set so it never
     *   blocks itself.
     * @param requireRoute when `true` (regular follow walking) a candidate is only returned if the
     *   route finder can path to it; when `false` (catch-up telejump) collision freedom is enough.
     * @param followDistance preferred chebyshev ring from the owner (Behaviour tab setting); rings
     *   are scanned closest-preference-first so the companion settles at its configured spacing
     *   whenever a free tile exists there.
     */
    public fun resolveTile(
        bot: Player,
        playerCoords: CoordGrid,
        slot: Int,
        occupied: Set<CoordGrid>,
        requireRoute: Boolean,
        followDistance: Int = 1,
    ): CoordGrid? =
        selectFollowTile(
            player = playerCoords,
            self = bot.coords,
            size = bot.size,
            slot = slot,
            occupied = occupied,
            followDistance = followDistance,
            walkable = { tile -> collision.isZoneValid(tile) && !collision.isWalkBlocked(tile) },
            reachable = { tile -> !requireRoute || routeFactory.create(bot.avatar, tile).success },
        )
}

/** Maximum chebyshev radius (in rings) scanned for a free follow tile. */
internal const val FOLLOW_SCAN_RADIUS: Int = 4

/**
 * Preferred formation direction per companion slot: companions fan out around the owner instead of
 * all collapsing onto the same tile.
 */
internal fun formationOffset(slot: Int): Pair<Int, Int> =
    when (slot) {
        1 -> Pair(-1, 0)
        2 -> Pair(0, -1)
        3 -> Pair(1, 0)
        4 -> Pair(0, 1)
        else -> Pair(-1, 0)
    }

/**
 * Deterministic candidate order: chebyshev rings around [player] ordered by proximity to
 * [preferredRing] (the Behaviour tab's follow distance, `1` by default which preserves the original
 * nearest-first scan). Ties inside a ring are broken by distance to the slot's preferred formation
 * anchor (the formation direction scaled to the ring radius), then by `(x, z)`.
 */
internal fun followCandidates(
    player: CoordGrid,
    slot: Int,
    preferredRing: Int = 1,
): Sequence<CoordGrid> = sequence {
    val (ox, oz) = formationOffset(slot)
    val rings = (1..FOLLOW_SCAN_RADIUS).sortedWith(compareBy({ abs(it - preferredRing) }, { it }))
    for (ring in rings) {
        val anchorX = player.x + ox * ring
        val anchorZ = player.z + oz * ring
        ringCells(player, ring)
            .sortedWith(
                compareBy({ max(abs(it.x - anchorX), abs(it.z - anchorZ)) }, { it.x }, { it.z })
            )
            .forEach { yield(it) }
    }
}

private fun ringCells(center: CoordGrid, ring: Int): List<CoordGrid> {
    val cells = ArrayList<CoordGrid>(ring * 8)
    for (dx in -ring..ring) {
        for (dz in -ring..ring) {
            if (max(abs(dx), abs(dz)) == ring) {
                cells += center.translate(dx, dz)
            }
        }
    }
    return cells
}

/** Every map cell covered by an entity of [size] tiles standing with its sw corner on [sw]. */
internal fun footprintCells(sw: CoordGrid, size: Int): List<CoordGrid> =
    if (size <= 1) {
        listOf(sw)
    } else {
        buildList {
            for (dx in 0 until size) {
                for (dz in 0 until size) {
                    add(sw.translate(dx, dz))
                }
            }
        }
    }

/**
 * Picks the best free follow tile for a companion of [size] tiles currently standing on [self], or
 * `null` when nothing usable exists.
 *
 * A candidate is accepted only when:
 * - its whole footprint is free of [occupied] tiles (the owner's tile, other companions' tiles and
 *   tiles already assigned this cycle). The companion's own tile is never in [occupied], so it can
 *   always keep standing where it is — but if it shares a tile with the owner or another companion
 *   the overlap is detected and the candidate rejected.
 * - every footprint cell satisfies [walkable] (zone allocated and not walk-blocked).
 * - it satisfies [reachable] (skipped for [self] — the companion is already standing there — so an
 *   unreachable current tile never forces a pointless move request).
 */
internal fun selectFollowTile(
    player: CoordGrid,
    self: CoordGrid,
    size: Int,
    slot: Int,
    occupied: Set<CoordGrid>,
    followDistance: Int = 1,
    walkable: (CoordGrid) -> Boolean,
    reachable: (CoordGrid) -> Boolean,
): CoordGrid? {
    for (candidate in followCandidates(player, slot, preferredRing = followDistance)) {
        val footprint = footprintCells(candidate, size)
        if (footprint.any(occupied::contains)) {
            continue
        }
        if (footprint.any { !walkable(it) }) {
            continue
        }
        if (candidate != self && !reachable(candidate)) {
            continue
        }
        return candidate
    }
    return null
}
