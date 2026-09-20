package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.Base64
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceMutationService
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.equipment.instance.ForgeConfig
import org.rsmod.api.equipment.instance.ForgeCost
import org.rsmod.api.equipment.instance.ForgeOperation
import org.rsmod.api.equipment.instance.ForgePreview
import org.rsmod.api.equipment.instance.ForgeResult
import org.rsmod.api.equipment.instance.ForgeService
import org.rsmod.api.equipment.instance.ItemActor
import org.rsmod.api.equipment.instance.ItemActorType
import org.rsmod.api.equipment.instance.ItemMutationOperation
import org.rsmod.api.equipment.instance.ItemMutationRequest
import org.rsmod.api.equipment.instance.ItemMutationResult
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.EquipmentInstanceHoverSync
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.output.mes
import org.rsmod.content.pvmprogression.events.LegendaryEnchantRolledEvent
import org.rsmod.content.pvmprogression.events.MysteryEnchantRolledEvent
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Forge 2.0 server endpoint: `forgeopen`/`forgeop` commands (sent by the client plugin through the
 * cheat-script channel) plus the in-interface entry point used by the Companion Hub equipment
 * screen ([openForCompanionGear]).
 *
 * Protocol (server -> client, [ChatType.Console] side channel, one message per line):
 * ```
 * UNFORGE_FORGE|BEGIN|instanceId|rev|objId|nameB64|rarity|tier|ilvl|quality|upgrade|scope|slot
 * UNFORGE_FORGE|AFF|slot|defIdB64|familyB64|stat|unit|polarity|mag|rerollable|min|max|qualityBps
 * UNFORGE_FORGE|SKA|skillB64|effectB64|unit|mag
 * UNFORGE_FORGE|COST|upT|upG|allT|allG|selBaseT|selPerT|selGoldPer|mysteryT|warnHighRoll
 * UNFORGE_FORGE|ENCHANT|id|tier|remaining|max|active
 * UNFORGE_FORGE|BAL|tickets|gold
 * UNFORGE_FORGE|END|instanceId
 * UNFORGE_FORGE|RESULT|op|instanceId|newRev|newUpgradeLevel
 * UNFORGE_FORGE|DIFF|slot|stat|unit|oldMag|newMag        (all affixes, incl. unchanged)
 * UNFORGE_FORGE|ERR|code|messageB64
 * ```
 *
 * The client only ever sends `instanceId`, `revision`, the operation and the selected affix slots;
 * everything else is resolved and validated server-side. The item must currently sit in one of the
 * player's own containers (inventory, worn, companion pack) or be equipped on one of their
 * companions - the request carries no scope, so a forged request can never target an item the
 * player does not hold.
 */
