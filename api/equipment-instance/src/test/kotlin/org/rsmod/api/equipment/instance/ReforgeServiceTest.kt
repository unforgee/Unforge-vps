package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class ReforgeServiceTest {
    @Test
    public fun `reforge preserves stable id and records history`() {
        val source =
            EquipmentInstance(
                42,
                4151,
                EquipmentCategory.RightHand,
                EquipmentRarity.Rare,
                99,
                listOf(
                    EquipmentAffixRoll(
                        0,
                        "strength-power",
                        "strength",
                        EquipmentStat.Strength,
                        ModifierUnit.BasisPoints,
                        ModifierPolarity.Boon,
                        10,
                    )
                ),
                emptyList(),
                emptyList(),
                "test",
            )
        val preview = ReforgeService().preview(source, ReforgeOperation.VALUE_ONLY, 0)
        val result = ReforgeService().commit(preview, 10_000)
        assertEquals(source.instanceId, result.instanceId)
        assertEquals(1, result.reforgeCount)
        assertEquals("VALUE_ONLY", result.reforgeHistory.single())
    }

    @Test
    public fun `locked modifier is not changed`() {
        val source =
            EquipmentInstance(
                7,
                4151,
                EquipmentCategory.RightHand,
                EquipmentRarity.Rare,
                4,
                listOf(
                    EquipmentAffixRoll(
                        0,
                        "strength-power",
                        "strength",
                        EquipmentStat.Strength,
                        ModifierUnit.BasisPoints,
                        ModifierPolarity.Boon,
                        10,
                    )
                ),
                emptyList(),
                emptyList(),
                "test",
                lockedAffixSlots = setOf(0),
            )
        val preview = ReforgeService().preview(source, ReforgeOperation.RANDOM, seed = 4)
        assertEquals(source.affixes, preview.after.affixes)
    }

    @Test
    public fun `insufficient currency is rejected`() {
        val source =
            EquipmentInstance(
                9,
                4151,
                EquipmentCategory.RightHand,
                EquipmentRarity.Rare,
                4,
                listOf(
                    EquipmentAffixRoll(
                        0,
                        "strength-power",
                        "strength",
                        EquipmentStat.Strength,
                        ModifierUnit.BasisPoints,
                        ModifierPolarity.Boon,
                        10,
                    )
                ),
                emptyList(),
                emptyList(),
                "test",
            )
        val preview = ReforgeService().preview(source, ReforgeOperation.RANDOM)
        assertFailsWith<IllegalArgumentException> { ReforgeService().commit(preview, 0) }
    }
}
