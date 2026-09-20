package org.rsmod.api.equipment.instance

import java.util.UUID

/**
 * Strong identity types for the Item Instance Evolution domain. The persisted columns remain
 * primitive (`equipment_instances.id` stays the primary key); these wrappers keep template ids,
 * instance ids and revisions from being silently interchanged in the new mutation APIs.
 */
@JvmInline public value class ItemInstanceId(public val value: Long)

@JvmInline public value class ItemRevision(public val value: Long)

@JvmInline public value class ItemTemplateId(public val value: Int)

/**
 * Who is allowed to possess/transfer an instance. Binding and inventory location are deliberately
 * separate: an unbound item still has no permanent owner, and a character-bound item keeps its
 * owner regardless of which inventory slot it sits in.
 */
public enum class ItemBinding {
    UNBOUND,
    ACCOUNT_BOUND,
    CHARACTER_BOUND,
    /** Transient lock held for the duration of a trade session; never a resting state. */
    TRADE_LOCKED,
    QUEST_BOUND,
    TEMPORARILY_BOUND,
    DESTROYED,
}

/**
 * Lifecycle state of an instance. Mutations may only run against [ACTIVE] items (plus the narrow
 * exceptions each operation declares); the state machine blocks conflicting concurrent operations
 * such as reforging an item that is already locked in a trade.
 */
public enum class EquipmentInstanceState {
    ACTIVE,
    BROKEN,
    REPAIRING,
    IN_UPGRADE,
    IN_TRADE,
    RETIRED,
    DESTROYED,
    CORRUPTED,
}

/** Who performed a mutation, for the audit log. */
public enum class ItemActorType {
    PLAYER,
    SYSTEM,
    ADMIN,
    MIGRATION,
}

public data class ItemActor(public val type: ItemActorType, public val id: Long?)

/** Every mutation that can appear in the append-only `equipment_instance_events` log. */
public enum class ItemMutationOperation {
    CREATED,
    MIGRATED,
    BOUND,
    UNBOUND,
    XP_GAINED,
    LEVEL_UP,
    MASTERY_CHANGED,
    EVOLVED,
    BRANCH_CHANGED,
    AFFIX_ROLLED,
    AFFIX_LOCKED,
    AFFIX_UNLOCKED,
    AFFIX_EXTRACTED,
    AFFIX_TRANSFERRED,
    SOCKET_INSERTED,
    SOCKET_REMOVED,
    SOCKET_REROLLED,
    GEM_UPGRADED,
    REFORGED,
    UPGRADED,
    MYSTERY_ENCHANTED,
    UPGRADE_FAILED,
    CORRUPTED,
    REPAIRED,
    TRADE_LOCKED,
    TRADED,
    DESTROYED,
    RESTORED,
    ADMIN_CORRECTION,
}

/**
 * One append-only audit record. `beforeFingerprint`/`afterFingerprint` tie the event to the
 * snapshot transition it describes; `previousEventHash` + `eventHash` form a tamper-evident chain
 * per instance (see [ItemEventHasher]).
 *
 * [snapshot] is the canonical post-mutation state serialization (see
 * [EquipmentInstanceSnapshotCodec]); `restoreFromEvent` rebuilds the exact historical state from
 * it. It is deliberately excluded from [ItemEventHasher]: its integrity is proven by
 * `fingerprint(decoded) == afterFingerprint` at restore time, and keeping it out of the hash input
 * preserves the chain validity of events written before the column existed (`null`).
 */
public data class ItemMutationEvent(
    public val eventId: UUID,
    public val instanceId: Long,
    public val operation: ItemMutationOperation,
    public val actor: ItemActor,
    public val source: String,
    public val beforeRevision: Long,
    public val afterRevision: Long,
    public val beforeFingerprint: String,
    public val afterFingerprint: String,
    public val payload: String,
    public val previousEventHash: String?,
    public val eventHash: String,
    public val createdAtEpochMillis: Long,
    public val snapshot: String? = null,
)

/**
 * Deterministic hash chain for the event log: `SHA-256(previousHash | canonical event fields)`. A
 * single edited historical row breaks every subsequent hash, which the invariant checker and
 * `::itemvalidate` detect.
 */
public object ItemEventHasher {
    public fun hash(event: ItemMutationEvent): String =
        EquipmentInstanceFingerprint.sha256(
            listOf(
                    event.eventId.toString(),
                    event.instanceId.toString(),
                    event.operation.name,
                    event.actor.type.name,
                    event.actor.id?.toString() ?: "-",
                    event.source,
                    event.beforeRevision.toString(),
                    event.afterRevision.toString(),
                    event.beforeFingerprint,
                    event.afterFingerprint,
                    event.payload,
                    event.previousEventHash ?: "-",
                )
                .joinToString("|")
        )
}

/** Structured result of a gateway mutation - never a bare Boolean. */
public sealed interface ItemMutationResult {
    public data class Success(public val instance: EquipmentInstance) : ItemMutationResult

    /** The client acted on a stale snapshot; [current] carries the live revision. */
    public data class RevisionConflict(public val current: EquipmentInstance) : ItemMutationResult

    public data class ValidationError(
        public val code: String,
        public val details: Map<String, String> = emptyMap(),
    ) : ItemMutationResult

    public data class CostError(public val missing: List<String>) : ItemMutationResult

    public data class InternalFailure(public val requestId: UUID) : ItemMutationResult
}

/**
 * Request-scoped deduplication key. Re-submitting the same key for the same operation replays the
 * stored result instead of charging costs or appending a duplicate event; the same key with a
 * different operation is rejected.
 */
public data class ItemMutationRequest(
    public val idempotencyKey: String,
    public val expectedRevision: Long?,
    public val actor: ItemActor,
    public val operation: ItemMutationOperation,
    public val source: String,
    public val payload: String = "",
) {
    init {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 80)
        require(source.isNotBlank() && source.length <= 64)
    }
}
