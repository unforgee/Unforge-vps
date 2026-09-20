package org.rsmod.api.equipment.instance

public enum class EquipmentRarity(
    public val weightBasisPoints: Int,
    public val affixMin: Int,
    public val affixMax: Int,
    public val socketMin: Int,
    public val socketMax: Int,
    public val uniqueEffectCount: Int = 0,
) {
    Uncommon(6_500, affixMin = 1, affixMax = 1, socketMin = 0, socketMax = 3),
    Rare(2_200, affixMin = 1, affixMax = 2, socketMin = 0, socketMax = 3, uniqueEffectCount = 1),
    Epic(900, affixMin = 1, affixMax = 3, socketMin = 0, socketMax = 3, uniqueEffectCount = 2),
    Legendary(300, affixMin = 2, affixMax = 4, socketMin = 0, socketMax = 3, uniqueEffectCount = 3),
    Mythic(80, affixMin = 3, affixMax = 4, socketMin = 0, socketMax = 3, uniqueEffectCount = 4),
    Jackpot(20, affixMin = 4, affixMax = 5, socketMin = 2, socketMax = 3, uniqueEffectCount = 5);

    public companion object {
        public const val TOTAL_WEIGHT_BASIS_POINTS: Int = 10_000
    }
}
