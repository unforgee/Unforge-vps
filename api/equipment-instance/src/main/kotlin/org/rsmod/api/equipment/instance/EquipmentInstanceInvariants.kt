package org.rsmod.api.equipment.instance

/**
 * Report-only invariant checker for Item Instance Evolution.
 *
 * Run after creation, after every gateway mutation (in debug), on admin request (`::itemvalidate`)
 * and after migrations. It never repairs data - every finding is a violation report; fixes are
 * separate, audited admin operations.
 */
public object EquipmentInstanceInvariants {
    /** Every checkable violation of a single snapshot. Empty list = valid. */
    public fun violations(instance: EquipmentInstance): List<String> = buildList {
        if (instance.instanceId < 0) add("instanceId-negative")
        if (instance.templateObj < 0) add("template-negative")
        if (
            instance.instanceUuid.isNotEmpty() &&
                !instance.instanceUuid.matches(
                    Regex(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}" +
                            "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    )
                )
        ) {
            add("uuid-malformed")
        }
        if (instance.experience < 0) add("experience-negative")
        if (instance.itemLevel < 1) add("item-level-out-of-range")
        if (instance.masteryLevel < 0) add("mastery-negative")
        if (instance.quality !in 0..100) add("quality-out-of-range")
        if (instance.revision < 0) add("revision-negative")
        if (instance.evolutionStage > ItemEvolutionCatalog.MAX_STAGE) {
            add("evolution-stage-unknown")
        }
        if (
            instance.evolutionBranch != null &&
                instance.evolutionBranch !in ItemEvolutionCatalog.branchesById
        ) {
            add("evolution-branch-unknown")
        }
        if (instance.evolutionBranch != null && instance.evolutionStage == 0) {
            add("evolution-branch-without-stage")
        }
        // Rarity capacity rules.
        if (instance.affixes.size !in instance.rarity.affixMin..instance.rarity.affixMax) {
            add("affix-count-rarity-mismatch")
        }
        if (instance.sockets.size !in instance.rarity.socketMin..instance.rarity.socketMax) {
            add("socket-count-rarity-mismatch")
        }
        if (instance.uniqueEffectIds.size > instance.rarity.uniqueEffectCount) {
            add("unique-effect-count-rarity-mismatch")
        }
        // One affix per family unless the catalog explicitly allows stacking.
        if (
            instance.affixes.map(EquipmentAffixRoll::family).distinct().size !=
                instance.affixes.size
        ) {
            add("affix-duplicate-family")
        }
        // Locked slots must address real affix slots.
        if (instance.lockedAffixSlots.any { it !in instance.affixes.indices }) {
            add("locked-slot-out-of-range")
        }
        // Binding vs owner consistency.
        when (instance.binding) {
            ItemBinding.CHARACTER_BOUND ->
                if (instance.ownerCharacterId == null) add("character-bound-without-owner")
            ItemBinding.ACCOUNT_BOUND ->
                if (instance.ownerAccountId == null) add("account-bound-without-owner")
            ItemBinding.DESTROYED ->
                if (instance.state != EquipmentInstanceState.DESTROYED) {
                    add("destroyed-binding-active-state")
                }
            else -> Unit
        }
        if (
            instance.state == EquipmentInstanceState.DESTROYED &&
                instance.binding != ItemBinding.DESTROYED
        ) {
            add("destroyed-state-live-binding")
        }
        // Fingerprint integrity (only when one has been persisted).
        if (instance.fingerprint.isNotEmpty() && !EquipmentInstanceFingerprint.verify(instance)) {
            add("fingerprint-mismatch")
        }
        // Lineage sanity.
        if (instance.lineageParentIds.any { it <= 0 }) add("lineage-parent-invalid")
        if (instance.lineageParentIds.distinct().size != instance.lineageParentIds.size) {
            add("lineage-parent-duplicate")
        }
    }

    /**
     * Validates the append-only event chain for [events] (must be ordered by `afterRevision`):
     * contiguous revisions, fingerprint links, and the tamper-evident hash chain.
     */
    public fun eventChainViolations(events: List<ItemMutationEvent>): List<String> = buildList {
        for (i in events.indices) {
            val event = events[i]
            if (ItemEventHasher.hash(event) != event.eventHash) {
                add("event-${event.eventId}:hash-mismatch")
            }
            if (i == 0) continue
            val previous = events[i - 1]
            if (event.beforeRevision != previous.afterRevision) {
                add("event-${event.eventId}:revision-gap")
            }
            if (event.beforeFingerprint != previous.afterFingerprint) {
                add("event-${event.eventId}:fingerprint-link-broken")
            }
            if (event.previousEventHash != previous.eventHash) {
                add("event-${event.eventId}:hash-chain-broken")
            }
        }
    }
}
