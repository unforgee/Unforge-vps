package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import kotlin.math.max
import kotlin.random.Random

public class ReforgeService(
    private val catalog: List<EquipmentAffixDefinition> = EquipmentAffixCatalog.definitions
) {
    @Inject public constructor() : this(EquipmentAffixCatalog.definitions)

    public fun preview(
        instance: EquipmentInstance,
        operation: ReforgeOperation,
        selectedSlot: Int? = null,
        seed: Long = instance.rollSeed,
    ): ReforgePreview {
        require(operation in ReforgeOperationRegistry.supported)
        require(selectedSlot == null || selectedSlot in instance.affixes.indices)
        val random = Random(seed xor operation.ordinal.toLong())
        val mutable = instance.affixes.toMutableList()
        val slots =
            when (operation) {
                ReforgeOperation.GREATER ->
                    mutable.indices
                        .filterNot(instance.lockedAffixSlots::contains)
                        .shuffled(random)
                        .take(2 + random.nextInt(3))
                ReforgeOperation.TOTAL ->
                    mutable.indices.filterNot(instance.lockedAffixSlots::contains)
                ReforgeOperation.PRECISION,
                ReforgeOperation.VALUE_ONLY ->
                    listOfNotNull(selectedSlot).filterNot(instance.lockedAffixSlots::contains)
                else ->
                    mutable.indices
                        .filterNot(instance.lockedAffixSlots::contains)
                        .shuffled(random)
                        .take(1)
            }
        for (slot in slots) {
            val old = mutable[slot]
            val definition =
                when (operation) {
                    ReforgeOperation.VALUE_ONLY,
                    ReforgeOperation.TIER ->
                        catalog.firstOrNull { it.id == old.definitionId } ?: continue
                    else ->
                        catalog
                            .filter {
                                it.categories.contains(instance.category) &&
                                    it.minRarity.ordinal <= instance.rarity.ordinal
                            }
                            .randomOrNull(random) ?: continue
                }
            val magnitude =
                definition.minMagnitude +
                    random.nextInt(max(1, definition.maxMagnitude - definition.minMagnitude + 1))
            mutable[slot] =
                old.copy(
                    definitionId = definition.id,
                    family = definition.family,
                    stat = definition.stat,
                    unit = definition.unit,
                    polarity = definition.polarity,
                    magnitude = magnitude,
                )
        }
        val after =
            instance.copy(
                affixes = mutable,
                reforgeCount = instance.reforgeCount + 1,
                reforgeHistory = (instance.reforgeHistory + operation.name).takeLast(50),
            )
        return ReforgePreview(
            operation,
            instance,
            after,
            ReforgeOperationRegistry.cost(operation),
            selectedSlot,
        )
    }

    public fun commit(preview: ReforgePreview, currencyBalance: Int): EquipmentInstance {
        require(currencyBalance >= preview.cost.amount) { "Insufficient reforge currency" }
        require(preview.before.instanceId == preview.after.instanceId) {
            "Reforge cannot replace unique instance id"
        }
        return preview.after
    }
}
