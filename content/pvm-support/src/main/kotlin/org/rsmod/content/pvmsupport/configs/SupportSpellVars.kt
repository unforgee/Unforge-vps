package org.rsmod.content.pvmsupport.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

typealias spell_varps = SupportSpellVarps

/**
 * Persistent PvM support-book progression.
 *
 * [spell_unlocks] is a single 32-bit bitmask, one bit per [SupportSpell] ordinal, so a player can
 * own up to 32 spells without a varp per spell. `permanent = true` is what makes the account saver
 * write it to `characters.varps` and the loader restore it, so unlocks survive a relog.
 */
object SupportSpellVarps : VarpReferences() {
    val spell_unlocks: VarpType = find("unforge_spell_unlocks")
}

internal object SupportSpellVarpBuilder : VarpBuilder() {
    init {
        build("unforge_spell_unlocks") {
            permanent = true
            transmitNever = true
        }
    }
}
