package org.rsmod.api.equipment.instance

public data class EquipmentAffixDefinition(
    public val id: String,
    public val family: String,
    public val stat: EquipmentStat,
    public val unit: ModifierUnit,
    public val polarity: ModifierPolarity,
    public val minMagnitude: Int,
    public val maxMagnitude: Int,
    public val minRarity: EquipmentRarity = EquipmentRarity.Uncommon,
    public val categories: Set<EquipmentCategory>,
    /**
     * `false` marks affixes the Forge may never reroll (legacy/exotic rolls). Forge reforge
     * operations skip them entirely - their magnitude stays byte-for-byte identical.
     */
    public val rerollable: Boolean = true,
) {
    init {
        require(id.isNotBlank() && family.isNotBlank())
        require(minMagnitude <= maxMagnitude)
        require(categories.isNotEmpty())
    }
}

public data class EquipmentAffixRoll(
    public val slot: Int,
    public val definitionId: String,
    public val family: String,
    public val stat: EquipmentStat,
    public val unit: ModifierUnit,
    public val polarity: ModifierPolarity,
    public val magnitude: Int,
)

public data class EquipmentSocket(
    public val slot: Int,
    public val type: String,
    public val socketedObj: Int? = null,
    public val magnitude: Int = 0,
)

/** A server-rolled skilling modifier carried by a wearable item instance. */
public data class SkillAffixRoll(
    public val slot: Int,
    public val skill: String,
    public val effect: String,
    public val unit: ModifierUnit,
    public val magnitude: Int,
) {
    init {
        require(slot >= 0)
        require(skill.isNotBlank() && effect.isNotBlank())
        require(magnitude >= 0)
    }
}

public enum class EquipmentTier(public val value: Int) {
    Bronze(1),
    Iron(2),
    Steel(3),
    Black(4),
    Mithril(5),
    Adamant(6),
    Rune(7),
    Dragon(8),
    Bandos(9),
    Torva(10),
}

/** Server-rolled, temporary combat enchantment carried by one item instance. */
public enum class MysteryEnchantId {
    BLOODTHIRST,
    EXECUTIONER,
    GIANTSLAYER,
    BERSERKER,
    FORTIFIED,
    PRECISION,
    ARCANE_FLOW,
    RAPID,
    DOUBLE_STRIKE,
    TREASURE_HUNTER,
    GUARDIAN,
}

