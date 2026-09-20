package org.rsmod.api.companion

import jakarta.inject.Inject
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the five independent companion progression tracks ([CompanionSkill]): experience, derived
 * 1-99 levels, and the anti-abuse validation applied to every award.
 *
 * Award methods never throw for gameplay conditions - a rejected event simply grants `0` and
 * returns it, so callers may fire freely and let the service decide. Every method returns the
 * amount of experience actually granted.
 *
 * Anti-abuse model:
 * - Awards require an **active**, living companion (never INCAPACITATED/DESPAWNED).
 * - TANK experience (damage taken, aggro held, protection) additionally requires the companion to
 *   be in [CompanionState.COMBAT] - a companion parked next to a hostile npc while its owner is not
 *   fighting earns nothing, which removes the AFK-soak exploit.
 * - SUPPORT experience requires the combat window: the companion must be in combat or have left it
 *   within [CompanionSkillXp.COMBAT_GRACE_MILLIS]. Fully idle companions cannot heal-farm.
 * - Callers must pass **effective** amounts only: hitpoints actually restored, shield points
 *   actually applied, damage actually suffered, effects actually removed. Overheal, no-op buffs and
 *   empty cleanses are rejected (`<= 0`) and buff awards carry an explicit `applied` flag so
 *   refresh ticks never pay out.
 * - Self-inflicted damage never earns TANK experience - callers report it via `fromHostileSource =
 *   false`, and the runtime wiring only observes hitpoint losses inflicted while the companion is
 *   genuinely fighting.
 * - Flat awards (taunt/buff/cleanse/aggro) are throttled per companion so duplicate reports in the
 *   same tick collapse to one payout.
 * - Every event is capped by `CompanionSkillXp.*_CAP_PER_EVENT` constants.
 *
 * Persistence flows through `CompanionCharacterPipeline`: rows are restored via [restore] on login
 * and written by the repository from [experienceMap] snapshots on save.
 */
