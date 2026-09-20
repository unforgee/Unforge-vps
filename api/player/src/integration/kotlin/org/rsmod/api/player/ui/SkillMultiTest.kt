package org.rsmod.api.player.ui

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.ui.Component

/**
 * Covers the argument layout decoded out of `skillmulti_setup` (cs 2046) and the subcomponent
 * channel `skillmulti_itembutton_triggered` (cs 2052) answers on.
 */
class SkillMultiTest {
    private val GameTestState.bronzeBar: ObjType
        get() = cacheTypes.objs.getValue(BRONZE_BAR)

    private val GameTestState.ironBar: ObjType
        get() = cacheTypes.objs.getValue(IRON_BAR)

    @Test
    fun GameTestState.`setup script receives the decoded argument layout`() = runGameTest {
        player.withProtectedAccess { openTestDialog(testOptions()) }

        val script = client.filterIsInstance<RunClientScript>().single { it.id == SETUP_SCRIPT }
        // 21 ints - verb, "All" count, eighteen option objs, selected count - plus the joined
        // title.
        assertEquals(22, script.values.size)
        assertEquals(SkillMultiVerb.Smelt.id, script.values[0])
        assertEquals(14, script.values[1])
        assertEquals(bronzeBar.id, script.values[2])
        assertEquals(ironBar.id, script.values[3])
        // Unused slots hold the `-1` that stops cs 2046 reading any further.
        assertEquals(-1, script.values[4])
        assertEquals(-1, script.values[19])
        assertEquals(3, script.values[20])
        assertEquals("Smelt|Bronze|Iron", script.values[21])
    }

    @Test
    fun GameTestState.`only the filled option slots are enabled`() = runGameTest {
        player.withProtectedAccess { openTestDialog(testOptions()) }

        val events = client.filterIsInstance<IfSetEventsV2>().filter { it.interfaceId == IFACE }
        assertEquals(2, events.size)
        assertEquals(listOf(FIRST_SLOT, FIRST_SLOT + 1), events.map { it.componentId })

        val pauseButton = IfEvent.PauseButton.bitmask.toInt()
        for (event in events) {
            // The chosen quantity rides in the subcomponent field, so the enabled range spans the
            // counts rather than the slot ids.
            assertEquals(0, event.start)
            assertEquals(SkillMulti.MAX_COUNT, event.end)
            assertEquals(pauseButton, event.events1 and pauseButton)
        }
    }

    @Test
    fun GameTestState.`every quantity the client can send is accepted`() = runGameTest {
        player.withProtectedAccess { openTestDialog(testOptions()) }

        val slot = optionComponent(FIRST_SLOT)
        // `0` is what verbs without a quantity row report; `28` is the client's own ceiling.
        assertTrue(player.ui.hasEvent(slot, 0, IfEvent.PauseButton))
        assertTrue(player.ui.hasEvent(slot, SkillMulti.MAX_COUNT, IfEvent.PauseButton))
        assertFalse(player.ui.hasEvent(slot, SkillMulti.MAX_COUNT + 1, IfEvent.PauseButton))
        // A slot past the end of the option list was never enabled.
        assertFalse(player.ui.hasEvent(optionComponent(FIRST_SLOT + 2), 1, IfEvent.PauseButton))
    }

    @Test
    fun GameTestState.`selection reports the clicked slot and its count`() = runGameTest {
        var selection: SkillMultiSelection? = null
        player.withProtectedAccess { selection = openTestDialog(testOptions()) }
        checkNotNull(player.activeCoroutine)

        player.resumeActiveCoroutine(
            ResumePauseButtonInput(optionComponent(FIRST_SLOT + 1), subcomponent = 7)
        )

        assertNotNull(selection)
        assertEquals(1, selection?.index)
        assertEquals("Iron", selection?.option?.label)
        assertEquals(7, selection?.count)
        assertNull(player.activeCoroutine)
    }

    @Test
    fun GameTestState.`quantity-less verbs report zero and are normalised to one`() = runGameTest {
        var selection: SkillMultiSelection? = null
        player.withProtectedAccess { selection = openTestDialog(testOptions()) }

        player.resumeActiveCoroutine(
            ResumePauseButtonInput(optionComponent(FIRST_SLOT), subcomponent = 0)
        )

        assertEquals(0, selection?.index)
        assertEquals(1, selection?.count)
    }

    @Test
    fun GameTestState.`pause button from elsewhere returns null instead of a selection`() =
        runGameTest {
            var resumed = false
            var selection: SkillMultiSelection? = null
            player.withProtectedAccess {
                selection = openTestDialog(testOptions())
                resumed = true
            }

            // `270:24` is the tooltip - never one of the option slots this dialog enabled.
            player.resumeActiveCoroutine(
                ResumePauseButtonInput(optionComponent(24), subcomponent = 1)
            )

            assertTrue(resumed)
            assertNull(selection)
        }

    private fun GameTestState.optionComponent(component: Int): ComponentType =
        cacheTypes.components.getValue(Component(IFACE, component).packed)

    private suspend fun ProtectedAccess.openTestDialog(
        options: List<SkillMultiOption>
    ): SkillMultiSelection? =
        skillMultiDialog(
            title = "Smelt",
            options = options,
            verb = SkillMultiVerb.Smelt,
            maxCount = 14,
            selectedCount = 3,
        )

    private fun GameTestState.testOptions(): List<SkillMultiOption> =
        listOf(SkillMultiOption(bronzeBar, "Bronze"), SkillMultiOption(ironBar, "Iron"))

    private companion object {
        private val IFACE = interfaces.skillmulti.id
        private const val FIRST_SLOT = SkillMulti.FIRST_OPTION_COMPONENT
        private const val SETUP_SCRIPT = 2046
        private const val BRONZE_BAR = 2349
        private const val IRON_BAR = 2351
    }
}
