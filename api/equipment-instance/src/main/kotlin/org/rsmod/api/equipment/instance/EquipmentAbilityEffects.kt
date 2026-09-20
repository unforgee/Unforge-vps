package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/**
 * The combat style an ability strike uses when it procs.
 *
 * The strike is resolved through that style's regular PvN pipeline (accuracy vs the target's
 * matching defence, max hit from the wearer's matching offensive bonuses) regardless of what the
 * equipped weapon actually is - so a sword whose instance carries a [Ranged] strike uses the
 * player's ranged accuracy/strength bonuses, and [Hybrid] picks whichever of the three style
 * pipelines currently yields the highest max hit (e.g. a "fire breath" that benefits from all of
 * the wearer's gear).
 */
public enum class AbilityStyle {
    None,
    Melee,
    Ranged,
    Magic,
    Hybrid,
}

/**
 * Status ailments an ability proc can apply on hit. Mirrors the combat `StatusEffect` enum; kept as
 * a separate type because this module cannot depend on `api:combat:combat-commons` (that module
 * depends on `api:npc`, which depends on this one).
 */
public enum class AbilityStatus {
    Poison,
    Venom,
    Burn,
    Bleed,
    Freeze,
    Vulnerable,
    Weaken,
}

/**
 * A single status ailment applied when the ability procs.
 *
 * @property status which ailment to apply.
 * @property potency periodic damage per proc tick for damage-over-time effects; `0` for utility
 *   statuses such as [AbilityStatus.Freeze].
 * @property cycles how long the ailment lasts.
 * @property stacks stacks granted per proc for stacking statuses ([AbilityStatus.Vulnerable],
 *   [AbilityStatus.Weaken]).
 */
public data class AbilityStatusProc(
    public val status: AbilityStatus,
    public val potency: Int = 0,
    public val cycles: Int = 0,
    public val stacks: Int = 1,
)

/**
 * Aggregated combat/loot procs granted by an equipment instance's
 * [EquipmentInstance.uniqueEffectIds].
 *
 * All values are additive across every instanced item in the player's worn inventory and are capped
 * by [EquipmentAbilityProcs.cap] to keep stacking bounded.
 *
 * @property onHitHealBps heals the attacker by this fraction (basis points) of dealt npc damage.
 * @property onKillHeal flat hitpoints restored when the wearer lands a kill.
 * @property onKillPrayer flat prayer points restored when the wearer lands a kill.
 * @property extraDropRolls additional rolls on the victim's Unforge drop table per kill.
 * @property outgoingDamageBps bonus outgoing npc damage in basis points.
 * @property strikeStyle when not [AbilityStyle.None], the proc queues a bonus hit resolved through
 *   that style's damage pipeline.
 * @property strikeMultiplierBps basis-points multiplier applied to the style's calculated max hit.
 * @property strikeBaseMaxHit flat bonus added to the strike's max hit; for [AbilityStyle.Magic] it
 *   acts as the spell's base max hit before `magic dmg %` gear scaling.
 * @property statuses ailment riders applied to the target when the proc lands on a hit.
 * @property aoeRadius radius (tiles per axis) around the primary target that secondary npc victims
 *   are splashed; `0` disables area damage.
 * @property aoeDamageBps fraction (basis points) of the dealt hit splashed onto secondary victims.
 *   Secondary victims also receive the proc's [statuses], matching boss mechanics such as venom
 *   clouds and barrages.
 */
