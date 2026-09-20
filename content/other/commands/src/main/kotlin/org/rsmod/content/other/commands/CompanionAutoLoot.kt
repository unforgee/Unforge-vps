package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.companion.CompanionRules
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategoryResolver
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos

/**
 * Intelligent Auto-Loot & Beast-of-Burden Smart Storage system for Unforge Companions.
 *
 * Automatically sweeps drops from defeated monsters directly into the active companion's 10-100
 * slot pack upon NPC death. Respects item stackability, existing inventory capacity, and item
 * ownership. Detects valuable treasures (>= 50k GP or equipment instances) and triggers dynamic
 * banter!
 */
@Singleton
public class CompanionAutoLoot
@Inject
constructor(
    private val companions: CompanionService,
    private val companionPlayerManager: CompanionPlayerManager,
    private val personality: CompanionPersonality,
    private val objRegistry: ObjRegistry,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
    private val equipmentInstances: EquipmentInstanceRegistry,
    private val mapClock: MapClock,
) {
    /** Character ID -> Auto-loot enabled flag (defaults to true). */
    private val autoLootDisabled = ConcurrentHashMap.newKeySet<Long>()

    public fun isEnabled(characterId: Long): Boolean = !autoLootDisabled.contains(characterId)

    public fun toggle(player: Player): Boolean {
        val cid = player.characterId.toLong()
        return setEnabled(player, !autoLootDisabled.contains(cid))
    }

    /** Explicit set used by the companion panel - a checkbox cannot blind-toggle. */
    public fun setEnabled(player: Player, enabled: Boolean): Boolean {
        val cid = player.characterId.toLong()
        if (enabled) {
            autoLootDisabled.remove(cid)
        } else {
            autoLootDisabled.add(cid)
        }
        val statusText = if (enabled) "<col=006600>ENABLED</col>" else "<col=ff0000>DISABLED</col>"
        player.mes("Companion Auto-Loot is now $statusText.")
        return enabled
    }

    public fun onNpcKilled(event: NpcKilledEvent) {
        val killer = event.killer
        val ownerId = runCatching { killer.characterId.toLong() }.getOrNull() ?: return
        if (!isEnabled(ownerId)) return

        val active = companions.owned(ownerId).firstOrNull { it.active } ?: return
        val bot = companionPlayerManager.getPlayer(active.id) ?: return
        if (!bot.isSlotAssigned) return

        // Leash check: companion must be near the battle to auto-loot
        if (bot.coords.chebyshevDistance(killer.coords) > 16) return

        val storage = killer.invMap.getValue(BaseInvs.companion_storage)
        val owner = companionPlayerManager.ownerPlayer(active.id)
        val ownerUuids = setOfNotNull(killer.observerUUID, owner?.observerUUID)
        val maxCapacity = active.packCapacity
        val dropCoords = event.npc.coords

        val groundObjs =
            objRegistry
                .findAll(dropCoords)
                .filter {
                    it.isVisibleTo(killer) &&
                        (it.receiverId in ownerUuids || it.ownerId in ownerUuids)
                }
                .toList()

        if (groundObjs.isEmpty()) return

        var lootedCount = 0
        var packFull = false
        val lootedNames = mutableListOf<String>()

        for (obj in groundObjs) {
            val type = objTypes[obj.type] ?: continue
            val isStackable = type.isStackable
            val occupied = storage.count { it != null }
            val existingSlot = if (isStackable) storage.indexOfFirst { it?.id == obj.type } else -1

            val canFit =
                if (existingSlot != -1) {
                    val currentCount = storage[existingSlot]!!.count
                    currentCount.toLong() + obj.count <= Int.MAX_VALUE
                } else {
                    occupied < maxCapacity
                }

            if (!canFit) {
                packFull = true
                continue
            }

            // Transfer obj to companion pack
            if (obj.instanceId != InvObj.NO_INSTANCE) {
                val freeSlot =
                    storage.indices.firstOrNull { storage[it] == null && it < maxCapacity }
                if (freeSlot != null) {
                    storage[freeSlot] = InvObj(type, obj.count, instanceId = obj.instanceId)
                } else {
                    packFull = true
                    continue
                }
            } else {
                val result = killer.invAdd(inv = storage, type = type, count = obj.count)
                if (!result.success) {
                    packFull = true
                    continue
                }
            }

            val equipped =
                obj.instanceId != InvObj.NO_INSTANCE &&
                    tryAutoEquip(ownerId, active.id, obj.instanceId, storage)
            objRepo.del(obj)
            lootedCount++

            val totalValue = type.cost.toLong() * obj.count
            if (totalValue >= 50_000L || obj.instanceId != InvObj.NO_INSTANCE) {
                personality.sayValuableLoot(bot, killer, active, type.name, mapClock.cycle.toLong())
            } else if (!equipped) {
                lootedNames.add("${obj.count}x ${type.name}")
            }
        }

        val occupiedNow = storage.count { it != null }
        if (packFull) {
            personality.sayFullPack(bot, killer, active, mapClock.cycle.toLong())
        }

        if (lootedNames.isNotEmpty()) {
            killer.mes(
                "<col=009933>[${active.name}]: Auto-looted ${lootedNames.joinToString(", ")} into pack ($occupiedNow/$maxCapacity slots used).</col>"
            )
        }
    }

    private fun tryAutoEquip(
        ownerId: Long,
        companionId: Long,
        instanceId: Long,
        storage: org.rsmod.game.inv.Inventory,
    ): Boolean {
        val candidate = equipmentInstances[instanceId] ?: return false
        val candidateType = objTypes[candidate.templateObj] ?: return false
        val category = EquipmentCategoryResolver.resolve(Wearpos[candidateType.wearpos1])
        if (category.name == "Custom") return false
        val current = companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return false
        val oldId =
            current.gearInstanceIds.firstOrNull { id ->
                val old = equipmentInstances[id] ?: return@firstOrNull false
                val oldType = objTypes[old.templateObj] ?: return@firstOrNull false
                EquipmentCategoryResolver.resolve(Wearpos[oldType.wearpos1]) == category
            }
        if (gearScore(candidate) <= gearScore(oldId?.let(equipmentInstances::get))) return false
        storage
            .indexOfFirst { it?.instanceId == instanceId }
            .takeIf { it >= 0 }
            ?.let { storage[it] = null }
        oldId?.let { old ->
            val oldInstance = equipmentInstances[old]
            val oldType = oldInstance?.let { objTypes[it.templateObj] }
            val slot = storage.indexOfFirst { it == null }
            if (oldInstance != null && oldType != null && slot >= 0) {
                storage[slot] = InvObj(oldType, 1, instanceId = old)
            }
        }
        companions.equipGear(
            ownerId,
            companionId,
            (current.gearInstanceIds.filter { it != oldId } + instanceId).take(
                CompanionRules.MAX_GEAR_ITEMS
            ),
        )
        return true
    }

    private fun gearScore(item: EquipmentInstance?): Int =
        item?.let {
            it.tier.value * 10_000 +
                it.itemLevel * 100 +
                it.quality +
                it.affixes.sumOf(EquipmentAffixRoll::magnitude)
        } ?: 0
}
