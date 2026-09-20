package org.rsmod.game.entity.player

import org.rsmod.game.type.bas.UnpackedBasType
import org.rsmod.game.type.npc.UnpackedNpcType

public class Appearance {
    /**
     * Tracks whether the player's appearance needs to be updated. Initially set to `true` so that
     * the player's appearance can be synchronized in the next update block.
     */
    public var rebuild: Boolean = true
        internal set

    public var bas: UnpackedBasType? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var transmog: UnpackedNpcType? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var skullIcon: Int? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var overheadIcon: Int? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var bodyType: Int = 0
        set(value) {
            field = value
            rebuild = true
        }

    public var pronoun: Int = 0
        set(value) {
            field = value
            rebuild = true
        }

    public var namePrefix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var nameSuffix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var combatLvlSuffix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var softHidden: Boolean = false
        set(value) {
            field = value
            rebuild = true
        }

    public var combatLevel: Int = 3
        set(value) {
            field = value
            rebuild = true
        }

    private val colours: ByteArray = ByteArray(5)
    private val identKit: ShortArray = ShortArray(7) { -1 }

    // TODO: Move default colours/identkit assignment to a relevant plugin and
    //  delete this init block.
    init {
        assignDefaultColours()
        assignDefaultIdentKit()
    }

    public fun setColour(index: Int, colour: Int) {
        require(colour in 0..255) { "colour must be in range [0..255]. ($colour)" }
        this.colours[index] = colour.toByte()
        this.rebuild = true
    }

    public fun setIdentKit(index: Int, identKit: Int) {
        require(identKit in 0..65535) { "identKit must be in range [0..65535]. ($identKit)" }
        this.identKit[index] = identKit.toShort()
        this.rebuild = true
    }

    public fun coloursSnapshot(): List<Byte> = colours.toList()

    public fun identKitSnapshot(): List<Short> = identKit.toList()

    public fun clearRebuildFlag() {
        rebuild = false
    }

    private fun assignDefaultColours() {
        // Live Tutorial Island first-login capture (all zeroes).
        colours[0] = 0
        colours[1] = 0
        colours[2] = 0
        colours[3] = 0
        colours[4] = 0
    }

    private fun assignDefaultIdentKit() {
        // Live male starter kits from OSRS rev 239 Tutorial Island capture
        // (AppearanceExtendedInfo look values minus 256 kit offset).
        identKit[0] = 0 // hair
        identKit[1] = 10 // jaw
        identKit[2] = 18 // torso
        identKit[3] = 26 // arms
        identKit[4] = 33 // hands
        identKit[5] = 36 // legs
        identKit[6] = 42 // feet
    }
}