public data class AbilityProc(
    public val onHitHealBps: Int = 0,
    public val onKillHeal: Int = 0,
    public val onKillPrayer: Int = 0,
    public val extraDropRolls: Int = 0,
    public val outgoingDamageBps: Int = 0,
    public val strikeStyle: AbilityStyle = AbilityStyle.None,
    public val strikeMultiplierBps: Int = 0,
    public val strikeBaseMaxHit: Int = 0,
    public val statuses: List<AbilityStatusProc> = emptyList(),
    public val aoeRadius: Int = 0,
    public val aoeDamageBps: Int = 0,
) {
    public operator fun plus(other: AbilityProc): AbilityProc =
        AbilityProc(
            onHitHealBps = onHitHealBps + other.onHitHealBps,
            onKillHeal = onKillHeal + other.onKillHeal,
            onKillPrayer = onKillPrayer + other.onKillPrayer,
            extraDropRolls = extraDropRolls + other.extraDropRolls,
            outgoingDamageBps = outgoingDamageBps + other.outgoingDamageBps,
            strikeStyle = if (strikeStyle != AbilityStyle.None) strikeStyle else other.strikeStyle,
            strikeMultiplierBps = strikeMultiplierBps + other.strikeMultiplierBps,
            strikeBaseMaxHit = strikeBaseMaxHit + other.strikeBaseMaxHit,
            statuses = statuses + other.statuses,
            aoeRadius = maxOf(aoeRadius, other.aoeRadius),
            aoeDamageBps = aoeDamageBps + other.aoeDamageBps,
        )

    public val isEmpty: Boolean
        get() = this == AbilityProc()

    /** `true` when the proc applies on-hit riders (statuses or AoE splash) to the target. */
    public val hasOnHitEffects: Boolean
        get() = statuses.isNotEmpty() || (aoeRadius > 0 && aoeDamageBps > 0)
}

/**
 * A single resolved ability proc: the [proc] effects gated by [chanceBps] (basis points, rolled per
 * hit/kill). The chance is derived from the owning instance's [EquipmentInstance.itemLevel] and
 * [EquipmentInstance.rarity] - better items proc more often.
 */
public data class ResolvedProc(
    public val abilityId: String,
    public val chanceBps: Int,
    public val proc: AbilityProc,
)

/**
 * Maps [EquipmentAbilityCatalog] ability ids onto concrete [AbilityProc]s.
 *
 * Resolution is keyword-based so all 1,000 catalog entries carry a real effect: weapon-special
 * (`spec-`), boss-attack (`boss-`) and empowered (`pow-`) ids are matched on their name tokens;
 * anything unmatched still grants a small damage bonus.
 */
public object EquipmentAbilityProcs {
    private val LIFESTEAL =
        Regex("vampiric|siphon|leech|drain|healing|blood|soul[- ]|harvest|consume")
    private val RESTORE = Regex("blessed|divine|holy|radiant|sacred|sanctuary|prayer|redeem|purif")
    private val GREED =
        Regex("ancient|eternal|golden|fortune|lucky|gilded|astral|eclipsed|astral|rune[- ]?bound")
    private val HYBRID = Regex("omni|dragon|chaos|prismatic|elemental|cataclysm")
    private val MAGIC =
        Regex(
            "fire|flame|burn|scorch|frost|ice|storm|arcane|magic|blaze|inferno|lightning|" +
                "thunder|zap|breath|spell|nova|eruption|volcan"
        )
    private val RANGED =
        Regex(
            "shot|arrow|bolt|pierce|volley|snipe|ballista|marksman|bullet|dart|javelin|" +
                "spray|spit"
        )
    private val HEAVY =
        Regex(
            "judgement|wrath|decimat|execute|maelstrom|cleave|rend|slam|barrage|smash|crush|" +
                "devastat|obliterat|annihilat|ruin"
        )
    private val WARDING = Regex("ward|shield|aegis|barrier|wall|bulwark|protect")

    // Ailment riders - matched independently so e.g. `boss-poison-slam-kril` keeps its melee
    // strike AND poisons, mirroring the boss's real attack.
    private val VENOMOUS = Regex("venom|toxic")
    private val POISONOUS = Regex("poison|acid|spore")
    private val BURNING =
        Regex(
            "burn|scorch|sear|immolat|ember|lava|magma|hellfire|pyro|dragonfire|inferno|blaz|" +
                "fire|flame"
        )
    private val BLEEDING = Regex("bleed|gore|lacerat|wound|gash|rend|mangle|punctur|cleave")
    private val FREEZING =
        Regex("freeze|frozen|frost|ice|blizzard|glac|cold|prison|snare|web|bind|shackl|entomb")
    private val VULNERATING = Regex("sunder|shatter|breach|fracture|corrupt|expos")
    private val WEAKENING = Regex("weaken|crippl|sap|enfeebl|blind|darkness|smoke|veil")

