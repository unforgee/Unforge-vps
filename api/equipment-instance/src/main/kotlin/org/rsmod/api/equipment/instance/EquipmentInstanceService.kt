package org.rsmod.api.equipment.instance

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos

/**
 * Central entry point for creating [EquipmentInstance]s for newly acquired equipment.
 *
 * Use this whenever wearable armour or weapons are generated for a player (npc drops, crafting,
 * rewards, ...). The instance is rolled via [EquipmentAffixCatalog] policy, persisted through
 * [EquipmentInstanceRepository] and registered into [EquipmentInstanceRegistry] before [onCreated]
 * is invoked - so the returned instance is immediately usable for `InvObj.instanceId`.
 *
 * Every creation writes a `CREATED` audit event in the same transaction as the row itself, and
 * every mutation routes through [EquipmentInstanceMutationService] - the single gateway enforcing
 * revision checks, fingerprints and the append-only event log.
 *
 * The response callback runs on the game thread via the db gateway synchronizer.
 */
@Singleton
public class EquipmentInstanceService
@Inject
constructor(
    private val db: GameDbManager,
    private val repository: EquipmentInstanceRepository,
    private val registry: EquipmentInstanceRegistry,
    private val mutations: EquipmentInstanceMutationService,
    private val rollModifiers: Set<EquipmentRollModifier> = emptySet(),
) {
    /**
     * `true` when [type] is a non-stackable wearable that maps to a real equipment category.
     *
     * Stackables are excluded: `InvObj.instanceId` describes a single copy and transaction rules
     * already reject instanced objs with `count != 1`.
     */
    public fun isInstanceEligible(type: UnpackedObjType): Boolean =
        !type.stackable && EquipmentCategoryResolver.isWearable(type)

    /**
     * Rolls a fresh instance for [type], persists it, registers it and invokes [onCreated] with the
     * persisted instance (containing its generated `instanceId`) on the game thread.
     *
     * [tier] labels the instance for display purposes - see
     * `org.rsmod.api.player.bonus.EquipmentTierResolver` (the instance module sits below
     * `api:config` in the dependency graph, so template-param resolution happens in callers).
     * [itemLevel] defaults to a tier/rarity-derived value; callers with a natural level - such as
     * the slain npc's `vislevel` - should pass it explicitly.
     *
     * [onFailure] is invoked if the type is not eligible or persistence fails - callers should fall
     * back to granting the plain item so loot is never lost.
     */
    public fun rollAndPersist(
        type: UnpackedObjType,
        source: String,
        tier: EquipmentTier,
        seed: Long = ThreadLocalRandom.current().nextLong(),
        itemLevel: Int = 0,
        skillAffixes: List<SkillAffixRoll> = emptyList(),
        ownerCharacterId: Long? = null,
        binding: ItemBinding = ItemBinding.UNBOUND,
        onFailure: () -> Unit = {},
        onCreated: (EquipmentInstance) -> Unit,
    ) {
        val wearpos = Wearpos[type.wearpos1]
        if (wearpos == null || !isInstanceEligible(type)) {
            onFailure()
            return
        }
        val category = EquipmentCategoryResolver.resolve(wearpos)
        val rolled =
            EquipmentAffixCatalog.policy()
                .roll(
                    type.id,
                    category,
                    seed,
                    source,
                    tier,
                    itemLevel,
                    ownerCharacterId,
                    rollModifiers,
                )
                .copy(
                    skillAffixes = skillAffixes,
                    instanceUuid = UUID.randomUUID().toString(),
                    ownerCharacterId = ownerCharacterId,
                    binding = binding,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
        db.request({ connection ->
            val created = repository.create(connection, rolled)
            val unsigned =
                ItemMutationEvent(
                    eventId = UUID.randomUUID(),
                    instanceId = created.instanceId,
                    operation = ItemMutationOperation.CREATED,
                    actor = ItemActor(ItemActorType.SYSTEM, null),
                    source = source,
                    beforeRevision = 0,
                    afterRevision = created.revision,
                    beforeFingerprint = "",
                    afterFingerprint = created.fingerprint,
                    payload = "template=${type.id}",
                    previousEventHash = null,
                    eventHash = "",
                    createdAtEpochMillis = System.currentTimeMillis(),
                    snapshot = EquipmentInstanceSnapshotCodec.encode(created),
                )
            repository.appendEvent(
                connection,
                unsigned.copy(eventHash = ItemEventHasher.hash(unsigned)),
            )
            GameDbResult.Ok(created)
        }) { result ->
            when (result) {
                is GameDbResult.Ok -> {
                    registry.put(result.value)
                    onCreated(result.value)
                }
                is GameDbResult.Err -> {
                    logger.warn {
                        "Equipment instance roll failed (obj=${type.id}, source=$source): $result"
                    }
                    onFailure()
                }
            }
        }
    }

    /**
     * Persists a mutated instance through the canonical mutation gateway: the expected revision is
     * taken from [instance] (the pre-mutation snapshot callers obtained from the registry), the
     * fingerprint is recomputed, and a [operation] audit event is appended atomically.
     *
     * [onUpdated] runs on the game thread - callers should re-sync any inventory slot holding the
     * instance (see `EquipmentInstanceHoverSync`). On a revision conflict or validation failure
     * [onFailure] fires instead and nothing is persisted.
     */
    public fun updateInstance(
        instance: EquipmentInstance,
        operation: ItemMutationOperation = ItemMutationOperation.REFORGED,
        source: String = "legacy-update",
        actor: ItemActor = ItemActor(ItemActorType.ADMIN, null),
        onFailure: () -> Unit = {},
        onUpdated: (EquipmentInstance) -> Unit,
    ) {
        val live = registry[instance.instanceId]
        if (live == null) {
            // Fall back to the caller's snapshot - it may be the only copy if the registry was
            // cleared, and the persisted revision check still protects against staleness.
            if (instance.instanceId == EquipmentInstance.UNPERSISTED_INSTANCE_ID) {
                onFailure()
                return
            }
        }
        val current = live ?: instance
        val request =
            ItemMutationRequest(
                idempotencyKey = "legacy-${instance.instanceId}-${current.revision}-$source",
                expectedRevision = current.revision,
                actor = actor,
                operation = operation,
                source = source,
            )
        // The caller already produced the post-mutation shape; the gateway seals identity fields,
        // bumps the revision and recomputes the fingerprint.
        mutations.mutate(current, request, transform = { instance }) { result ->
            when (result) {
                is ItemMutationResult.Success -> onUpdated(result.instance)
                else -> {
                    logger.warn {
                        "Equipment instance update rejected " +
                            "(id=${instance.instanceId}, result=$result)"
                    }
                    onFailure()
                }
            }
        }
    }

    /**
     * Marks the instance `CORRUPTED` (failed risky upgrade, damage overflow, admin flag). The
     * corrupted state then blocks every further non-bypass mutation through the gateway until the
     * item is [repair]ed or [restoreFromEvent]d. Only `ACTIVE` items can be corrupted.
     */
    public fun corrupt(
        instanceId: Long,
        actor: ItemActor,
        payload: String = "",
        onResult: (ItemMutationResult) -> Unit,
    ) {
        val current = registry[instanceId]
        if (current == null) {
            onResult(
                ItemMutationResult.ValidationError(
                    "instance-not-found",
                    mapOf("instanceId" to instanceId.toString()),
                )
            )
            return
        }
        val request =
            ItemMutationRequest(
                idempotencyKey = "corrupt:$instanceId:${current.revision}",
                expectedRevision = current.revision,
                actor = actor,
                operation = ItemMutationOperation.CORRUPTED,
                source = "corrupt",
                payload = payload,
            )
        mutations.mutate(
            current,
            request,
            transform = { it.copy(state = EquipmentInstanceState.CORRUPTED) },
            onResult = onResult,
        )
    }

    /**
     * Brings a `BROKEN`/`REPAIRING`/`CORRUPTED` instance back to `ACTIVE`. `DESTROYED` items are
     * deliberately excluded - their binding/owner invariants can only be rebuilt consistently via
     * [restoreFromEvent].
     */
    public fun repair(instanceId: Long, actor: ItemActor, onResult: (ItemMutationResult) -> Unit) {
        val current = registry[instanceId]
        if (current == null) {
            onResult(
                ItemMutationResult.ValidationError(
                    "instance-not-found",
                    mapOf("instanceId" to instanceId.toString()),
                )
            )
            return
        }
        val request =
            ItemMutationRequest(
                idempotencyKey = "repair:$instanceId:${current.revision}",
                expectedRevision = current.revision,
                actor = actor,
                operation = ItemMutationOperation.REPAIRED,
                source = "repair",
            )
        mutations.mutate(
            current,
            request,
            transform = {
                require(
                    it.state == EquipmentInstanceState.BROKEN ||
                        it.state == EquipmentInstanceState.REPAIRING ||
                        it.state == EquipmentInstanceState.CORRUPTED
                ) {
                    "repairable-state-required"
                }
                it.copy(state = EquipmentInstanceState.ACTIVE)
            },
            onResult = onResult,
        )
    }

    /**
     * Audited restore of the instance to the exact post-state of audit event [eventId] - see
     * [EquipmentInstanceMutationService.restoreToEvent] for the validation contract. The
     * idempotency key is derived from the target event, so repeating the same restore replays the
     * stored result instead of appending a duplicate event.
     */
    public fun restoreFromEvent(
        instanceId: Long,
        eventId: UUID,
        actor: ItemActor,
        expectedRevision: Long? = null,
        payload: String = "",
        onResult: (ItemMutationResult) -> Unit,
    ) {
        val current = registry[instanceId]
        if (current == null) {
            onResult(
                ItemMutationResult.ValidationError(
                    "instance-not-found",
                    mapOf("instanceId" to instanceId.toString()),
                )
            )
            return
        }
        val request =
            ItemMutationRequest(
                idempotencyKey = "restore:$instanceId:$eventId",
                expectedRevision = expectedRevision,
                actor = actor,
                operation = ItemMutationOperation.RESTORED,
                source = "restore",
                payload = payload.ifEmpty { "restore-to:$eventId" },
            )
        mutations.restoreToEvent(current, eventId, request, onResult)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
