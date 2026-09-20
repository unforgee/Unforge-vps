package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.game.type.obj.WeaponCategory

public class CompanionServiceTest {
    @Test
    public fun `combat levels follow the selected attack style`() {
        fun companion(style: CompanionAttackStyle) =
            Companion(1L, 42L, 1, "Test", CompanionClass.DPS, 1000, level = 50, attackStyle = style)

        assertEquals(50, companion(CompanionAttackStyle.MELEE).combatLevels().attack)
        assertEquals(50, companion(CompanionAttackStyle.MELEE).combatLevels().strength)
        assertEquals(45, companion(CompanionAttackStyle.MELEE).combatLevels().defence)

        assertEquals(50, companion(CompanionAttackStyle.RANGED).combatLevels().ranged)
        assertEquals(1, companion(CompanionAttackStyle.RANGED).combatLevels().strength)

        assertEquals(50, companion(CompanionAttackStyle.MAGIC).combatLevels().magic)
        assertEquals(1, companion(CompanionAttackStyle.MAGIC).combatLevels().attack)
    }

    @Test
    public fun `companion roles and attack style resolve to existing hitmark groups`() {
        fun companion(
            role: CompanionClass,
            style: CompanionAttackStyle = CompanionAttackStyle.MELEE,
        ) = Companion(1L, 42L, 1, "Test", role, 1000, attackStyle = style)

        assertEquals(
            CompanionHitmarkStyle.TANK,
            CompanionHitmarkResolver.styleForCompanion(companion(CompanionClass.TANK)),
        )
        assertEquals(
            CompanionHitmarkStyle.SUPPORT,
            CompanionHitmarkResolver.styleForCompanion(companion(CompanionClass.SUPPORT)),
        )
        assertEquals(
            CompanionHitmarkStyle.DAMAGE,
            CompanionHitmarkResolver.styleForCompanion(companion(CompanionClass.DPS)),
        )
        assertEquals(
            CompanionHitmarkStyle.MAGIC,
            CompanionHitmarkResolver.styleForCompanion(
                companion(CompanionClass.DPS, CompanionAttackStyle.MAGIC)
            ),
        )
    }

    @Test
    public fun `ability hitmarks preserve heal and damage semantics including legacy fallback`() {
        val dps = Companion(1L, 42L, 1, "Test", CompanionClass.DPS, 1000)
        assertEquals(
            CompanionHitmarkStyle.SUPPORT,
            CompanionHitmarkResolver.styleForAbility(dps, "mending-wave"),
        )
        assertEquals(
            CompanionHitmarkStyle.DAMAGE,
            CompanionHitmarkResolver.styleForAbility(dps, "focused-volley"),
        )
        assertEquals(
            CompanionHitmarkStyle.MAGIC,
            CompanionHitmarkResolver.styleForAbility(dps, "death-mark"),
        )
        assertEquals(
            CompanionHitmarkStyle.BOSS,
            CompanionHitmarkResolver.styleForAbility(dps, "perfect-execution"),
        )
        assertEquals(
            CompanionHitmarkStyle.BOSS,
            CompanionHitmarkResolver.styleForAbility(
                Companion(2L, 42L, 2, "Tank", CompanionClass.TANK, 1001),
                "iron-bastion",
            ),
        )
        assertEquals(
            CompanionHitmarkStyle.DAMAGE,
            CompanionHitmarkResolver.styleForAbility(dps, "shadow-step"),
        )
        assertEquals(
            CompanionHitmarkStyle.SUPPORT,
            CompanionHitmarkResolver.styleForAbility(
                Companion(3L, 42L, 3, "Support", CompanionClass.SUPPORT, 1002),
                "rallying-call",
            ),
        )
        assertEquals(
            CompanionHitmarkStyle.DAMAGE,
            CompanionHitmarkResolver.styleForAbility(dps, "legacy-ability"),
        )
    }

