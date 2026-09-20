package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.shops.ShopEvents
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Applies [ShopEditRegistry] overrides to a shop inventory each time it is opened. Runs before the
 * stock transmit, so players only ever see the edited stock.
 */
@OptIn(UncheckedType::class)
class ShopEditScript @Inject constructor(private val store: ShopEditRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        store.load()

        onEvent<ShopEvents.Open> {
            val invType = shop.inv.type.id
            val edits = store.editsFor(invType) ?: return@onEvent
            var applied = 0
            for ((slot, edit) in edits) {
                if (slot !in shop.inv.indices) {
                    continue
                }
                shop.inv[slot] = edit?.let { InvObj(it.id, it.count) }
                applied++
            }
            if (applied > 0) {
                logger.debug { "Applied $applied shop edits to invType=$invType" }
            }
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
