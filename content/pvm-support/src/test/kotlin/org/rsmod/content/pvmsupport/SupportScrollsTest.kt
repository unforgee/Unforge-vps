package org.rsmod.content.pvmsupport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.content.pvmsupport.configs.SupportScrolls

public class SupportScrollsTest {
    @Test
    public fun `every spell has a scroll`() {
        assertEquals(
            SupportSpell.entries.toSet(),
            SupportScrolls.all.map { (spell, _) -> spell }.toSet(),
        )
    }

    @Test
    public fun `each spell has exactly one scroll`() {
        val spells = SupportScrolls.all.map { (spell, _) -> spell }
        assertEquals(spells.size, spells.distinct().size)
    }

    @Test
    public fun `each scroll is used by exactly one spell`() {
        val names = SupportScrolls.all.map { (_, scroll) -> scroll.internalName }
        assertTrue(names.none { it.isNullOrBlank() })
        assertEquals(names.size, names.distinct().size, names.toString())
    }

    @Test
    public fun `scroll lookup returns the mapped scroll`() {
        for ((spell, scroll) in SupportScrolls.all) {
            assertEquals(scroll.internalName, SupportScrolls.scrollFor(spell).internalName)
        }
    }
}
