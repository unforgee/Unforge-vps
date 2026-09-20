package org.rsmod.api.player.output

import java.util.Base64
import java.util.WeakHashMap
import org.rsmod.api.equipment.instance.EquipmentAbilityCatalog
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.equipment.instance.EquipmentCategoryResolver
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Sends the authoritative equipment data alongside the normal inventory update.
 *
 * The rev 239 inventory protocol only carries object id and quantity. The item-instance id, affix
 * rolls, sockets, unique effects and the item's gear attack-speed bonus therefore use a small
 * versioned game-message side channel. The messages are emitted as [ChatType.Console] so they never
 * render in the game chatbox while still being visible to client-side consumers (e.g. a RuneLite
 * plugin listening to chat events).
 *
 * Every **wearable** slot is published - instanced or not - so the hover can show the gear speed
 * bonus of plain items too. Non-wearable slots only emit when they hold an instance (which cannot
 * happen today) and are otherwise silent.
 *
 * Only slots whose payload actually changed produce traffic: each inventory keeps a map of the last
 * synced `slot -> encoded payload`, so ordinary items never re-send and `remove` is sent exactly
 * once when a payload leaves a slot. The payload - not just the instance id - is what is compared,
 * so in-place mutations (reforge, socketing) re-sync even though the id is unchanged.
 */
public object EquipmentInstanceHoverSync {
    private const val PREFIX = "UNFORGE_ITEM_INSTANCE"
    private const val VERSION = "3"
    private const val EMPTY = "-"
    private const val NO_INSTANCE = "0"

    private val synced = WeakHashMap<Inventory, MutableMap<Int, String>>()

    /**
     * Synchronises item data for [inventory].
     *
     * When [slots] is `null` every slot is reconciled (full update), otherwise only the given slots
     * are checked (partial update).
     */
    public fun sync(
        player: Player,
        inventory: Inventory,
        registry: EquipmentInstanceRegistry,
        objTypes: ObjTypeList,
        slots: Iterable<Int>? = null,
    ) {
        val syncedSlots = synced.getOrPut(inventory) { mutableMapOf() }
        val candidates = slots ?: inventory.objs.indices.asIterable()
        for (slot in candidates) {
            val obj = inventory[slot]
            val type = objTypes.getOrNull(obj)
            val instance = obj?.let { registry[it.instanceId] }
            val published =
                type != null && (instance != null || EquipmentCategoryResolver.isWearable(type))
            if (obj != null && type != null && published) {
                val payload =
                    encode(inventory.type.id, slot, type, GearSpeedBonus.bps(type), instance)
                if (syncedSlots[slot] != payload) {
                    wireMessages(payload).forEach { player.mes(it, ChatType.Console) }
                    instance?.let {
                        player.mes(
                            EquipmentInstanceDescribe.broadcast(obj.id, it),
                            ChatType.Console,
                        )
                        player.mes(evolutionEnvelope(it), ChatType.Console)
                    }
                    syncedSlots[slot] = payload
                }
            } else if (syncedSlots.remove(slot) != null) {
                player.mes("$PREFIX|$VERSION|remove|${inventory.type.id}|$slot", ChatType.Console)
            }
        }
    }

    public fun clear(player: Player, inventory: Inventory) {
        synced[inventory]?.clear()
        player.mes("$PREFIX|$VERSION|clear|${inventory.type.id}", ChatType.Console)
    }

    public fun remove(player: Player, inventory: Inventory, slot: Int) {
        synced[inventory]?.remove(slot)
        player.mes("$PREFIX|$VERSION|remove|${inventory.type.id}|$slot", ChatType.Console)
    }

    public fun upsert(
        player: Player,
        inventory: Inventory,
        slot: Int,
        obj: InvObj,
        registry: EquipmentInstanceRegistry,
        objTypes: ObjTypeList,
    ) {
        val syncedSlots = synced.getOrPut(inventory) { mutableMapOf() }
        val type = objTypes.getOrNull(obj)
        val instance = registry[obj.instanceId]
        if (type != null && (instance != null || EquipmentCategoryResolver.isWearable(type))) {
            val payload = encode(inventory.type.id, slot, type, GearSpeedBonus.bps(type), instance)
            wireMessages(payload).forEach { player.mes(it, ChatType.Console) }
            instance?.let {
                player.mes(EquipmentInstanceDescribe.broadcast(obj.id, it), ChatType.Console)
                player.mes(evolutionEnvelope(it), ChatType.Console)
            }
            syncedSlots[slot] = payload
            return
        }

        // Never let an unknown registry entry turn into client-side guessed data.
        syncedSlots.remove(slot)
        remove(player, inventory, slot)
    }