    @Test
    public fun `combat modes gate targets and aggressive uses deterministic 25x25 bounds`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        assertNull(
            service.tick(
                42,
                c.id,
                CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100),
                listOf(CompanionTarget(1, false, true, true, 2, 112, 100, 0)),
            )
        )
        service.setCombatMode(42, c.id, CompanionCombatMode.AGGRESSIVE)
        val action =
            service.tick(
                42,
                c.id,
                CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100),
                listOf(
                    CompanionTarget(2, false, true, true, 2, 112, 100, 0),
                    CompanionTarget(1, false, true, true, 2, 101, 100, 0),
                ),
            )!!
        assertEquals(listOf(1), action.targetIds)
        service.setCombatMode(42, c.id, CompanionCombatMode.PASSIVE)
        assertNull(
            service.tick(
                42,
                c.id,
                CompanionCombatContext(
                    true,
                    true,
                    true,
                    false,
                    CompanionTarget(1, false, true, true, 2),
                ),
                emptyList(),
            )
        )
    }

    @Test
    public fun `companion never engages npcs that are not attackable`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val c = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, c.id)
        val ctx = CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100)

        service.setCombatMode(42, c.id, CompanionCombatMode.AGGRESSIVE)
        // The nearest candidate is a non-attackable npc (e.g. a shopkeeper) - it must be
        // skipped in favour of the next attackable one.
        val action =
            service.tick(
                42,
                c.id,
                ctx,
                listOf(
                    CompanionTarget(1, false, true, true, 1, 101, 100, 0, attackable = false),
                    CompanionTarget(2, false, true, true, 3, 103, 100, 0, attackable = true),
                ),
            )!!
        assertEquals(listOf(2), action.targetIds)

        // No attackable candidates at all -> no engagement.
        assertNull(
            service.tick(
                42,
                c.id,
                ctx,
                listOf(CompanionTarget(1, false, true, true, 1, 101, 100, 0, attackable = false)),
            )
        )

        // A non-attackable owner target is rejected in DEFENSIVE as well.
        service.setCombatMode(42, c.id, CompanionCombatMode.DEFENSIVE)
        assertNull(
            service.tick(
                42,
                c.id,
                CompanionCombatContext(
                    true,
                    true,
                    true,
                    false,
                    CompanionTarget(1, false, true, true, 1, attackable = false),
                    ownerX = 100,
                    ownerY = 100,
                ),
                emptyList(),
            )
        )

        // Nearby non-attackable npcs are never pulled in as secondary defensive targets.
        val defensiveAction =
            service.tick(
                42,
                c.id,
                CompanionCombatContext(
                    true,
                    true,
                    true,
                    false,
                    CompanionTarget(1, false, true, true, 1, attackable = true),
                    ownerX = 100,
                    ownerY = 100,
                ),
                listOf(CompanionTarget(2, false, true, true, 1, 101, 100, 0, attackable = false)),
            )!!
        assertEquals(listOf(1), defensiveAction.targetIds)
    }

    @Test
    public fun `progression matches design checkpoints`() {
        assertEquals(3, CompanionRules.talentPointsForLevel(1))
        assertEquals(13, CompanionRules.talentPointsForLevel(50))
        assertEquals(30, CompanionRules.talentPointsForLevel(100))
        assertEquals(50, CompanionRules.talentPointsForLevel(120))
        assertEquals(80, CompanionRules.talentPointsForLevel(150))
    }

    @Test
    public fun `expanded talent catalog has three branches and ability references`() {
        assertEquals(72, CompanionTalentCatalog.definitions.size)
        assertEquals(
            24,
            CompanionTalentCatalog.definitions.count { it.companionClass == CompanionClass.TANK },
        )
        assertEquals(
            24,
            CompanionTalentCatalog.definitions.count { it.companionClass == CompanionClass.SUPPORT },
        )
        assertEquals(
            24,
            CompanionTalentCatalog.definitions.count { it.companionClass == CompanionClass.DPS },
        )
        CompanionAbilityCatalog.validateTalentReferences()
        CompanionAbilityEffects.validateCatalog()
        assertEquals(
            CompanionAbilityEffectType.HEAL,
            CompanionAbilityEffects.effect("mending-wave").type,
        )
        assertEquals(
            CompanionAbilityEffectType.DAMAGE,
            CompanionAbilityEffects.effect("focused-volley").type,
        )
        CompanionAbilityCatalog.definitions
            .filter { it.active }
            .forEach { definition ->
                assertEquals(
                    definition.target,
                    CompanionAbilityEffects.effect(definition.id).target,
                )
            }
    }

    @Test
    public fun `active ability requires unlocked talent and observes cooldown`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        service.activate(42, companion.id)
        assertThrows(IllegalStateException::class.java) {
            service.useAbility(42, companion.id, "focused-volley", nowEpochMillis = 1_000L)
        }
        unlockTalent(service, companion.id, "shadow-step")
        service.setAbilityLoadout(42, companion.id, listOf("shadow-step"))
        assertThrows(IllegalArgumentException::class.java) {
            service.useAbility(42, companion.id, "shadow-step", emptyList(), 1_000L)
        }
        val used = service.useAbility(42, companion.id, "shadow-step", listOf(7), 1_000L)
        assertEquals("shadow-step", used.ability.id)
        assertEquals(1, used.rank)
        assertThrows(IllegalArgumentException::class.java) {
            service.useAbility(42, companion.id, "shadow-step", listOf(7), 1_001L)
        }
        assertTrue(service.abilityCooldownRemainingMillis(companion.id, "shadow-step", 1_001L) > 0)
    }

    @Test
    public fun `timed ability effects are server-owned and expire`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        service.activate(42, companion.id)
        service.addExperience(42, companion.id, 1_000_000L)
        unlockTalent(service, companion.id, "iron-bastion")
        service.setAbilityLoadout(42, companion.id, listOf("iron-bastion"))
        service.useAbility(42, companion.id, "iron-bastion", nowEpochMillis = 1_000L)
        assertEquals(1, service.activeAbilityEffects(companion.id, 1_001L).size)
        assertEquals(0.65, service.incomingDamageMultiplier(companion.id, 1_001L), 0.0001)
        assertEquals(0, service.activeAbilityEffects(companion.id, 61_001L).size)
        assertEquals(1.0, service.incomingDamageMultiplier(companion.id, 61_001L), 0.0001)
    }

    @Test
    public fun `ability loadout only accepts unlocked active abilities`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 100)
        assertThrows(IllegalArgumentException::class.java) {
            service.setAbilityLoadout(42, companion.id, listOf("shadow-step"))
        }
        unlockTalent(service, companion.id, "shadow-step")
        val updated = service.setAbilityLoadout(42, companion.id, listOf("shadow-step"))
        assertEquals(listOf("shadow-step"), updated.abilityLoadout)
        assertThrows(IllegalArgumentException::class.java) {
            service.setAbilityLoadout(42, companion.id, listOf("shadow-step", "shadow-step"))
        }
    }

    @Test
    public fun `recruitment enforces ordered slots and up to four active companions`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val first = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 250)
        assertThrows(IllegalArgumentException::class.java) {
            service.recruit(42, 3, "Missing", CompanionClass.DPS, 1001, 200)
        }
        val second = service.recruit(42, 2, "Mender", CompanionClass.SUPPORT, 1001, 200)
        service.activate(42, first.id)
        service.activate(42, second.id)
        assertTrue(service.owned(42).first { it.id == first.id }.active)
        assertTrue(service.owned(42).first { it.id == second.id }.active)
    }

    private fun unlockTalent(service: CompanionService, companionId: Long, talentId: String) {
        service.addExperience(42L, companionId, 100_000L)
        if (talentId == "shadow-step") {
            repeat(5) { service.allocateTalent(42L, companionId, "opportunist") }
            repeat(5) { service.allocateTalent(42L, companionId, "weapon-mastery") }
            service.allocateTalent(42L, companionId, talentId)
            return
        }
        val definition = CompanionTalentCatalog.definition(talentId)
        definition.prerequisites.forEach { prerequisite ->
            unlockTalent(service, companionId, prerequisite)
        }

        val ownerId = 42L
        while (spentTalentPoints(service, ownerId, companionId) < tierMinimum(definition.tier)) {
            val current = service.owned(ownerId).first { it.id == companionId }
            val unlocked = current.talents.mapTo(mutableSetOf()) { it.definitionId }
            val ranksByTalent = current.talents.associate { it.definitionId to it.ranks }
            val highestUnlockedTier = spentTalentPoints(service, ownerId, companionId) / 5 + 1
            val filler =
                CompanionTalentCatalog.definitions.firstOrNull { candidate ->
                    candidate.companionClass == current.companionClass &&
                        candidate.id != talentId &&
                        candidate.tier <= highestUnlockedTier &&
                        candidate.prerequisites.all(unlocked::contains) &&
                        (ranksByTalent[candidate.id] ?: 0) < candidate.maxRanks
                }
                    ?: CompanionTalentCatalog.definitions.first { candidate ->
                        candidate.companionClass == current.companionClass &&
                            candidate.tier <= highestUnlockedTier &&
                            candidate.prerequisites.all(unlocked::contains) &&
                            (ranksByTalent[candidate.id] ?: 0) < candidate.maxRanks
                    }
            service.allocateTalent(ownerId, companionId, filler.id)
        }
        if (
            service
                .owned(ownerId)
                .first { it.id == companionId }
                .talents
                .none { it.definitionId == talentId }
        ) {
            service.allocateTalent(ownerId, companionId, talentId)
        }
    }

    private fun spentTalentPoints(
        service: CompanionService,
        ownerId: Long,
        companionId: Long,
    ): Int =
        service
            .owned(ownerId)
            .first { it.id == companionId }
            .talents
            .sumOf { talent ->
                CompanionTalentCatalog.definition(talent.definitionId).pointsPerRank * talent.ranks
            }

    private fun tierMinimum(tier: Int): Int = (tier - 1) * 5

    @Test
    public fun `combat is only selected from owners target`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val dps = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 200)
        service.activate(42, dps.id)
        val action =
            service.tick(
                42,
                dps.id,
                CompanionCombatContext(
                    true,
                    true,
                    true,
                    false,
                    CompanionTarget(9, false, true, true, 2),
                ),
                listOf(CompanionTarget(10, false, true, true, 3)),
            )!!
        assertEquals(listOf(9, 10), action.targetIds)
        assertNull(
            service.tick(
                42,
                dps.id,
                CompanionCombatContext(true, true, true, false, null),
                emptyList(),
            )
        )
    }

    @Test
    public fun `damage incapacitates and resummon requires cooldown`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val tank = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        service.activate(42, tank.id)
        val down = service.damage(42, tank.id, 100, 1_000L)
        assertEquals(CompanionState.INCAPACITATED, down.state)
        assertFalse(down.active)
        assertThrows(IllegalArgumentException::class.java) { service.resummon(42, tank.id, 1_000L) }
        assertEquals(CompanionState.FOLLOWING, service.resummon(42, tank.id, 301_000L).state)
        assertTrue(service.owned(42).single { it.id == tank.id }.active)
    }

    @Test
    public fun `death cooldown is exactly five minutes from the death time`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        val down = service.damage(42, companion.id, 100, 10_000L)
        assertEquals(310_000L, down.incapacitatedUntilEpochMillis)
        assertEquals(300_000L, service.cooldownRemainingMillis(down, 10_000L))
        assertEquals(60_000L, service.cooldownRemainingMillis(down, 250_000L))
        assertEquals(0L, service.cooldownRemainingMillis(down, 310_000L))
        assertEquals(0L, service.cooldownRemainingMillis(down, 999_999_999L))
        // Not-incapacitated companions never report a cooldown.
        assertEquals(0L, service.cooldownRemainingMillis(companion, 10_000L))
    }

    @Test
    public fun `duplicate death events never restart the cooldown`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        val first = service.damage(42, companion.id, 100, 1_000L)
        assertEquals(CompanionState.INCAPACITATED, first.state)
        // A second lethal hit arriving in the same tick must be a no-op.
        val second = service.damage(42, companion.id, 50, 2_000L)
        assertEquals(CompanionState.INCAPACITATED, second.state)
        assertEquals(first.incapacitatedUntilEpochMillis, second.incapacitatedUntilEpochMillis)
        assertEquals(0, second.hitpoints)
        assertFalse(second.active)
    }

    @Test
    public fun `activate is blocked during cooldown and auto-resummons after expiry`() {
        var now = 1_000L
        val service =
            CompanionService(
                NoopCompanionContractSource,
                NoopCompanionPersistence,
                clockMillis = { now },
            )
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        service.activate(42, companion.id)
        service.deactivate(42, companion.id)
        service.damage(42, companion.id, 100, now)

        // Still inside the 5-minute window: activation is rejected and the bot stays inactive.
        assertThrows(IllegalArgumentException::class.java) { service.activate(42, companion.id) }
        now += 299_999L
        assertThrows(IllegalArgumentException::class.java) { service.activate(42, companion.id) }

        // Once the cooldown expires, activation resummons at full hitpoints automatically.
        now += 1L
        val revived = service.activate(42, companion.id)
        assertEquals(CompanionState.FOLLOWING, revived.state)
        assertEquals(revived.maximumHitpoints, revived.hitpoints)
        assertNull(revived.incapacitatedUntilEpochMillis)
        assertTrue(revived.active)
    }

    @Test
    public fun `activateAll resummons only companions whose cooldown expired`() {
        var now = 1_000L
        val service =
            CompanionService(
                NoopCompanionContractSource,
                NoopCompanionPersistence,
                clockMillis = { now },
            )
        val first = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        val second = service.recruit(42, 2, "Mender", CompanionClass.SUPPORT, 1001, 100)
        service.damage(42, first.id, 100, now)
        service.damage(42, second.id, 50, now)

        now += 300_000L
        val actives = service.activateAll(42)
        val revivedFirst = service.owned(42).single { it.id == first.id }
        val aliveSecond = service.owned(42).single { it.id == second.id }
        assertTrue(revivedFirst.active)
        assertEquals(CompanionState.FOLLOWING, revivedFirst.state)
        assertEquals(revivedFirst.maximumHitpoints, revivedFirst.hitpoints)
        // The survivor kept its remaining hitpoints and was reactivated.
        assertTrue(aliveSecond.active)
        assertEquals(50, aliveSecond.hitpoints)
        assertTrue(actives.all { it.state != CompanionState.INCAPACITATED })
    }

    @Test
    public fun `revive clears death and cooldown without activating`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        service.damage(42, companion.id, 100, 1_000L)
        val revived = service.revive(42, companion.id)
        assertEquals(CompanionState.FOLLOWING, revived.state)
        assertEquals(revived.maximumHitpoints, revived.hitpoints)
        assertNull(revived.incapacitatedUntilEpochMillis)
        assertFalse(revived.active)
    }

    @Test
    public fun `talent tree gates by points and class`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val support = service.recruit(42, 1, "Mender", CompanionClass.SUPPORT, 1000, 100)
        assertThrows(IllegalArgumentException::class.java) {
            service.allocateTalent(42, support.id, "annihilation")
        }
        assertEquals(
            1,
            service.allocateTalent(42, support.id, "mending-light").talents.single().ranks,
        )
    }

    @Test
    public fun `restore keeps companion identity and gear`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val original = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        val restored = original.copy(id = original.id, gearInstanceIds = listOf(77L))
        service.restore(42, listOf(restored))
        assertEquals(original.id, service.owned(42).single().id)
        assertEquals(listOf(77L), service.owned(42).single().gearInstanceIds)
    }

    @Test
    public fun `frame layout stays within server limits`() {
        val store = CompanionFrameLayoutStore()
        val layout = store.set(42L, CompanionFrameLayout(120, 80, scalePercent = 150))
        assertEquals(layout, store.get(42L))
    }

    @Test
    public fun `recruited companion defaults to standard spellbook and no autocast`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Aegis", CompanionClass.TANK, 1000, 100)
        assertEquals(CompanionSpellbook.STANDARD, companion.spellbook)
        assertEquals(CompanionAttackStyle.MELEE, companion.attackStyle)
        assertEquals(0, companion.autocastSpellId)
    }

    @Test
    public fun `setSpellbook switches book and clears invalid autocast`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Sage", CompanionClass.DPS, 1000, 100)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        val withSpell =
            service.setAutocastSpell(42, companion.id, 15, CompanionSpellbook.STANDARD, 1)
        assertEquals(15, withSpell.autocastSpellId)
        // A standard spell is invalid on Ancients, so the switch clears it.
        val ancients =
            service.setSpellbook(
                42,
                companion.id,
                CompanionSpellbook.ANCIENTS,
                autocastStillValid = false,
            )
        assertEquals(CompanionSpellbook.ANCIENTS, ancients.spellbook)
        assertEquals(0, ancients.autocastSpellId)
        // A valid selection in the new book survives future switches while still valid.
        val ancientSpell =
            service.setAutocastSpell(42, companion.id, 40, CompanionSpellbook.ANCIENTS, 1)
        assertEquals(40, ancientSpell.autocastSpellId)
        val backToStandard =
            service.setSpellbook(
                42,
                companion.id,
                CompanionSpellbook.STANDARD,
                autocastStillValid = true,
            )
        assertEquals(40, backToStandard.autocastSpellId)
    }

    @Test
    public fun `autocast selection is level-gated and exact-level unlock works`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Sage", CompanionClass.DPS, 1000, 100)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        // Companion is level 1; a level-20 spell must be rejected server-side.
        assertThrows(IllegalArgumentException::class.java) {
            service.setAutocastSpell(42, companion.id, 15, CompanionSpellbook.STANDARD, 20)
        }
        // A level-1 spell at the exact companion level unlocks.
        val unlocked =
            service.setAutocastSpell(42, companion.id, 15, CompanionSpellbook.STANDARD, 1)
        assertEquals(15, unlocked.autocastSpellId)
    }

    @Test
    public fun `autocast selection rejects out-of-book spells`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Sage", CompanionClass.DPS, 1000, 100)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        // Companion is on STANDARD; an ANCIENTS spell must be rejected.
        assertThrows(IllegalArgumentException::class.java) {
            service.setAutocastSpell(42, companion.id, 40, CompanionSpellbook.ANCIENTS, 1)
        }
    }

    @Test
    public fun `autocast selection rejects arbitrary spell ids`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Sage", CompanionClass.DPS, 1000, 100)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        assertThrows(IllegalArgumentException::class.java) {
            service.setAutocastSpell(42, companion.id, 0, CompanionSpellbook.STANDARD, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.setAutocastSpell(42, companion.id, -7, CompanionSpellbook.STANDARD, 1)
        }
    }

    @Test
    public fun `clearAutocast removes selection`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Sage", CompanionClass.DPS, 1000, 100)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        service.setAutocastSpell(42, companion.id, 15, CompanionSpellbook.STANDARD, 1)
        val cleared = service.clearAutocast(42, companion.id)
        assertEquals(0, cleared.autocastSpellId)
        assertEquals(0, service.owned(42).single().autocastSpellId)
    }

    @Test
    public fun `pack capacity can be upgraded up to 100 slots`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Mule", CompanionClass.TANK, 1000, 100)
        assertEquals(10, companion.packCapacity)
        val upgraded = service.setPackCapacity(42, companion.id, 50)
        assertEquals(50, upgraded.packCapacity)
        assertEquals(50, service.owned(42).single().packCapacity)

        // Cannot downgrade
        assertThrows(IllegalArgumentException::class.java) {
            service.setPackCapacity(42, companion.id, 40)
        }

        // Cannot exceed max (100)
        assertThrows(IllegalArgumentException::class.java) {
            service.setPackCapacity(42, companion.id, 110)
        }

        val maxed = service.setPackCapacity(42, companion.id, 100)
        assertEquals(100, maxed.packCapacity)
    }

    @Test
    public fun `defensive engages the npc attacking the owner`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val dps = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 200)
        service.activate(42, dps.id)
        val attacker = CompanionTarget(7, false, true, true, 3, 101, 100, 0)
        val action =
            service.tick(
                42,
                dps.id,
                CompanionCombatContext(
                    true,
                    true,
                    true,
                    false,
                    null,
                    ownerUnderAttackTarget = attacker,
                    ownerX = 100,
                    ownerY = 100,
                ),
                emptyList(),
            )!!
        assertEquals(listOf(7), action.targetIds)
    }

    @Test
    public fun `defensive never starts combat on its own`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val dps = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 200)
        service.activate(42, dps.id)
        // A hostile npc right next to the owner is ignored while nobody fights the owner.
        assertNull(
            service.tick(
                42,
                dps.id,
                CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100),
                listOf(CompanionTarget(1, false, true, true, 1, 101, 100, 0)),
            )
        )
    }

    @Test
    public fun `aggressive picks deterministically and replaces dead targets`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val dps = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 200)
        service.activate(42, dps.id)
        service.setCombatMode(42, dps.id, CompanionCombatMode.AGGRESSIVE)
        val context =
            CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100)
        val targets =
            listOf(
                CompanionTarget(2, false, true, true, 2, 102, 100, 0),
                CompanionTarget(1, false, true, true, 2, 101, 100, 0),
            )
        // Identical input always yields the identical target - no per-tick target flipping.
        val first = service.tick(42, dps.id, context, targets)!!
        val second = service.tick(42, dps.id, context, targets)!!
        assertEquals(first.targetIds, second.targetIds)
        assertEquals(listOf(1), first.targetIds)
        // Once the target dies, the next best valid candidate replaces it.
        val deadFirst =
            listOf(
                CompanionTarget(1, false, false, true, 2, 101, 100, 0),
                CompanionTarget(2, false, true, true, 2, 102, 100, 0),
            )
        val replacement = service.tick(42, dps.id, context, deadFirst)!!
        assertEquals(listOf(2), replacement.targetIds)
        // NPCs outside the 25x25 search box are never picked.
        val far = listOf(CompanionTarget(3, false, true, true, 13, 113, 100, 0))
        assertNull(service.tick(42, dps.id, context, far))
    }

    @Test
    public fun `aggressive assists the owner's target instead of ignoring it`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val dps = service.recruit(42, 1, "Blade", CompanionClass.DPS, 1000, 200)
        service.activate(42, dps.id)
        service.setCombatMode(42, dps.id, CompanionCombatMode.AGGRESSIVE)
        // The owner's target is the only npc around (e.g. a solo-boss arena). Previously
        // aggressive mode ignored it entirely - the runtime excludes it from `nearbyTargets` -
        // so the companion dropped back to following instead of fighting.
        val ownerTarget = CompanionTarget(7, false, true, true, 3, 103, 100, 0)
        val context =
            CompanionCombatContext(true, true, true, false, ownerTarget, ownerX = 100, ownerY = 100)
        val action = service.tick(42, dps.id, context, emptyList())!!
        assertEquals(listOf(7), action.targetIds)
        // The owner's target takes priority over other nearby npcs so companions assist the
        // owner's fight.
        val nearby = listOf(CompanionTarget(1, false, true, true, 1, 101, 100, 0))
        val assisted = service.tick(42, dps.id, context, nearby)!!
        assertEquals(listOf(7), assisted.targetIds)
        // An out-of-range owner target is skipped and the companion picks the nearest valid
        // npc instead.
        val farContext =
            CompanionCombatContext(
                true,
                true,
                true,
                false,
                CompanionTarget(7, false, true, true, 9, 109, 100, 0),
                ownerX = 100,
                ownerY = 100,
            )
        val fallback = service.tick(42, dps.id, farContext, nearby)!!
        assertEquals(listOf(1), fallback.targetIds)
        // A dead owner target is never engaged.
        val deadContext =
            CompanionCombatContext(
                true,
                true,
                true,
                false,
                CompanionTarget(7, false, false, true, 3, 103, 100, 0),
                ownerX = 100,
                ownerY = 100,
            )
        assertNull(service.tick(42, dps.id, deadContext, emptyList()))
    }

    @Test
    public fun `support heal restores hitpoints and skips dead companions`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val mender = service.recruit(42, 1, "Mender", CompanionClass.SUPPORT, 1000, 100)
        service.activate(42, mender.id)
        val hurt = service.damage(42, mender.id, 40, 1_000L)
        assertEquals(60, hurt.hitpoints)
        val healed = service.heal(42, mender.id, 25)
        assertEquals(85, healed.hitpoints)
        // Capped at the companion's maximum hitpoints.
        assertEquals(100, service.heal(42, mender.id, 999).hitpoints)
        // Incapacitated companions are never healed - they recover through resummon only.
        val down = service.damage(42, mender.id, 100, 2_000L)
        assertEquals(CompanionState.INCAPACITATED, down.state)
        assertEquals(0, service.heal(42, mender.id, 50).hitpoints)
    }

    @Test
    public fun `combat mode spellbook autocast and style survive a full reload`() {
        val store = MemoryPersistence()
        val service = CompanionService(NoopCompanionContractSource, store)
        val companion = service.recruit(42, 1, "Mage", CompanionClass.DPS, 1000, 100)
        service.setCombatMode(42, companion.id, CompanionCombatMode.AGGRESSIVE)
        service.setSpellbook(42, companion.id, CompanionSpellbook.ANCIENTS, false)
        service.setAttackStyle(42, companion.id, CompanionAttackStyle.MAGIC)
        service.setAutocastSpell(42, companion.id, 40, CompanionSpellbook.ANCIENTS, 1)

        // Simulate a logout/login (or restart): a fresh service hydrating from persistence.
        val reloaded = CompanionService(NoopCompanionContractSource, store)
        reloaded.load(42)
        val restored = reloaded.owned(42).single { it.id == companion.id }
        assertEquals(CompanionCombatMode.AGGRESSIVE, restored.combatMode)
        assertEquals(CompanionSpellbook.ANCIENTS, restored.spellbook)
        assertEquals(CompanionAttackStyle.MAGIC, restored.attackStyle)
        assertEquals(40, restored.autocastSpellId)
    }

    @Test
    public fun `weapon categories map to the expected combat pipeline`() {
        assertEquals(CompanionAttackStyle.MAGIC, companionWeaponStyle(WeaponCategory.Staff.id))
        assertEquals(
            CompanionAttackStyle.MAGIC,
            companionWeaponStyle(WeaponCategory.PoweredStaff.id),
        )
        assertEquals(
            CompanionAttackStyle.MAGIC,
            companionWeaponStyle(WeaponCategory.BladedStaff.id),
        )
        assertEquals(CompanionAttackStyle.RANGED, companionWeaponStyle(WeaponCategory.Bow.id))
        assertEquals(CompanionAttackStyle.RANGED, companionWeaponStyle(WeaponCategory.Crossbow.id))
        assertEquals(CompanionAttackStyle.MELEE, companionWeaponStyle(WeaponCategory.Whip.id))
        assertEquals(CompanionAttackStyle.MELEE, companionWeaponStyle(WeaponCategory.Unarmed.id))
        assertTrue(isMagicWeapon(WeaponCategory.PoweredStaff.id))
        assertFalse(isMagicWeapon(WeaponCategory.Bow.id))
    }

    @Test
    public fun `pack capacity survives a full reload`() {
        // Simulates logout/login (or a restart): a fresh service hydrating from persistence
        // must report the upgraded capacity - the record is authoritative, never the client.
        val store = MemoryPersistence()
        val service = CompanionService(NoopCompanionContractSource, store)
        val companion = service.recruit(42, 1, "Porter", CompanionClass.DPS, 1000, 100)
        assertEquals(CompanionRules.MIN_PACK_CAPACITY, companion.packCapacity)
        service.setPackCapacity(42, companion.id, 60)

        val reloaded = CompanionService(NoopCompanionContractSource, store)
        reloaded.load(42)
        val restored = reloaded.owned(42).single { it.id == companion.id }
        assertEquals(60, restored.packCapacity)
    }

    @Test
    public fun `pack upgrade preserves every other companion field`() {
        // An upgrade mutates only `packCapacity` - gear, spells, mode and vitals are untouched,
        // and the pack inventory itself is never part of the companion record.
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Porter", CompanionClass.DPS, 1000, 100)
        service.setCombatMode(42, companion.id, CompanionCombatMode.AGGRESSIVE)
        service.setSpellbook(42, companion.id, CompanionSpellbook.ANCIENTS, false)
        val before = service.owned(42).single { it.id == companion.id }

        val upgraded = service.setPackCapacity(42, companion.id, 20)
        assertEquals(before.copy(packCapacity = 20), upgraded)
    }

    @Test
    public fun `evolution requires the level gate and grants stage rewards`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val companion = service.recruit(42, 1, "Pup", CompanionClass.DPS, 1000, 100)

        // Level 1 cannot evolve past the stage-1 gate of 25.
        assertThrows(IllegalArgumentException::class.java) { service.evolve(42, companion.id) }

        // 30,000 xp = exactly level 25 on the shared 100*level curve.
        val grown = service.addExperience(42, companion.id, 30_000L)
        assertEquals(25, grown.level)

        val evolved = service.evolve(42, companion.id)
        assertEquals(1, evolved.evolutionStage)
        assertEquals(grown.talentPoints + 1, evolved.talentPoints)

        // The stage feeds the canonical stat pipeline.
        val sheet =
            CompanionStatCalculator.calculate(evolved, EquipmentInstanceRegistry(), emptyMap())
        assertEquals(500, sheet.value(CompanionStat.DAMAGE_BPS) - 10_000 - (25 - 1) * 100)

        // Reaching the final stage makes evolve impossible rather than a silent no-op.
        val ascendant = evolved.copy(level = 75, evolutionStage = 3)
        service.restore(42, listOf(ascendant))
        assertThrows(IllegalStateException::class.java) { service.evolve(42, companion.id) }
    }

    @Test
    public fun `old saves without an evolution stage load at stage zero`() {
        val service = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        val legacy = Companion(1L, 42L, 1, "Old", CompanionClass.TANK, 1000)
        service.restore(42, listOf(legacy))
        val loaded = service.owned(42).single()
        assertEquals(0, loaded.evolutionStage)
        assertEquals(
            CompanionEvolutionCatalog.stages.first(),
            CompanionEvolutionCatalog.nextStage(loaded.evolutionStage),
        )
    }

    private class MemoryPersistence : CompanionPersistence {
        private val saved = LinkedHashMap<Long, Companion>()

        override fun save(companion: Companion) {
            saved[companion.id] = companion
        }

        override fun load(ownerCharacterId: Long): List<Companion> =
            saved.values.filter { it.ownerCharacterId == ownerCharacterId }.sortedBy { it.slot }
    }
}
