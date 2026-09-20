package org.rsmod.content.pvmsupport

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.pvmsupport.configs.SupportScrolls
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Learns a PvM support spell from its scroll.
 *
 * Studying consumes the scroll and unlocks the spell permanently. A scroll for a spell that is
 * already known is left untouched, so a duplicate is never wasted by accident.
 */
public class SupportScrollScript : PluginScript() {
    override fun ScriptContext.startup() {
        for ((spell, scroll) in SupportScrolls.all) {
            onOpHeld1(scroll) { study(spell, it.slot) }
        }
    }

    private fun ProtectedAccess.study(spell: SupportSpell, slot: Int) {
        if (SupportSpellUnlocks.isUnlocked(player, spell)) {
            mes("You already know <col=66ccff>${spell.displayName}</col>.")
            return
        }
        if (!invDel(inv, SupportScrolls.scrollFor(spell), count = 1, slot = slot).success) {
            return
        }
        SupportSpellUnlocks.unlock(player, spell)
        mes("You study the scroll and learn <col=66ccff>${spell.displayName}</col>.")
    }
}
