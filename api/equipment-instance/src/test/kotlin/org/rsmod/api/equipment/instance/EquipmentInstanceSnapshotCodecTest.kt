package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip coverage for [EquipmentInstanceSnapshotCodec]: the audit-event `snapshot` column must
 * rebuild the exact recorded state (so `fingerprint(decoded) == event.afterFingerprint` holds) and
 * fail closed on any malformed payload. Identity fields are never part of a snapshot.
 */
class EquipmentInstanceSnapshotCodecTest {
    @Test
    fun `encode then decode reproduces every mutable field and the fingerprint`() {
        val instance = populated()
        val decoded =
            assertNotNull(
                EquipmentInstanceSnapshotCodec.decode(
                    EquipmentInstanceSnapshotCodec.encode(instance)
                )
            )
        // Identity columns are intentionally not serialized; everything else must round-trip.
        val expected =
            instance.copy(
                instanceId = EquipmentInstance.UNPERSISTED_INSTANCE_ID,
                instanceUuid = "",
                revision = 0L,
                fingerprint = "",
                createdAtEpochMillis = 0L,
            )
        assertEquals(expected, decoded)
        assertEquals(
            EquipmentInstanceFingerprint.of(instance),
            EquipmentInstanceFingerprint.of(decoded),
        )
    }

    @Test
    fun `decoded snapshots carry safe placeholder identity fields`() {
        val decoded =
            assertNotNull(
                EquipmentInstanceSnapshotCodec.decode(
                    EquipmentInstanceSnapshotCodec.encode(populated())
                )
            )
        assertEquals(EquipmentInstance.UNPERSISTED_INSTANCE_ID, decoded.instanceId)
        assertEquals("", decoded.instanceUuid)
        assertEquals(0L, decoded.revision)
        assertEquals("", decoded.fingerprint)
        assertEquals(0L, decoded.createdAtEpochMillis)
    }

    @Test
    fun `malformed serializations fail closed with null`() {
        val canonical = EquipmentInstanceSnapshotCodec.encode(populated())
        val cases =
            listOf(
                "",
                "not-a-snapshot",
                // Two fields short: neither the current 33 nor a supported legacy shape.
                canonical.split(FIELD).dropLast(2).joinToString("$FIELD"),
                // One field too many.
                canonical + FIELD + "extra",
                // A corrupted enum name inside an otherwise well-formed record.
                canonical.replace("Epic", "NotARarity"),
                // A non-numeric template id.
                canonical.replace("4151", "obj-id"),
            )
        for (input in cases) {
            assertNull(
                EquipmentInstanceSnapshotCodec.decode(input),
                "expected null for: ${input.take(40)}",
            )
        }
    }

    @Test
    fun `legacy 28-field snapshots decode with upgrade level zero`() {
        val canonical = EquipmentInstanceSnapshotCodec.encode(populated())
        val legacy = canonical.split(FIELD).dropLast(5).joinToString("$FIELD")
        val decoded = assertNotNull(EquipmentInstanceSnapshotCodec.decode(legacy))
        assertEquals(0, decoded.upgradeLevel)
        assertEquals(null, decoded.mysteryEnchantId)
        assertEquals(4151, decoded.templateObj)
        assertEquals(EquipmentRarity.Epic, decoded.rarity)
    }

    @Test
    fun `pre-enchant 29-field snapshots retain forge upgrade and clear enchant state`() {
        val canonical = EquipmentInstanceSnapshotCodec.encode(populated())
        val preEnchant = canonical.split(FIELD).dropLast(4).joinToString("$FIELD")
        val decoded = assertNotNull(EquipmentInstanceSnapshotCodec.decode(preEnchant))
        assertEquals(3, decoded.upgradeLevel)
        assertEquals(null, decoded.mysteryEnchantId)
        assertEquals(0, decoded.mysteryEnchantKillsMax)
    }

    @Test
    fun `the serialized form is exactly the canonical fingerprint input`() {
        val instance = populated()
        assertEquals(
            EquipmentInstanceFingerprint.canonical(instance),
            EquipmentInstanceSnapshotCodec.encode(instance),
        )
    }

