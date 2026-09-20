package org.rsmod.content.other.fragments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FragmentCatalogTest {
    @Test
    fun `catalog has exactly 250 fragments and 50 five-piece sets`() {
        assertEquals(50, FragmentCatalog.sets.size)
        assertEquals(250, FragmentCatalog.fragments.size)
        assertTrue(FragmentCatalog.sets.all { it.fragments.size == FragmentConfig.SET_SIZE })
        assertEquals(250, FragmentCatalog.fragmentsById.size)
    }

    @Test
    fun `xp stops at level ten`() {
        val progress = FragmentXp.add(FragmentProgress(), Int.MAX_VALUE)
        assertEquals(FragmentConfig.MAX_LEVEL, progress.level)
        assertEquals(FragmentXp.xpForLevel(FragmentConfig.MAX_LEVEL), progress.xp)
    }
}
