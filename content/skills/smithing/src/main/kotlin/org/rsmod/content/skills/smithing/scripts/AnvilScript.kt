package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.ui.SkillMulti
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.smithing.configs.smithing_components
import org.rsmod.content.skills.smithing.configs.smithing_content
import org.rsmod.content.skills.smithing.configs.smithing_queues
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The anvil: interface **312 `smithing`**.
 *
 * Architecturally the opposite of the furnace. Every slot and quantity button on 312 already
 * carries Op1 in the cache (`events=0x2`, which the v1 mask reads as `DeprecatedOp1`), so nothing
 * needs enabling - clicks arrive as soon as the interface is open. The grid itself is drawn
 * client-side, so the server never sends its contents. See [SmithingActions] for the logic.
 */
class AnvilScript
@Inject
constructor(private val actions: SmithingActions, private val objTypes: ObjTypeList) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(smithing_content.anvil) { actions.openAnvil(this, preferred = null) }
        onOpLocU(smithing_content.anvil, smithing_content.smithable_bar) {
            actions.openAnvil(this, preferred = objTypes[it.objType])
        }

        onIfModalButton(smithing_components.make_1) { actions.setQuantity(this, 1) }
        onIfModalButton(smithing_components.make_5) { actions.setQuantity(this, 5) }
        onIfModalButton(smithing_components.make_10) { actions.setQuantity(this, 10) }
        onIfModalButton(smithing_components.make_all) {
            actions.setQuantity(this, SkillMulti.MAX_COUNT)
        }
        onIfModalButton(smithing_components.make_x) { actions.askQuantity(this) }

        for (slot in actions.productSlots) {
            onIfModalButton(slot) { actions.smithSlot(this, slot.component) }
        }

        // Drives every make loop, the furnace's included - one registration, because a second
        // subscription for the same queue would silently replace this one.
        onPlayerQueueWithArgs<MakeJob>(smithing_queues.make) { actions.continueJob(this, it.args) }
    }
}
