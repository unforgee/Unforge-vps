package org.rsmod.api.equipment.instance

import java.security.MessageDigest

/**
 * Canonical, tamper-evident identity of an [EquipmentInstance]'s mutable state.
 *
 * The fingerprint is `SHA-256` over a field-delimited canonical serialization of every
 * gameplay-relevant column: same state always produces the same fingerprint, and any hidden edit
 * (db tamper, missed mutation path, stale-registry write) produces a mismatch that
 * [EquipmentInstanceInvariants] and `::itemfingerprint` can detect.
 *
 * Excluded by design: `instanceId`/`instanceUuid`/`createdAtEpochMillis` (identity, not state -
 * they are folded into the event log instead), `fingerprint` itself, and `revision` (the revision
 * is the optimistic-lock counter; its integrity is enforced by `saveSnapshotWithRevision`).
 */
public object EquipmentInstanceFingerprint {
    public fun of(instance: EquipmentInstance): String = sha256(canonical(instance))

    /** `true` when [instance]'s stored fingerprint matches its recomputed value. */
    public fun verify(instance: EquipmentInstance): Boolean =
        instance.fingerprint.isNotEmpty() && instance.fingerprint == of(instance)

    public fun canonical(instance: EquipmentInstance): String = buildString {
        append(instance.templateObj).append('\u001F')
        append(instance.category.name).append('\u001F')
        append(instance.rarity.name).append('\u001F')
        append(instance.tier.name).append('\u001F')
        append(instance.state.name).append('\u001F')
        append(instance.binding.name).append('\u001F')
        append(instance.ownerAccountId?.toString() ?: "-").append('\u001F')
        append(instance.ownerCharacterId?.toString() ?: "-").append('\u001F')
        append(instance.rollSeed).append('\u001F')
        append(instance.schemaVersion).append('\u001F')
        append(instance.balanceVersion).append('\u001F')
        append(instance.itemLevel).append('\u001F')
        append(instance.masteryLevel).append('\u001F')
        append(instance.experience).append('\u001F')
        append(instance.quality).append('\u001F')
        append(instance.evolutionStage).append('\u001F')
        append(instance.evolutionBranch ?: "-").append('\u001F')
        append(instance.source).append('\u001F')
        append(instance.lineageParentIds.joinToString(",")).append('\u001F')
        append(instance.lineageRecipeId ?: "-").append('\u001F')
        append(instance.lineageDropId ?: "-").append('\u001F')
        for (a in instance.affixes.sortedBy { it.slot }) {
            append(a.slot).append('/')
            append(a.definitionId).append('/')
            append(a.family).append('/')
            append(a.stat.name).append('/')
            append(a.unit.name).append('/')
            append(a.polarity.name).append('/')
            append(a.magnitude).append('\u001E')
        }
        append('\u001F')
        for (s in instance.sockets.sortedBy { it.slot }) {
            append(s.slot).append('/')
            append(s.type).append('/')
            append(s.socketedObj?.toString() ?: "-").append('/')
            append(s.magnitude).append('\u001E')
        }
        append('\u001F')
        for (s in instance.skillAffixes.sortedBy { it.slot }) {
            append(s.slot).append('/')
            append(s.skill).append('/')
            append(s.effect).append('/')
            append(s.unit.name).append('/')
            append(s.magnitude).append('\u001E')
        }
        append('\u001F')
        append(instance.uniqueEffectIds.joinToString(",")).append('\u001F')
        append(instance.lockedAffixSlots.sorted().joinToString(",")).append('\u001F')
        append(instance.reforgeCount).append('\u001F')
        append(instance.reforgeHistory.joinToString(",")).append('\u001F')
        // Field 28 (Forge 2.0): upgrade level. Appended last so pre-V36 snapshots keep their
        // field ordering; the snapshot codec accepts both arities.
        append(instance.upgradeLevel).append('\u001F')
        // Mystery Enchant state is deliberately appended so older snapshots remain readable.
        append(instance.mysteryEnchantId?.name ?: "-").append('\u001F')
        append(instance.mysteryEnchantTier?.name ?: "-").append('\u001F')
        append(instance.mysteryEnchantKillsRemaining).append('\u001F')
        append(instance.mysteryEnchantKillsMax)
    }

    public fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
    }
}
