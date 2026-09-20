package org.rsmod.api.config.editors

import org.rsmod.api.config.refs.invs
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType

internal object InvEdits : InvEditor() {
    init {
        edit(invs.inv) {
            scope = InvScope.Perm
            protect = false
            runWeight = true
        }

        edit(invs.worn) {
            scope = InvScope.Perm
            protect = false
            runWeight = true
        }

        edit(invs.bank) {
            scope = InvScope.Perm
            stack = InvStackType.Always
            // The 239 rsprot inventory update pool supports slots 0..5712.
            // Keep the cache inventory within that protocol limit until the
            // client/server packet contract is upgraded as well.
            size = 5_713
            protect = false
            placeholders = true
        }

        edit(invs.companion_storage) {
            scope = InvScope.Perm
            size = 100
            protect = false
        }
    }
}
