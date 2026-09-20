package org.rsmod.content.other.login

import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.client.HideLocOps
import net.rsprot.protocol.game.outgoing.misc.client.HideNpcOps
import net.rsprot.protocol.game.outgoing.misc.client.HideObjOps
import net.rsprot.protocol.game.outgoing.misc.client.MinimapToggle
import net.rsprot.protocol.game.outgoing.misc.client.ResetAnims
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettings
import net.rsprot.protocol.game.outgoing.varp.VarpReset
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.inv.weight.InvWeight
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.UpdateStat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onEvent
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.game.type.varp.UnpackedVarpType
import org.rsmod.game.type.varp.VarpType
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LoginScript
@Inject
constructor(
    private val realm: Realm,
    private val mapClock: MapClock,
    private val objTypes: ObjTypeList,
    private val varpTypes: VarpTypeList,
    private val statTypes: StatTypeList,
    private val invisibleLevels: InvisibleLevels,
    private val eventBus: EventBus,
) : PluginScript() {
    /**
     * Script 828 controls the client-side item access state. Keep the standalone value in one
     * place and resend it after the login state is initialized.
     */
    private companion object {
        private const val STANDALONE_MEMBERS_WORLD: Int = 1

        /** Matches RuneLite `ScriptID.CAMERA_SET_ZOOM_LIMITS` / cache script 605. */
        private const val CAMERA_SET_ZOOM_LIMITS = 605
        /** Matches RuneLite `ScriptID.CAMERA_DO_ZOOM` / cache script 42. */
        private const val CAMERA_DO_ZOOM = 42
        private const val DEFAULT_CAMERA_ZOOM = 512
        // Pre-varc defaults from historical camera_do_zoom clamp (128–896).
        private const val ZOOM_SMALL_MIN = 128
        private const val ZOOM_SMALL_MAX = 896
        private const val ZOOM_BIG_MIN = 128
        private const val ZOOM_BIG_MAX = 896
        private const val DEFAULT_MASTER_VOLUME = 100
        private const val DEFAULT_MUSIC_VOLUME = 20
        private const val DEFAULT_SOUND_VOLUME = 45
        private const val DEFAULT_AREA_SOUND_VOLUME = 25
    }

    private val transmitVars by lazy { transmitVars() }

    private var Player.chatboxUnlocked: Boolean by boolVarBit(varbits.has_displayname_transmitter)
    private var Player.hideRoofs by boolVarBit(varbits.option_hide_rooftops)

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLogin>(0L) { player.engineLogin() }
    }

    private fun Player.engineLogin() {
        sendHighPriority()
        // Early camera limits before toplevel, matching live client order (605 before 901).
        sendEarlyCamera()
        eventBus.publish(SessionStateEvent.EngineLoginUi(this))
        sendLowPriority()
    }

    private fun Player.sendHighPriority() {
        sendChatFilters()
        sendOpVisibility()
        sendWelcomeMessage()
        sendVars()
    }

    private fun Player.sendChatFilters() {
        client.write(ChatFilterSettings(0, 0))
    }

    private fun Player.sendOpVisibility() {
        client.write(HideNpcOps(false))
        client.write(HideLocOps(false))
        client.write(HideObjOps(false))
    }

    private fun Player.sendWelcomeMessage() {
        val message = realm.config.loginMessage
        message?.let { mes(it, ChatType.Welcome) }

        val broadcast = realm.config.loginBroadcast
        broadcast?.let { mes(it, ChatType.Broadcast) }
    }

    private fun Player.sendVars() {
        client.write(VarpReset)
        chatboxUnlocked = displayName.isNotBlank()
        hideRoofs = true
        // After VarpReset the client zeroes all varps. New accounts never have audio volume
        // varps in the save map, so without defaults the client stays muted (varp 168/169/872/3796
        // at 0) even when the server emits MidiSongV2 / SynthSound. Only initialize when unset so
        // intentionally muted players (value 0 stored in the map) remain muted.
        ensureDefaultAudioVolumes()
        for (varp in transmitVars) {
            if (varp in vars) {
                resyncVar(varp)
            }
        }
    }

    private fun Player.ensureDefaultAudioVolumes() {
        // Live new-account defaults from OSRS rev 239 Tutorial Island first-login capture:
        // master=100, music=20, sounds=45, areasounds=25.
        setDefaultVolumeIfUnset(varps.option_master_volume, DEFAULT_MASTER_VOLUME)
        setDefaultVolumeIfUnset(varps.option_music, DEFAULT_MUSIC_VOLUME)
        setDefaultVolumeIfUnset(varps.option_sounds, DEFAULT_SOUND_VOLUME)
        setDefaultVolumeIfUnset(varps.option_areasounds, DEFAULT_AREA_SOUND_VOLUME)
    }

    private fun Player.setDefaultVolumeIfUnset(varp: VarpType, volume: Int) {
        if (varp !in vars) {
            VarPlayerIntMapSetter.set(this, varp, volume)
        }
    }

    private fun Player.sendEarlyCamera() {
        resetCam()
        // Rev 239 camera_do_zoom clamps against varcs 1338–1341. Script 605
        // (CAMERA_SET_ZOOM_LIMITS)
        // initializes those limits; live clients run this before toplevel open.
        runClientScript(
            CAMERA_SET_ZOOM_LIMITS,
            ZOOM_SMALL_MIN,
            ZOOM_SMALL_MAX,
            ZOOM_BIG_MIN,
            ZOOM_BIG_MAX,
        )
        runClientScript(CAMERA_DO_ZOOM, DEFAULT_CAMERA_ZOOM, DEFAULT_CAMERA_ZOOM)
    }

    private fun Player.sendLowPriority() {
        sendInvs()
        runClientScript(2498, 1, 0, 0)
        // Must be sent after inventory transmission and before the player can open an item menu.
        runClientScript(828, STANDALONE_MEMBERS_WORLD)
        runClientScript(5141)
        sendPlayerOps()
        runClientScript(876, mapClock.cycle, 0, displayName, "REGULAR")
        sendStats()
        sendRun()
        client.write(ResetAnims)
        client.write(MinimapToggle(0))
    }

    private fun Player.sendInvs() {
        startInvTransmit(inv)
        startInvTransmit(worn)
    }

    private fun Player.resetCam() {
        Camera.camReset(this)
    }

    private fun Player.sendStats() {
        for (stat in statTypes.values) {
            val currXp = statMap.getXP(stat)
            val currLvl = stat(stat)
            val hiddenLvl = currLvl + invisibleLevels.get(this, stat)
            UpdateStat.update(this, stat, currXp, currLvl, hiddenLvl)
        }
    }

    private fun Player.sendRun() {
        val weightInGrams = InvWeight.calculateWeightInGrams(this, objTypes)
        runWeight = weightInGrams
        UpdateRun.weight(this, kg = weightInGrams / 1000)
        UpdateRun.energy(this, runEnergy)
    }

    private fun Player.sendPlayerOps() {
        // Slot 1 hosts the companion interaction op (see CompanionRuntimeScript `onOpPlayer1`);
        // player ops are global so it appears on every player entity, but the handler only
        // opens the menu for the clicking player's own companion.
        MiscOutput.setPlayerOp(this, slot = 1, op = "Companion")
        // Slot 2 stays empty: the client injects the PvP "Attack" op on it when applicable.
        MiscOutput.setPlayerOp(this, slot = 2, op = null)
        MiscOutput.setPlayerOp(this, slot = 3, op = "Follow")
        MiscOutput.setPlayerOp(this, slot = 4, op = "Trade with")
        MiscOutput.setPlayerOp(this, slot = 5, op = "Spells")
        MiscOutput.setPlayerOp(this, slot = 8, op = "Report")
    }

    private fun transmitVars(): List<UnpackedVarpType> {
        return varpTypes.filterTransmitKeys().sorted().map(varpTypes::getValue)
    }
}
