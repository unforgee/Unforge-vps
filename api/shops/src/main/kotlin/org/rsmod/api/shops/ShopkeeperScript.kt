package org.rsmod.api.shops

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.currencies
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.currency.CurrencyType
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.inv.UnpackedInvType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Opens shops based on the npc **type**, not on where the npc happens to stand.
 *
 * An npc becomes a shopkeeper by setting [ShopParams.npc_shop] on its type (e.g. via an
 * [org.rsmod.api.type.editors.npc.NpcEditor] edit: `param[ShopParams.npc_shop] =
 * "cw_general_store"`). Every spawn of that npc then opens the same shop - coordinates are
 * irrelevant.
 *
 * The script binds the npc's "Trade"-style ops. If the npc has no explicitly labelled trade op but
 * only a single visible option, that option opens the shop; otherwise an error is logged so the npc
 * can be given a proper "Trade" option.
 */
public class ShopkeeperScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val invTypes: InvTypeList,
    private val shops: Shops,
    private val nameMapping: NameMapping,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        val invsByName = HashMap<String, UnpackedInvType>(invTypes.size)
        for (inv in invTypes.values) {
            val name = inv.internalName ?: continue
            invsByName.putIfAbsent(name, inv)
        }

        var bound = 0
        for (npc in npcTypes.values) {
            val shopName = npc.paramOrNull(ShopParams.npc_shop)?.takeUnless(String::isBlank)
            if (shopName == null) {
                continue
            }
            val inv = invsByName[shopName]
            if (inv == null) {
                logger.error {
                    "Shopkeeper npc '${npc.internalName}' references unknown shop inv '$shopName'."
                }
                continue
            }
            val title =
                npc.paramOrNull(ShopParams.npc_shop_title)?.takeUnless(String::isBlank) ?: npc.name
            val currency = resolveCurrency(npc) ?: continue
            if (bindShopOps(npc, inv, title, currency)) {
                bound++
            }
        }
        if (bound > 0) {
            logger.info { "Bound $bound npc shopkeeper types." }
        }
    }

    /**
     * Resolves [ShopParams.npc_shop_currency] to a [CurrencyType]. Returns `null` (and logs an
     * error) when the param names a currency that does not exist in `currency.sym` - the shop must
     * not open with a silently wrong currency.
     */
    private fun resolveCurrency(npc: UnpackedNpcType): CurrencyType? {
        val name =
            npc.paramOrNull(ShopParams.npc_shop_currency)?.takeUnless(String::isBlank)
                ?: return currencies.standard_gp
        val id = nameMapping.currencies[name]
        if (id == null) {
            logger.error {
                "Shopkeeper npc '${npc.internalName}' references unknown currency '$name'."
            }
            return null
        }
        return CurrencyType(internalId = id, internalName = name)
    }

    private fun ScriptContext.bindShopOps(
        npc: UnpackedNpcType,
        inv: UnpackedInvType,
        title: String,
        currency: CurrencyType,
    ): Boolean {
        var slots = npc.op.indices.filter { isTradeOp(npc.op[it]) }
        if (slots.isEmpty()) {
            val usable = npc.op.indices.filter { isUsableOp(npc.op[it]) }
            if (usable.size == 1) {
                // Single-option shopkeeper: its only interaction opens the shop.
                slots = usable
            }
        }
        if (slots.isEmpty()) {
            logger.error {
                "Shopkeeper npc '${npc.internalName}' has no Trade/Shop op " +
                    "(ops=${npc.op.toList()}). Add a 'Trade' op or fix `npc_shop`."
            }
            return false
        }
        for (slot in slots) {
            bindOp(npc, slot + 1, inv, title, currency)
        }
        return true
    }

    private fun ScriptContext.bindOp(
        npc: UnpackedNpcType,
        slot: Int,
        inv: UnpackedInvType,
        title: String,
        currency: CurrencyType,
    ) {
        when (slot) {
            1 -> onOpNpc1(npc) { openShop(it.npc, inv, title, currency) }
            2 -> onOpNpc2(npc) { openShop(it.npc, inv, title, currency) }
            3 -> onOpNpc3(npc) { openShop(it.npc, inv, title, currency) }
            4 -> onOpNpc4(npc) { openShop(it.npc, inv, title, currency) }
            5 -> onOpNpc5(npc) { openShop(it.npc, inv, title, currency) }
        }
    }

    private fun ProtectedAccess.openShop(
        npc: Npc,
        inv: UnpackedInvType,
        title: String,
        currency: CurrencyType,
    ) {
        shops.open(player, npc, title, inv, currency)
    }

    private fun isUsableOp(op: String?): Boolean =
        !op.isNullOrBlank() && !op.equals("hidden", ignoreCase = true)

    private fun isTradeOp(op: String?): Boolean =
        isUsableOp(op) &&
            (op!!.contains("trade", ignoreCase = true) || op.contains("shop", ignoreCase = true))
}
