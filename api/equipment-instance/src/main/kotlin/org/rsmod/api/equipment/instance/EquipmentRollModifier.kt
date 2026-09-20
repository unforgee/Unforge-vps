package org.rsmod.api.equipment.instance

/** Context supplied to optional systems that modify a player's newly rolled item instance. */
public data class EquipmentRollContext(
    public val templateObj: Int,
    public val category: EquipmentCategory,
    public val source: String,
    public val tier: EquipmentTier,
    public val itemLevel: Int,
    public val ownerCharacterId: Long?,
)

/** Additive, deterministic hooks for item rarity and quality changes. */
public interface EquipmentRollModifier {
    public fun rarityWeightBonus(context: EquipmentRollContext, rarity: EquipmentRarity): Int = 0

    public fun qualityBonus(context: EquipmentRollContext, rarity: EquipmentRarity): Int = 0

    /** Chance for one additional unique item effect, expressed in basis points. */
    public fun additionalUniqueEffectChanceBps(
        context: EquipmentRollContext,
        rarity: EquipmentRarity,
    ): Int = 0
}
