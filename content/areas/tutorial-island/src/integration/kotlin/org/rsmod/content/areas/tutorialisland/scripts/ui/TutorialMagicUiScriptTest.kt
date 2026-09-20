package org.rsmod.content.areas.tutorialisland.scripts.ui

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.realm.Realm
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.progression.TutorialProgression
import org.rsmod.content.areas.tutorialisland.progression.TutorialStep
import org.rsmod.game.type.interf.IfEvent

class TutorialMagicUiScriptTest {
    @Test
    fun GameTestState.`home teleport sends a non-tutorial player to the realm spawn`() =
        runInjectedGameTest(HomeTeleportDeps::class, null, TutorialMagicUiScript::class) { deps ->
            deps.progression.setStep(player, TutorialStep.Completed)
            player.ifSetEvents(tutorial_components.spell_home_teleport, -1..-1, IfEvent.Op1)

            player.ifButton(tutorial_components.spell_home_teleport)
            advance(3)

            assertEquals(deps.realm.config.spawnCoord, player.coords)
        }
}

private class HomeTeleportDeps
@Inject
constructor(val realm: Realm, val progression: TutorialProgression)
