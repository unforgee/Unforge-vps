package org.rsmod.game.ui.collection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.comp.HashedComponentType
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface

class ComponentEventMapTest {
    @Test
    fun `static range covers the static slot`() {
        val map = ComponentEventMap()
        map.add(component(929, 5), -1..-1, IfEvent.Op1.bitmask)

        assertTrue(map.contains(component(929, 5), STATIC_SLOT))
        assertEquals(IfEvent.Op1.bitmask, map[component(929, 5), STATIC_SLOT])
    }

    @Test
    fun `static range covers dynamic slots as well`() {
        val map = ComponentEventMap()
        map.add(component(929, 5), -1..-1, IfEvent.Op1.bitmask)

        assertTrue(map.contains(component(929, 5), 0))
        assertTrue(map.contains(component(929, 5), 4))
    }

    @Test
    fun `empty event set still counts as an entry`() {
        val map = ComponentEventMap()
        // `ifSetPauseText` / `ifObjbox` send an empty set over `-1..-1` to disable a component.
        map.add(component(162, 559), -1..-1, 0L)

        assertTrue(map.contains(component(162, 559), STATIC_SLOT))
        assertEquals(0L, map[component(162, 559), STATIC_SLOT])
    }

    @Test
    fun `bounded range does not cover the static slot`() {
        val map = ComponentEventMap()
        map.add(component(929, 4), 0..3, IfEvent.Op1.bitmask)

        assertFalse(map.contains(component(929, 4), STATIC_SLOT))
        assertTrue(map.contains(component(929, 4), 3))
        assertFalse(map.contains(component(929, 4), 4))
    }

    @Test
    fun `unknown component is not contained`() {
        val map = ComponentEventMap()
        map.add(component(929, 5), -1..-1, IfEvent.Op1.bitmask)

        assertFalse(map.contains(component(929, 6), STATIC_SLOT))
        assertFalse(map.contains(component(930, 5), STATIC_SLOT))
    }

    @Test
    fun `clear removes every component in the interface`() {
        val map = ComponentEventMap()
        map.add(component(929, 5), -1..-1, IfEvent.Op1.bitmask)
        map.clear(UserInterface(929))

        assertFalse(map.contains(component(929, 5), STATIC_SLOT))
    }

    private fun component(interfaceId: Int, child: Int): ComponentType =
        HashedComponentType(
            startHash = null,
            internalName = "$interfaceId:$child",
            internalId = Component(interfaceId, child).packed,
        )

    private companion object {
        /** Slot sent by the client for a click on a static (non-dynamic) component. */
        private const val STATIC_SLOT = -1
    }
}
