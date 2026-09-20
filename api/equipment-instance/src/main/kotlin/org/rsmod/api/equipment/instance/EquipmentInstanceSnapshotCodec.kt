package org.rsmod.api.equipment.instance

/**
 * Rebuilds an [EquipmentInstance] from the canonical state serialization produced by
 * [EquipmentInstanceFingerprint.canonical] - the same format stored in the `snapshot` column of
 * `equipment_instance_events`.
 *
 * The codec exists so `restoreFromEvent` can resurrect the exact post-mutation state recorded by an
 * audit event. Integrity is fail-closed: a malformed serialization returns `null`, and callers
 * additionally verify `fingerprint(decoded) == event.afterFingerprint`, so a subtly corrupted or
 * delimiter-ambiguous payload can never be silently restored.
 *
 * Identity fields are not part of the canonical state and decode to safe placeholders (`instanceId
 * = 0`, empty uuid, `revision = 0`, `createdAtEpochMillis = 0`, empty fingerprint); the restore
 * path re-applies the live row's identity before persisting.
 */
public object EquipmentInstanceSnapshotCodec {
    /** The serialized state stored on an audit event - the canonical fingerprint input itself. */
    public fun encode(instance: EquipmentInstance): String =
        EquipmentInstanceFingerprint.canonical(instance)

    /**
     * Rebuilds the recorded state, or `null` when [serialized] cannot be parsed into a valid
     * [EquipmentInstance]. The returned instance carries placeholder identity fields.
     */
    public fun decode(serialized: String): EquipmentInstance? {
        val parts = serialized.split(FIELD)
        // 33 fields is the current shape; 29 is the pre-Mystery-Enchant shape and 28 is the
        // pre-Forge-2.0 shape. All remain readable so restore-from-event works across migrations.
        if (parts.size !in setOf(FIELD_COUNT, FIELD_COUNT_PRE_ENCHANT, FIELD_COUNT_LEGACY))
            return null
        return try {
            EquipmentInstance(
                instanceId = EquipmentInstance.UNPERSISTED_INSTANCE_ID,
                templateObj = parts[0].toInt(),
                category = EquipmentCategory.valueOf(parts[1]),
                rarity = EquipmentRarity.valueOf(parts[2]),
                tier = EquipmentTier.valueOf(parts[3]),
                state = EquipmentInstanceState.valueOf(parts[4]),
                binding = ItemBinding.valueOf(parts[5]),
                ownerAccountId = parts[6].nullableLong(),
                ownerCharacterId = parts[7].nullableLong(),
                rollSeed = parts[8].toLong(),
                schemaVersion = parts[9].toInt(),
                balanceVersion = parts[10].toInt(),
                itemLevel = parts[11].toInt(),
                masteryLevel = parts[12].toInt(),
                experience = parts[13].toLong(),
                quality = parts[14].toInt(),
                evolutionStage = parts[15].toInt(),
                evolutionBranch = parts[16].nullableText(),
                source = parts[17],
                lineageParentIds = parts[18].csvLongs(),
                lineageRecipeId = parts[19].nullableText(),
                lineageDropId = parts[20].nullableText(),
                affixes = parts[21].records(::decodeAffix),
                sockets = parts[22].records(::decodeSocket),
                skillAffixes = parts[23].records(::decodeSkillAffix),
                uniqueEffectIds = parts[24].csv(),
                lockedAffixSlots = parts[25].csv().map(String::toInt).toSet(),
                reforgeCount = parts[26].toInt(),
                reforgeHistory = parts[27].csv(),
                upgradeLevel = if (parts.size >= FIELD_COUNT_PRE_ENCHANT) parts[28].toInt() else 0,
                mysteryEnchantId =
                    if (parts.size == FIELD_COUNT)
                        parts[29].nullableText()?.let(MysteryEnchantId::valueOf)
                    else null,
                mysteryEnchantTier =
                    if (parts.size == FIELD_COUNT)
                        parts[30].nullableText()?.let(MysteryEnchantTier::valueOf)
                    else null,
                mysteryEnchantKillsRemaining =
                    if (parts.size == FIELD_COUNT) parts[31].toInt() else 0,
                mysteryEnchantKillsMax = if (parts.size == FIELD_COUNT) parts[32].toInt() else 0,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeAffix(record: String): EquipmentAffixRoll {
        val fields = record.split('/')
        require(fields.size == 7)
        return EquipmentAffixRoll(
            slot = fields[0].toInt(),
            definitionId = fields[1],
            family = fields[2],
            stat = EquipmentStat.valueOf(fields[3]),
            unit = ModifierUnit.valueOf(fields[4]),
            polarity = ModifierPolarity.valueOf(fields[5]),
            magnitude = fields[6].toInt(),
        )
    }

    private fun decodeSocket(record: String): EquipmentSocket {
        val fields = record.split('/')
        require(fields.size == 4)
        return EquipmentSocket(
            slot = fields[0].toInt(),
            type = fields[1],
            socketedObj = fields[2].nullableText()?.toInt(),
            magnitude = fields[3].toInt(),
        )
    }

    private fun decodeSkillAffix(record: String): SkillAffixRoll {
        val fields = record.split('/')
        require(fields.size == 5)
        return SkillAffixRoll(
            slot = fields[0].toInt(),
            skill = fields[1],
            effect = fields[2],
            unit = ModifierUnit.valueOf(fields[3]),
            magnitude = fields[4].toInt(),
        )
    }

    private fun String.nullableText(): String? = takeUnless { it == EMPTY }

    private fun String.nullableLong(): Long? = nullableText()?.toLong()

    private fun String.csv(): List<String> = if (isEmpty()) emptyList() else split(',')

    private fun String.csvLongs(): List<Long> = csv().map(String::toLong)

    // The canonical writer terminates every record with RECORD, so the final split element is
    // always "" - filter it (and any other empty record) rather than feeding it to the parser.
    private fun <T> String.records(parse: (String) -> T): List<T> =
        if (isEmpty()) emptyList() else split(RECORD).filter(String::isNotEmpty).map(parse)

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'
    private const val EMPTY = "-"
    private const val FIELD_COUNT = 33
    private const val FIELD_COUNT_PRE_ENCHANT = 29
    private const val FIELD_COUNT_LEGACY = 28
}
