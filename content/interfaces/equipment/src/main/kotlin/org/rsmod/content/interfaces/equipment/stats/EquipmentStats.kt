@file:OptIn(UncheckedType::class)

package org.rsmod.content.interfaces.equipment.stats

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionGearCalculator
import org.rsmod.api.companion.CompanionPlayerRegistry
import org.rsmod.api.companion.CompanionRules
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.equipment.instance.EquipmentTier
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.interact.WornInteractions
import org.rsmod.api.player.output.ClientScripts.statGroupTooltip
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.mesInstanceData
import org.rsmod.api.player.output.objExamine
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfModalDrag
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.equipment.configs.equip_components
import org.rsmod.content.interfaces.equipment.configs.equip_enums
import org.rsmod.content.interfaces.equipment.configs.equip_interfaces
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.enums.EnumTypeMapResolver
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
public class EquipmentStats
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val eventBus: EventBus,
    private val enumResolver: EnumTypeMapResolver,
    private val protectedAccess: ProtectedAccessLauncher,
    private val wornInteractions: WornInteractions,
    private val wornBonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
    private val marketPrices: MarketPrices,
    private val instances: EquipmentInstanceRegistry,
    private val equipmentInstanceService: EquipmentInstanceService,
    private val companions: CompanionService,
    private val companionPlayerRegistry: CompanionPlayerRegistry,
) : PluginScript() {
    private val companionViewing = WeakHashMap<Player, Long>()

    override fun ScriptContext.startup() {
        onIfOverlayButton(equip_components.equipment) { player.selectStats() }

        val componentWornSlots = enumResolver[equip_enums.mapped_wearpos].filterValuesNotNull()
        for ((slot, component) in componentWornSlots) {
            onIfModalButton(component) { opWornMain(slot, it.op) }
        }

        onIfModalButton(equip_components.equipment_stats_side_inv) { opHeldSide(it.comsub, it.op) }
        onIfModalDrag(equip_components.equipment_stats_side_inv) { dragHeldButton(it) }
        onIfClose(equip_interfaces.equipment_stats_main) { companionViewing.remove(player) }
    }

    private fun Player.selectStats() {
        companionViewing.remove(this)
        ifClose(eventBus)
        protectedAccess.launch(this) { openStats() }
    }

    private fun ProtectedAccess.openStats() {
        stopAction()
        resetAnim()
        resetSpotanim()
        invTransmit(inv)
        ifOpenMainSidePair(
            main = equip_interfaces.equipment_stats_main,
            side = equip_interfaces.equipment_stats_side,
        )
        interfaceInvInit(
            inv = inv,
            target = equip_components.equipment_stats_side_inv,
            objRowCount = 4,
            objColCount = 7,
            dragType = 1,
            op1 = "Equip",
        )
        ifSetEvents(
            target = equip_components.equipment_stats_side_inv,
            range = inv.indices,
            IfEvent.Op1,
            IfEvent.Op10,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )
        updateBonuses()
    }

    private fun ProtectedAccess.updateBonuses() {
        val companionId = companionViewing[player]
        if (companionId != null) {
            val ownerId = player.characterId.toLong()
            val companion = companions.owned(ownerId).firstOrNull { it.id == companionId }
            if (companion != null) {
                updateCompanionBonuses(companion)
                return
            }
        }

        val comps = equip_components
        val stats = wornBonuses.calculate(player)
        val speedBase = weaponSpeeds.baseCenti(player)
        val speedActual = weaponSpeeds.actualCenti(player)
        val magicDmg = stats.finalMagicDmg
        val magicDmgSuffix = stats.magicDmgSuffix
        val undeadSuffix = stats.undeadSuffix
        val slayerSuffix = stats.slayerSuffix
        ifSetText(comps.equipment_stats_off_stab, "Stab: ${stats.offStab.signed}")
        ifSetText(comps.equipment_stats_off_slash, "Slash: ${stats.offSlash.signed}")
        ifSetText(comps.equipment_stats_off_crush, "Crush: ${stats.offCrush.signed}")
        ifSetText(comps.equipment_stats_off_magic, "Magic: ${stats.offMagic.signed}")
        ifSetText(comps.equipment_stats_off_range, "Range: ${stats.offRange.signed}")
        ifSetText(comps.equipment_stats_speed_base, "Base: ${speedBase.centiTicksToSecs}")
        ifSetText(comps.equipment_stats_speed, "Actual: ${speedActual.centiTicksToSecs}")
        ifSetText(comps.equipment_stats_def_stab, "Stab: ${stats.defStab.signed}")
        ifSetText(comps.equipment_stats_def_slash, "Slash: ${stats.defSlash.signed}")
        ifSetText(comps.equipment_stats_def_crush, "Crush: ${stats.defCrush.signed}")
        ifSetText(comps.equipment_stats_def_range, "Range: ${stats.defRange.signed}")
        ifSetText(comps.equipment_stats_def_magic, "Magic: ${stats.defMagic.signed}")
        ifSetText(comps.equipment_stats_melee_str, "Melee STR: ${stats.meleeStr.signed}")
        ifSetText(comps.equipment_stats_ranged_str, "Ranged STR: ${stats.rangedStr.signed}")
        ifSetText(comps.equipment_stats_magic_dmg, "Magic DMG: $magicDmg$magicDmgSuffix")
        ifSetText(comps.equipment_stats_prayer, "Prayer: ${stats.prayer.signed}")
        ifSetText(
            comps.equipment_stats_undead,
            "Undead: ${stats.undead.formatWholePercent}$undeadSuffix",
        )
        statGroupTooltip(
            player,
            comps.equipment_stats_undead_tooltip,
            comps.equipment_stats_undead,
            "Increases your effective accuracy and damage against undead creatures. " +
                "For multi-target Ranged and Magic attacks, this applies only to the " +
                "primary target. It does not stack with the Slayer multiplier.",
        )
        ifSetText(
            comps.equipment_stats_slayer,
            "Slayer: ${stats.slayer.formatWholePercent}$slayerSuffix",
        )
    }

    private fun ProtectedAccess.updateCompanionBonuses(companion: Companion) {
        val comps = equip_components
        val gear = CompanionGearCalculator.calculate(companion, instances)
        val spentTalents = companion.talents.sumOf { it.ranks }
        val remainingTalents = (companion.talentPoints - spentTalents).coerceAtLeast(0)

        // Offence column
        ifSetText(comps.equipment_stats_off_stab, "Level: ${companion.level}")
        ifSetText(comps.equipment_stats_off_slash, "Class: ${companion.companionClass.name}")
        ifSetText(
            comps.equipment_stats_off_crush,
            "HP: ${companion.hitpoints}/${gear.maximumHitpoints}",
        )
        val dmgPercent = "+${((gear.damageMultiplier - 1.0) * 100).toInt()}%"
        ifSetText(comps.equipment_stats_off_magic, "Damage: $dmgPercent")
        val suppPercent = "+${((gear.supportPower - 1.0) * 100).toInt()}%"
        ifSetText(comps.equipment_stats_off_range, "Support: $suppPercent")
        ifSetText(comps.equipment_stats_speed_base, "Delay: ${gear.attackDelay} ticks")
        ifSetText(
            comps.equipment_stats_speed,
            "Speed: ${String.format(java.util.Locale.ROOT, "%.2fs", gear.attackDelay * 0.6)}",
        )

        // Defence column
        ifSetText(
            comps.equipment_stats_def_stab,
            "Gear: ${companion.gearInstanceIds.size}/${CompanionRules.MAX_GEAR_ITEMS}",
        )
        ifSetText(comps.equipment_stats_def_slash, "Talents: $spentTalents pts")
        ifSetText(comps.equipment_stats_def_crush, "Unused: $remainingTalents pts")
        ifSetText(comps.equipment_stats_def_range, "Status: ${companion.state.name}")
        ifSetText(comps.equipment_stats_def_magic, "XP: ${companion.experience}")

        // Other column
        ifSetText(
            comps.equipment_stats_melee_str,
            "Dmg: x${String.format(java.util.Locale.ROOT, "%.2f", gear.damageMultiplier)}",
        )
        ifSetText(
            comps.equipment_stats_ranged_str,
            "Supp: x${String.format(java.util.Locale.ROOT, "%.2f", gear.supportPower)}",
        )
        ifSetText(comps.equipment_stats_magic_dmg, "Max HP: ${gear.maximumHitpoints}")
        ifSetText(comps.equipment_stats_prayer, "Total Pts: ${companion.talentPoints}")
        ifSetText(comps.equipment_stats_undead, "Companion:")
        ifSetText(comps.equipment_stats_slayer, companion.name)
    }

    private suspend fun ProtectedAccess.opWornMain(wornSlot: Int, op: IfButtonOp) {
        val companionId = companionViewing[player]
        if (companionId != null) {
            val ownerId = player.characterId.toLong()
            val companion = companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return
            val companionPlayer = companionPlayerRegistry.getPlayer(companionId)

            val emptySlot = player.inv.indexOfFirst { it == null }
            if (emptySlot == -1) {
                player.mes("You don't have enough free inventory space to unequip that.")
                return
            }

            val matchedInstance =
                companion.gearInstanceIds.firstOrNull { id ->
                    val inst = instances[id]
                    if (inst != null) {
                        val type = objTypes[inst.templateObj]
                        type != null && Wearpos[type.wearpos1]?.slot == wornSlot
                    } else false
                }
                    ?: companion.gearInstanceIds.firstOrNull { id ->
                        val wornObj = companionPlayer?.worn?.get(wornSlot)
                        val inst = instances[id]
                        wornObj != null && inst != null && inst.templateObj == wornObj.id
                    }

            if (matchedInstance != null) {
                val inst = instances[matchedInstance]
                companions.unequipGearItem(ownerId, companionId, matchedInstance)
                if (companionPlayer != null) {
                    companionPlayer.worn[wornSlot] = null
                    companionPlayer.rebuildAppearance()
                }

                val templateId = inst?.templateObj ?: companionPlayer?.worn?.get(wornSlot)?.id
                if (templateId != null) {
                    player.inv[emptySlot] = InvObj(templateId, 1, instanceId = matchedInstance)
                    resendSlot(inv, emptySlot)
                    val name = objTypes[templateId]?.name ?: "item"
                    player.mes("<col=660000>Unequipped $name from ${companion.name}.</col>")
                    updateBonuses()
                }
            } else {
                player.mes("${companion.name} is not wearing any equipment in that slot.")
            }
            return
        }

        val obj = worn[wornSlot] ?: return resendSlot(worn, wornSlot)
        wornInteractions.interact(this, worn, wornSlot, op)

        if (op == IfButtonOp.Op1) {
            val unequipped = obj != worn[wornSlot]
            if (unequipped) {
                updateBonuses()
            }
        }
    }

    private suspend fun ProtectedAccess.opHeldSide(invSlot: Int, op: IfButtonOp) {
        val obj = inv[invSlot] ?: return resendSlot(inv, invSlot)

        val companionId = companionViewing[player]
        if (companionId != null) {
            if (op == IfButtonOp.Op10) {
                val type = objTypes[obj]
                val price = marketPrices[type] ?: 0
                player.objExamine(type, obj.count, price)
                val instance = instances[obj.instanceId] ?: return
                player.mesInstanceData(type, instance)
                return
            }

            if (op == IfButtonOp.Op1) {
                val ownerId = player.characterId.toLong()
                if (companions.owned(ownerId).none { it.id == companionId }) return
                val type = objTypes[obj]
                val wearpos = Wearpos[type.wearpos1]
                if (wearpos == null || wearpos.isClientOnly) {
                    player.mes("This item cannot be equipped.")
                    return
                }

                if (obj.instanceId > 0L) {
                    finishCompanionEquip(
                        player,
                        ownerId,
                        companionId,
                        invSlot,
                        wearpos,
                        type,
                        obj.instanceId,
                    )
                    updateBonuses()
                    return
                }

                // A non-instanced item must be rolled + persisted first: a synthetic
                // in-memory id would only live in the registry, so the companion's
                // gear_instance_ids would resolve to nothing after a relog.
                equipmentInstanceService.rollAndPersist(
                    type = type,
                    source = "companion-equip",
                    tier = EquipmentTier.Bronze,
                    onFailure = { player.mes("<col=660000>Failed to equip ${type.name}.</col>") },
                ) { instance ->
                    finishCompanionEquip(
                        player,
                        ownerId,
                        companionId,
                        invSlot,
                        wearpos,
                        type,
                        instance.instanceId,
                    )
                    protectedAccess.launch(player) { updateBonuses() }
                }
                return
            }
            return
        }

        if (op == IfButtonOp.Op10) {
            val type = objTypes[obj]
            val price = marketPrices[type] ?: 0
            player.objExamine(type, obj.count, price)
            val instance = instances[obj.instanceId] ?: return
            player.mesInstanceData(type, instance)
            return
        }

        if (op == IfButtonOp.Op1) {
            if (!objTypes[obj].isEquipable) {
                mes("You can't equip that.")
                return
            }

            opHeld2(invSlot)

            val equipped = obj != inv[invSlot]
            if (equipped) {
                updateBonuses()
            }
            return
        }

        throw IllegalStateException("Op not allowed: $op (obj=$obj, invSlot=$invSlot, inv=$inv)")
    }

    private fun finishCompanionEquip(
        player: Player,
        ownerId: Long,
        companionId: Long,
        invSlot: Int,
        wearpos: Wearpos,
        type: UnpackedObjType,
        instanceId: Long,
    ) {
        val companion = companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return
        val inv = player.inv
        val obj = inv[invSlot]
        if (obj == null || obj.id != type.id) {
            player.mes("The item is no longer in that inventory slot.")
            return
        }
        val companionPlayer = companionPlayerRegistry.getPlayer(companionId)

        // If companion already has an item in this slot, find and unequip it first
        val existingInst =
            companion.gearInstanceIds.firstOrNull { id ->
                val inst = instances[id]
                if (inst != null) {
                    objTypes[inst.templateObj]?.let {
                        Wearpos[it.wearpos1]?.slot == wearpos.slot
                    } == true
                } else false
            }
        val existingWornObj =
            existingInst
                ?.let { instances[it] }
                ?.let { InvObj(it.templateObj, 1, instanceId = it.instanceId) }

        // Swapping an occupied slot keeps the count the same - the cap only applies
        // when equipping into a previously empty slot.
        if (
            existingInst == null && companion.gearInstanceIds.size >= CompanionRules.MAX_GEAR_ITEMS
        ) {
            player.mes(
                "${companion.name} is already wearing the maximum number of gear items (${CompanionRules.MAX_GEAR_ITEMS}/${CompanionRules.MAX_GEAR_ITEMS})."
            )
            return
        }

        if (existingInst != null) {
            companions.unequipGearItem(ownerId, companionId, existingInst)
            if (companionPlayer != null) {
                companionPlayer.worn[wearpos.slot] = null
            }
        }

        // Transfer from player inventory to companion
        val returnedObj = existingWornObj
        if (obj.count > 1) {
            inv[invSlot] = InvObj(obj.id, obj.count - 1, obj.vars, obj.instanceId)
        } else {
            inv[invSlot] = returnedObj
        }
        resendSlot(inv, invSlot)

        if (returnedObj != null && obj.count > 1) {
            val emptySlot = inv.indexOfFirst { it == null }
            if (emptySlot != -1) {
                inv[emptySlot] = returnedObj
                resendSlot(inv, emptySlot)
            }
        }

        companions.equipGearItem(ownerId, companionId, instanceId)
        if (companionPlayer != null) {
            companionPlayer.worn[wearpos.slot] = InvObj(type, 1, instanceId = instanceId)
            companionPlayer.rebuildAppearance()
        }
        player.mes("<col=006600>Equipped ${type.name} to ${companion.name}!</col>")
    }

    private fun ProtectedAccess.dragHeldButton(drag: IfModalDrag) {
        val fromSlot = drag.selectedSlot ?: return
        val intoSlot = drag.targetSlot ?: return
        invMoveToSlot(inv, inv, fromSlot, intoSlot)
    }
}

private val Int.signed: String
    get() = if (this < 0) "$this" else "+$this"

private val Int.formatPercent: String
    get() = "+${this / 10.0}%"

private val Int.formatWholePercent: String
    get() = "+${this / 10}%"

private val Int.centiTicksToSecs: String
    get() = String.format(java.util.Locale.ROOT, "%.2fs", this * 0.006)

private val WornBonuses.Bonuses.finalMagicDmg: String
    get() = multipliedMagicDmg.formatPercent

private val WornBonuses.Bonuses.magicDmgSuffix: String
    get() = if (magicDmgAdditive == 0) "" else "<col=be66f4> ($magicDmgAdditive%)</col>"

// Undead bonus has a trailing whitespace when bonus is at 0.
private val WornBonuses.Bonuses.undeadSuffix: String
    get() = if (undead == 0) " " else if (undeadMeleeOnly) " (melee)" else " (all styles)"

private val WornBonuses.Bonuses.slayerSuffix: String
    get() = if (slayer == 0) "" else if (slayerMeleeOnly) " (melee)" else " (all styles)"
