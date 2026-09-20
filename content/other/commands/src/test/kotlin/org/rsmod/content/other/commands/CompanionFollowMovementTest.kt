package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

public class CompanionFollowMovementTest {
    private val player = CoordGrid(100, 100, 0)
    private val farAway = CoordGrid(90, 90, 0)

    private fun grid(x: Int, z: Int, level: Int = 0) = CoordGrid(x, z, level)

    private fun select(
        self: CoordGrid = farAway,
        size: Int = 1,
        slot: Int = 1,
        occupied: Set<CoordGrid> = setOf(player),
        blocked: Set<CoordGrid> = emptySet(),
        unreachable: Set<CoordGrid> = emptySet(),
    ): CoordGrid? =
        selectFollowTile(
            player = player,
            self = self,
            size = size,
            slot = slot,
            occupied = occupied,
            walkable = { it !in blocked },
            reachable = { it !in unreachable },
        )

    @Test
    public fun `single companion picks its preferred tile next to the player`() {
        // Slot 1 prefers the west side: (99, 100).
        val tile = select(slot = 1)
        assertEquals(grid(99, 100), tile)
        assertNotEquals(player, tile)
    }

    @Test
    public fun `two companions never share a follow tile`() {
        val reserved = mutableSetOf<CoordGrid>()
        val occupied = mutableSetOf(player)
        val first =
            selectFollowTile(
                player = player,
                self = farAway,
                size = 1,
                slot = 1,
                occupied = occupied + reserved,
                walkable = { true },
                reachable = { true },
            )!!
        reserved += first
        val second =
            selectFollowTile(
                player = player,
                self = farAway,
                size = 1,
                slot = 2,
                occupied = occupied + reserved,
                walkable = { true },
                reachable = { true },
            )!!
        assertNotEquals(first, second)
        assertEquals(grid(100, 99), second) // slot 2 prefers the south side
    }

    @Test
    public fun `four companions spread to distinct tiles around the player`() {
        val reserved = mutableSetOf<CoordGrid>()
        val chosen = mutableListOf<CoordGrid>()
        for (slot in 1..4) {
            val tile =
                selectFollowTile(
                    player = player,
                    self = farAway,
                    size = 1,
                    slot = slot,
                    occupied = setOf(player) + reserved,
                    walkable = { true },
                    reachable = { true },
                )!!
            reserved += tile
            chosen += tile
        }
        assertEquals(4, chosen.toSet().size)
        assertTrue(player !in chosen)
        // Each slot settles on its own cardinal side of the owner.
        assertEquals(listOf(grid(99, 100), grid(100, 99), grid(101, 100), grid(100, 101)), chosen)
    }

    @Test
    public fun `companion standing on a valid tile keeps it`() {
        // Its own tile is never in `occupied`, so it does not block itself.
        val self = grid(99, 100)
        val tile = select(self = self, slot = 1)
        assertEquals(self, tile)
    }

    @Test
    public fun `companion standing on the owner moves off the tile`() {
        val tile = select(self = player, slot = 1)
        assertEquals(grid(99, 100), tile)
        assertNotEquals(player, tile)
    }

    @Test
    public fun `companion standing on another companion moves off the tile`() {
        val other = grid(99, 100)
        val tile = select(self = other, slot = 2, occupied = setOf(player, other))
        assertEquals(grid(100, 99), tile)
        assertNotEquals(other, tile)
    }

    @Test
    public fun `blocked nearest tiles fall back to the next ring`() {
        val ring1 =
            setOf(
                grid(99, 99),
                grid(99, 100),
                grid(99, 101),
                grid(100, 99),
                grid(100, 101),
                grid(101, 99),
                grid(101, 100),
                grid(101, 101),
            )
        val tile = select(slot = 1, blocked = ring1)
        assertEquals(2, tile!!.chebyshevDistance(player))
        assertTrue(tile !in ring1)
    }

    @Test
    public fun `reserved tile is skipped for the next free candidate`() {
        val reserved = grid(99, 100)
        val tile = select(slot = 1, occupied = setOf(player, reserved))
        // Next slot-1-preferred candidate after the west tile is taken.
        assertEquals(grid(99, 99), tile)
    }

    @Test
    public fun `unreachable tiles are skipped`() {
        val tile = select(slot = 1, unreachable = setOf(grid(99, 100)))
        assertEquals(grid(99, 99), tile)
    }

    @Test
    public fun `everything blocked and occupied returns null instead of stacking`() {
        val everything = followCandidates(player, 1).take(80).toList() + listOf(farAway, player)
        val tile = select(slot = 1, blocked = everything.toSet())
        assertNull(tile)
    }

    @Test
    public fun `larger than 1x1 companion needs a fully free footprint`() {
        // A 2x2 companion cannot anchor on a sw tile whose footprint touches an occupied
        // tile: (99,100) occupied rejects both (99,100) and (99,99) as sw candidates.
        val occupied = setOf(player, grid(99, 100))
        val tile = select(slot = 1, size = 2, occupied = occupied)!!
        val footprint = footprintCells(tile, 2)
        assertEquals(4, footprint.size)
        assertTrue(footprint.none { it in occupied })
    }

    @Test
    public fun `candidate order is deterministic`() {
        val first = followCandidates(player, 1).take(8).toList()
        val second = followCandidates(player, 1).take(8).toList()
        assertEquals(first, second)
        assertEquals(8, first.size)
        // Nearest ring first, slot-1 anchor (west) preferred inside the ring.
        assertEquals(grid(99, 100), first.first())
        assertTrue(first.all { it.chebyshevDistance(player) == 1 })
    }
}