public class ForgeScript
@Inject
constructor(
    private val forge: ForgeService,
    private val registry: EquipmentInstanceRegistry,
    private val mutations: EquipmentInstanceMutationService,
    private val instances: EquipmentInstanceService,
    private val companions: CompanionService,
    private val players: PlayerList,
    private val objTypes: ObjTypeList,
    private val eventBus: EventBus,
) : PluginScript() {

    override fun ScriptContext.startup() {
        // `forgeopen`/`forgeop` are the Forge panel's protocol channel - the client plugin sends
        // them for the player who clicked an item, so they must be reachable without admin rights.
        playerCommand("forgeopen", "Open the Forge for an instanced item", ::forgeOpen) {
            invalidArgs = "Use as ::forgeopen <inv|worn|pack|gear> <slot>"
        }
        playerCommand("forgeop", "Execute a Forge operation", ::forgeOp) {
            invalidArgs =
                "Use as ::forgeop <instanceId> <revision> <upgrade|all|sel|mystery> [slots]"
        }
        onCommand("forgedebug", "Dump complete Forge metadata for an item", ::forgeDebug) {
            invalidArgs = "Use as ::forgedebug <inv slot|instanceId>"
        }
    }

    // ------------------------------------------------------------------ open

    /**
     * `forgeopen <scope> <slot>` resolves the item the player clicked, lazily creates a persistent
     * instance for legacy wearables without one (the metadata-migration path), and publishes the
     * full Forge snapshot to the client panel.
     */
    private fun forgeOpen(cheat: Cheat) =
        with(cheat) {
            val scope = args.getOrNull(0) ?: return
            val slot = args.getOrNull(1)?.toIntOrNull() ?: return
            val player = player
            when (scope) {
                "inv" -> openInventoryScope(player, player.inv, slot, "inv")
                "worn" -> openInventoryScope(player, player.worn, slot, "worn")
                "pack" -> {
                    val pack = player.invMap[BaseInvs.companion_storage]
                    if (pack == null) {
                        sendError(player, "no-pack", "Your companion has no pack.")
                        return
                    }
                    openInventoryScope(player, pack, slot, "pack")
                }
                "gear" -> openCompanionGearScope(player, slot)
                else -> sendError(player, "bad-scope", "Unknown forge scope '$scope'.")
            }
        }

    private fun openInventoryScope(player: Player, inv: Inventory, slot: Int, scope: String) {
        val obj = inv[slot]
        if (obj == null) {
            sendError(player, "empty-slot", "There is no item in that slot.")
            return
        }
        val instance = registry[obj.instanceId]
        if (instance != null) {
            sendSnapshot(player, instance, scope, slot)
            return
        }
        // Migration path: a wearable with no instance gets one rolled deterministically the first
        // time it enters the Forge - its rarity is decided by the normal rarity table, nothing is
        // silently converted.
        migrateInventoryItem(player, inv, slot, obj, scope)
    }

    private fun migrateInventoryItem(
        player: Player,
        inv: Inventory,
        slot: Int,
        obj: InvObj,
        scope: String,
    ) {
        val type = objTypes[obj.id]
        if (type == null || !instances.isInstanceEligible(type) || obj.count != 1) {
            sendError(player, "not-forgeable", "That item cannot be forged.")
            return
        }
        val uid = player.uid
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull()
        instances.rollAndPersist(
            type,
            source = "forge-migrate",
            tier = EquipmentTierResolver.resolve(type),
            ownerCharacterId = ownerId,
            onFailure = {
                uid.resolve(players)?.let {
                    sendError(it, "migrate-failed", "That item cannot be forged.")
                }
            },
        ) { created ->
            val target = uid.resolve(players) ?: return@rollAndPersist
            // Re-anchor to whichever slot still holds the identical un-instanced obj.
            val currentSlot =
                if (inv[slot]?.id == obj.id && inv[slot]?.instanceId == InvObj.NO_INSTANCE) slot
                else
                    inv.objs.indexOfFirst {
                        it?.id == obj.id && it.instanceId == InvObj.NO_INSTANCE
                    }
            if (currentSlot < 0) {
                logger.warn {
                    "Forge migration orphaned instance ${created.instanceId} " +
                        "(obj=${obj.id}, player=${target.username})"
                }
                sendError(target, "item-moved", "The item moved - try again.")
                return@rollAndPersist
            }
            inv[currentSlot] = InvObj(type, 1, instanceId = created.instanceId)
            resendSlot(inv, currentSlot)
            EquipmentInstanceHoverSync.upsert(
                target,
                inv,
                currentSlot,
                inv[currentSlot]!!,
                registry,
                objTypes,
            )
            sendSnapshot(target, created, scope, currentSlot)
        }
    }

    /**
     * `forgeopen gear <wearpos>` - the fallback entry for companion gear used when the Companion
     * Hub interface op is not the entry point (e.g. a client-side request). Resolves any owned
     * companion's equipped instance whose template occupies [wearpos].
     */
    private fun openCompanionGearScope(player: Player, wearpos: Int) {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        for (companion in companions.owned(ownerId)) {
            for (id in companion.gearInstanceIds) {
                val inst = registry[id] ?: continue
                val type = objTypes[inst.templateObj] ?: continue
                if (Wearpos[type.wearpos1]?.slot == wearpos) {
                    sendSnapshot(player, inst, "gear", wearpos)
                    return
                }
            }
        }
        sendError(player, "empty-slot", "No companion gear in that slot.")
    }

    /**
     * Entry point for the Companion Hub equipment interface (`Forge` op on a gear slot): the
     * instance is already resolved by the caller, so the snapshot goes straight out.
     */
    public fun openForCompanionGear(player: Player, instance: EquipmentInstance) {
        sendSnapshot(player, instance, "gear", -1)
    }

    // ------------------------------------------------------------------ op

    /**
     * `forgeop <instanceId> <expectedRevision> <upgrade|all|sel> [slotCsv]` - validates location,
     * ownership, revision and cost, then commits through the audited mutation gateway. Currency is
     * charged only after the mutation has been *prepared* (synchronously validated); a persistence
     * failure refunds it, and a replayed idempotency key is never charged twice.
     */
    private fun forgeOp(cheat: Cheat) =
        with(cheat) {
            val instanceId = args.getOrNull(0)?.toLongOrNull() ?: return
            val revision = args.getOrNull(1)?.toLongOrNull() ?: return
            val opName = args.getOrNull(2) ?: return
            val slotCsv = args.getOrNull(3).orEmpty()
            val player = player

            val instance = registry[instanceId]
            if (instance == null) {
                sendError(player, "not-found", "That item no longer exists.")
                return
            }
            if (instance.revision != revision) {
                // Stale UI snapshot - the client is told to resync rather than erroring out.
                sendSnapshot(player, instance, "resync", -1)
                return
            }
            val location = locateInstance(player, instanceId)
            if (location == null) {
                logger.info {
                    "Forge request for instance $instanceId not in ${player.username}'s " +
                        "possession (op=$opName rev=$revision)"
                }
                sendError(player, "not-held", "You do not hold that item.")
                return
            }
            val ownerId = runCatching { player.characterId.toLong() }.getOrNull()
            if (instance.ownerCharacterId != null && instance.ownerCharacterId != ownerId) {
                logger.info {
                    "Forge ownership mismatch for instance $instanceId " +
                        "(owner=${instance.ownerCharacterId}, player=${player.username})"
                }
                sendError(player, "not-owner", "That item is bound to someone else.")
                return
            }

            val operation =
                when (opName) {
                    "upgrade" -> ForgeOperation.UPGRADE
                    "all" -> ForgeOperation.REFORGE_ALL
                    "sel" -> ForgeOperation.REFORGE_SELECTED
                    "mystery",
                    "enchant" -> ForgeOperation.MYSTERY_ENCHANT
                    else -> {
                        sendError(player, "bad-op", "Unknown forge operation.")
                        return
                    }
                }
            val slots =
                if (operation == ForgeOperation.REFORGE_SELECTED) {
                    slotCsv
                        .split(',')
                        .filter(String::isNotBlank)
                        .mapNotNull(String::toIntOrNull)
                        .toSet()
                } else {
                    emptySet()
                }
            if (operation == ForgeOperation.REFORGE_SELECTED && slots.isEmpty()) {
                sendError(player, "empty-selection", "Select at least one affix to reforge.")
                return
            }

            val preview =
                when (
                    val result =
                        when (operation) {
                            ForgeOperation.UPGRADE -> forge.previewUpgrade(instance)
                            ForgeOperation.REFORGE_ALL ->
                                forge.previewReforgeAll(instance, rerollSeed(instance, player))
                            ForgeOperation.REFORGE_SELECTED ->
                                forge.previewReforgeSelected(
                                    instance,
                                    slots,
                                    rerollSeed(instance, player),
                                )
                            ForgeOperation.MYSTERY_ENCHANT ->
                                forge.previewMysteryEnchant(instance, rerollSeed(instance, player))
                        }
                ) {
                    is ForgeResult.Err -> {
                        sendError(player, result.error.code, result.error.message)
                        return
                    }
                    is ForgeResult.Ok -> result.preview
                }

            // Currency check happens against the live inventory before anything is consumed.
            val tickets = objTypes[ForgeConfig.TICKET_OBJ_ID]?.let { player.inv.count(it) } ?: 0
            val gold = objTypes[ForgeConfig.GOLD_OBJ_ID]?.let { player.inv.count(it) } ?: 0
            if (tickets < preview.cost.tickets || gold < preview.cost.gold) {
                sendError(
                    player,
                    "insufficient-funds",
                    "Requires ${preview.cost.tickets} Forge ticket(s) and " +
                        "${preview.cost.gold} coins (you have $tickets / $gold).",
                )
                return
            }

            commit(player, instance, preview, slots)
        }

    /**
     * Prepares the mutation synchronously (revision + state + identity + invariants), charges the
     * cost, then persists. A rejected prepare never charges; an accepted mutation that fails to
     * persist is refunded.
     */
    private fun commit(
        player: Player,
        instance: EquipmentInstance,
        preview: ForgePreview,
        slots: Set<Int>,
    ) {
        val request =
            ItemMutationRequest(
                idempotencyKey =
                    "forge:${instance.instanceId}:${preview.operation}:" +
                        "${instance.revision}:${slots.sorted().joinToString(",")}",
                expectedRevision = instance.revision,
                actor =
                    ItemActor(
                        ItemActorType.PLAYER,
                        runCatching { player.characterId.toLong() }.getOrNull(),
                    ),
                operation =
                    if (preview.operation == ForgeOperation.UPGRADE) {
                        ItemMutationOperation.UPGRADED
                    } else if (preview.operation == ForgeOperation.MYSTERY_ENCHANT) {
                        ItemMutationOperation.MYSTERY_ENCHANTED
                    } else {
                        ItemMutationOperation.REFORGED
                    },
                source = "forge",
                payload = auditPayload(instance, preview),
            )

        // `prepare` runs the full validation chain synchronously: a failed prepare means nothing
        // is charged and nothing is persisted; `Ok` means the mutation is safe to commit.
        when (val prepared = mutations.prepare(instance, request) { preview.after }) {
            is EquipmentInstanceMutationService.Prepared.Failed -> {
                val failure = prepared.result
                if (failure is ItemMutationResult.RevisionConflict) {
                    sendSnapshot(player, failure.current, "resync", -1)
                } else {
                    sendError(player, "rejected", describeMutationError(failure))
                }
                return
            }
            is EquipmentInstanceMutationService.Prepared.Replay -> {
                // Same request already committed - resend the current state, never re-charge.
                sendSnapshot(player, registry[instance.instanceId] ?: instance, "resync", -1)
                return
            }
            is EquipmentInstanceMutationService.Prepared.Ok -> Unit
        }

        // Charge now that the mutation is validated; persistence runs off-thread and the result
        // arrives back on the game thread.
        if (!charge(player, preview.cost)) {
            sendError(player, "insufficient-funds", "You no longer have enough currency.")
            return
        }

        val uid = player.uid
        mutations.mutate(instance, request, { preview.after }) { result ->
            val target = uid.resolve(players)
            when (result) {
                is ItemMutationResult.Success -> {
                    if (target == null) return@mutate
                    if (preview.operation == ForgeOperation.MYSTERY_ENCHANT) {
                        eventBus.publish(MysteryEnchantRolledEvent(target, result.instance))
                        if (result.instance.mysteryEnchantTier?.name == "LEGENDARY") {
                            eventBus.publish(LegendaryEnchantRolledEvent(target, result.instance))
                        }
                    }
                    resyncForgeItem(target, result.instance)
                    sendResult(target, preview, result.instance)
                    logger.debug {
                        "Forge ${preview.operation} by ${target.username}: " +
                            "instance=${result.instance.instanceId} " +
                            "rev=${instance.revision}->${result.instance.revision} " +
                            "cost=${preview.cost.tickets}t/${preview.cost.gold}g"
                    }
                }
                else -> {
                    // The commit failed after charging - refund on the game thread.
                    target?.let { refund(it, preview.cost) }
                        ?: logger.warn {
                            "Forge refund pending for offline player ${player.username} " +
                                "(instance=${instance.instanceId}, cost=${preview.cost})"
                        }
                    target?.let { sendError(it, "commit-failed", describeMutationError(result)) }
                }
            }
        }
    }

    /** Removes the full [cost] atomically; `false` leaves the inventory untouched. */
    private fun charge(player: Player, cost: ForgeCost): Boolean {
        if (cost.tickets > 0) {
            val tickets =
                player.invDel(player.inv, ForgeConfig.TICKET_OBJ_ID, cost.tickets, strict = true)
            if (tickets.failure) return false
        }
        if (cost.gold > 0) {
            val gold = player.invDel(player.inv, ForgeConfig.GOLD_OBJ_ID, cost.gold, strict = true)
            if (gold.failure) {
                // Roll back the tickets so a partial charge can never be observed.
                if (cost.tickets > 0) {
                    objTypes[ForgeConfig.TICKET_OBJ_ID]?.let {
                        player.invAdd(player.inv, it, cost.tickets, strict = false)
                    }
                }
                return false
            }
        }
        return true
    }

    private fun refund(player: Player, cost: ForgeCost) {
        if (cost.tickets > 0) {
            objTypes[ForgeConfig.TICKET_OBJ_ID]?.let {
                player.invAdd(player.inv, it, cost.tickets, strict = false)
            }
        }
        if (cost.gold > 0) {
            objTypes[ForgeConfig.GOLD_OBJ_ID]?.let {
                player.invAdd(player.inv, it, cost.gold, strict = false)
            }
        }
    }

    // ------------------------------------------------------------------ wire

    private fun sendSnapshot(
        player: Player,
        instance: EquipmentInstance,
        scope: String,
        slot: Int,
    ) {
        val type = objTypes[instance.templateObj]
        player.mes(
            listOf(
                    PREFIX,
                    "BEGIN",
                    instance.instanceId.toString(),
                    instance.revision.toString(),
                    instance.templateObj.toString(),
                    b64(type?.name ?: "item"),
                    instance.rarity.name,
                    instance.tier.name,
                    instance.itemLevel.toString(),
                    instance.quality.toString(),
                    instance.upgradeLevel.toString(),
                    scope,
                    slot.toString(),
                )
                .joinToString("|"),
            ChatType.Console,
        )
        val rerollableSlots = forge.rerollableSlots(instance)
        for (affix in instance.affixes) {
            val range = forge.affixRange(instance, affix)
            val rerollable = affix.slot in rerollableSlots
            player.mes(
                listOf(
                        PREFIX,
                        "AFF",
                        affix.slot.toString(),
                        b64(affix.definitionId),
                        b64(affix.family),
                        affix.stat.name,
                        affix.unit.name,
                        affix.polarity.name,
                        affix.magnitude.toString(),
                        if (rerollable) "1" else "0",
                        range?.first?.toString() ?: "-",
                        range?.last?.toString() ?: "-",
                        forge.qualityBps(instance, affix).toString(),
                    )
                    .joinToString("|"),
                ChatType.Console,
            )
        }
        for (affix in instance.skillAffixes) {
            player.mes(
                listOf(
                        PREFIX,
                        "SKA",
                        b64(affix.skill),
                        b64(affix.effect),
                        affix.unit.name,
                        affix.magnitude.toString(),
                    )
                    .joinToString("|"),
                ChatType.Console,
            )
        }
        val upCost = ForgeConfig.upgradeCost(instance.upgradeLevel)
        val allCost = ForgeConfig.reforgeAllCost()
        player.mes(
            listOf(
                    PREFIX,
                    "COST",
                    upCost.tickets.toString(),
                    upCost.gold.toString(),
                    allCost.tickets.toString(),
                    allCost.gold.toString(),
                    ForgeConfig.REFORGE_SELECTED_BASE_TICKETS.toString(),
                    ForgeConfig.REFORGE_SELECTED_TICKETS_PER_AFFIX.toString(),
                    ForgeConfig.REFORGE_SELECTED_GOLD_PER_AFFIX.toString(),
                    ForgeConfig.MYSTERY_ENCHANT_TICKETS.toString(),
                    if (forge.hasHighRoll(instance)) "1" else "0",
                )
                .joinToString("|"),
            ChatType.Console,
        )
        player.mes(
            listOf(
                    PREFIX,
                    "ENCHANT",
                    instance.mysteryEnchantId?.name ?: "NONE",
                    instance.mysteryEnchantTier?.name ?: "NONE",
                    instance.mysteryEnchantKillsRemaining.toString(),
                    instance.mysteryEnchantKillsMax.toString(),
                    if (instance.mysteryEnchantId != null) "1" else "0",
                )
                .joinToString("|"),
            ChatType.Console,
        )
        sendBalance(player)
        player.mes("$PREFIX|END|${instance.instanceId}", ChatType.Console)
    }

    private fun sendBalance(player: Player) {
        val tickets = objTypes[ForgeConfig.TICKET_OBJ_ID]?.let { player.inv.count(it) } ?: 0
        val gold = objTypes[ForgeConfig.GOLD_OBJ_ID]?.let { player.inv.count(it) } ?: 0
        player.mes("$PREFIX|BAL|$tickets|$gold", ChatType.Console)
    }

    private fun sendResult(player: Player, preview: ForgePreview, updated: EquipmentInstance) {
        player.mes(
            "$PREFIX|RESULT|${preview.operation.name}|${updated.instanceId}|" +
                "${updated.revision}|${updated.upgradeLevel}",
            ChatType.Console,
        )
        val before = preview.before.affixes.associateBy { it.slot }
        for (affix in updated.affixes) {
            val old = before[affix.slot]
            player.mes(
                "$PREFIX|DIFF|${affix.slot}|${affix.stat.name}|${affix.unit.name}|" +
                    "${old?.magnitude ?: affix.magnitude}|${affix.magnitude}",
                ChatType.Console,
            )
        }
        // Full post-state so the panel snapshot and the result view share one source of truth.
        sendSnapshot(player, updated, "result", -1)
    }

    private fun sendError(player: Player, code: String, message: String) {
        player.mes("$PREFIX|ERR|$code|${b64(message)}", ChatType.Console)
        player.mes("<col=ff4444>Forge: $message</col>")
    }

    // ------------------------------------------------------------------ locate

    /**
     * Where [instanceId] currently sits for [player]: their inventory, worn equipment, companion
     * pack, or any owned companion's gear. A request for an instance in none of those places is a
     * forged/stale request and is rejected.
     */
    private fun locateInstance(player: Player, instanceId: Long): Location? {
        player.inv.forEachIndexed { slot, obj ->
            if (obj?.instanceId == instanceId) return Location("inv", player.inv, slot)
        }
        player.worn.forEachIndexed { slot, obj ->
            if (obj?.instanceId == instanceId) return Location("worn", player.worn, slot)
        }
        player.invMap[BaseInvs.companion_storage]?.let { pack ->
            pack.forEachIndexed { slot, obj ->
                if (obj?.instanceId == instanceId) return Location("pack", pack, slot)
            }
        }
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull()
        if (ownerId != null) {
            for (companion in companions.owned(ownerId)) {
                if (instanceId in companion.gearInstanceIds) {
                    return Location("gear", null, -1)
                }
            }
        }
        return null
    }

    private data class Location(val scope: String, val inv: Inventory?, val slot: Int)

    /** Re-publishes the hover payload for wherever the item currently sits (incl. pack). */
    private fun resyncForgeItem(player: Player, instance: EquipmentInstance) {
        for (inv in
            listOfNotNull(player.inv, player.worn, player.invMap[BaseInvs.companion_storage])) {
            inv.forEachIndexed { slot, obj ->
                if (obj?.instanceId == instance.instanceId) {
                    EquipmentInstanceHoverSync.upsert(player, inv, slot, obj, registry, objTypes)
                    resendSlot(inv, slot)
                }
            }
        }
    }

    // ------------------------------------------------------------------ debug

    private fun forgeDebug(cheat: Cheat) =
        with(cheat) {
            val arg = args.getOrNull(0)?.toLongOrNull() ?: return
            val instance =
                registry[arg]
                    ?: player.inv[arg.toInt()]?.instanceId?.let(registry::get)
                    ?: run {
                        player.mes("No instance with id/slot $arg.")
                        return
                    }
            player.mes(
                "<col=ffb84d>Forge #${instance.instanceId}</col> ${instance.rarity.name} " +
                    "+${instance.upgradeLevel}/${ForgeConfig.MAX_UPGRADE_LEVEL} " +
                    "rev=${instance.revision} forgeable=${forge.isForgeable(instance)}"
            )
            player.mes(
                "  costs: upgrade=${ForgeConfig.upgradeCost(instance.upgradeLevel)} " +
                    "all=${ForgeConfig.reforgeAllCost()} " +
                    "sel(n)=${ForgeConfig.REFORGE_SELECTED_BASE_TICKETS}+n*${ForgeConfig.REFORGE_SELECTED_TICKETS_PER_AFFIX}t " +
                    "+n*${ForgeConfig.REFORGE_SELECTED_GOLD_PER_AFFIX}g"
            )
            for (affix in instance.affixes) {
                val range = forge.affixRange(instance, affix)
                player.mes(
                    "  [${affix.slot}] ${affix.definitionId} ${affix.stat.name}=${affix.magnitude} " +
                        "range=${range?.first ?: "-"}..${range?.last ?: "-"} " +
                        "q=${forge.qualityBps(instance, affix)}bps " +
                        "rerollable=${affix.slot in forge.rerollableSlots(instance)}"
                )
            }
        }

    // ------------------------------------------------------------------ misc

    /**
     * Deterministic-but-unpredictable reroll seed: the instance's own seed mixed with its current
     * revision, the requester and wall time. Persistence-safe (a replayed request reproduces the
     * same stored snapshot, not a re-roll).
     */
    private fun rerollSeed(instance: EquipmentInstance, player: Player): Long =
        instance.rollSeed * 31L +
            instance.revision * 1_000_003L +
            player.hashCode().toLong() +
            System.nanoTime()

    private fun auditPayload(instance: EquipmentInstance, preview: ForgePreview): String {
        val changed =
            preview.changedSlots.joinToString(",") { slot ->
                val old = preview.before.affixes.getOrNull(slot)?.magnitude
                val new = preview.after.affixes.getOrNull(slot)?.magnitude
                "$slot:$old->$new"
            }
        return "op=${preview.operation.name};slots=${preview.changedSlots};" +
            "upgrade=${instance.upgradeLevel}->${preview.after.upgradeLevel};" +
            "cost=t${preview.cost.tickets},g${preview.cost.gold};" +
            "diff=${changed.ifEmpty { "-" }}"
    }

    private fun describeMutationError(result: ItemMutationResult): String =
        when (result) {
            is ItemMutationResult.ValidationError -> "Rejected: ${result.code}"
            is ItemMutationResult.CostError -> "Missing: ${result.missing.joinToString()}"
            is ItemMutationResult.RevisionConflict -> "The item changed - please retry."
            is ItemMutationResult.InternalFailure -> "Forge error - please retry."
            is ItemMutationResult.Success -> "Unexpected state."
        }

    private companion object {
        private const val PREFIX = "UNFORGE_FORGE"
        private val logger = InlineLogger()

        private fun b64(value: String): String =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}
