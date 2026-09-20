package org.rsmod.content.interfaces.settings.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.utils.vars.VarEnumDelegate
import org.rsmod.content.interfaces.settings.configs.setting_components
import org.rsmod.content.interfaces.settings.configs.setting_varbits
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SettingsSideScript
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private val logger = InlineLogger()

    private var Player.panel by enumVarBit<Panel>(setting_varbits.panel_tab)

    override fun ScriptContext.startup() {
        onIfOpen(interfaces.settings_side) { player.updateIfEvents() }

        // Click targets must match CS2 3910 / rev 239 children 58/66/67 (varbit 9683 = 0/1/2).
        onIfOverlayButton(setting_components.settings_tab) {
            player.selectPanel(Panel.Control, component.component)
        }
        onIfOverlayButton(setting_components.audio_tab) {
            player.selectPanel(Panel.Audio, component.component)
        }
        onIfOverlayButton(setting_components.display_tab) {
            player.selectPanel(Panel.Display, component.component)
        }

        onIfOverlayButton(setting_components.settings_open) { player.selectAllSettings() }
        onIfOverlayButton(setting_components.settings_close) { player.closeAllSettings() }
    }

    private fun Player.selectPanel(next: Panel, clickedChild: Int) {
        // TEMP protocol-audit: remove after Display/Audio tab verification.
        logger.info {
            "[protocol-audit] settings_side panel click child=$clickedChild " +
                "from=$panel to=$next (varbit 9683)"
        }
        panel = next
    }

    private fun Player.updateIfEvents() {
        ifSetEvents(setting_components.music_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.sound_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.areasounds_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.master_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.attack_priority_player_buttons, 1..5, IfEvent.Op1)
        ifSetEvents(setting_components.attack_priority_npc_buttons, 1..4, IfEvent.Op1)
        ifSetEvents(setting_components.client_type_buttons, 1..3, IfEvent.Op1)
        ifSetEvents(setting_components.brightness_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.zoom_slider, 0..21, IfEvent.Op1)
        // Re-assert zoom limits when the side panel opens (covers re-login UI rebuilds).
        runClientScript(
            CAMERA_SET_ZOOM_LIMITS,
            ZOOM_SMALL_MIN,
            ZOOM_SMALL_MAX,
            ZOOM_BIG_MIN,
            ZOOM_BIG_MAX,
        )
    }

    private fun Player.selectAllSettings() {
        val opened = protectedAccess.launch(this) { openAllSettings() }
        if (!opened) {
            mes("Please finish what you are doing before opening the settings menu.")
        }
    }

    private fun Player.closeAllSettings() {
        ifCloseOverlay(interfaces.settings, eventBus)
    }

    private fun ProtectedAccess.openAllSettings() {
        // TODO(content): varp `settings_tracking` is spam synced here for some reason.
        ifOpenOverlay(interfaces.settings)
    }

    private companion object {
        private const val CAMERA_SET_ZOOM_LIMITS = 605
        private const val ZOOM_SMALL_MIN = 128
        private const val ZOOM_SMALL_MAX = 896
        private const val ZOOM_BIG_MIN = 128
        private const val ZOOM_BIG_MAX = 896
    }
}

private enum class Panel(override val varValue: Int) : VarEnumDelegate {
    Control(0),
    Audio(1),
    Display(2),
}
