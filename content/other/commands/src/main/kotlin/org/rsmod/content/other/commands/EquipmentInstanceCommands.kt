package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.FashionScapeDrops
import org.rsmod.api.config.refs.varps
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.equipment.instance.EquipmentInstanceFingerprint
import org.rsmod.api.equipment.instance.EquipmentInstanceInvariants
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentInstanceRepository
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.equipment.instance.ForgeConfig
import org.rsmod.api.equipment.instance.ForgeResult
import org.rsmod.api.equipment.instance.ForgeService
import org.rsmod.api.equipment.instance.ItemActor
import org.rsmod.api.equipment.instance.ItemActorType
import org.rsmod.api.equipment.instance.ItemEvolutionCatalog
import org.rsmod.api.equipment.instance.ItemMutationOperation
import org.rsmod.api.equipment.instance.MysteryEnchantId
import org.rsmod.api.equipment.instance.MysteryEnchantRoll
import org.rsmod.api.equipment.instance.MysteryEnchantService
import org.rsmod.api.equipment.instance.MysteryEnchantTier
import org.rsmod.api.equipment.instance.ReforgeOperation
import org.rsmod.api.equipment.instance.ReforgeService
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.output.EquipmentInstanceHoverSync
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class EquipmentInstanceCommands
@Inject
constructor(
    private val instances: EquipmentInstanceService,
    private val registry: EquipmentInstanceRegistry,
    private val reforges: ReforgeService,
    private val forge: ForgeService,
    private val players: PlayerList,
    private val objTypes: ObjTypeList,
    private val names: NameMapping,
    private val fashionScape: FashionScapeDrops,
    private val db: GameDbManager,
    private val repository: EquipmentInstanceRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("invequiproll", "Create deterministic wearable instance", ::roll)
        onCommand("invequipreforge", "Reforge an instanced inventory item", ::reforge)
        onCommand("itemupgrade", "Upgrade an instanced item with Forge ticks", ::upgrade)
        onCommand("enchant", "Give a held/equipped item a test Mystery Enchant", ::enchant)
        onCommand(
            "enchantkills",
            "Set the held/equipped Mystery Enchant kill counter",
            ::enchantKills,
        )
        objTypes[TRADING_STICK_BANK_SCROLL_ID]?.let { scroll ->
            onOpHeld1(scroll) { useTradingStickBankScroll(it.slot) }
        }
        onCommand("fashiondrop", "Roll a FashionScape cosmetic drop", ::fashionDrop) {
            invalidArgs = "Use as ::fashiondrop [skill] (e.g. ::fashiondrop woodcutting)"
        }
        onCommand("iteminspect", "Show the canonical instance snapshot", ::itemInspect) {
            invalidArgs = "Use as ::iteminspect <instanceId|inv slot>"
        }
        onCommand("itemhistory", "Show the instance audit event log", ::itemHistory) {
            invalidArgs = "Use as ::itemhistory <instanceId|inv slot>"
        }
        onCommand("itemvalidate", "Run the instance invariant checker", ::itemValidate) {
            invalidArgs = "Use as ::itemvalidate <instanceId|inv slot>"
        }
        onCommand("itemfingerprint", "Show/verify the instance fingerprint", ::itemFingerprint) {
            invalidArgs = "Use as ::itemfingerprint <instanceId|inv slot>"
        }
    }

    /**
     * Resolves an instance by registry id, or (when [bySlot] is true) by inventory slot. Reports
     * the failure to the player and returns `null`.
     */
    private fun Cheat.instanceArg(
        bySlot: Boolean
    ): org.rsmod.api.equipment.instance.EquipmentInstance? {
        val arg = args[0].toLongOrNull()
        if (arg == null) {
            player.mes("Instance id/slot must be a number.")
            return null
        }
        val id =
            if (bySlot) {
                val obj = player.inv[arg.toInt()]
                if (obj == null || obj.instanceId <= 0) {
                    player.mes("No instanced item in inventory slot $arg.")
                    return null
                }
                obj.instanceId
            } else {
                arg
            }
        val instance = registry[id]
        if (instance == null) {
            player.mes("Instance #$id is not loaded in the registry.")
        }
        return instance
    }

    private fun itemInspect(cheat: Cheat) =
        with(cheat) {
            val inst = instanceArg(bySlot = false) ?: instanceArg(bySlot = true) ?: return
            player.mes(
                "<col=ff981f>Instance #${inst.instanceId}</col> ${inst.rarity.name} " +
                    "template=${inst.templateObj} (${inst.category.name})"
            )
            player.mes(
                "  uuid=${inst.instanceUuid.ifEmpty { "-" }} rev=${inst.revision} " +
                    "state=${inst.state.name} binding=${inst.binding.name}"
            )
            player.mes(
                "  ownerAcc=${inst.ownerAccountId ?: "-"} ownerChar=${inst.ownerCharacterId ?: "-"} " +
                    "ilvl=${inst.itemLevel} mastery=${inst.masteryLevel} xp=${inst.experience} " +
                    "quality=${inst.quality}"
            )
            player.mes(
                "  evolution=${inst.evolutionStage}" +
                    (inst.evolutionBranch?.let { " ($it)" } ?: "") +
                    " nextGate=" +
                    (ItemEvolutionCatalog.nextStage(inst.evolutionStage)?.let {
                        "ilvl ${it.requiredItemLevel} (${it.title})"
                    } ?: "max")
            )
            player.mes(
                "  affixes=${inst.affixes.size} sockets=${inst.sockets.size} " +
                    "uniques=${inst.uniqueEffectIds.size} locked=${inst.lockedAffixSlots.size} " +
                    "reforges=${inst.reforgeCount} tier=${inst.tier.name}"
            )
            player.mes(
                "  lineage: source=${inst.source} parents=${inst.lineageParentIds} " +
                    "recipe=${inst.lineageRecipeId ?: "-"} drop=${inst.lineageDropId ?: "-"}"
            )
        }

    private fun itemHistory(cheat: Cheat) =
        with(cheat) {
            val inst = instanceArg(bySlot = false) ?: instanceArg(bySlot = true) ?: return
            val uid = player.uid
            db.request({ conn ->
                GameDbResult.Ok(repository.loadHistory(conn, inst.instanceId))
            }) { result ->
                val target = uid.resolve(players) ?: return@request
                when (result) {
                    is GameDbResult.Ok -> {
                        if (result.value.isEmpty()) {
                            target.mes("Instance #${inst.instanceId} has no recorded events.")
                            return@request
                        }
                        target.mes("History of instance #${inst.instanceId}:")
                        for (event in result.value.takeLast(15)) {
                            target.mes(
                                "  rev ${event.beforeRevision}->${event.afterRevision} " +
                                    "${event.operation.name} by ${event.actor.type.name}" +
                                    (event.actor.id?.let { ":$it" } ?: "") +
                                    " (${event.source})"
                            )
                        }
                        val broken = EquipmentInstanceInvariants.eventChainViolations(result.value)
                        if (broken.isNotEmpty()) {
                            target.mes("<col=ff3333>Event chain broken: ${broken.first()}</col>")
                        }
                    }
                    is GameDbResult.Err -> target.mes("History query failed.")
                }
            }
        }

    private fun itemValidate(cheat: Cheat) =
        with(cheat) {
            val inst = instanceArg(bySlot = false) ?: instanceArg(bySlot = true) ?: return
            val violations = EquipmentInstanceInvariants.violations(inst)
            if (violations.isEmpty()) {
                player.mes("<col=00b33c>Instance #${inst.instanceId}: all invariants hold.</col>")
            } else {
                player.mes(
                    "<col=ff3333>Instance #${inst.instanceId}: ${violations.size} violation(s): " +
                        violations.joinToString() +
                        "</col>"
                )
            }
        }

    private fun itemFingerprint(cheat: Cheat) =
        with(cheat) {
            val inst = instanceArg(bySlot = false) ?: instanceArg(bySlot = true) ?: return
            val recomputed = EquipmentInstanceFingerprint.of(inst)
            val status =
                when {
                    inst.fingerprint.isEmpty() -> "<col=ff981f>not stamped yet</col>"
                    inst.fingerprint == recomputed -> "<col=00b33c>match</col>"
                    else -> "<col=ff3333>MISMATCH</col>"
                }
            player.mes("Instance #${inst.instanceId} fingerprint: $status")
            player.mes(
                "  stored=${inst.fingerprint.take(16).ifEmpty { "-" }}... " +
                    "computed=${recomputed.take(16)}..."
            )
        }

    private fun fashionDrop(cheat: Cheat) =
        with(cheat) {
            val skill = (args.getOrNull(0) ?: "WOODCUTTING").uppercase()
            if (skill !in FashionScapeDrops.SKILL_EFFECTS && skill != "ALL") {
                player.mes("Unknown skill: $skill")
                return
            }
            fashionScape.grant(player, skill)
            player.mes("Rolling a FashionScape drop for ${skill.lowercase()}...")
        }

    private fun roll(cheat: Cheat) =
        with(cheat) {
            val rawName = args.getOrNull(0) ?: "bronze_sword"
            val seed = args.getOrNull(1)?.toLongOrNull() ?: 239L
            val normalized = rawName.replace("-", "_")
            val typeId = normalized.toIntOrNull() ?: names.objs[normalized]
            if (typeId == null) {
                player.mes("Unknown object: $rawName")
                return
            }
            val type =
                objTypes[typeId]
                    ?: run {
                        player.mes("Object does not exist: $typeId")
                        return
                    }
            val uid = player.uid
            instances.rollAndPersist(
                type,
                source = "owner-command",
                tier = EquipmentTierResolver.resolve(type),
                seed = seed,
                onFailure = { uid.resolve(players)?.mes("Equipment roll failed (not wearable?)") },
            ) { instance ->
                uid.resolve(players)?.let { target ->
                    target.invAdd(
                        target.inv,
                        type,
                        count = 1,
                        instanceId = instance.instanceId,
                        strict = false,
                    )
                    target.mes(
                        "Created ${instance.rarity.name} ${type.name} instance #${instance.instanceId}"
                    )
                }
            }
        }

    private fun reforge(cheat: Cheat) =
        with(cheat) {
            val slot = args.getOrNull(0)?.toIntOrNull()
            val opName = args.getOrNull(1)
            val affixSlot = args.getOrNull(2)?.toIntOrNull()
            if (slot == null || opName == null) {
                player.mes("Usage: invequipreforge <inv slot> <operation> [affix slot]")
                return
            }
            val operation =
                ReforgeOperation.entries.firstOrNull { it.name.equals(opName, ignoreCase = true) }
            if (operation == null) {
                player.mes(
                    "Unknown reforge operation: $opName " +
                        "(options: ${ReforgeOperation.entries.joinToString { it.name }})"
                )
                return
            }
            val obj =
                player.inv[slot]
                    ?: run {
                        player.mes("No item in inventory slot $slot.")
                        return
                    }
            val instance =
                registry[obj.instanceId]
                    ?: run {
                        player.mes("Item in slot $slot has no equipment instance.")
                        return
                    }
            val preview =
                try {
                    reforges.preview(instance, operation, affixSlot)
                } catch (e: IllegalArgumentException) {
                    player.mes("Cannot reforge: ${e.message}")
                    return
                }
            val currencyType =
                objTypes[preview.cost.currencyObj]
                    ?: run {
                        player.mes("Reforge currency obj ${preview.cost.currencyObj} is missing.")
                        return
                    }
            val balance = player.inv.count(currencyType)
            if (balance < preview.cost.amount) {
                player.mes(
                    "Reforge requires ${preview.cost.amount}x ${currencyType.name} " +
                        "(you have $balance)."
                )
                return
            }
            val committed =
                try {
                    reforges.commit(preview, balance)
                } catch (e: IllegalArgumentException) {
                    player.mes("Cannot reforge: ${e.message}")
                    return
                }
            val uid = player.uid
            val inv = player.inv
            instances.updateInstance(
                committed,
                onFailure = {
                    uid.resolve(players)?.mes("Reforge could not be persisted - nothing charged.")
                },
            ) { updated ->
                uid.resolve(players)?.let { target ->
                    target.invDel(
                        inv,
                        preview.cost.currencyObj,
                        preview.cost.amount,
                        strict = false,
                    )
                    // Push the mutated instance to the hover channel if the obj is still here.
                    val current = inv[slot]
                    if (current != null && current.instanceId == updated.instanceId) {
                        EquipmentInstanceHoverSync.upsert(
                            target,
                            inv,
                            slot,
                            current,
                            registry,
                            objTypes,
                        )
                    }
                    target.mes(
                        "Reforged instance #${updated.instanceId} " +
                            "(${operation.name}, total ${updated.reforgeCount})."
                    )
                }
            }
        }

    /**
     * Permanently upgrades an instanced item through the shared Forge upgrade path.
     *
     * This is the dev-console shortcut for the Forge UI's UPGRADE button: it uses the same
     * [ForgeService] scaling, [ForgeConfig] costs and +10-level cap, so `::itemupgrade` and the
     * Forge panel can never diverge. Currency is consumed only after the audited instance gateway
     * has accepted the mutation.
     */
    private fun upgrade(cheat: Cheat) =
        with(cheat) {
            val slot = args.getOrNull(0)?.toIntOrNull()
            if (slot == null) {
                player.mes("Usage: ::itemupgrade <inventory slot>")
                return
            }
            val obj =
                player.inv[slot]
                    ?: run {
                        player.mes("No item in slot $slot.")
                        return
                    }
            val instance =
                registry[obj.instanceId]
                    ?: run {
                        player.mes("That item is not an upgradeable equipment instance.")
                        return
                    }
            val preview =
                when (val result = forge.previewUpgrade(instance)) {
                    is ForgeResult.Err -> {
                        player.mes("Forge: ${result.error.message}")
                        return
                    }
                    is ForgeResult.Ok -> result.preview
                }
            val cost = preview.cost
            val tickType = objTypes[ForgeConfig.TICKET_OBJ_ID]
            val goldType = objTypes[ForgeConfig.GOLD_OBJ_ID]
            val held = tickType?.let { player.inv.count(it) } ?: 0
            val heldGold = goldType?.let { player.inv.count(it) } ?: 0
            if (tickType == null || held < cost.tickets || heldGold < cost.gold) {
                player.mes(
                    "You need ${cost.tickets} Forge ticket(s) and ${cost.gold} coins " +
                        "to upgrade this item."
                )
                return
            }
            val uid = player.uid
            val inv = player.inv
            instances.updateInstance(
                preview.after,
                operation = org.rsmod.api.equipment.instance.ItemMutationOperation.UPGRADED,
                source = "forge-tick",
                onFailure = {
                    uid.resolve(players)
                        ?.mes("Forge upgrade failed; your tickets were not consumed.")
                },
            ) { result ->
                uid.resolve(players)?.let { target ->
                    target.invDel(inv, ForgeConfig.TICKET_OBJ_ID, cost.tickets, strict = false)
                    if (cost.gold > 0) {
                        target.invDel(inv, ForgeConfig.GOLD_OBJ_ID, cost.gold, strict = false)
                    }
                    val current = inv[slot]
                    if (current != null && current.instanceId == result.instanceId) {
                        EquipmentInstanceHoverSync.upsert(
                            target,
                            inv,
                            slot,
                            current,
                            registry,
                            objTypes,
                        )
                    }
                    target.mes(
                        "<col=ffb84d>Forge complete!</col> Upgrade " +
                            "+${result.upgradeLevel}/${ForgeConfig.MAX_UPGRADE_LEVEL} — " +
                            "all item stats increased by 10%."
                    )
                }
            }
        }

    private fun enchant(cheat: Cheat) =
        with(cheat) {
            val id =
                args.getOrNull(0)?.uppercase()?.let {
                    runCatching { MysteryEnchantId.valueOf(it) }.getOrNull()
                }
                    ?: run {
                        player.mes("Usage: ::enchant <name> <tier>")
                        return
                    }
            val tier =
                args.getOrNull(1)?.uppercase()?.let {
                    runCatching { MysteryEnchantTier.valueOf(it) }.getOrNull()
                }
                    ?: run {
                        player.mes("Tier must be COMMON, RARE, EPIC or LEGENDARY.")
                        return
                    }
            val instance =
                findHeldInstance(player)
                    ?: run {
                        player.mes("No held or equipped item instance found.")
                        return
                    }
            val updated = MysteryEnchantService.apply(instance, MysteryEnchantRoll(id, tier))
            instances.updateInstance(
                updated,
                operation = ItemMutationOperation.MYSTERY_ENCHANTED,
                source = "admin-enchant",
                actor = ItemActor(ItemActorType.ADMIN, player.characterId.toLong()),
                onFailure = { player.mes("Mystery Enchant could not be persisted.") },
            ) { result ->
                player.mes(
                    "Applied ${MysteryEnchantService.displayName(id)} ${tier.name} " +
                        "(${result.mysteryEnchantKillsRemaining}/${result.mysteryEnchantKillsMax} kills)."
                )
            }
        }

    private fun enchantKills(cheat: Cheat) =
        with(cheat) {
            val amount =
                args.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0)
                    ?: run {
                        player.mes("Usage: ::enchantkills <amount>")
                        return
                    }
            val instance =
                findHeldInstance(player)
                    ?: run {
                        player.mes("No held or equipped item instance found.")
                        return
                    }
            if (instance.mysteryEnchantId == null || instance.mysteryEnchantTier == null) {
                player.mes("The held item has no active Mystery Enchant.")
                return
            }
            val updated =
                if (amount == 0) MysteryEnchantService.clear(instance)
                else
                    instance.copy(
                        mysteryEnchantKillsRemaining =
                            amount.coerceAtMost(instance.mysteryEnchantKillsMax)
                    )
            instances.updateInstance(
                updated,
                operation = ItemMutationOperation.MYSTERY_ENCHANTED,
                source = "admin-enchant-kills",
                actor = ItemActor(ItemActorType.ADMIN, player.characterId.toLong()),
                onFailure = { player.mes("Mystery Enchant counter could not be persisted.") },
            ) { result ->
                player.mes(
                    "Mystery Enchant counter: ${result.mysteryEnchantKillsRemaining}/${result.mysteryEnchantKillsMax}."
                )
            }
        }

    private fun findHeldInstance(player: org.rsmod.game.entity.Player) =
        player.invMap.values
            .asSequence()
            .flatMap { it.objs.asSequence() }
            .filterNotNull()
            .mapNotNull { registry[it.instanceId] }
            .firstOrNull()

    private fun ProtectedAccess.useTradingStickBankScroll(slot: Int) {
        if (player.vars[varps.generic_temp_state_65516] == 1) {
            player.mes("You have already unlocked automatic Trading Stick banking.")
            return
        }
        player.invDel(player.inv, TRADING_STICK_BANK_SCROLL_ID, 1, strict = true)
        VarPlayerIntMapSetter.set(player, varps.generic_temp_state_65516, 1)
        player.mes("Your Trading Stick auto-banking unlock is now permanent.")
    }

    private companion object {
        const val TRADING_STICK_BANK_SCROLL_ID = 607
    }
}
