package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.service.ResponseDbGatewayService

/**
 * Synchronous validation contract of the mutation gateway ([EquipmentInstanceMutationService.
 * prepare]): optimistic locking, lifecycle-state gating, identity sealing, fingerprint stamping and
 * invariant enforcement - the parts that must reject a mutation *before* any persistence is
 * attempted. The async persist path is covered end-to-end by
 * [EquipmentInstanceMutationGatewayTest].
 */
class EquipmentInstanceMutationServiceTest {
    private val service =
        EquipmentInstanceMutationService(
            db = GameDbManager(ResponseDbGatewayService(neverUsedDatabase)),
            repository = EquipmentInstanceRepository(),
            registry = EquipmentInstanceRegistry(),
        )

    @Test
    fun `a valid transform produces a bumped revision and fresh fingerprint`() {
        val current = testInstance()
        val prepared =
            service.prepare(current, request(expectedRevision = 0L)) { it.copy(quality = 90) }
        val ok = assertIs<EquipmentInstanceMutationService.Prepared.Ok>(prepared)
        assertEquals(current.revision + 1, ok.updated.revision)
        assertEquals(90, ok.updated.quality)
        assertTrue(EquipmentInstanceFingerprint.verify(ok.updated))
    }

    @Test
    fun `a stale expected revision is rejected as a conflict`() {
        val current = testInstance().copy(revision = 3)
        val prepared =
            service.prepare(current, request(expectedRevision = 2L)) { it.copy(quality = 90) }
        val failed = assertIs<EquipmentInstanceMutationService.Prepared.Failed>(prepared)
        val conflict = assertIs<ItemMutationResult.RevisionConflict>(failed.result)
        assertEquals(3L, conflict.current.revision)
    }

    @Test
    fun `a null expected revision skips the optimistic-lock check`() {
        val current = testInstance().copy(revision = 9)
        val prepared =
            service.prepare(current, request(expectedRevision = null)) { it.copy(quality = 90) }
        assertIs<EquipmentInstanceMutationService.Prepared.Ok>(prepared)
    }

    @Test
    fun `mutations against a non-active state are rejected`() {
        for (state in
            listOf(
                EquipmentInstanceState.IN_TRADE,
                EquipmentInstanceState.IN_UPGRADE,
                EquipmentInstanceState.DESTROYED,
                EquipmentInstanceState.CORRUPTED,
                EquipmentInstanceState.RETIRED,
            )) {
            val current = testInstance().copy(state = state)
            val prepared =
                service.prepare(current, request(expectedRevision = null)) { it.copy(quality = 90) }
            val failed = assertIs<EquipmentInstanceMutationService.Prepared.Failed>(prepared)
            val error = assertIs<ItemMutationResult.ValidationError>(failed.result)
            assertEquals("invalid-state", error.code, "state=$state")
        }
    }

    @Test
    fun `state-bypass operations run even on destroyed items`() {
        val current =
            testInstance()
                .copy(state = EquipmentInstanceState.DESTROYED, binding = ItemBinding.DESTROYED)
        val prepared =
            service.prepare(
                current,
                request(expectedRevision = null, operation = ItemMutationOperation.RESTORED),
            ) {
                it.copy(state = EquipmentInstanceState.ACTIVE, binding = ItemBinding.UNBOUND)
            }
        assertIs<EquipmentInstanceMutationService.Prepared.Ok>(prepared)
    }

    @Test
    fun `identity fields cannot be changed by a mutation`() {
        val current = testInstance()
        for (tamper in
            listOf<(EquipmentInstance) -> EquipmentInstance>(
                { it.copy(instanceId = it.instanceId + 1) },
                { it.copy(templateObj = it.templateObj + 1) },
                { it.copy(rollSeed = it.rollSeed + 1) },
                { it.copy(instanceUuid = "11111111-2222-3333-4444-555555555555") },
            )) {
            val prepared = service.prepare(current, request(expectedRevision = null), tamper)
            val failed = assertIs<EquipmentInstanceMutationService.Prepared.Failed>(prepared)
            val error = assertIs<ItemMutationResult.ValidationError>(failed.result)
            assertTrue(error.code.startsWith("identity-"), "unexpected code: ${error.code}")
        }
    }

    @Test
    fun `a transform exception surfaces as a structured validation error`() {
        val current = testInstance()
        val prepared =
            service.prepare(current, request(expectedRevision = null)) {
                require(false) { "no-reforges-today" }
                it
            }
        val failed = assertIs<EquipmentInstanceMutationService.Prepared.Failed>(prepared)
        val error = assertIs<ItemMutationResult.ValidationError>(failed.result)
        assertEquals("no-reforges-today", error.code)
    }

    @Test
    fun `a transform that breaks invariants is rejected`() {
        val current = testInstance()
        val prepared =
            service.prepare(current, request(expectedRevision = null)) {
                it.copy(evolutionStage = 3, evolutionBranch = "not-a-branch")
            }
        val failed = assertIs<EquipmentInstanceMutationService.Prepared.Failed>(prepared)
        val error = assertIs<ItemMutationResult.ValidationError>(failed.result)
        assertEquals("invariant-violation", error.code)
        assertTrue("evolution-branch-unknown" in error.details)
    }

    private fun request(
        expectedRevision: Long?,
        operation: ItemMutationOperation = ItemMutationOperation.REFORGED,
        key: String = "test-key",
    ) =
        ItemMutationRequest(
            idempotencyKey = key,
            expectedRevision = expectedRevision,
            actor = ItemActor(ItemActorType.PLAYER, 7L),
            operation = operation,
            source = "test",
        )

    private fun testInstance(): EquipmentInstance =
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

    private companion object {
        private val neverUsedDatabase =
            object : Database {
                override suspend fun <T> withTransaction(block: (DatabaseConnection) -> T): T =
                    error("prepare-only tests never touch the database")
            }
    }
}
