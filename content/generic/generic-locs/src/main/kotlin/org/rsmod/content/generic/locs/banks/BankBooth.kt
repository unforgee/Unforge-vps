package org.rsmod.content.generic.locs.banks

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankBooth : PluginScript() {
    override fun ScriptContext.startup() {
        // Classic booths/counters: op1=-, op2=Bank, op3=Collect, op4=Deposit-box.
        onOpLoc2(content.bank_booth) { openBank() }
        onOpLoc3(content.bank_booth) { openCollectionBox() }
        onOpLoc4(content.bank_booth) { openBank() }

        // Chests and misc bank objects: op1=Use|Bank, op3=Collect.
        onOpLoc1(content.bank_chest) { openBank() }
        onOpLoc3(content.bank_chest) { openCollectionBox() }

        // Deposit boxes/chests/pots: op1=Deposit. The dedicated deposit-box
        // interface is not implemented; the full bank is opened instead.
        onOpLoc1(content.bank_deposit_box) { openBank() }
    }

    private fun ProtectedAccess.openBank() {
        val bankCheck = IronmanPolicy.canUseBank(player)
        if (!bankCheck.allowed) {
            bankCheck.denialMessage?.let(player::mes)
            return
        }
        ifOpenMainSidePair(main = interfaces.bank_main, side = interfaces.bank_side)
    }

    private fun ProtectedAccess.openCollectionBox() {
        ifOpenMainModal(interfaces.ge_collection_box)
    }
}
