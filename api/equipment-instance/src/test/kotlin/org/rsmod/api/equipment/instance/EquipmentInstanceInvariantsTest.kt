package org.rsmod.api.equipment.instance

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Report-only invariant coverage: the checker must accept every constructible valid snapshot and
 * flag every mutation-legal-but-invalid state. Note that `EquipmentInstance.init` already rejects
 * the hardest violations (affix family duplicates, rarity counts, bound-without-owner), so the
 * tests below exercise what the constructor deliberately tolerates - tampered fingerprints,
 * lifecycle/binding divergence, legacy fields and event-chain corruption.
 */
class EquipmentInstanceInvariantsTest {
    @Test
    fun `a fresh valid instance reports no violations`() {
        assertEquals(emptyList(), EquipmentInstanceInvariants.violations(validInstance()))
    }

    @Test
    fun `a stamped valid instance reports no violations`() {
        val stamped =
            validInstance().let { it.copy(fingerprint = EquipmentInstanceFingerprint.of(it)) }
        assertEquals(emptyList(), EquipmentInstanceInvariants.violations(stamped))
    }

    @Test
    fun `tampered state under a stale fingerprint is flagged`() {
        val stamped =
            validInstance().let { it.copy(fingerprint = EquipmentInstanceFingerprint.of(it)) }
        val tampered = stamped.copy(quality = stamped.quality - 10)
        assertTrue("fingerprint-mismatch" in EquipmentInstanceInvariants.violations(tampered))
    }

    @Test
    fun `destroyed state with a live binding is flagged`() {
        val instance =
            validInstance()
                .copy(state = EquipmentInstanceState.DESTROYED, binding = ItemBinding.UNBOUND)
        assertTrue(
            "destroyed-state-live-binding" in EquipmentInstanceInvariants.violations(instance)
        )
    }

    @Test
    fun `destroyed binding with an active state is flagged`() {
        val instance =
            validInstance()
                .copy(state = EquipmentInstanceState.ACTIVE, binding = ItemBinding.DESTROYED)
        assertTrue(
            "destroyed-binding-active-state" in EquipmentInstanceInvariants.violations(instance)
        )
    }

    @Test
    fun `an unknown evolution branch is flagged`() {
        val instance = validInstance().copy(evolutionStage = 3, evolutionBranch = "not-a-branch")
        assertTrue("evolution-branch-unknown" in EquipmentInstanceInvariants.violations(instance))
    }

    @Test
    fun `a known evolution branch at a gated stage is accepted`() {
        val instance =
            validInstance().copy(evolutionStage = 3, evolutionBranch = "reaver", itemLevel = 50)
        assertEquals(emptyList(), EquipmentInstanceInvariants.violations(instance))
    }

    @Test
    fun `duplicate lineage parents are flagged`() {
        val instance = validInstance().copy(lineageParentIds = listOf(5L, 5L))
        assertTrue("lineage-parent-duplicate" in EquipmentInstanceInvariants.violations(instance))
    }

    @Test
    fun `a well-formed event chain reports no violations`() {
        val first = event(revision = 0L to 1L, beforeFp = "fp0", afterFp = "fp1", prevHash = null)
        val second =
            event(
                revision = 1L to 2L,
                beforeFp = "fp1",
                afterFp = "fp2",
                prevHash = first.eventHash,
            )
        assertEquals(
            emptyList(),
            EquipmentInstanceInvariants.eventChainViolations(listOf(first, second)),
        )
    }

    @Test
    fun `a revision gap in the event chain is flagged`() {
        val first = event(revision = 0L to 1L, beforeFp = "fp0", afterFp = "fp1", prevHash = null)
        val second =
            event(
                revision = 5L to 6L,
                beforeFp = "fp1",
                afterFp = "fp2",
                prevHash = first.eventHash,
            )
        val violations = EquipmentInstanceInvariants.eventChainViolations(listOf(first, second))
        assertTrue(violations.any { it.endsWith("revision-gap") })
    }

    @Test
    fun `a broken fingerprint link is flagged`() {
        val first = event(revision = 0L to 1L, beforeFp = "fp0", afterFp = "fp1", prevHash = null)
        val second =
            event(
                revision = 1L to 2L,
                beforeFp = "different",
                afterFp = "fp2",
                prevHash = first.eventHash,
            )
        val violations = EquipmentInstanceInvariants.eventChainViolations(listOf(first, second))
        assertTrue(violations.any { it.endsWith("fingerprint-link-broken") })
    }

    @Test
    fun `a tampered event hash is flagged`() {
        val first = event(revision = 0L to 1L, beforeFp = "fp0", afterFp = "fp1", prevHash = null)
        val tampered = first.copy(payload = "edited-after-the-fact")
        val violations = EquipmentInstanceInvariants.eventChainViolations(listOf(tampered))
        assertTrue(violations.any { it.endsWith("hash-mismatch") })
    }

    @Test
    fun `a broken hash chain link is flagged`() {
        val first = event(revision = 0L to 1L, beforeFp = "fp0", afterFp = "fp1", prevHash = null)
        val second =
            event(
                revision = 1L to 2L,
                beforeFp = "fp1",
                afterFp = "fp2",
                prevHash = "not-the-previous-hash",
            )
        val violations = EquipmentInstanceInvariants.eventChainViolations(listOf(first, second))
        assertTrue(violations.any { it.endsWith("hash-chain-broken") })
    }

    private fun event(
        revision: Pair<Long, Long>,
        beforeFp: String,
        afterFp: String,
        prevHash: String?,
    ): ItemMutationEvent {
        val unsigned =
            ItemMutationEvent(
                eventId = UUID.randomUUID(),
                instanceId = 42L,
                operation = ItemMutationOperation.REFORGED,
                actor = ItemActor(ItemActorType.PLAYER, 7L),
                source = "test",
                beforeRevision = revision.first,
                afterRevision = revision.second,
                beforeFingerprint = beforeFp,
                afterFingerprint = afterFp,
                payload = "",
                previousEventHash = prevHash,
                eventHash = "",
                createdAtEpochMillis = 1_700_000_000_000,
            )
        return unsigned.copy(eventHash = ItemEventHasher.hash(unsigned))
    }

    private fun validInstance(): EquipmentInstance =
        EquipmentInstance(
            instanceId = 42L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Uncommon,
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
                    )
                ),
            sockets = emptyList(),
            uniqueEffectIds = emptyList(),
            source = "test",
            instanceUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        )
}