    @Test
    fun `a snapshot containing every collection type round-trips`() {
        val instance = populated()
        assertTrue(instance.affixes.size > 1)
        assertTrue(instance.sockets.isNotEmpty())
        assertTrue(instance.skillAffixes.isNotEmpty())
        assertTrue(instance.uniqueEffectIds.isNotEmpty())
        assertTrue(instance.lockedAffixSlots.isNotEmpty())
        assertTrue(instance.lineageParentIds.isNotEmpty())
        assertTrue(instance.reforgeHistory.isNotEmpty())
        val decoded =
            assertNotNull(
                EquipmentInstanceSnapshotCodec.decode(
                    EquipmentInstanceSnapshotCodec.encode(instance)
                )
            )
        assertEquals(instance.affixes, decoded.affixes)
        assertEquals(instance.sockets, decoded.sockets)
        assertEquals(instance.skillAffixes, decoded.skillAffixes)
        assertEquals(instance.uniqueEffectIds, decoded.uniqueEffectIds)
        assertEquals(instance.lockedAffixSlots, decoded.lockedAffixSlots)
        assertEquals(instance.lineageParentIds, decoded.lineageParentIds)
        assertEquals(instance.reforgeHistory, decoded.reforgeHistory)
    }

    private fun populated(): EquipmentInstance =
        EquipmentInstance(
            instanceId = 42L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Epic,
            rollSeed = 239L,
            affixes =
                listOf(
                    EquipmentAffixRoll(
                        slot = 0,
                        definitionId = "keen",
                        family = "keen",
                        stat = EquipmentStat.AttackStab,
                        unit = ModifierUnit.Flat,
                        polarity = ModifierPolarity.Boon,
                        magnitude = 3,
                    ),
                    EquipmentAffixRoll(
                        slot = 1,
                        definitionId = "warded",
                        family = "warded",
                        stat = EquipmentStat.DefenceStab,
                        unit = ModifierUnit.BasisPoints,
                        polarity = ModifierPolarity.Boon,
                        magnitude = 250,
                    ),
                ),
            sockets =
                listOf(
                    EquipmentSocket(0, "Universal", socketedObj = 1603, magnitude = 2),
                    EquipmentSocket(1, "Gem", socketedObj = null, magnitude = 0),
                ),
            uniqueEffectIds = listOf("boss-test-1", "pow-test-2"),
            source = "test",
            tier = EquipmentTier.Dragon,
            itemLevel = 50,
            quality = 77,
            lockedAffixSlots = setOf(1),
            reforgeCount = 2,
            reforgeHistory = listOf("reroll:keen", "lock:1"),
            skillAffixes =
                listOf(
                    SkillAffixRoll(
                        slot = 0,
                        skill = "mining",
                        effect = "MINING_GEM_CHANCE",
                        unit = ModifierUnit.BasisPoints,
                        magnitude = 500,
                    )
                ),
            instanceUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            state = EquipmentInstanceState.ACTIVE,
            binding = ItemBinding.CHARACTER_BOUND,
            ownerAccountId = 1234L,
            ownerCharacterId = 77L,
            masteryLevel = 4,
            experience = 9_500L,
            evolutionStage = 2,
            evolutionBranch = "reaver",
            revision = 9L,
            fingerprint = "deadbeef",
            lineageParentIds = listOf(9001L, 9002L),
            lineageRecipeId = "recipe-forge-1",
            lineageDropId = "drop-abyss-3",
            createdAtEpochMillis = 1_700_000_000_000L,
            upgradeLevel = 3,
            mysteryEnchantId = MysteryEnchantId.BLOODTHIRST,
            mysteryEnchantTier = MysteryEnchantTier.EPIC,
            mysteryEnchantKillsRemaining = 777,
            mysteryEnchantKillsMax = 1_000,
        )

    private companion object {
        /** The codec's unit-separator field delimiter. */
        private const val FIELD = ''
    }
}