public class CompanionSkillService
internal constructor(
    private val companions: CompanionService,
    private val clockMillis: () -> Long,
) {
    @Inject
    public constructor(companions: CompanionService) : this(companions, System::currentTimeMillis)

    /** companion id -> skill -> accumulated experience. */
    private val experience = ConcurrentHashMap<Long, EnumMap<CompanionSkill, Long>>()

    /** (companion id, award kind) -> epoch millis of the last payout, for throttling. */
    private val lastAwards = ConcurrentHashMap<Pair<Long, String>, Long>()

    /** companion id -> epoch millis the companion was last observed in COMBAT state. */
    private val lastCombatMillis = ConcurrentHashMap<Long, Long>()

    // --- queries ---

    public fun experienceOf(companionId: Long, skill: CompanionSkill): Long =
        experience[companionId]?.get(skill) ?: 0L

    public fun levelOf(companionId: Long, skill: CompanionSkill): Int =
        CompanionSkillXp.levelFor(experienceOf(companionId, skill))

    public fun levelsOf(companionId: Long): Map<CompanionSkill, Int> =
        CompanionSkill.entries.associateWith { levelOf(companionId, it) }

    /** Snapshot of every track's experience for [companionId]; used by the save pipeline. */
    public fun experienceMap(companionId: Long): Map<CompanionSkill, Long> =
        experience[companionId]?.let { synchronized(it) { it.toMap() } }.orEmpty()

    // --- persistence ---

    /**
     * Restores persisted skill experience for [ownerCharacterId]'s companions. Called by the
     * character-load pipeline; keys are companion ids, values are per-skill experience.
     */
    public fun restore(ownerCharacterId: Long, rows: Map<Long, Map<CompanionSkill, Long>>) {
        for ((companionId, skills) in rows) {
            for ((skill, xp) in skills) {
                val clamped = xp.coerceIn(0L, CompanionSkillXp.MAX_TRACK_EXPERIENCE)
                if (clamped > 0) {
                    track(companionId)[skill] = clamped
                }
            }
        }
    }

    // --- SUPPORT awards ---

    /**
     * Awards experience for [healedHitpoints] hitpoints **actually restored** to an ally. Callers
     * must cap overheal before calling (the role-spell layer already caps heals at the target's
     * missing hp). `<= 0` grants nothing, so overheal can never produce experience.
     */
    public fun awardEffectiveHealing(
        ownerCharacterId: Long,
        companionId: Long,
        healedHitpoints: Int,
    ): Long {
        if (healedHitpoints <= 0) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (!inCombatWindow(companion)) return 0L
        return grant(
            companionId,
            CompanionSkill.SUPPORT,
            (healedHitpoints.toLong() * CompanionSkillXp.HEAL_XP_PER_HP).coerceAtMost(
                CompanionSkillXp.HEAL_XP_CAP_PER_EVENT
            ),
        )
    }

    /**
     * Awards experience for [effectiveShieldPoints] of absorption/protection actually granted to
     * allies. Only call with the applied amount; `<= 0` grants nothing.
     */
    public fun awardShielding(
        ownerCharacterId: Long,
        companionId: Long,
        effectiveShieldPoints: Int,
    ): Long {
        if (effectiveShieldPoints <= 0) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (!inCombatWindow(companion)) return 0L
        return grant(
            companionId,
            CompanionSkill.SUPPORT,
            (effectiveShieldPoints.toLong() * CompanionSkillXp.SHIELD_XP_PER_POINT).coerceAtMost(
                CompanionSkillXp.SHIELD_XP_CAP_PER_EVENT
            ),
        )
    }

    /**
     * Awards flat buff experience. [appliedNewEffect] must be `true` only when the cast actually
     * applied (or upgraded) a buff - periodic refreshes of an unchanged buff pass `false` and earn
     * nothing.
     */
    public fun awardBuff(
        ownerCharacterId: Long,
        companionId: Long,
        appliedNewEffect: Boolean,
    ): Long {
        if (!appliedNewEffect) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (!inCombatWindow(companion)) return 0L
        if (!throttle(companionId, "buff", CompanionSkillXp.FLAT_ACTION_MIN_INTERVAL_MILLIS))
            return 0L
        return grant(companionId, CompanionSkill.SUPPORT, CompanionSkillXp.BUFF_XP)
    }

    /**
     * Awards cleanse experience scaled by the number of harmful effects **actually removed**. A
     * cleanse that removed nothing must be reported as `0` and grants nothing.
     */
    public fun awardCleanse(ownerCharacterId: Long, companionId: Long, effectsRemoved: Int): Long {
        if (effectsRemoved <= 0) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (!inCombatWindow(companion)) return 0L
        if (!throttle(companionId, "cleanse", CompanionSkillXp.FLAT_ACTION_MIN_INTERVAL_MILLIS))
            return 0L
        return grant(
            companionId,
            CompanionSkill.SUPPORT,
            (effectsRemoved.toLong() * CompanionSkillXp.CLEANSE_XP_PER_EFFECT).coerceAtMost(
                CompanionSkillXp.CLEANSE_XP_CAP_PER_EVENT
            ),
        )
    }

    // --- combat style awards (MELEE/RANGED/MAGIC) ---

    /**
     * Awards [damage] hitpoints of damage dealt to a hostile npc on the [style] progression track
     * (`MELEE`/`RANGED`/`MAGIC` from [CompanionAttackStyle]). Requirements:
     * - [damage] must be the applied hit damage; `<= 0` grants nothing.
     * - The companion must be in [CompanionState.COMBAT] - passive trickle damage while parked
     *   never earns experience.
     */
    public fun awardDamageDealt(
        ownerCharacterId: Long,
        companionId: Long,
        style: CompanionAttackStyle,
        damage: Int,
    ): Long {
        if (damage <= 0) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (companion.state != CompanionState.COMBAT) return 0L
        noteCombatActivity(companionId)
        return grant(
            companionId,
            style.toCompanionSkill(),
            (damage.toLong() * CompanionSkillXp.STYLE_XP_PER_DAMAGE).coerceAtMost(
                CompanionSkillXp.STYLE_XP_CAP_PER_EVENT
            ),
        )
    }

    // --- TANK awards ---

    /**
     * Awards flat taunt experience. Call only when the taunt actually applied (e.g.
     * `ThreatService.taunt` returned `true`); failed/no-op taunts must not reach this method.
     * Throttled per companion as a backstop on top of the spell cooldown.
     */
    public fun awardTaunt(ownerCharacterId: Long, companionId: Long): Long {
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (companion.state == CompanionState.COMBAT) noteCombatActivity(companionId)
        if (!throttle(companionId, "taunt", CompanionSkillXp.TAUNT_MIN_INTERVAL_MILLIS)) return 0L
        return grant(companionId, CompanionSkill.TANK, CompanionSkillXp.TAUNT_XP)
    }

    /**
     * Awards experience for [damageTaken] hitpoints the companion **actually lost** to a hostile
     * npc while fighting. Requirements:
     * - [damageTaken] must be the post-mitigation amount that removed real hitpoints.
     * - [fromHostileSource] attests the damage came from an enemy, not self-inflicted or allied
     *   effects; `false` always grants nothing.
     * - The companion must be in [CompanionState.COMBAT], which excludes idle/AFK soaking.
     */
    public fun awardDamageTankled(
        ownerCharacterId: Long,
        companionId: Long,
        damageTaken: Int,
        fromHostileSource: Boolean = true,
    ): Long {
        if (damageTaken <= 0 || !fromHostileSource) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (companion.state != CompanionState.COMBAT) return 0L
        noteCombatActivity(companionId)
        return grant(
            companionId,
            CompanionSkill.TANK,
            (damageTaken.toLong() * CompanionSkillXp.TANKED_XP_PER_DAMAGE).coerceAtMost(
                CompanionSkillXp.TANKED_XP_CAP_PER_EVENT
            ),
        )
    }

    /**
     * Awards the periodic aggro-hold payout. Callers report once per observation cycle while the
     * companion verifiably holds aggro (is the npc's current target or top threat holder); the
     * [CompanionSkillXp.AGGRO_HOLD_INTERVAL_MILLIS] throttle collapses the per-cycle reports into
     * one payout per interval, and the COMBAT-state gate keeps passive holders at zero.
     */
    public fun awardAggroHeld(ownerCharacterId: Long, companionId: Long): Long {
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (companion.state != CompanionState.COMBAT) return 0L
        noteCombatActivity(companionId)
        if (!throttle(companionId, "aggro", CompanionSkillXp.AGGRO_HOLD_INTERVAL_MILLIS)) return 0L
        return grant(companionId, CompanionSkill.TANK, CompanionSkillXp.AGGRO_HOLD_XP)
    }

    /**
     * Awards experience for [preventedDamage] hitpoints of damage actually prevented or redirected
     * by a companion protection effect (e.g. `incomingDamageMultiplier`). Call with `rawDamage -
     * appliedDamage`; `<= 0` grants nothing.
     */
    public fun awardProtection(
        ownerCharacterId: Long,
        companionId: Long,
        preventedDamage: Int,
    ): Long {
        if (preventedDamage <= 0) return 0L
        val companion = eligible(ownerCharacterId, companionId) ?: return 0L
        if (companion.state != CompanionState.COMBAT) return 0L
        noteCombatActivity(companionId)
        return grant(
            companionId,
            CompanionSkill.TANK,
            (preventedDamage.toLong() * CompanionSkillXp.PROTECTION_XP_PER_DAMAGE).coerceAtMost(
                CompanionSkillXp.PROTECTION_XP_CAP_PER_EVENT
            ),
        )
    }

    // --- internals ---

    /**
     * Records that [companionId] is fighting right now. The runtime observer calls this every cycle
     * a companion is in [CompanionState.COMBAT]; award methods also note it themselves when they
     * observe combat. Drives the support-award combat window.
     */
    public fun noteCombatActivity(companionId: Long) {
        lastCombatMillis[companionId] = clockMillis()
    }

    private fun eligible(ownerCharacterId: Long, companionId: Long): Companion? =
        companions
            .owned(ownerCharacterId)
            .firstOrNull { it.id == companionId }
            ?.takeIf {
                it.active &&
                    it.state != CompanionState.INCAPACITATED &&
                    it.state != CompanionState.DESPAWNED
            }

    private fun inCombatWindow(companion: Companion): Boolean {
        if (companion.state == CompanionState.COMBAT) {
            noteCombatActivity(companion.id)
            return true
        }
        val last = lastCombatMillis[companion.id] ?: return false
        return clockMillis() - last <= CompanionSkillXp.COMBAT_GRACE_MILLIS
    }

    private fun throttle(companionId: Long, kind: String, intervalMillis: Long): Boolean {
        val now = clockMillis()
        var allowed = false
        lastAwards.compute(companionId to kind) { _, last ->
            if (last == null || now - last >= intervalMillis) {
                allowed = true
                now
            } else {
                last
            }
        }
        return allowed
    }

    private fun grant(companionId: Long, skill: CompanionSkill, amount: Long): Long {
        if (amount <= 0) return 0L
        val track = track(companionId)
        synchronized(track) {
            val current = track[skill] ?: 0L
            val next = (current + amount).coerceAtMost(CompanionSkillXp.MAX_TRACK_EXPERIENCE)
            val gained = next - current
            if (gained > 0) {
                track[skill] = next
            }
            return gained
        }
    }

    private fun track(companionId: Long): EnumMap<CompanionSkill, Long> =
        experience.computeIfAbsent(companionId) { EnumMap(CompanionSkill::class.java) }
}
