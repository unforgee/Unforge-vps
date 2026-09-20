package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.smithing.configs.smithing_content
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Smelting: the furnace half of Smithing, driven through the generic `skillmulti` dialog.
 *
 * Furnaces put `Smelt` on **op2**, but `lovakengj_furnace_large_01` puts it on op1, so both are
 * bound rather than splitting the content group in two. Using any ore on a furnace opens the same
 * menu, which is what the client's own furnace interactions do.
 */
class FurnaceScript @Inject constructor(private val actions: SmithingActions) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(smithing_content.furnace) { actions.openSmeltDialog(this) }
        onOpLoc2(smithing_content.furnace) { actions.openSmeltDialog(this) }
        onOpLocU(smithing_content.furnace, smithing_content.smeltable_ore) {
            actions.openSmeltDialog(this)
        }
    }
}
