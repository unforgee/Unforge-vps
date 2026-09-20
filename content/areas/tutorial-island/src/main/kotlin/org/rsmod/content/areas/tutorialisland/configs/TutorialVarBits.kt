@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.refs.varbit.VarBitReferences

typealias tutorial_varbits = TutorialVarBits

object TutorialVarBits : VarBitReferences() {
    val adventurepath_player_participating = find("adventurepath_player_participating")
    /** Drives the depressed Body Type A/B buttons on `player_design`. */
    val player_design_bodytype = find("player_design_bodytype")
    /** Drives pronoun radio selection; packed on `settings_tracking`. */
    val settings_transmit_pronouns = find("settings_transmit_pronouns")
    /** Four bits on varp 1021; the client reads it to flash a toplevel side icon. */
    val flashside = find("flashside")
}
