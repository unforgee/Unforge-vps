package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.player.output.mes
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.operation.ShopOperations
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.currency.CurrencyType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public object CompanionStorageCurrency {
    public val type: CurrencyType =
        CurrencyType(internalId = 88888, internalName = "companion_storage_currency")
}

@Singleton
public class CompanionStorageShopOperations
@Inject
constructor(private val objTypes: ObjTypeList, private val companions: CompanionService) :
    ShopOperations {
    override fun shopInvOp(
        player: Player,
        sideInv: Inventory,
        shop: Shop,
        slot: Int,
        op: IfButtonOp,
    ) {
        val item = shop.inv[slot] ?: return
        val count =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> 50
                else -> item.count
            }
        val moved =
            player.invTransfer(
                from = shop.inv,
                fromSlot = slot,
                count = count,
                into = sideInv,
                strict = false,
            )
        if (moved.completed() > 0) {
            val name = objTypes[item.id]?.name ?: "item"
            player.mes("Withdrew ${moved.completed()}x $name from pack.")
        } else {
            player.mes("Your inventory is full.")
        }
    }

    override fun sideInvOp(
        player: Player,
        sideInv: Inventory,
        shop: Shop,
        slot: Int,
        op: IfButtonOp,
    ) {
        val item = sideInv[slot] ?: return
        val objType = objTypes[item.id]
        val isStackable = objType?.isStackable ?: false
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull()
        val active = ownerId?.let { companions.owned(it).firstOrNull { c -> c.active } }
        val maxCapacity = active?.packCapacity ?: 10
        val occupied = shop.inv.count { it != null }

        val canStack = isStackable && shop.inv.any { it?.id == item.id }
        if (!canStack && occupied >= maxCapacity) {
            player.mes(
                "Your companion's pack is full ($occupied/$maxCapacity slots). Upgrade your pack to carry more!"
            )
            return
        }

        var count =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> 50
                else -> item.count
            }
        if (!isStackable) {
            val availableSlots = (maxCapacity - occupied).coerceAtLeast(0)
            count = min(count, availableSlots)
            if (count <= 0) {
                player.mes(
                    "Your companion's pack is full ($occupied/$maxCapacity slots). Upgrade your pack to carry more!"
                )
                return
            }
        }

        val moved =
            player.invTransfer(
                from = sideInv,
                fromSlot = slot,
                count = count,
                into = shop.inv,
                strict = false,
            )
        if (moved.completed() > 0) {
            val name = objTypes[item.id]?.name ?: "item"
            player.mes("Stored ${moved.completed()}x $name in pack.")
        } else {
            val nowOccupied = shop.inv.count { it != null }
            player.mes(
                "Your companion's pack is full ($nowOccupied/$maxCapacity slots). Upgrade your pack to carry more!"
            )
        }
    }
}

@Singleton
public class CompanionStorageShopScript
@Inject
constructor(
    private val shops: Shops,
    private val operationMap: ShopOperationMap,
    private val storageOperations: CompanionStorageShopOperations,
) : PluginScript() {

    override fun ScriptContext.startup() {
        operationMap.register(CompanionStorageCurrency.type, storageOperations)
    }

    public fun openStorage(player: Player, companionName: String, packCapacity: Int = 10) {
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        shops.open(
            player = player,
            title = "$companionName's Pack",
            shopInv = storage,
            sideInv = player.inv,
            currency = CompanionStorageCurrency.type,
            buyPercentage = 1.0,
            sellPercentage = 1.0,
            changePercentage = 0.0,
            subtext = "Beast of Burden Storage ($occupied / $packCapacity Slots)",
        )
    }
}