    // Area attacks - splashed onto npcs around the primary target like the boss originals.
    private val AOE =
        Regex(
            "barrage|nova|storm|volley|eruption|burst|rain|shockwave|blast|cloud|pool|wall|" +
                "vortex|tornado|swarm|explosion|cataclysm|meteor|deluge|quake|avalanche|tempest|cyclone"
        )

    private const val CAP_HEAL_BPS = 5000
    private const val CAP_ON_KILL_HEAL = 20
    private const val CAP_ON_KILL_PRAYER = 10
    private const val CAP_EXTRA_ROLLS = 3
    private const val CAP_DAMAGE_BPS = 5000
    private const val CAP_AOE_RADIUS = 5
    private const val CAP_AOE_DAMAGE_BPS = 10_000
    private const val CAP_STATUS_STACKS = 5
    private const val CAP_STATUS_CYCLES = 400
    private const val CAP_STATUS_POTENCY = 20

    /** Proc chance bounds in basis points: 1% floor, 90% ceiling. */
    public const val MIN_CHANCE_BPS: Int = 100
    public const val MAX_CHANCE_BPS: Int = 9000

    public fun procFor(abilityId: String): AbilityProc {
        val id = abilityId.lowercase()
        var proc = damageProcFor(id) + statusProcFor(id) + aoeProcFor(id)
        // Boss-attack abilities hit a little harder than generated/spec counterparts, and boss
        // area attacks reach one tile further - the "improved" version of the original mechanic.
        if (id.startsWith("boss-")) {
            proc =
                proc.copy(
                    outgoingDamageBps = proc.outgoingDamageBps + 200,
                    strikeMultiplierBps = proc.strikeMultiplierBps + 1500,
                    strikeBaseMaxHit = proc.strikeBaseMaxHit + 4,
                    aoeRadius = if (proc.aoeRadius > 0) proc.aoeRadius + 1 else proc.aoeRadius,
                )
        }
        return proc
    }

    private fun damageProcFor(id: String): AbilityProc =
        when {
            LIFESTEAL.containsMatchIn(id) -> AbilityProc(onHitHealBps = 1200)
            RESTORE.containsMatchIn(id) -> AbilityProc(onKillHeal = 4, onKillPrayer = 2)
            GREED.containsMatchIn(id) -> AbilityProc(extraDropRolls = 1)
            HYBRID.containsMatchIn(id) ->
                AbilityProc(
                    strikeStyle = AbilityStyle.Hybrid,
                    strikeMultiplierBps = 5000,
                    strikeBaseMaxHit = 6,
                )
            MAGIC.containsMatchIn(id) ->
                AbilityProc(
                    strikeStyle = AbilityStyle.Magic,
                    strikeMultiplierBps = 10_000,
                    strikeBaseMaxHit = 8,
                )
            RANGED.containsMatchIn(id) ->
                AbilityProc(
                    strikeStyle = AbilityStyle.Ranged,
                    strikeMultiplierBps = 4000,
                    strikeBaseMaxHit = 3,
                )
            HEAVY.containsMatchIn(id) ->
                AbilityProc(
                    strikeStyle = AbilityStyle.Melee,
                    strikeMultiplierBps = 4500,
                    strikeBaseMaxHit = 2,
                )
            WARDING.containsMatchIn(id) -> AbilityProc(outgoingDamageBps = 150)
            else -> AbilityProc(outgoingDamageBps = 200)
        }