/** Independent rarity of a Mystery Enchantment; this never changes [EquipmentInstance.rarity]. */
public enum class MysteryEnchantTier {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

public data class EquipmentInstance(
    public val instanceId: Long,
    public val templateObj: Int,
    public val category: EquipmentCategory,
    public val rarity: EquipmentRarity,
    public val rollSeed: Long,
    public val affixes: List<EquipmentAffixRoll>,
    public val sockets: List<EquipmentSocket>,
    public val uniqueEffectIds: List<String>,
    public val source: String,
    public val tier: EquipmentTier = EquipmentTier.Bronze,
    public val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    public val balanceVersion: Int = CURRENT_BALANCE_VERSION,
    public val itemLevel: Int = 1,
    public val quality: Int = 100,
    /**
     * Forge upgrade level (`+0`..`+10`). Raised only by the Forge upgrade operation; reforge
     * operations never touch it. Persisted in the `upgrade_level` column (V36).
     */
    public val upgradeLevel: Int = 0,
    public val lockedAffixSlots: Set<Int> = emptySet(),
    public val reforgeCount: Int = 0,
    public val reforgeHistory: List<String> = emptyList(),
    public val skillAffixes: List<SkillAffixRoll> = emptyList(),
    // --- Item Instance Evolution fields (V32). Defaults keep every pre-Evolution constructor
    // call site and legacy row valid: an instance that never went through the gateway is simply
    // an ACTIVE/UNBOUND item at revision 0 with a backfilled uuid.
    public val instanceUuid: String = "",
    public val state: EquipmentInstanceState = EquipmentInstanceState.ACTIVE,
    public val binding: ItemBinding = ItemBinding.UNBOUND,
    public val ownerAccountId: Long? = null,
    public val ownerCharacterId: Long? = null,
    public val masteryLevel: Int = 0,
    public val experience: Long = 0L,
    public val evolutionStage: Int = 0,
    public val evolutionBranch: String? = null,
    /** Optimistic-lock counter; bumps by exactly one on every gateway mutation. */
    public val revision: Long = 0L,
    /** SHA-256 fingerprint of the canonical field set; empty until first canonical persist. */
    public val fingerprint: String = "",
    public val lineageParentIds: List<Long> = emptyList(),
    public val lineageRecipeId: String? = null,
    public val lineageDropId: String? = null,
    public val createdAtEpochMillis: Long = 0L,
    public val mysteryEnchantId: MysteryEnchantId? = null,
    public val mysteryEnchantTier: MysteryEnchantTier? = null,
    public val mysteryEnchantKillsRemaining: Int = 0,
    public val mysteryEnchantKillsMax: Int = 0,
) {
    init {
        require(instanceId >= 0L && templateObj >= 0 && source.isNotBlank())
        require(itemLevel >= 1 && quality in 0..100)
        require(upgradeLevel >= 0)
        require(lockedAffixSlots.all { it in affixes.indices })
        require(reforgeCount >= 0)
        require(revision >= 0L && experience >= 0L && masteryLevel >= 0)
        require(createdAtEpochMillis >= 0L)
        require(mysteryEnchantKillsRemaining >= 0 && mysteryEnchantKillsMax >= 0)
        require(mysteryEnchantKillsRemaining <= mysteryEnchantKillsMax)
        require(
            (mysteryEnchantId == null &&
                mysteryEnchantTier == null &&
                mysteryEnchantKillsMax == 0) ||
                (mysteryEnchantId != null &&
                    mysteryEnchantTier != null &&
                    mysteryEnchantKillsMax > 0)
        ) {
            "Mystery enchant state must be empty or complete."
        }
        require(instanceUuid.isEmpty() || instanceUuid.matches(UUID_SHAPE)) {
            "instanceUuid must be empty or a canonical uuid."
        }
        require(binding != ItemBinding.CHARACTER_BOUND || ownerCharacterId != null) {
            "CHARACTER_BOUND instances must carry ownerCharacterId."
        }
        require(binding != ItemBinding.ACCOUNT_BOUND || ownerAccountId != null) {
            "ACCOUNT_BOUND instances must carry ownerAccountId."
        }
        require(evolutionStage <= ItemEvolutionCatalog.MAX_STAGE) {
            "Unknown evolution stage $evolutionStage."
        }
        require(evolutionBranch == null || evolutionStage > 0) {
            "An evolution branch requires stage > 0."
        }
        require(lineageParentIds.all { it > 0L })
        require(affixes.map(EquipmentAffixRoll::family).distinct().size == affixes.size)
        // Persisted instances may have been rolled under an older rarity table.  Keep those
        // items loadable so one legacy item cannot make the whole character profile fail to load.
        // New rolls are still constrained by EquipmentRollPolicy, while
        // EquipmentInstanceInvariants reports old capacity mismatches for repair/audit tooling.
        // Do not enforce the capacity range on the value object itself: repository restores must
        // accept instances rolled by older balance tables. Creation remains constrained by
        // EquipmentRollPolicy, and EquipmentInstanceInvariants reports mismatches for repair.
        // `<=` so instances persisted under older rarity counts still load; fresh rolls
        // always produce exactly `rarity.uniqueEffectCount` from the ability pool.
        require(uniqueEffectIds.size <= rarity.uniqueEffectCount)
        require(
            uniqueEffectIds.none(String::isBlank) &&
                uniqueEffectIds.distinct().size == uniqueEffectIds.size
        )
        require(skillAffixes.map { it.slot }.distinct().size == skillAffixes.size)
        SkillingCosmeticCatalog.validate(skillAffixes)
    }

    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 2
        public const val CURRENT_BALANCE_VERSION: Int = 3
        public const val UNPERSISTED_INSTANCE_ID: Long = 0L

        private val UUID_SHAPE =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
