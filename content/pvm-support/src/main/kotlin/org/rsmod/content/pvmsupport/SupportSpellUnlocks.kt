package org.rsmod.content.pvmsupport

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.pvmsupport.configs.spell_varps
import org.rsmod.game.entity.Player

/**
 * Per-account spell unlocks for the PvM support book.
 *
 * A spell must be **unlocked** (by studying its scroll) before it can be cast - the level
 * requirement and the active spellbook are checked on top of this. The unlocked set is a bitmask in
 * a permanent varp, so it survives a reconnect and a server restart.
 *
 * The bit index is [SupportSpell.ordinal]; reordering the enum would remap existing unlocks, so new
 * spells must always be appended.
 */
public object SupportSpellUnlocks {
    public fun isUnlocked(player: Player, spell: SupportSpell): Boolean =
        (player.vars[spell_varps.spell_unlocks] ushr spell.ordinal) and 1 == 1

    /** Unlocks [spell]; returns `false` when it was already known. */
    public fun unlock(player: Player, spell: SupportSpell): Boolean {
        if (isUnlocked(player, spell)) {
            return false
        }
        val unlocked = player.vars[spell_varps.spell_unlocks] or (1 shl spell.ordinal)
        VarPlayerIntMapSetter.set(player, spell_varps.spell_unlocks, unlocked)
        return true
    }

    public fun unlockAll(player: Player) {
        VarPlayerIntMapSetter.set(
            player,
            spell_varps.spell_unlocks,
            (1 shl SupportSpell.entries.size) - 1,
        )
    }
}
