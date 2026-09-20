package org.rsmod.content.skillcapes

import jakarta.inject.Inject
import org.rsmod.api.commons.gameplay.missingMaxedStats
import org.rsmod.api.config.refs.content
import org.rsmod.api.player.events.interact.HeldContentEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Equip gate for the Max cape and Max hood: both require [StatType.maxLevel] (99) in every skill.
 * Cache `statreq` params only carry two skill slots, so the all-skills check lives here in script
 * form instead - regular single-skill capes keep their cache-driven `statreq1` gate.
 *
 * On success the normal [HeldEquipOp] path runs unchanged (transform to the worn variant, wearpos
 * resolution, equip events), so nothing in the equip pipeline is duplicated.
 */
class MaxCapeScript
@Inject
constructor(private val statTypes: StatTypeList, private val equipOp: HeldEquipOp) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld2(content.max_cape) { wearMaxItem(it, "Max cape") }
        onOpHeld2(content.max_hood) { wearMaxItem(it, "Max hood") }
    }

    private fun ProtectedAccess.wearMaxItem(event: HeldContentEvents.Op2, itemName: String) {
        val missing = player.missingMaxedStats(statTypes.values)
        if (missing.isNotEmpty()) {
            mes("You need to have all stats at level 99 to wear the $itemName.")
            return
        }
        val result = equipOp.equip(player, event.slot, event.inventory)
        if (result is HeldEquipResult.Fail) {
            result.messages.forEach(::mes)
        }
    }
}
