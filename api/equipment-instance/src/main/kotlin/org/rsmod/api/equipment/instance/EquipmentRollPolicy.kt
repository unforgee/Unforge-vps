package org.rsmod.api.equipment.instance

import java.util.SplittableRandom

public class EquipmentRollPolicy(
    public val affixes: List<EquipmentAffixDefinition>,
    public val uniqueEffects: List<String>,
    public val socketType: String = "Universal",
) {
    init {
        require(
            EquipmentRarity.entries.sumOf(EquipmentRarity::weightBasisPoints) ==
                EquipmentRarity.TOTAL_WEIGHT_BASIS_POINTS
        )
        require(affixes.map(EquipmentAffixDefinition::id).distinct().size == affixes.size)
        require(uniqueEffects.isNotEmpty() && uniqueEffects.none(String::isBlank))
        require(uniqueEffects.none(String::isBlank) && socketType.isNotBlank())
    }

    public fun roll(
        templateObj: Int,
        category: EquipmentCategory,
        seed: Long,
        source: String,
        tier: EquipmentTier = EquipmentTier.Bronze,
        itemLevel: Int = 0,
        ownerCharacterId: Long? = null,
        modifiers: Collection<EquipmentRollModifier> = emptyList(),
    ): EquipmentInstance {
        val random = SplittableRandom(seed)
        val context =
            EquipmentRollContext(templateObj, category, source, tier, itemLevel, ownerCharacterId)
        val rarity = selectRarity(random, context, modifiers)
        val eligible =
            affixes.filter { category in it.categories && rarity.ordinal >= it.minRarity.ordinal }
        val count = random.nextInt(rarity.affixMin, rarity.affixMax + 1)
        val selected = selectAffixes(random, eligible, count, rarity, category, tier)
        val sockets =
            List(random.nextInt(rarity.socketMin, rarity.socketMax + 1)) {
                EquipmentSocket(it, socketType)
            }
        val effectCandidates = uniqueEffects.toMutableList()
        val extraEffectChance =
            modifiers
                .sumOf { it.additionalUniqueEffectChanceBps(context, rarity) }
                .coerceIn(0, 10_000)
        val extraEffect =
            if (extraEffectChance > 0 && random.nextInt(10_000) < extraEffectChance) 1 else 0
        val effects = buildList {
            repeat(minOf(rarity.uniqueEffectCount + extraEffect, effectCandidates.size)) {
                add(effectCandidates.removeAt(random.nextInt(effectCandidates.size)))
            }
        }
        val qualityMin = EquipmentBalance.qualityMin(rarity)
        val quality =
            (qualityMin +
                    random.nextInt(101 - qualityMin) +
                    modifiers.sumOf { it.qualityBonus(context, rarity) })
                .coerceIn(qualityMin, 100)
        return EquipmentInstance(
            0L,
            templateObj,
            category,
            rarity,
            seed,
            selected,
            sockets,
            effects,
            source,
            tier,
            itemLevel =
                if (itemLevel > 0) itemLevel else EquipmentBalance.defaultItemLevel(tier, rarity),
            quality = quality,
        )
    }

    private fun selectRarity(
        random: SplittableRandom,
        context: EquipmentRollContext,
        modifiers: Collection<EquipmentRollModifier>,
    ): EquipmentRarity {
        val weights =
            EquipmentRarity.entries.map { rarity ->
                rarity to
                    (rarity.weightBasisPoints +
                            modifiers.sumOf { it.rarityWeightBonus(context, rarity) })
                        .coerceAtLeast(1)
            }
        val totalWeight = weights.sumOf { it.second }
        val roll = random.nextInt(totalWeight)
        var cumulative = 0
        return weights
            .first {
                cumulative += it.second
                roll < cumulative
            }
            .first
    }

    private fun selectAffixes(
        random: SplittableRandom,
        eligible: List<EquipmentAffixDefinition>,
        count: Int,
        rarity: EquipmentRarity,
        category: EquipmentCategory,
        tier: EquipmentTier,
    ): List<EquipmentAffixRoll> {
        val candidates = eligible.toMutableList()
        val families = HashSet<String>(count)
        val result = ArrayList<EquipmentAffixRoll>(count)
        while (candidates.isNotEmpty() && result.size < count) {
            val definition = candidates.removeAt(random.nextInt(candidates.size))
            if (!families.add(definition.family)) continue
            // Shared with the Forge reroll path - a rerolled affix lands in exactly the range it
            // could have been created with.
            val magnitude = AffixRollService.rollMagnitude(random, definition, rarity, tier)
            result +=
                EquipmentAffixRoll(
                    result.size,
                    definition.id,
                    definition.family,
                    definition.stat,
                    definition.unit,
                    definition.polarity,
                    magnitude,
                )
        }
        require(result.size == count) { "Not enough unique affix families for rarity roll." }
        return result
    }
}
