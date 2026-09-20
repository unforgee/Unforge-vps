package org.rsmod.api.equipment.instance

public enum class ReforgeOperation {
    RANDOM,
    GREATER,
    TOTAL,
    PRECISION,
    VALUE_ONLY,
    ABILITY_REPLACE,
    PROC,
    POWER,
    COOLDOWN,
    TRIGGER,
    TIER,
    SOCKET,
    AUGMENT,
    CLEANSE,
    CORRUPT,
    MUTATION,
    EMPOWER,
    SAFE,
    LUCKY,
}

public data class ReforgeCost(public val currencyObj: Int, public val amount: Int) {
    init {
        require(currencyObj >= 0 && amount >= 0)
    }
}

public data class ReforgePreview(
    public val operation: ReforgeOperation,
    public val before: EquipmentInstance,
    public val after: EquipmentInstance,
    public val cost: ReforgeCost,
    public val selectedSlot: Int? = null,
)

public object ReforgeOperationRegistry {
    public val supported: Set<ReforgeOperation> = ReforgeOperation.entries.toSet()

    public fun cost(operation: ReforgeOperation, base: Int = 100): ReforgeCost =
        ReforgeCost(
            currencyObj = 13215,
            amount =
                when (operation) {
                    ReforgeOperation.RANDOM -> base
                    ReforgeOperation.GREATER -> base * 2
                    ReforgeOperation.TOTAL -> base * 5
                    ReforgeOperation.PRECISION,
                    ReforgeOperation.VALUE_ONLY -> base * 3
                    else -> base * 4
                },
        )
}