    private fun statusProcFor(id: String): AbilityProc {
        val statuses = ArrayList<AbilityStatusProc>(3)
        if (VENOMOUS.containsMatchIn(id)) {
            // Venom ticks like poison but ramps +2 potency each tick up to 20 (OSRS-like).
            statuses += AbilityStatusProc(AbilityStatus.Venom, potency = 6, cycles = 180)
        }
        if (POISONOUS.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Poison, potency = 4, cycles = 150)
        }
        if (BURNING.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Burn, potency = 3, cycles = 60)
        }
        if (BLEEDING.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Bleed, potency = 2, cycles = 50)
        }
        if (FREEZING.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Freeze, cycles = 25)
        }
        if (VULNERATING.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Vulnerable, cycles = 90)
        }
        if (WEAKENING.containsMatchIn(id)) {
            statuses += AbilityStatusProc(AbilityStatus.Weaken, cycles = 90)
        }
        return AbilityProc(statuses = statuses)
    }

    private fun aoeProcFor(id: String): AbilityProc =
        if (AOE.containsMatchIn(id)) {
            AbilityProc(aoeRadius = 1, aoeDamageBps = 5000)
        } else {
            AbilityProc()
        }

    /**
     * Human-readable summary of what [abilityId]'s proc does, resolved through the same [procFor]
     * table the combat rolls use. Sent to the client hover overlay so the displayed effect can
     * never drift from the real mechanic.
     */
    public fun describe(abilityId: String): String {
        val proc = procFor(abilityId)
        val parts = ArrayList<String>(4)
        if (proc.strikeStyle != AbilityStyle.None) {
            parts +=
                "bonus ${proc.strikeStyle.name.lowercase()} hit at " +
                    "${proc.strikeMultiplierBps / 100.0}%+${proc.strikeBaseMaxHit} max hit"
        }
        if (proc.onHitHealBps != 0) {
            parts += "heals you for ${proc.onHitHealBps / 100.0}% of damage dealt"
        }
        if (proc.onKillHeal != 0 || proc.onKillPrayer != 0) {
            parts += "restores ${proc.onKillHeal} HP / ${proc.onKillPrayer} prayer on kill"
        }
        if (proc.extraDropRolls != 0) {
            parts += "+${proc.extraDropRolls} extra drop roll per kill"
        }
        for (status in proc.statuses) {
            parts += describeStatus(status)
        }
        if (proc.aoeRadius > 0 && proc.aoeDamageBps > 0) {
            parts +=
                "splashes ${proc.aoeDamageBps / 100.0}% of the hit to enemies within " +
                    "${proc.aoeRadius * 2 + 1}x${proc.aoeRadius * 2 + 1} tiles"
        }
        if (proc.outgoingDamageBps != 0) {
            parts += "+${proc.outgoingDamageBps / 100.0}% damage"
        }
        return parts.joinToString("; ").ifEmpty { "passive bonus" }
    }

    private fun describeStatus(status: AbilityStatusProc): String =
        when (status.status) {
            AbilityStatus.Poison -> "poisons the target (${status.potency} dmg every 30 cycles)"
            AbilityStatus.Venom ->
                "venoms the target (${status.potency} dmg, grows +2 each tick up to 20)"
            AbilityStatus.Burn -> "burns the target (${status.potency} dmg every 5 cycles)"
            AbilityStatus.Bleed -> "makes the target bleed (${status.potency} dmg every 5 cycles)"
            AbilityStatus.Freeze -> "freezes the target for ${status.cycles} cycles"
            AbilityStatus.Vulnerable ->
                "+${status.stacks} vulnerability stack (+5% damage taken per stack, " +
                    "${status.cycles} cycles)"
            AbilityStatus.Weaken ->
                "+${status.stacks} weaken stack (-5% damage dealt per stack, ${status.cycles} cycles)"
        }

    /**
     * Proc chance in basis points (`100` = 1%, `9000` = 90%) derived from the instance's rarity and
     * item level: higher-rarity, higher-level equipment procs noticeably more often.
     */
    public fun chanceBps(rarity: EquipmentRarity, itemLevel: Int): Int =
        (500 + rarity.ordinal * 1_200 + itemLevel * 30).coerceIn(MIN_CHANCE_BPS, MAX_CHANCE_BPS)

    /** Resolves every unique effect on [instance] into a chance-gated [ResolvedProc]. */
    public fun resolve(instance: EquipmentInstance): List<ResolvedProc> {
        val chance = chanceBps(instance.rarity, instance.itemLevel)
        return instance.uniqueEffectIds.map { id -> ResolvedProc(id, chance, procFor(id)) }
    }

    /** Applies the stacking caps to a summed [proc] (see [aggregate]). */
    public fun cap(proc: AbilityProc): AbilityProc =
        AbilityProc(
            onHitHealBps = proc.onHitHealBps.coerceAtMost(CAP_HEAL_BPS),
            onKillHeal = proc.onKillHeal.coerceAtMost(CAP_ON_KILL_HEAL),
            onKillPrayer = proc.onKillPrayer.coerceAtMost(CAP_ON_KILL_PRAYER),
            extraDropRolls = proc.extraDropRolls.coerceAtMost(CAP_EXTRA_ROLLS),
            outgoingDamageBps = proc.outgoingDamageBps.coerceAtMost(CAP_DAMAGE_BPS),
            strikeStyle = proc.strikeStyle,
            strikeMultiplierBps = proc.strikeMultiplierBps,
            strikeBaseMaxHit = proc.strikeBaseMaxHit,
            statuses = mergeStatuses(proc.statuses),
            aoeRadius = proc.aoeRadius.coerceAtMost(CAP_AOE_RADIUS),
            aoeDamageBps = proc.aoeDamageBps.coerceAtMost(CAP_AOE_DAMAGE_BPS),
        )

    /** Merges duplicate ailments, keeping the strongest potency and longest duration. */
    private fun mergeStatuses(statuses: List<AbilityStatusProc>): List<AbilityStatusProc> =
        statuses
            .groupBy { it.status }
            .map { (status, procs) ->
                AbilityStatusProc(
                    status = status,
                    potency = procs.maxOf { it.potency }.coerceAtMost(CAP_STATUS_POTENCY),
                    cycles = procs.maxOf { it.cycles }.coerceAtMost(CAP_STATUS_CYCLES),
                    stacks = procs.sumOf { it.stacks }.coerceAtMost(CAP_STATUS_STACKS),
                )
            }

    public fun aggregate(abilityIds: Collection<String>): AbilityProc {
        var sum = AbilityProc()
        for (id in abilityIds) {
            sum += procFor(id)
        }
        return cap(sum)
    }
}

/**
 * Runtime lookup: resolves the combined [AbilityProc] for every instanced item currently worn in
 * the given inventory (via [EquipmentInstanceRegistry]).
 */
@Singleton
public class EquipmentAbilityEffects
@Inject
constructor(private val registry: EquipmentInstanceRegistry) {
    /**
     * Resolves every worn instance's unique effects into per-ability [ResolvedProc]s carrying the
     * chance roll derived from that instance's item level and rarity.
     */
    public fun procs(worn: Inventory): List<ResolvedProc> {
        val resolved = ArrayList<ResolvedProc>(16)
        for (obj in worn) {
            if (obj == null || obj.instanceId == InvObj.NO_INSTANCE) {
                continue
            }
            val instance = registry[obj.instanceId] ?: continue
            resolved += EquipmentAbilityProcs.resolve(instance)
        }
        return resolved
    }

    /**
     * Legacy unconditional aggregation: every worn ability's effects summed and capped, without a
     * chance roll. Prefer [procs] for chance-gated behavior.
     */
    public fun aggregate(worn: Inventory): AbilityProc {
        var sum = AbilityProc()
        for (resolved in procs(worn)) {
            sum += resolved.proc
        }
        return EquipmentAbilityProcs.cap(sum)
    }
}