    internal fun encode(
        scope: Int,
        slot: Int,
        type: UnpackedObjType,
        speedBps: Int,
        instance: EquipmentInstance?,
    ): String {
        val objId = type.id
        val stats =
            EquipmentInstanceDescribe.totalStats(type, instance?.affixes.orEmpty())
                .takeIf { totals -> totals.any { it != 0 } }
                ?.joinToString(",") ?: EMPTY
        val affixes =
            instance
                ?.affixes
                ?.joinToString(",") { affix ->
                    listOf(
                            affix.slot.toString(),
                            encodeText(affix.definitionId),
                            encodeText(affix.family),
                            affix.stat.name,
                            affix.unit.name,
                            affix.polarity.name,
                            affix.magnitude.toString(),
                        )
                        .joinToString("~")
                }
                .orEmpty()
                .ifEmpty { EMPTY }

        val sockets =
            instance
                ?.sockets
                ?.joinToString(",") { socket ->
                    listOf(
                            socket.slot.toString(),
                            encodeText(socket.type),
                            socket.socketedObj?.toString() ?: EMPTY,
                            socket.magnitude.toString(),
                        )
                        .joinToString("~")
                }
                .orEmpty()
                .ifEmpty { EMPTY }

        // Each entry is `b64(displayName)~b64(description)`; a lone `b64(id)` from older
        // payloads still parses client-side as a name with no description.
        val abilities =
            instance
                ?.uniqueEffectIds
                ?.joinToString(",") { id ->
                    listOf(
                            encodeText(EquipmentAbilityCatalog.displayName(id)),
                            encodeText(EquipmentAbilityProcs.describe(id)),
                        )
                        .joinToString("~")
                }
                .orEmpty()
                .ifEmpty { EMPTY }

        val skillAffixes =
            instance
                ?.skillAffixes
                ?.joinToString(",") { affix ->
                    listOf(
                            encodeText(affix.skill),
                            encodeText(affix.effect),
                            affix.unit.name,
                            affix.magnitude.toString(),
                        )
                        .joinToString("~")
                }
                .orEmpty()
                .ifEmpty { EMPTY }

        val head =
            listOf(
                PREFIX,
                VERSION,
                "upsert",
                scope.toString(),
                // The slot is repeated in the envelope so the client can address the item directly.
                // The instance payload itself remains independent from the visual cache item.
                slot.toString(),
                instance?.instanceId?.toString() ?: NO_INSTANCE,
                objId.toString(),
                instance?.category?.name ?: EMPTY,
                instance?.rarity?.name ?: EMPTY,
                instance?.tier?.name ?: EMPTY,
                instance?.itemLevel?.toString() ?: NO_INSTANCE,
                instance?.quality?.toString() ?: NO_INSTANCE,
            )

        return (head +
                listOf(affixes, sockets, abilities, skillAffixes, speedBps.toString(), stats))
            .joinToString("|")
    }

    /**
     * Splits [payload] into one or more `MessageGame`-sized strings.
     *
     * `MessageGame` only carries 255 bytes, and an oversized message is dropped by the protocol
     * layer - the client would get nothing at all. A Jackpot item with five base64-encoded affixes
     * does not fit into a single message, so a payload that exceeds the budget is emitted as a
     * section-less `upsert` (head + speed, which always fits) followed by `sect` messages that
     * carry each optional section in `,`-entry chunks. Older clients keep the head and simply do
     * not recognise the extra `sect` messages, so nothing is ever silently lost on the current
     * client.
     */
    internal fun wireMessages(payload: String): List<String> {
        if (payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            return listOf(payload)
        }
        val parts = payload.split("|")
        if (parts.size != UPSERT_PARTS || parts[2] != "upsert") {
            return listOf(payload)
        }

        val head = parts.subList(0, SECTION_START)
        val sections = parts.subList(SECTION_START, SPEED_INDEX)
        val messages =
            mutableListOf(
                (head + List(SECTION_COUNT) { EMPTY } + parts.subList(SPEED_INDEX, parts.size))
                    .joinToString("|")
            )
        val envelope = "$PREFIX|$VERSION|sect|${parts[3]}|${parts[4]}|${parts[6]}|"

        SECTION_NAMES.forEachIndexed { index, name ->
            val data = sections[index]
            if (data == EMPTY) {
                return@forEachIndexed
            }

            val chunks = mutableListOf<String>()
            val current = StringBuilder()
            var bytes = 0
            for (entry in data.split(",")) {
                val entryBytes = entry.toByteArray(Charsets.UTF_8).size + if (bytes > 0) 1 else 0
                if (bytes > 0 && bytes + entryBytes > SECTION_DATA_BYTES) {
                    chunks += current.toString()
                    current.setLength(0)
                    bytes = 0
                }
                if (current.isNotEmpty()) {
                    current.append(',')
                }
                current.append(entry)
                bytes += entryBytes
            }
            if (current.isNotEmpty()) {
                chunks += current.toString()
            }
            chunks.forEachIndexed { seq, chunk ->
                messages += "$envelope$name|$seq|${chunks.size}|$chunk"
            }
        }
        return messages
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    /**
     * The Item Instance Evolution v4 envelope:
     * `UNFORGE_ITEM_INSTANCE|4|upsert|instanceId|revision|uuid|state|binding|fp8`
     *
     * Emitted alongside (never instead of) the v3 upsert, so older clients that only parse the v3
     * payload keep working and v4-aware consumers get the optimistic-lock revision, the stable
     * uuid, the lifecycle state/binding and a fingerprint prefix for staleness checks.
     */
    internal fun evolutionEnvelope(instance: EquipmentInstance): String =
        listOf(
                PREFIX,
                "4",
                "upsert",
                instance.instanceId.toString(),
                instance.revision.toString(),
                instance.instanceUuid.ifEmpty { EMPTY },
                instance.state.name,
                instance.binding.name,
                instance.fingerprint.take(8).ifEmpty { EMPTY },
            )
            .joinToString("|")

    /** Conservatively below the 255-byte `MessageGame` limit, leaving room for the header. */
    private const val MAX_PAYLOAD_BYTES = 240

    /** Byte budget for `sect` section data; the envelope takes the rest of the message. */
    private const val SECTION_DATA_BYTES = 175

    private const val SECTION_START = 12
    private const val SPEED_INDEX = 16
    private const val UPSERT_PARTS = 18
    private const val SECTION_COUNT = 4
    private val SECTION_NAMES = listOf("affixes", "sockets", "abilities", "skillaffixes")
}
