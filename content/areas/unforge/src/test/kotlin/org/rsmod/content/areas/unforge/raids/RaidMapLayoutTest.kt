package org.rsmod.content.areas.unforge.raids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RaidMapLayoutTest {
    @Test
    fun `theatre rooms resolve to distinct cache chunks`() {
        val keys = listOf("maiden", "bloat", "nylocas", "sotetseg", "xarpus", "verzik")
        val layouts = keys.map { assertNotNull(RaidMapLayouts.forRoom(RaidKind.THEATRE, it)) }
        assertEquals(6, layouts.map { it.sourceRegionX to it.sourceRegionZ }.distinct().size)
        assertTrue(layouts.all { it.zoneWidth > 0 && it.zoneLength > 0 })
    }

    @Test
    fun `room bounds are derived from layout dimensions`() {
        val layout = assertNotNull(RaidMapLayouts.forRoom(RaidKind.THEATRE, "bloat"))
        assertEquals(layout.zoneWidth * 8, layout.widthTiles)
        assertEquals(layout.zoneLength * 8, layout.lengthTiles)
    }

    @Test
    fun `known chambers variants use audited cache regions`() {
        val olm = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "olm"))
        val tekton = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "tekton"))
        val vespula = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "vespula"))
        val storage = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "storage"))
        assertEquals(51 to 80, olm.sourceRegionX to olm.sourceRegionZ)
        assertEquals(51 to 83, tekton.sourceRegionX to tekton.sourceRegionZ)
        assertEquals(1, tekton.sourceLevel)
        assertEquals(51 to 83, vespula.sourceRegionX to vespula.sourceRegionZ)
        assertEquals(2, vespula.sourceLevel)
        assertEquals(51 to 89, storage.sourceRegionX to storage.sourceRegionZ)

        val iceDemon = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "icedemon"))
        val tightrope = assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "tightrope"))
        assertEquals(51 to 84, iceDemon.sourceRegionX to iceDemon.sourceRegionZ)
        assertEquals(51 to 84, tightrope.sourceRegionX to tightrope.sourceRegionZ)
        assertTrue(olm.cacheVerified)
        assertEquals(4, olm.zoneLength)
        assertEquals(
            51 to 89,
            RaidMapLayouts.forLobby(RaidKind.CHAMBERS).sourceRegionX to
                RaidMapLayouts.forLobby(RaidKind.CHAMBERS).sourceRegionZ,
        )
        assertTrue(
            assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "mystics")).cacheVerified
        )
        assertEquals(emptySet(), RaidMapLayouts.unresolvedCoxVariants)
    }

    @Test
    fun `template source coordinates are cache zone coordinates`() {
        val layouts =
            listOf(
                assertNotNull(RaidMapLayouts.forRoom(RaidKind.THEATRE, "maiden")),
                assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "olm")),
                assertNotNull(RaidMapLayouts.forRoom(RaidKind.CHAMBERS, "tekton")),
            )
        assertTrue(layouts.all { it.sourceZoneX == it.sourceRegionX })
        assertTrue(layouts.all { it.sourceZoneZ == it.sourceRegionZ })
    }
}
