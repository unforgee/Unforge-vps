package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val OWNER: Long = 42L

public class CompanionSkillServiceTest {
    private var now: Long = 0L

    private fun newServices(): Pair<CompanionService, CompanionSkillService> {
        val companions = CompanionService(NoopCompanionContractSource, NoopCompanionPersistence)
        return companions to CompanionSkillService(companions) { now }
    }

    private fun CompanionService.fightingCompanion(owner: Long = OWNER): Companion {
        val companion = recruit(owner, 1, "Tank", CompanionClass.TANK, 1000, 100)
        activate(owner, companion.id)
        setCombatMode(owner, companion.id, CompanionCombatMode.AGGRESSIVE)
        tick(
            owner,
            companion.id,
            CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100),
            listOf(CompanionTarget(1, false, true, true, 2, 101, 100, 0)),
        )
        return owned(owner).first { it.id == companion.id }
    }

    @Test
    public fun `skill levels start at 1 and follow the shared xp curve up to 99`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()
        assertEquals(1, skills.levelOf(companion.id, CompanionSkill.SUPPORT))
        assertEquals(1, skills.levelOf(companion.id, CompanionSkill.TANK))

        // 100 xp = level 2, matching CompanionRules.experienceToNextLevel(1).
        skills.awardEffectiveHealing(OWNER, companion.id, healedHitpoints = 50)
        assertEquals(100L, skills.experienceOf(companion.id, CompanionSkill.SUPPORT))
        assertEquals(2, skills.levelOf(companion.id, CompanionSkill.SUPPORT))

        // Tracks are independent: support xp must not move the tank track.
        assertEquals(0L, skills.experienceOf(companion.id, CompanionSkill.TANK))
        assertEquals(1, skills.levelOf(companion.id, CompanionSkill.TANK))
    }

    @Test
    public fun `skill levels cap at 99 and stored experience is bounded`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()
        repeat(2_000) { i ->
            now = i * 10_000L
            skills.awardProtection(OWNER, companion.id, preventedDamage = 200)
        }
        assertEquals(99, skills.levelOf(companion.id, CompanionSkill.TANK))
        assertTrue(
            skills.experienceOf(companion.id, CompanionSkill.TANK) <=
                CompanionSkillXp.MAX_TRACK_EXPERIENCE
        )
    }

    @Test
    public fun `overheal and idle healing grant no support xp`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        // Zero/negative effective healing is overheal - never pays out, even in combat.
        assertEquals(0L, skills.awardEffectiveHealing(OWNER, companion.id, healedHitpoints = 0))

        // A companion that was never in combat cannot farm heals while idle.
        val idle = companions.recruit(OWNER, 2, "Medic", CompanionClass.SUPPORT, 1001, 100)
        companions.activate(OWNER, idle.id)
        assertEquals(0L, skills.awardEffectiveHealing(OWNER, idle.id, healedHitpoints = 20))

        // The fighting companion heals for real: effective hp restored pays out.
        assertEquals(40L, skills.awardEffectiveHealing(OWNER, companion.id, healedHitpoints = 20))
    }

    @Test
    public fun `support heals still pay inside the post-combat grace window`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()
        // Drive the companion back to FOLLOWING: the fight is over but grace must still pay.
        now = 5_000L
        companions.tick(
            OWNER,
            companion.id,
            CompanionCombatContext(true, true, true, false, null, ownerX = 100, ownerY = 100),
            emptyList(),
        )
        skills.noteCombatActivity(companion.id)

        // Fight ended (state FOLLOWING) but the grace window is still open.
        now += CompanionSkillXp.COMBAT_GRACE_MILLIS - 1_000L
        assertTrue(skills.awardEffectiveHealing(OWNER, companion.id, 10) > 0)

        // Long after combat, idle healing pays nothing again.
        now += CompanionSkillXp.COMBAT_GRACE_MILLIS + 1_000L
        assertEquals(0L, skills.awardEffectiveHealing(OWNER, companion.id, 10))
    }

    @Test
    public fun `buff xp requires a real application and cleanses require real removals`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        // A refresh of an unchanged buff is not a new application.
        assertEquals(0L, skills.awardBuff(OWNER, companion.id, appliedNewEffect = false))
        assertEquals(
            CompanionSkillXp.BUFF_XP,
            skills.awardBuff(OWNER, companion.id, appliedNewEffect = true),
        )
        // The same logical cast reported twice in one tick pays out once.
        assertEquals(0L, skills.awardBuff(OWNER, companion.id, appliedNewEffect = true))

        // A cleanse that removed nothing earns nothing; real removals scale.
        assertEquals(0L, skills.awardCleanse(OWNER, companion.id, effectsRemoved = 0))
        assertEquals(40L, skills.awardCleanse(OWNER, companion.id, effectsRemoved = 2))
    }

    @Test
    public fun `tanked damage requires combat and a hostile source`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        // Self-inflicted or allied damage never counts as tanking.
        assertEquals(
            0L,
            skills.awardDamageTankled(OWNER, companion.id, 30, fromHostileSource = false),
        )

        // Real hostile damage while fighting pays out.
        assertEquals(30L, skills.awardDamageTankled(OWNER, companion.id, 30))

        // Per-event cap keeps single huge hits bounded.
        assertEquals(
            CompanionSkillXp.TANKED_XP_CAP_PER_EVENT,
            skills.awardDamageTankled(OWNER, companion.id, 999),
        )

        // An idle companion soaking ambient damage earns nothing (AFK protection).
        val parked = companions.recruit(OWNER, 2, "Sponge", CompanionClass.TANK, 1001, 100)
        companions.activate(OWNER, parked.id)
        assertEquals(0L, skills.awardDamageTankled(OWNER, parked.id, 30))
    }

    @Test
    public fun `taunt and aggro hold are throttled flat awards`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        assertEquals(CompanionSkillXp.TAUNT_XP, skills.awardTaunt(OWNER, companion.id))
        assertEquals(0L, skills.awardTaunt(OWNER, companion.id))
        now += CompanionSkillXp.TAUNT_MIN_INTERVAL_MILLIS
        assertEquals(CompanionSkillXp.TAUNT_XP, skills.awardTaunt(OWNER, companion.id))

        // Aggro-hold reports fire every cycle but only pay once per interval.
        assertEquals(CompanionSkillXp.AGGRO_HOLD_XP, skills.awardAggroHeld(OWNER, companion.id))
        assertEquals(0L, skills.awardAggroHeld(OWNER, companion.id))
        now += CompanionSkillXp.AGGRO_HOLD_INTERVAL_MILLIS
        assertEquals(CompanionSkillXp.AGGRO_HOLD_XP, skills.awardAggroHeld(OWNER, companion.id))
    }

    @Test
    public fun `protection pays for damage actually prevented`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()
        assertEquals(0L, skills.awardProtection(OWNER, companion.id, preventedDamage = 0))
        assertEquals(200L, skills.awardProtection(OWNER, companion.id, preventedDamage = 100))
        assertEquals(
            CompanionSkillXp.PROTECTION_XP_CAP_PER_EVENT,
            skills.awardProtection(OWNER, companion.id, preventedDamage = 300),
        )
    }

    @Test
    public fun `skill experience survives a restore round trip`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()
        skills.awardEffectiveHealing(OWNER, companion.id, 25)
        skills.awardTaunt(OWNER, companion.id)
        val snapshot = mapOf(companion.id to skills.experienceMap(companion.id))

        val (companions2, skills2) = newServices()
        companions2.restore(OWNER, companions.owned(OWNER))
        skills2.restore(OWNER, snapshot)

        assertEquals(
            skills.experienceOf(companion.id, CompanionSkill.SUPPORT),
            skills2.experienceOf(companion.id, CompanionSkill.SUPPORT),
        )
        assertEquals(
            skills.experienceOf(companion.id, CompanionSkill.TANK),
            skills2.experienceOf(companion.id, CompanionSkill.TANK),
        )
        assertEquals(
            skills.levelOf(companion.id, CompanionSkill.SUPPORT),
            skills2.levelOf(companion.id, CompanionSkill.SUPPORT),
        )
    }

    @Test
    public fun `damage dealt pays the matching style track only`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        assertEquals(
            30L,
            skills.awardDamageDealt(OWNER, companion.id, CompanionAttackStyle.MELEE, 30),
        )
        assertEquals(30L, skills.experienceOf(companion.id, CompanionSkill.MELEE))
        // Style tracks are independent - melee damage never feeds ranged/magic/support.
        assertEquals(0L, skills.experienceOf(companion.id, CompanionSkill.RANGED))
        assertEquals(0L, skills.experienceOf(companion.id, CompanionSkill.MAGIC))
        assertEquals(0L, skills.experienceOf(companion.id, CompanionSkill.SUPPORT))

        assertEquals(
            20L,
            skills.awardDamageDealt(OWNER, companion.id, CompanionAttackStyle.MAGIC, 20),
        )
        assertEquals(20L, skills.experienceOf(companion.id, CompanionSkill.MAGIC))
    }

    @Test
    public fun `style xp requires combat and respects the per-event cap`() {
        val (companions, skills) = newServices()
        val companion = companions.fightingCompanion()

        // Misses/zero-damage hits never pay out.
        assertEquals(
            0L,
            skills.awardDamageDealt(OWNER, companion.id, CompanionAttackStyle.MELEE, 0),
        )

        // A single huge hit is bounded by the style cap.
        assertEquals(
            CompanionSkillXp.STYLE_XP_CAP_PER_EVENT,
            skills.awardDamageDealt(OWNER, companion.id, CompanionAttackStyle.RANGED, 9_999),
        )

        // A companion not in combat (parked) cannot farm style experience.
        val parked = companions.recruit(OWNER, 2, "Idle", CompanionClass.DPS, 1001, 100)
        companions.activate(OWNER, parked.id)
        assertEquals(0L, skills.awardDamageDealt(OWNER, parked.id, CompanionAttackStyle.MELEE, 50))
    }

    @Test
    public fun `inactive and incapacitated companions earn nothing`() {
        val (companions, skills) = newServices()
        val companion = companions.recruit(OWNER, 1, "Rest", CompanionClass.TANK, 1000, 100)

        // Never activated.
        assertEquals(0L, skills.awardTaunt(OWNER, companion.id))
        assertEquals(0L, skills.awardDamageTankled(OWNER, companion.id, 10))
        assertEquals(0L, skills.awardEffectiveHealing(OWNER, companion.id, 10))

        // Incapacitated: knocked out companions cannot accrue role experience.
        companions.activate(OWNER, companion.id)
        companions.damage(OWNER, companion.id, 100, nowEpochMillis = 1_000L)
        assertEquals(0L, skills.awardTaunt(OWNER, companion.id))
        assertEquals(0L, skills.awardDamageTankled(OWNER, companion.id, 10))
    }
}
