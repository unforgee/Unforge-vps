package org.rsmod.content.interfaces.bank.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.invs
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.ClientScripts.mesLayerClose
import org.rsmod.api.player.output.ClientScripts.tooltip
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.configs.bank_components
import org.rsmod.content.interfaces.bank.configs.bank_comsubs
import org.rsmod.content.interfaces.bank.configs.bank_constants
import org.rsmod.content.interfaces.bank.configs.bank_interfaces
import org.rsmod.content.interfaces.bank.configs.bank_queues
import org.rsmod.content.interfaces.bank.configs.bank_varbits
import org.rsmod.content.interfaces.bank.disableIfEvents
import org.rsmod.content.interfaces.bank.highlightNoClickClear
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.interfaces.bank.setBankWornBonuses
import org.rsmod.content.interfaces.bank.setBanksideExtraOps
import org.rsmod.content.interfaces.bank.util.offset
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.IronmanPolicy
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankOpenScript
@Inject
constructor(
    private val invTypes: InvTypeList,
    private val objTypes: ObjTypeList,
    private val wornBonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
    private val eventBus: EventBus,
) : PluginScript() {
    private val logger = InlineLogger()

    private val Player.bank
        get() = invMap.getOrPut(invTypes[invs.bank])

    private var Player.withdrawCert by boolVarBit(bank_varbits.withdraw_mode)

    override fun ScriptContext.startup() {
        onCommand("bank") {
            desc = "Open your bank"
            cheat { player.openBank(eventBus) }
        }
        // `onBankOpen` occurs on `bank_side` trigger for emulation purposes.
        onIfOpen(interfaces.bank_side) { player.onBankOpen() }
        onIfClose(interfaces.bank_main) { player.onBankClose() }
    }

    private fun Player.onBankOpen() {
        // Server-side safety net: Ultimate Ironman accounts may not use the bank, no matter
        // which code path opened the interface (banker, booth, deposit box, dialogue).
        val bankCheck = IronmanPolicy.canUseBank(this)
        if (!bankCheck.allowed) {
            bankCheck.denialMessage?.let(::mes)
            ifClose(eventBus)
            return
        }

        // Game tests and legacy characters may reach the bank without the
        // login hook having initialized the new standalone capacity yet.
        if (bankCapacity != bank_constants.default_capacity) {
            bankCapacity = bank_constants.default_capacity
        }

        // A fresh account (and an account whose last item was withdrawn) can have an entirely
        // empty bank.  Do not trust stale tab-size varbits in that state: the client uses those
        // values to calculate the displayed slot ranges, and a stale custom-tab size makes an
        // empty bank look like it has an invalid backing slot.  Normalize the empty-bank state
        // before installing events or starting inventory transmission.
        if (bank.occupiedSpace() == 0) {
            for (tab in BankTab.entries) {
                VarPlayerIntMapSetter.set(this, tab.sizeVarBit, 0)
            }
            VarPlayerIntMapSetter.set(this, bank_varbits.selected_tab, BankTab.Main.varValue)
        }

        if (!disableIfEvents) {
            val capacityIncrease = bank_constants.purchasable_capacity
            withdrawCert = false
            setBanksideExtraOps(objTypes)
            val firstObjs =
                bank.indices.mapNotNull { i -> bank[i]?.let { "$i:${it.id}x${it.count}" } }.take(10)
            logger.info {
                "[bankdump] onBankOpen: bankSize=${bank.size} " +
                    "indices=${bank.indices.first}..${bank.indices.last} " +
                    "invIndices=${inv.indices.first}..${inv.indices.last} capacity=$bankCapacity " +
                    "occupied=${bank.occupiedSpace()} objs=$firstObjs"
            }
            setBankIfEvents()
            setBankWornBonuses(wornBonuses, weaponSpeeds)
            ifSetText(bank_components.capacity_text, bankCapacity.toString())
            val capacityTooltip =
                if (capacityIncrease > 0) {
                    "Bank capacity: ${bank_constants.default_capacity}<br>" +
                        "A banker can sell you up to $capacityIncrease more."
                } else {
                    "Bank capacity: ${bank_constants.default_capacity}"
                }
            tooltip(
                this,
                capacityTooltip,
                bank_components.capacity_container,
                bank_components.tooltip,
            )
        }

        startInvTransmit(bank)
    }

    private fun Player.onBankClose() {
        stopInvTransmit(bank)
        mesLayerClose(this, constants.meslayer_mode_objsearch)
        if (!ui.containsOverlay(bank_interfaces.tutorial_overlay)) {
            highlightNoClickClear()
        }
        queue(bank_queues.bank_compress, 1)
    }

    private fun Player.setBankIfEvents() {
        val lastIndex = bank.indices.last
        ifSetEvents(
            bank_components.main_inventory,
            bank.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op8,
            IfEvent.Op9,
            IfEvent.Op10,
            IfEvent.Depth2,
            IfEvent.DragTarget,
        )
        ifSetEvents(bank_components.main_inventory, lastIndex + 10..lastIndex + 18, IfEvent.Op1)

        // When dragging an item to a tab beyond its current size, these are the subcomponent ids
        // the server will receive from the client.
        val extendedTabOffsets = bank_comsubs.tab_extended_slots_offset
        val extendedTabSlots = extendedTabOffsets.offset(lastIndex)
        ifSetEvents(bank_components.main_inventory, extendedTabSlots, IfEvent.DragTarget)

        ifSetEvents(
            bank_components.tabs,
            bank_comsubs.main_tab..bank_comsubs.main_tab,
            IfEvent.Op1,
            IfEvent.Op7,
            IfEvent.DragTarget,
        )
        ifSetEvents(
            bank_components.tabs,
            bank_comsubs.other_tabs,
            IfEvent.Op1,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )

        ifSetEvents(
            bank_components.side_inventory,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op8,
            IfEvent.Op9,
            IfEvent.Op10,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )
        ifSetEvents(
            bank_components.lootingbag_inventory,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op10,
        )
        ifSetEvents(
            bank_components.league_inventory,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op10,
        )
        ifSetEvents(
            bank_components.worn_inventory,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op9,
            IfEvent.Op10,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )

        ifSetEvents(bank_components.incinerator_confirm, 1..bank.size, IfEvent.Op1)
        ifSetEvents(bank_components.bank_tab_display, 0..8, IfEvent.Op1)
        // The 239 cache does not reliably carry the static click mask for these controls.
        // Enable them explicitly so the full-inventory and worn-item deposit buttons reach
        // BankInvScript on both the standalone client and the integration harness.
        ifSetEvents(bank_components.deposit_inventory, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.deposit_worn, -1..-1, IfEvent.Op1)
        // The mode buttons and settings toggles have no cached click mask either
        // (verified against the vanilla cache), so their clicks never reach the
        // client->server packet unless enabled explicitly here.
        ifSetEvents(bank_components.rearrange_mode_swap, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.rearrange_mode_insert, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.incinerator_toggle, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.tutorial_button_toggle, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.inventory_item_options_toggle, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.deposit_inv_toggle, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.deposit_worn_toggle, -1..-1, IfEvent.Op1)
        ifSetEvents(bank_components.release_placehold, -1..-1, IfEvent.Op1)
    }
}
