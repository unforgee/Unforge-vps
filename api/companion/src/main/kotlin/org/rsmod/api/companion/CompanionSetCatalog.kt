package org.rsmod.api.companion

import org.rsmod.api.equipment.instance.EquipmentInstance

/**
 * A single tiered bonus of a companion gear set, applied once the wearer equips at least [pieces]
 * members of the set.
 */
public data class CompanionSetBonus(
    public val pieces: Int,
    public val stat: CompanionStat,
    public val value: Int,
    public val description: String,
) {
    init {
        require(pieces >= 2)
        require(description.isNotBlank())
    }
}

/** Data-driven companion equipment set. */
public data class CompanionSetDefinition(
    public val id: String,
    public val name: String,
    public val bonuses: List<CompanionSetBonus>,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{2,48}")))
        require(name.isNotBlank())
        require(bonuses.map(CompanionSetBonus::pieces).distinct().size == bonuses.size)
        require(bonuses.all { it.pieces <= maxPieces })
    }

    public val maxPieces: Int
        get() = CompanionSetCatalog.MAX_SET_PIECES
}

/** A set with its live piece count and which tiers are currently active. */
public data class CompanionSetActivation(
    public val definition: CompanionSetDefinition,
    public val equippedPieces: Int,
) {
    public val activeBonuses: List<CompanionSetBonus>
        get() = definition.bonuses.filter { equippedPieces >= it.pieces }

    public fun isActive(pieces: Int): Boolean = equippedPieces >= pieces
}

/**
 * Companion equipment set registry.
 *
 * Set membership rides on the already-persisted `EquipmentInstance.uniqueEffectIds` list: an
 * instance carrying the `"set:<id>"` tag belongs to that set. No schema change is required and set
 * gear can be granted by drops/shops simply by adding the tag - the roll policy never auto-rolls
 * set tags, so random loot cannot accidentally join a set.
 */
public object CompanionSetCatalog {
    public const val MAX_SET_PIECES: Int = 5

    /** Prefix marking a unique-effect entry as a set-membership tag. */
    public const val SET_TAG_PREFIX: String = "set:"

    public val definitions: List<CompanionSetDefinition> =
        listOf(
            CompanionSetDefinition(
                "infernal-pact",
                "Infernal Pact",
                listOf(
                    CompanionSetBonus(2, CompanionStat.DAMAGE_BPS, 500, "+5% Damage"),
                    CompanionSetBonus(
                        3,
                        CompanionStat.DAMAGE_REDUCTION_BPS,
                        1000,
                        "+10% Damage Reduction",
                    ),
                    CompanionSetBonus(
                        4,
                        CompanionStat.HEALING_POWER_BPS,
                        1500,
                        "+15% Healing Power",
                    ),
                    CompanionSetBonus(5, CompanionStat.LIFESTEAL_BPS, 500, "+5% Lifesteal"),
                ),
            ),
            CompanionSetDefinition(
                "wardens-oath",
                "Warden's Oath",
                listOf(
                    CompanionSetBonus(
                        2,
                        CompanionStat.THREAT_GENERATION_BPS,
                        1000,
                        "+10% Threat Generation",
                    ),
                    CompanionSetBonus(
                        3,
                        CompanionStat.DAMAGE_REDUCTION_BPS,
                        500,
                        "+5% Damage Reduction",
                    ),
                    CompanionSetBonus(4, CompanionStat.MAX_HEALTH, 100, "+100 Maximum Health"),
                    CompanionSetBonus(5, CompanionStat.BLOCK_CHANCE_BPS, 500, "+5% Block Chance"),
                ),
            ),
            CompanionSetDefinition(
                "wildhunt",
                "Wildhunt",
                listOf(
                    CompanionSetBonus(
                        2,
                        CompanionStat.CRITICAL_CHANCE_BPS,
                        500,
                        "+5% Critical Chance",
                    ),
                    CompanionSetBonus(
                        3,
                        CompanionStat.CRITICAL_DAMAGE_BPS,
                        1000,
                        "+10% Critical Damage",
                    ),
                    CompanionSetBonus(4, CompanionStat.ATTACK_SPEED_BPS, 500, "+5% Attack Speed"),
                    CompanionSetBonus(5, CompanionStat.BOSS_DAMAGE_BPS, 1000, "+10% Boss Damage"),
                ),
            ),
            CompanionSetDefinition(
                "menders-circle",
                "Mender's Circle",
                listOf(
                    CompanionSetBonus(
                        2,
                        CompanionStat.HEALING_POWER_BPS,
                        1000,
                        "+10% Healing Power",
                    ),
                    CompanionSetBonus(
                        3,
                        CompanionStat.COOLDOWN_REDUCTION_BPS,
                        500,
                        "+5% Cooldown Reduction",
                    ),
                    CompanionSetBonus(4, CompanionStat.SHIELD_POWER_BPS, 1000, "+10% Shield Power"),
                    CompanionSetBonus(
                        5,
                        CompanionStat.BUFF_DURATION_BPS,
                        1500,
                        "+15% Buff Duration",
                    ),
                ),
            ),
        )

    public val byId: Map<String, CompanionSetDefinition> =
        definitions.associateBy(CompanionSetDefinition::id)

    init {
        definitions.forEach { definition ->
            definition.bonuses.forEach { bonus -> require(bonus.pieces <= MAX_SET_PIECES) }
        }
    }

    /** The set an instance belongs to, or `null` when it carries no set tag. */
    public fun setIdOf(instance: EquipmentInstance): String? =
        instance.uniqueEffectIds
            .firstOrNull { it.startsWith(SET_TAG_PREFIX) }
            ?.removePrefix(SET_TAG_PREFIX)
            ?.takeIf(byId::containsKey)

    /**
     * All sets the equipped [items] participate in, including sets whose piece count has not yet
     * reached any bonus tier (so the UI can render `(2) LOCKED` states).
     */
    public fun activations(items: List<EquipmentInstance>): List<CompanionSetActivation> =
        items
            .mapNotNull(::setIdOf)
            .groupingBy { it }
            .eachCount()
            .map { (setId, count) -> CompanionSetActivation(byId.getValue(setId), count) }
}
