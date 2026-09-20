package org.rsmod.api.equipment.instance

/**
 * Data-driven item evolution track. Stages are ordered; an instance at `evolutionStage = n` can
 * evolve to `n + 1` once the level gate (and any future material/quest gates) are met.
 *
 * Stage effects are intentionally descriptive (`unlock`) rather than hardcoded combat values: the
 * concrete stat contributions resolve through the same canonical stat pipeline as affixes, so a
 * balance change never requires touching combat code.
 */
public data class ItemEvolutionStage(
    public val stage: Int,
    public val requiredItemLevel: Int,
    public val title: String,
    public val unlock: String,
    /** Whether the stage opens a [ItemEvolutionBranch] choice. */
    public val opensBranchChoice: Boolean = false,
) {
    init {
        require(stage >= 1)
        require(requiredItemLevel >= 1)
        require(title.isNotBlank() && unlock.isNotBlank())
    }
}

/**
 * A stage-3+ specialization branch. `statTags` are consumed by the canonical stat pipeline;
 * `incompatible` lists branch ids that cannot coexist on the same instance.
 */
public data class ItemEvolutionBranch(
    public val id: String,
    public val name: String,
    public val description: String,
    public val categories: Set<EquipmentCategory>,
    public val statTags: Set<String>,
    public val incompatible: Set<String> = emptySet(),
    public val balanceVersion: Int = EquipmentInstance.CURRENT_BALANCE_VERSION,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{2,48}")))
        require(name.isNotBlank())
        require(categories.isNotEmpty())
    }
}

public object ItemEvolutionCatalog {
    public const val MAX_STAGE: Int = 5

    public val stages: List<ItemEvolutionStage> =
        listOf(
            ItemEvolutionStage(1, 10, "Tempered", "First passive trait"),
            ItemEvolutionStage(2, 25, "Honed", "Additional affix/socket feature"),
            ItemEvolutionStage(3, 50, "Attuned", "Branch choice", opensBranchChoice = true),
            ItemEvolutionStage(4, 75, "Exalted", "Signature effect"),
            ItemEvolutionStage(5, 100, "Transcendent", "Endgame mastery"),
        )

    public val branches: List<ItemEvolutionBranch> =
        listOf(
            ItemEvolutionBranch(
                "reaver",
                "Reaver",
                "Offensive specialization: damage, criticals, penetration.",
                EquipmentCategory.entries.toSet(),
                setOf("offensive", "critical", "penetration"),
            ),
            ItemEvolutionBranch(
                "bulwark",
                "Bulwark",
                "Defensive specialization: health, armour, mitigation.",
                EquipmentCategory.entries.toSet(),
                setOf("defensive", "health", "mitigation"),
            ),
            ItemEvolutionBranch(
                "channeler",
                "Channeler",
                "Utility specialization: cooldowns, support, sustain.",
                EquipmentCategory.entries.toSet(),
                setOf("utility", "cooldown", "support"),
            ),
        )

    public val branchesById: Map<String, ItemEvolutionBranch> =
        branches.associateBy(ItemEvolutionBranch::id)

    init {
        require(stages.size == MAX_STAGE)
        require(stages.map(ItemEvolutionStage::stage) == (1..MAX_STAGE).toList())
        for (branch in branches) {
            for (other in branch.incompatible) {
                require(branch.id in branchesById.getValue(other).incompatible) {
                    "Incompatibility must be symmetric: ${branch.id} / $other"
                }
            }
        }
    }

    public fun stage(stage: Int): ItemEvolutionStage? = stages.firstOrNull { it.stage == stage }

    public fun nextStage(currentStage: Int): ItemEvolutionStage? = stage(currentStage + 1)

    /** Server-side gate check for an evolve request. */
    public fun canEvolve(instance: EquipmentInstance): Boolean {
        val next = nextStage(instance.evolutionStage) ?: return false
        return instance.itemLevel >= next.requiredItemLevel
    }

    /**
     * Validates [branchId] for an instance that is evolving into [targetStage]. Returns a stable
     * error code or `null` when the branch is acceptable (`null` branchId is always valid when the
     * stage does not open the branch choice).
     */
    public fun branchError(
        instance: EquipmentInstance,
        targetStage: Int,
        branchId: String?,
    ): String? {
        val stage = stage(targetStage) ?: return "unknown-stage"
        if (branchId == null) {
            return if (stage.opensBranchChoice) "branch-required" else null
        }
        val branch = branchesById[branchId] ?: return "unknown-branch"
        if (!stage.opensBranchChoice) return "branch-not-open"
        if (instance.category !in branch.categories) return "branch-category"
        val current = instance.evolutionBranch?.let(branchesById::get)
        if (current != null && current.id != branchId) {
            if (branchId in current.incompatible || current.id in branch.incompatible) {
                return "branch-incompatible"
            }
        }
        return null
    }
}
