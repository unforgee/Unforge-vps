package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialFocusGuidance
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.type.comp.ComponentType

class TutorialCharacterScriptTest {
    @Test
    fun GameTestState.`brand new button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.experience_button_new)

    @Test
    fun GameTestState.`returning button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.experience_button_returning)

    @Test
    fun GameTestState.`experienced button advances past the experience modal`() =
        runPastExperienceTest(tutorial_components.experience_button_experienced)

    /**
     * `tutorial_overlay:noclick` swallows every game-view click while it is shown, so leaving the
     * experience modal has to hide it again.
     */
    @Test
    fun GameTestState.`leaving the experience modal hides the click blocker`() =
        runInjectedGameTest(
            PastExperienceDependencies::class,
            null,
            TutorialCharacterScript::class,
        ) { deps ->
            deps.progression.setStep(player, TutorialStep.ExpSelect)
            deps.focus.openPastExperience(player)
            client.clearOutgoing()

            player.ifButton(tutorial_components.experience_button_new)
            advance()

            val blocker = tutorial_components.tutorial_noclick
            val hides =
                client.outgoingMessages.filterIsInstance<IfSetHide>().filter {
                    it.interfaceId == blocker.interfaceId && it.componentId == blocker.component
                }
            assertEquals(true, hides.lastOrNull()?.hidden, "Click blocker was left visible.")
        }

    /**
     * The three choices are static components, so their clicks arrive with `comsub == -1`. They are
     * only reachable because `openPastExperience` enables Op1 over the `-1..-1` range; the buttons
     * have no cache-baked events of their own.
     */
    private fun GameTestState.runPastExperienceTest(button: ComponentType) =
        runInjectedGameTest(
            PastExperienceDependencies::class,
            null,
            TutorialCharacterScript::class,
        ) { deps ->
            deps.progression.setStep(player, TutorialStep.ExpSelect)
            deps.focus.openPastExperience(player)

            player.ifButton(button)
            advance()

            assertEquals(TutorialStep.GielinorTalk, deps.progression.current(player))
        }
}

private class PastExperienceDependencies
@Inject
constructor(val progression: TutorialProgression, val focus: TutorialFocusGuidance)
