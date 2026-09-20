package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Determinism and sensitivity of the canonical SHA-256 fingerprint: identical state must hash
 * identically, any gameplay-state change must change the hash, and pure identity fields
 * (instanceId/uuid/revision/timestamps) must never affect it.
 */
class EquipmentInstanceFingerprintTest {
    @Test
    fun `same state produces the same fingerprint`() {
        val a = testInstance()
        val b = testInstance()
        assertEquals(EquipmentInstanceFingerprint.of(a), EquipmentInstanceFingerprint.of(b))
    }

    @Test
    fun `fingerprint is a 64-char lowercase sha-256 hex`() {
        val fingerprint = EquipmentInstanceFingerprint.of(testInstance())
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `identity fields are excluded from the fingerprint`() {
        val base = testInstance()
        val differentIdentity =
            base.copy(
                instanceId = base.instanceId + 999,
                instanceUuid = "11111111-2222-3333-4444-555555555555",
                revision = base.revision + 7,
                createdAtEpochMillis = 1_700_000_000_000,
            )
        assertEquals(
            EquipmentInstanceFingerprint.of(base),
            EquipmentInstanceFingerprint.of(differentIdentity),
        )
    }

    @Test
    fun `any state mutation changes the fingerprint`() {
        val base = testInstance()
        val fingerprint = EquipmentInstanceFingerprint.of(base)
        val variants =
            listOf(
                base.copy(quality = base.quality - 1),
                base.copy(itemLevel = base.itemLevel + 1),
                base.copy(binding = ItemBinding.QUEST_BOUND),
                base.copy(state = EquipmentInstanceState.IN_TRADE),
                base.copy(evolutionStage = 1),
                base.copy(experience = base.experience + 10),
                base.copy(reforgeCount = base.reforgeCount + 1),
                base.copy(upgradeLevel = base.upgradeLevel + 1),
                base.copy(source = "test-mutated"),
                base.copy(ownerAccountId = 42L),
                base.copy(lineageParentIds = listOf(7L)),
                base.copy(affixes = base.affixes.map { it.copy(magnitude = it.magnitude + 1) }),
            )
        for (variant in variants) {
            assertNotEquals(
                fingerprint,
                EquipmentInstanceFingerprint.of(variant),
                "Fingerprint did not change for variant: $variant",
            )
        }
    }

    @Test
    fun `affix order does not affect the fingerprint`() {
        val twoAffixes =
            testInstance(
                rarity = EquipmentRarity.Rare,
                affixes =
                    listOf(
                        affix(slot = 0, family = "a", magnitude = 5),
                        affix(slot = 1, family = "b", magnitude = 7),
                    ),
            )
        val reversed = twoAffixes.copy(affixes = twoAffixes.affixes.reversed())
        assertEquals(
            EquipmentInstanceFingerprint.of(twoAffixes),
            EquipmentInstanceFingerprint.of(reversed),
        )
    }

    @Test
    fun `verify accepts a stamped instance and rejects a tampered one`() {
        val stamped = testInstance().stamped()
        assertTrue(EquipmentInstanceFingerprint.verify(stamped))
        val tampered = stamped.copy(quality = stamped.quality - 50)
        assertFalse(EquipmentInstanceFingerprint.verify(tampered))
        assertFalse(EquipmentInstanceFingerprint.verify(testInstance()))
    }

    internal fun testInstance(
        rarity: EquipmentRarity = EquipmentRarity.Uncommon,
        affixes: List<EquipmentAffixRoll> = listOf(affix(0, "keen", 3)),
        sockets: List<EquipmentSocket> = emptyList(),
    ): EquipmentInstance =
        EquipmentInstance(
            instanceId = 42L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = rarity,
            rollSeed = 239L,
            affixes = affixes,
            sockets = sockets,
            uniqueEffectIds = emptyList(),
            source = "test",
            instanceUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        )

    private fun affix(slot: Int, family: String, magnitude: Int) =
        EquipmentAffixRoll(
            slot = slot,
            definitionId = "def-$family",
            family = family,
            stat = EquipmentStat.AttackStab,
            unit = ModifierUnit.Flat,
            polarity = ModifierPolarity.Boon,
            magnitude = magnitude,
        )

    private fun EquipmentInstance.stamped(): EquipmentInstance =
        copy(fingerprint = EquipmentInstanceFingerprint.of(this))
}
