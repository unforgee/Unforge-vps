package org.rsmod.content.interfaces.skill.guides

import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.skill.guides.configs.guide_components
import org.rsmod.content.interfaces.skill.guides.configs.guide_interfaces
import org.rsmod.game.type.comp.ComponentType

class SkillGuideTest {
    /**
     * The guide is client-rendered: the server opens interface 860 and hands `skill_guide_v2_init`
     * a guide index. Nothing else reaches the client, so the `RunClientScript` args are the whole
     * contract.
     *
     * Every expectation below was read off the client - click a skill, see which guide opens - so
     * this test pins observed behaviour rather than a guess about the numbering.
     */
    @Test
    fun GameTestState.`clicking a skill opens its guide`() =
        runGameTest(SkillGuideScript::class) {
            player.ifOpenOverlay(interfaces.stats, components.toplevel_target_side1)

            fun assertOpensGuide(button: ComponentType, guide: Int) {
                client.clearOutgoing()
                player.ifButton(button)
                advance()

                assertTrue(player.ui.containsOverlay(guide_interfaces.skill_guide_v2)) {
                    "Skill guide should be open after clicking $button:"
                }

                val script = client.single<RunClientScript>()
                assertEquals(SKILL_GUIDE_V2_INIT, script.id)
                assertEquals(listOf(guide, 0, 0, 0), script.values)
            }

            // The guide list is 1-based and in its own order, so a stat id is wrong for all but a
            // coincidental few. These five each break differently under stat ids: attack would send
            // 0 and open a broken guide, and the rest would land on another skill entirely.
            assertOpensGuide(guide_components.attack, 1)
            assertOpensGuide(guide_components.defence, 5)
            assertOpensGuide(guide_components.hitpoints, 6)
            assertOpensGuide(guide_components.runecraft, 12)
            assertOpensGuide(guide_components.hunter, 23)
        }

    /** Guards the 1-based numbering: nothing may send 0, which opens a broken guide in-game. */
    @Test
    fun GameTestState.`every skill button sends a guide index in range`() =
        runGameTest(SkillGuideScript::class) {
            player.ifOpenOverlay(interfaces.stats, components.toplevel_target_side1)

            val sent = mutableListOf<Int>()
            for (button in SKILL_BUTTONS) {
                client.clearOutgoing()
                player.ifButton(button)
                advance()
                sent += client.single<RunClientScript>().values.first() as Int
            }

            assertEquals((1..23).toList(), sent.sorted())
        }

    @Test
    fun GameTestState.`close button shuts the guide`() =
        runGameTest(SkillGuideScript::class) {
            player.ifOpenOverlay(interfaces.stats, components.toplevel_target_side1)

            player.ifButton(guide_components.attack)
            advance()
            assertTrue(player.ui.containsOverlay(guide_interfaces.skill_guide_v2))

            player.ifButton(guide_components.close_button)
            advance()
            assertFalse(player.ui.containsOverlay(guide_interfaces.skill_guide_v2)) {
                "Close button should have shut the guide:"
            }
        }

    private companion object {
        private const val SKILL_GUIDE_V2_INIT = 1902

        private val SKILL_BUTTONS =
            listOf(
                guide_components.attack,
                guide_components.strength,
                guide_components.ranged,
                guide_components.magic,
                guide_components.defence,
                guide_components.hitpoints,
                guide_components.prayer,
                guide_components.agility,
                guide_components.herblore,
                guide_components.thieving,
                guide_components.crafting,
                guide_components.runecraft,
                guide_components.mining,
                guide_components.smithing,
                guide_components.fishing,
                guide_components.cooking,
                guide_components.firemaking,
                guide_components.woodcutting,
                guide_components.fletching,
                guide_components.slayer,
                guide_components.farming,
                guide_components.construction,
                guide_components.hunter,
            )
    }
}
