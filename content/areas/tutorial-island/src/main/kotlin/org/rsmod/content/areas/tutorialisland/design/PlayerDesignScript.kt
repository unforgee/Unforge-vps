package org.rsmod.content.areas.tutorialisland.design

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.areas.tutorialisland.configs.tutorial_components
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varbits
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Live `player_design` (679) left/right style, colour, gender, and pronoun controls.
 *
 * Each click mutates [Player.appearance] and flags a rebuild so the next PlayerInfo sync mirrors
 * OSRS (AppearanceExtendedInfo after every If3Button). Body-type / pronoun button chrome is driven
 * by varbits the client CS2 reads for the depressed state.
 */
class PlayerDesignScript @Inject constructor(private val kits: PlayerDesignKits) : PluginScript() {
    private var Player.designBodyType by intVarBit(tutorial_varbits.player_design_bodytype)
    private var Player.transmitPronouns by intVarBit(tutorial_varbits.settings_transmit_pronouns)

    override fun ScriptContext.startup() {
        kits.ensureLoaded()

        bindKit(tutorial_components.head_left, DesignKitSlot.Hair, -1)
        bindKit(tutorial_components.head_right, DesignKitSlot.Hair, +1)
        bindKit(tutorial_components.jaw_left, DesignKitSlot.Jaw, -1)
        bindKit(tutorial_components.jaw_right, DesignKitSlot.Jaw, +1)
        bindKit(tutorial_components.torso_left, DesignKitSlot.Torso, -1)
        bindKit(tutorial_components.torso_right, DesignKitSlot.Torso, +1)
        bindKit(tutorial_components.arms_left, DesignKitSlot.Arms, -1)
        bindKit(tutorial_components.arms_right, DesignKitSlot.Arms, +1)
        bindKit(tutorial_components.hands_left, DesignKitSlot.Hands, -1)
        bindKit(tutorial_components.hands_right, DesignKitSlot.Hands, +1)
        bindKit(tutorial_components.legs_left, DesignKitSlot.Legs, -1)
        bindKit(tutorial_components.legs_right, DesignKitSlot.Legs, +1)
        bindKit(tutorial_components.feet_left, DesignKitSlot.Feet, -1)
        bindKit(tutorial_components.feet_right, DesignKitSlot.Feet, +1)

        bindColour(tutorial_components.hair_left, DesignColourSlot.Hair, -1)
        bindColour(tutorial_components.hair_right, DesignColourSlot.Hair, +1)
        bindColour(tutorial_components.torso_col_left, DesignColourSlot.Torso, -1)
        bindColour(tutorial_components.torso_col_right, DesignColourSlot.Torso, +1)
        bindColour(tutorial_components.legs_col_left, DesignColourSlot.Legs, -1)
        bindColour(tutorial_components.legs_col_right, DesignColourSlot.Legs, +1)
        bindColour(tutorial_components.feet_col_left, DesignColourSlot.Feet, -1)
        bindColour(tutorial_components.feet_col_right, DesignColourSlot.Feet, +1)
        bindColour(tutorial_components.skin_left, DesignColourSlot.Skin, -1)
        bindColour(tutorial_components.skin_right, DesignColourSlot.Skin, +1)

        onIfModalButton(tutorial_components.gender_male) {
            player.setBodyType(constants.bodytype_a)
        }
        onIfModalButton(tutorial_components.gender_female) {
            player.setBodyType(constants.bodytype_b)
        }

        onIfModalButton(tutorial_components.player_design_pronouns) { event ->
            player.setPronoun(event.comsub)
        }
    }

    private fun ScriptContext.bindKit(button: ComponentType, slot: DesignKitSlot, delta: Int) {
        onIfModalButton(button) { player.cycleKit(slot, delta) }
    }

    private fun ScriptContext.bindColour(
        button: ComponentType,
        slot: DesignColourSlot,
        delta: Int,
    ) {
        onIfModalButton(button) { player.cycleColour(slot, delta) }
    }

    private fun Player.cycleKit(slot: DesignKitSlot, delta: Int) {
        val options = kits.kitsFor(slot, appearance.bodyType)
        if (options.isEmpty()) {
            return
        }
        val current = appearance.identKitSnapshot()[slot.appearanceIndex].toInt()
        val index = options.indexOf(current).let { if (it < 0) 0 else it }
        val next = options[Math.floorMod(index + delta, options.size)]
        appearance.setIdentKit(slot.appearanceIndex, next)
    }

    private fun Player.cycleColour(slot: DesignColourSlot, delta: Int) {
        val current = appearance.coloursSnapshot()[slot.appearanceIndex].toInt() and 0xFF
        val max = slot.maxInclusive
        val next = Math.floorMod(current + delta, max + 1)
        appearance.setColour(slot.appearanceIndex, next)
    }

    private fun Player.setBodyType(bodyType: Int) {
        // Always sync the UI varbit first — client CS2 uses it for the depressed A/B button.
        // If this stays at 0 while kits are female, A stays depressed and often won't re-fire.
        designBodyType = bodyType
        if (appearance.bodyType == bodyType) {
            return
        }
        appearance.bodyType = bodyType
        // Live gender flip also updates textGender / pronoun transmit (capture: female → 1).
        appearance.pronoun = bodyType
        transmitPronouns = bodyType
        val defaults = kits.defaultKits(bodyType)
        for (slot in DesignKitSlot.entries) {
            appearance.setIdentKit(slot.appearanceIndex, defaults[slot.appearanceIndex])
        }
    }

    /** Pronoun button layer uses dynamic children `1..3` (he / they / she). */
    private fun Player.setPronoun(comsub: Int) {
        if (comsub !in 1..3) {
            return
        }
        val pronoun = comsub - 1
        appearance.pronoun = pronoun
        transmitPronouns = pronoun
    }
}
