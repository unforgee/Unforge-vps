package org.rsmod.api.equipment.instance

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult

/**
 * The single mutation gateway for Item Instance Evolution.
 *
 * Every gameplay state change to an [EquipmentInstance] flows through [mutate]:
 * 1. the live snapshot is read from [EquipmentInstanceRegistry]
 * 2. the idempotency key is checked - replays return the stored result without re-charging
 * 3. `expectedRevision` (optimistic locking) is validated
 * 4. the [EquipmentInstanceState] gate rejects conflicting operations (no reforge while `IN_TRADE`,
 *    no mutation at all on `DESTROYED`/`CORRUPTED` items)
 * 5. the caller's `transform` produces the candidate snapshot; identity fields are sealed
 * 6. the candidate's revision bumps by one and its fingerprint is recomputed
 * 7. snapshot + audit event are persisted **in one transaction** - the conditional `WHERE revision
 *    = ?` update is the final concurrency backstop
 * 8. the registry is updated and the structured [ItemMutationResult] delivered on the game thread
 *
 * There is deliberately no "raw update" path here: [EquipmentInstanceService.updateInstance]
 * (legacy callers) routes through this gateway as well.
 */
@Singleton
public class EquipmentInstanceMutationService
@Inject
constructor(
    private val db: GameDbManager,
    private val repository: EquipmentInstanceRepository,
    private val registry: EquipmentInstanceRegistry,
) {
    /**
     * instanceId -> idempotencyKey -> stored result. In-memory is sufficient for the request-scoped
     * replay window; the persisted `idempotency_key` column additionally makes replays across
     * restarts detectable.
     */
    private val idempotentResults = ConcurrentHashMap<String, Stored>()

    /** Operations that may run regardless of the item's lifecycle state. */
    private val stateBypass =
        setOf(
            ItemMutationOperation.CREATED,
            ItemMutationOperation.MIGRATED,
            ItemMutationOperation.RESTORED,
            ItemMutationOperation.REPAIRED,
            ItemMutationOperation.ADMIN_CORRECTION,
        )

    /**
     * Synchronous validation + transformation step, split from persistence for testability. Returns
     * either a prepared mutation (updated snapshot + event template) or the structured failure to
     * report.
     */
    public fun prepare(
        current: EquipmentInstance,
        request: ItemMutationRequest,
        transform: (EquipmentInstance) -> EquipmentInstance,
    ): Prepared {
        val replayKey = "${current.instanceId}:${request.idempotencyKey}"
        idempotentResults[replayKey]?.let { stored ->
            return if (stored.operation == request.operation) {
                Prepared.Replay(stored.result)
            } else {
                Prepared.Failed(
                    ItemMutationResult.ValidationError(
                        "idempotency-key-reuse",
                        mapOf("key" to request.idempotencyKey),
                    )
                )
            }
        }
        if (request.expectedRevision != null && request.expectedRevision != current.revision) {
            return Prepared.Failed(ItemMutationResult.RevisionConflict(current))
        }
        if (request.operation !in stateBypass && current.state != EquipmentInstanceState.ACTIVE) {
            return Prepared.Failed(
                ItemMutationResult.ValidationError(
                    "invalid-state",
                    mapOf("state" to current.state.name, "operation" to request.operation.name),
                )
            )
        }
        val candidate =
            try {
                transform(current)
            } catch (e: IllegalArgumentException) {
                return Prepared.Failed(
                    ItemMutationResult.ValidationError(
                        e.message ?: "transform-rejected",
                        emptyMap(),
                    )
                )
            }
        // Identity fields are sealed: a mutation can never change what the item *is*.
        val identityError =
            when {
                candidate.instanceId != current.instanceId -> "identity-instanceId"
                candidate.instanceUuid != current.instanceUuid -> "identity-uuid"
                candidate.templateObj != current.templateObj -> "identity-template"
                candidate.rollSeed != current.rollSeed -> "identity-seed"
                else -> null
            }
        if (identityError != null) {
            return Prepared.Failed(ItemMutationResult.ValidationError(identityError))
        }
        val updated =
            candidate.copy(
                revision = current.revision + 1,
                fingerprint = EquipmentInstanceFingerprint.of(candidate),
            )
        val violations = EquipmentInstanceInvariants.violations(updated)
        if (violations.isNotEmpty()) {
            return Prepared.Failed(
                ItemMutationResult.ValidationError(
                    "invariant-violation",
                    violations.associateWith { "" },
                )
            )
        }
        return Prepared.Ok(updated, replayKey)
    }

    public sealed interface Prepared {
        public data class Ok(public val updated: EquipmentInstance, public val replayKey: String) :
            Prepared

        public data class Replay(public val result: ItemMutationResult) : Prepared

        public data class Failed(public val result: ItemMutationResult) : Prepared
    }

    /**
     * Executes a validated mutation asynchronously: persists the snapshot (conditional on the
     * expected revision) and the audit event in one transaction, updates the registry and invokes
     * [onResult] on the game thread.
     *
     * For items not yet in the registry (e.g. freshly created rows), [current] may be supplied
     * directly; registry presence is not a prerequisite.
     */
    public fun mutate(
        current: EquipmentInstance,
        request: ItemMutationRequest,
        transform: (EquipmentInstance) -> EquipmentInstance,
        onResult: (ItemMutationResult) -> Unit,
    ) {
        when (val prepared = prepare(current, request, transform)) {
            is Prepared.Failed -> onResult(prepared.result)
            is Prepared.Replay -> onResult(prepared.result)
            is Prepared.Ok -> {
                val updated = prepared.updated
                db.request({ connection ->
                    // Persisted revision is the authoritative concurrency check - the in-memory
                    // revision could be stale if another writer committed meanwhile.
                    if (
                        !repository.saveWithRevision(
                            connection,
                            updated,
                            expectedRevision = current.revision,
                        )
                    ) {
                        return@request GameDbResult.Ok(PersistOutcome.CONFLICT)
                    }
                    val previous = repository.latestEvent(connection, updated.instanceId)
                    val unsigned =
                        ItemMutationEvent(
                            eventId = UUID.randomUUID(),
                            instanceId = updated.instanceId,
                            operation = request.operation,
                            actor = request.actor,
                            source = request.source,
                            beforeRevision = current.revision,
                            afterRevision = updated.revision,
                            beforeFingerprint =
                                current.fingerprint.ifEmpty {
                                    EquipmentInstanceFingerprint.of(current)
                                },
                            afterFingerprint = updated.fingerprint,
                            payload = request.payload,
                            previousEventHash = previous?.eventHash,
                            eventHash = "",
                            createdAtEpochMillis = System.currentTimeMillis(),
                            // The canonical post-state lets `restoreToEvent` rebuild this exact
                            // snapshot; integrity is proven by afterFingerprint at restore time.
                            snapshot = EquipmentInstanceSnapshotCodec.encode(updated),
                        )
                    val event = unsigned.copy(eventHash = ItemEventHasher.hash(unsigned))
                    repository.appendEvent(connection, event, request.idempotencyKey)
                    GameDbResult.Ok(PersistOutcome.OK)
                }) { result ->
                    when (result) {
                        is GameDbResult.Ok ->
                            when (result.value) {
                                PersistOutcome.OK -> {
                                    registry.put(updated)
                                    val success = ItemMutationResult.Success(updated)
                                    idempotentResults[prepared.replayKey] =
                                        Stored(request.operation, success)
                                    onResult(success)
                                }
                                PersistOutcome.CONFLICT -> {
                                    val live = registry[updated.instanceId] ?: current
                                    onResult(ItemMutationResult.RevisionConflict(live))
                                }
                            }
                        is GameDbResult.Err -> {
                            logger.warn {
                                "Instance mutation failed (id=${updated.instanceId}, " +
                                    "op=${request.operation}): $result"
                            }
                            onResult(ItemMutationResult.InternalFailure(UUID.randomUUID()))
                        }
                    }
                }
            }
        }
        Unit
    }

    /** Convenience lookup + mutate for registry-resident instances. */
    public fun mutateById(
        instanceId: Long,
        request: ItemMutationRequest,
        transform: (EquipmentInstance) -> EquipmentInstance,
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
        mutate(current, request, transform, onResult)
    }

    /**
     * Restores [current] to the exact post-state recorded by audit event [eventId].
     *
     * Restoration is itself a mutation: the rebuilt snapshot is routed through [mutate], so the
     * restore is idempotent (the caller's key), optimistic-locked, identity-sealed, invariant-
     * checked and persisted with its own `RESTORED` audit event in one transaction.
     *
     * The audit trail is validated before anything is rebuilt:
     * - the instance's full event chain must be intact (`event-chain-broken`)
     * - the target event must exist in that chain (`event-not-found`)
     * - the event must carry a snapshot - pre-V34 events are deliberately not restorable
     *   (`no-snapshot`)
     * - the snapshot must decode (`snapshot-decode-failed`) and re-fingerprint to the event's
     *   `afterFingerprint` (`snapshot-fingerprint-mismatch`)
     *
     * Identity fields are never taken from the snapshot: the live row's `instanceId`, `uuid` and
     * creation timestamp are re-applied, and [mutate]'s identity seal rejects any divergence in the
     * recorded template/seed.
     */
    public fun restoreToEvent(
        current: EquipmentInstance,
        eventId: UUID,
        request: ItemMutationRequest,
        onResult: (ItemMutationResult) -> Unit,
    ) {
        if (request.operation != ItemMutationOperation.RESTORED) {
            onResult(
                ItemMutationResult.ValidationError(
                    "operation-mismatch",
                    mapOf("operation" to request.operation.name),
                )
            )
            return
        }
        // Mirror `prepare`'s cheap checks so an idempotent replay short-circuits before any
        // database work and a stale expected revision fails fast.
        idempotentResults["${current.instanceId}:${request.idempotencyKey}"]?.let { stored ->
            if (stored.operation == request.operation) {
                onResult(stored.result)
            } else {
                onResult(
                    ItemMutationResult.ValidationError(
                        "idempotency-key-reuse",
                        mapOf("key" to request.idempotencyKey),
                    )
                )
            }
            return
        }
        if (request.expectedRevision != null && request.expectedRevision != current.revision) {
            onResult(ItemMutationResult.RevisionConflict(current))
            return
        }
        db.request({ connection ->
            // `-1`: the cursor is an exclusive after_revision bound, and the baseline
            // MIGRATED event legitimately sits at revision 0.
            val history =
                repository.loadHistory(
                    connection,
                    current.instanceId,
                    cursor = -1L,
                    limit = RESTORE_HISTORY_LIMIT,
                )
            val violations = EquipmentInstanceInvariants.eventChainViolations(history)
            if (violations.isNotEmpty()) {
                return@request GameDbResult.Ok(
                    RestoreLookup.Error(
                        ItemMutationResult.ValidationError(
                            "event-chain-broken",
                            mapOf("first" to violations.first()),
                        )
                    )
                )
            }
            val event = history.firstOrNull { it.eventId == eventId }
            if (event == null) {
                return@request GameDbResult.Ok(
                    RestoreLookup.Error(
                        ItemMutationResult.ValidationError(
                            "event-not-found",
                            mapOf("eventId" to eventId.toString()),
                        )
                    )
                )
            }
            val serialized = event.snapshot
            if (serialized == null) {
                return@request GameDbResult.Ok(
                    RestoreLookup.Error(
                        ItemMutationResult.ValidationError(
                            "no-snapshot",
                            mapOf("eventId" to eventId.toString()),
                        )
                    )
                )
            }
            val decoded = EquipmentInstanceSnapshotCodec.decode(serialized)
            if (decoded == null) {
                return@request GameDbResult.Ok(
                    RestoreLookup.Error(
                        ItemMutationResult.ValidationError(
                            "snapshot-decode-failed",
                            mapOf("eventId" to eventId.toString()),
                        )
                    )
                )
            }
            if (EquipmentInstanceFingerprint.of(decoded) != event.afterFingerprint) {
                return@request GameDbResult.Ok(
                    RestoreLookup.Error(
                        ItemMutationResult.ValidationError(
                            "snapshot-fingerprint-mismatch",
                            mapOf("eventId" to eventId.toString()),
                        )
                    )
                )
            }
            GameDbResult.Ok(RestoreLookup.Decoded(decoded))
        }) { result ->
            when (result) {
                is GameDbResult.Ok ->
                    when (val lookup = result.value) {
                        is RestoreLookup.Error -> onResult(lookup.result)
                        is RestoreLookup.Decoded ->
                            mutate(
                                current,
                                request,
                                transform = {
                                    lookup.state.copy(
                                        instanceId = it.instanceId,
                                        instanceUuid = it.instanceUuid,
                                        createdAtEpochMillis = it.createdAtEpochMillis,
                                    )
                                },
                                onResult = onResult,
                            )
                    }
                is GameDbResult.Err -> {
                    logger.warn {
                        "Instance restore lookup failed " +
                            "(id=${current.instanceId}, event=$eventId): $result"
                    }
                    onResult(ItemMutationResult.InternalFailure(UUID.randomUUID()))
                }
            }
        }
    }

    private sealed interface RestoreLookup {
        public data class Decoded(val state: EquipmentInstance) : RestoreLookup

        public data class Error(val result: ItemMutationResult) : RestoreLookup
    }

    private enum class PersistOutcome {
        OK,
        CONFLICT,
    }

    private data class Stored(val operation: ItemMutationOperation, val result: ItemMutationResult)

    private companion object {
        private val logger = InlineLogger()

        /** Upper bound of the audit chain read for a restore; far beyond realistic histories. */
        private const val RESTORE_HISTORY_LIMIT: Int = 10_000
    }
}
