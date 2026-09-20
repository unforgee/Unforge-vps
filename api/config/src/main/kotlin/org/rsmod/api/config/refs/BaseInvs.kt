@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.api.config.refs

import org.rsmod.api.type.refs.inv.InvReferences

typealias invs = BaseInvs

object BaseInvs : InvReferences() {
    val tradeoffer = find("tradeoffer", 850951859)
    val inv = find("inv", 850981630)
    val worn = find("worn", 847803897)
    val bank = find("bank", 2155303762)
    /**
     * The cache's looting-bag container is reused as the persistent backing inventory for the
     * Explorer Backpack / looting bag feature. All 28 slots of the cache container are usable.
     */
    val explorer_backpack = find("looting_bag")

    val generalshop1 = find("generalshop1", 62547837000)

    val companion_storage = find("raids_privatestorage")
}
