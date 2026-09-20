package org.rsmod.api.companion

import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ItemEvolutionCatalog

/** UI grouping for the universal companion stat registry. */
public enum class CompanionStatGroup {
    OFFENSIVE,
    DEFENSIVE,
    SUPPORT,
    THREAT,
    ABILITY,
    UTILITY,
}

/** How a stat's raw value is rendered (`BPS` values are shown as percentages). */
public enum class CompanionStatFormat {
    FLAT,
    PERCENT_BPS,
}

/**
 * The canonical companion stat registry. Every combat-facing number a companion build produces is
 * one of these stats; nothing outside [CompanionStatCalculator] may invent combat values.
 */
public enum class CompanionStat(
    public val group: CompanionStatGroup,
    public val format: CompanionStatFormat,
) {
    // offensive
    MELEE_POWER(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    RANGED_POWER(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    MAGIC_POWER(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    MELEE_ACCURACY(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    RANGED_ACCURACY(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    MAGIC_ACCURACY(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.FLAT),
    CRITICAL_CHANCE_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),
    CRITICAL_DAMAGE_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),
    DAMAGE_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),
    BOSS_DAMAGE_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),
    ATTACK_SPEED_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),
    RANGED_PENETRATION_BPS(CompanionStatGroup.OFFENSIVE, CompanionStatFormat.PERCENT_BPS),

    // defensive
    MAX_HEALTH(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.FLAT),
    ARMOUR(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.FLAT),
    DAMAGE_REDUCTION_BPS(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.PERCENT_BPS),
    BLOCK_CHANCE_BPS(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.PERCENT_BPS),
    MELEE_RESISTANCE_BPS(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.PERCENT_BPS),
    RANGED_RESISTANCE_BPS(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.PERCENT_BPS),
    MAGIC_RESISTANCE_BPS(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.PERCENT_BPS),
    HEALTH_REGEN(CompanionStatGroup.DEFENSIVE, CompanionStatFormat.FLAT),

    // support
    HEALING_POWER(CompanionStatGroup.SUPPORT, CompanionStatFormat.FLAT),
    HEALING_POWER_BPS(CompanionStatGroup.SUPPORT, CompanionStatFormat.PERCENT_BPS),
    SHIELD_POWER_BPS(CompanionStatGroup.SUPPORT, CompanionStatFormat.PERCENT_BPS),
    BUFF_POWER_BPS(CompanionStatGroup.SUPPORT, CompanionStatFormat.PERCENT_BPS),
    BUFF_DURATION_BPS(CompanionStatGroup.SUPPORT, CompanionStatFormat.PERCENT_BPS),
    INCOMING_HEALING_BPS(CompanionStatGroup.SUPPORT, CompanionStatFormat.PERCENT_BPS),

    // threat
    THREAT_GENERATION_BPS(CompanionStatGroup.THREAT, CompanionStatFormat.PERCENT_BPS),
    TAUNT_STRENGTH_BPS(CompanionStatGroup.THREAT, CompanionStatFormat.PERCENT_BPS),

    // ability
    COOLDOWN_REDUCTION_BPS(CompanionStatGroup.ABILITY, CompanionStatFormat.PERCENT_BPS),
    ABILITY_POWER_BPS(CompanionStatGroup.ABILITY, CompanionStatFormat.PERCENT_BPS),
    ABILITY_DURATION_BPS(CompanionStatGroup.ABILITY, CompanionStatFormat.PERCENT_BPS),

    // utility
    LIFESTEAL_BPS(CompanionStatGroup.UTILITY, CompanionStatFormat.PERCENT_BPS),
}

/** The pipeline stage a stat contribution came from, shown in UI breakdowns. */
public enum class CompanionStatSource {
    BASE,
    LEVEL,
    PROGRESSION,
    EQUIPMENT,
    SET,
    TALENT,
    EVOLUTION,
    BUFF,
    FRAGMENT,
}

/** Optional data-driven contributions supplied by content modules such as fragment progression. */
public interface CompanionStatAugmenter {
    public fun cacheStamp(companion: Companion): String = "static"

    public fun augment(
        companion: Companion,
        add: (stat: CompanionStat, value: Int, label: String) -> Unit,
    )
}

public data class CompanionStatContribution(
    public val source: CompanionStatSource,
    public val label: String,
    public val value: Int,
)

/** Central stat caps - no literal cap may live inside combat code. */
public object CompanionStatCaps {
    public const val MAX_COOLDOWN_REDUCTION_BPS: Int = 4_000
    public const val MAX_DAMAGE_REDUCTION_BPS: Int = 5_000
    public const val MAX_CRITICAL_CHANCE_BPS: Int = 5_000
    public const val MAX_LIFESTEAL_BPS: Int = 2_500
    public const val MAX_BLOCK_CHANCE_BPS: Int = 7_500
    public const val MAX_STYLE_RESISTANCE_BPS: Int = 6_000
    public const val MAX_PENETRATION_BPS: Int = 8_000
    public const val MIN_ATTACK_DELAY_TICKS: Int = 2
    public const val MAX_ATTACK_DELAY_TICKS: Int = 10
    public const val MIN_ABILITY_COOLDOWN_TICKS: Int = 1

    /** Single canonical cooldown rule: `base * (1 - cdr)`, clamped to cap and minimum. */
    public fun effectiveCooldownTicks(baseTicks: Int, cooldownReductionBps: Int): Int {
        val cdr = cooldownReductionBps.coerceIn(0, MAX_COOLDOWN_REDUCTION_BPS)
        return (baseTicks * (10_000 - cdr) / 10_000).coerceAtLeast(MIN_ABILITY_COOLDOWN_TICKS)
    }

    /** Single canonical incoming-damage rule for the reduction stat (applied exactly once). */
    public fun applyDamageReduction(damage: Int, reductionBps: Int): Int {
        val capped = reductionBps.coerceIn(0, MAX_DAMAGE_REDUCTION_BPS)
        return (damage.toLong() * (10_000 - capped) / 10_000).toInt().coerceAtLeast(0)
    }
}

/**
 * The fully derived, immutable result of the companion stat pipeline. `combat` preserves the legacy
 * [CompanionCombatStats] projection so the existing combat adapter keeps one entry point.
 */
public class CompanionStatSheet(
    public val companionId: Long,
    private val values: Map<CompanionStat, Int>,
    private val contributions: Map<CompanionStat, List<CompanionStatContribution>>,
    public val combat: CompanionCombatStats,
    public val activeSets: List<CompanionSetActivation>,
) {
    public fun value(stat: CompanionStat): Int = values[stat] ?: 0

    public fun breakdown(stat: CompanionStat): List<CompanionStatContribution> =
        contributions[stat].orEmpty()

    public val damageReductionBps: Int
        get() = value(CompanionStat.DAMAGE_REDUCTION_BPS)

    public val cooldownReductionBps: Int
        get() = value(CompanionStat.COOLDOWN_REDUCTION_BPS)

    public val healingPower: Int
        get() = value(CompanionStat.HEALING_POWER)

    public val threatGenerationBps: Int
        get() = value(CompanionStat.THREAT_GENERATION_BPS)

    /** Effective cooldown for an ability with [baseTicks], already capped. */
    public fun effectiveCooldownTicks(baseTicks: Int): Int =
        CompanionStatCaps.effectiveCooldownTicks(baseTicks, cooldownReductionBps)

    /** Post-reduction incoming damage for a hit of [damage], already capped. */
    public fun reducedDamage(damage: Int): Int =
        CompanionStatCaps.applyDamageReduction(damage, damageReductionBps)

    public val blockChanceBps: Int
        get() = value(CompanionStat.BLOCK_CHANCE_BPS)

    public val penetrationBps: Int
        get() = value(CompanionStat.RANGED_PENETRATION_BPS)

    /**
     * Style-specific resistance for an incoming attack. [attackStyle] is the raw `npc_attack_style`
     * param value (`0`=melee, `1`=ranged, `2`=magic); unknown values resist nothing.
     */
    public fun resistanceBpsFor(attackStyle: Int): Int =
        when (attackStyle) {
            0 -> value(CompanionStat.MELEE_RESISTANCE_BPS)
            1 -> value(CompanionStat.RANGED_RESISTANCE_BPS)
            2 -> value(CompanionStat.MAGIC_RESISTANCE_BPS)
            else -> 0
        }
}

/**
 * The one canonical companion stat pipeline: base data + level + the five progression tracks +
 * equipment affixes + set bonuses + talents + evolution. Pure function of its inputs; callers that
 * recalculate per cycle should route through [CompanionStatCache].
 */
public object CompanionStatCalculator {
    /** Per-level progression grants fed by [CompanionSkillService] levels. */
    public object Progression {
        public const val POWER_PER_STYLE_LEVEL: Int = 1
        public const val HEALING_PER_SUPPORT_LEVEL: Int = 2
        public const val HEALTH_PER_TANK_LEVEL: Int = 1
        public const val THREAT_BPS_PER_TANK_LEVEL: Int = 20
    }

    public fun calculate(
        companion: Companion,
        registry: EquipmentInstanceRegistry,
        skillLevels: Map<CompanionSkill, Int> = emptyMap(),
        augmenters: Collection<CompanionStatAugmenter> = emptyList(),
    ): CompanionStatSheet {
        val items = companion.gearInstanceIds.mapNotNull(registry::get)
        val totals = linkedMapOf<CompanionStat, Int>()
        val breakdown = linkedMapOf<CompanionStat, MutableList<CompanionStatContribution>>()

        fun add(stat: CompanionStat, source: CompanionStatSource, label: String, value: Int) {
            if (value == 0) return
            totals.merge(stat, value, Int::plus)
            breakdown.getOrPut(stat) { mutableListOf() } +=
                CompanionStatContribution(source, label, value)
        }

        fun affixSum(stat: EquipmentStat): Int =
            items.sumOf { item -> item.affixes.filter { it.stat == stat }.sumOf { it.magnitude } }

        // --- base + combat level (legacy scaling kept identical to the old gear calculator) ---
        val level = companion.level.coerceAtLeast(1)
        add(CompanionStat.MAX_HEALTH, CompanionStatSource.BASE, "Base", companion.maximumHitpoints)
        add(CompanionStat.MAX_HEALTH, CompanionStatSource.LEVEL, "Level $level", (level - 1) * 5)
        add(CompanionStat.DAMAGE_BPS, CompanionStatSource.BASE, "Base", 10_000)
        add(CompanionStat.DAMAGE_BPS, CompanionStatSource.LEVEL, "Level $level", (level - 1) * 100)
        add(CompanionStat.HEALING_POWER_BPS, CompanionStatSource.BASE, "Base", 10_000)
        add(
            CompanionStat.HEALING_POWER_BPS,
            CompanionStatSource.LEVEL,
            "Level $level",
            (level - 1) * 50,
        )

        // --- five progression tracks ---
        val meleeLevel = skillLevels[CompanionSkill.MELEE] ?: 0
        val rangedLevel = skillLevels[CompanionSkill.RANGED] ?: 0
        val magicLevel = skillLevels[CompanionSkill.MAGIC] ?: 0
        val supportLevel = skillLevels[CompanionSkill.SUPPORT] ?: 0
        val tankLevel = skillLevels[CompanionSkill.TANK] ?: 0
        add(
            CompanionStat.MELEE_POWER,
            CompanionStatSource.PROGRESSION,
            "Melee $meleeLevel",
            meleeLevel * Progression.POWER_PER_STYLE_LEVEL,
        )
        add(
            CompanionStat.RANGED_POWER,
            CompanionStatSource.PROGRESSION,
            "Ranged $rangedLevel",
            rangedLevel * Progression.POWER_PER_STYLE_LEVEL,
        )
        add(
            CompanionStat.MAGIC_POWER,
            CompanionStatSource.PROGRESSION,
            "Magic $magicLevel",
            magicLevel * Progression.POWER_PER_STYLE_LEVEL,
        )
        add(
            CompanionStat.HEALING_POWER,
            CompanionStatSource.PROGRESSION,
            "Support $supportLevel",
            supportLevel * Progression.HEALING_PER_SUPPORT_LEVEL,
        )
        add(
            CompanionStat.MAX_HEALTH,
            CompanionStatSource.PROGRESSION,
            "Tank $tankLevel",
            tankLevel * Progression.HEALTH_PER_TANK_LEVEL,
        )
        add(
            CompanionStat.THREAT_GENERATION_BPS,
            CompanionStatSource.PROGRESSION,
            "Tank $tankLevel",
            tankLevel * Progression.THREAT_BPS_PER_TANK_LEVEL,
        )

        // --- equipment affixes (each flat point of a power affix = +1% = 100 bps) ---
        val gearDamage =
            affixSum(EquipmentStat.DamageMax) +
                affixSum(EquipmentStat.AverageHit) +
                affixSum(EquipmentStat.Strength)
        add(CompanionStat.DAMAGE_BPS, CompanionStatSource.EQUIPMENT, "Affixes", gearDamage * 100)
        add(
            CompanionStat.MELEE_POWER,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.Strength) + affixSum(EquipmentStat.MeleePower),
        )
        add(
            CompanionStat.RANGED_POWER,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.RangedStrength) + affixSum(EquipmentStat.RangedPower),
        )
        add(
            CompanionStat.MAGIC_POWER,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.MagicDamage) + affixSum(EquipmentStat.MagicPower),
        )
        add(
            CompanionStat.MELEE_ACCURACY,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AttackStab) +
                affixSum(EquipmentStat.AttackSlash) +
                affixSum(EquipmentStat.AttackCrush),
        )
        add(
            CompanionStat.RANGED_ACCURACY,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AttackRanged),
        )
        add(
            CompanionStat.MAGIC_ACCURACY,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AttackMagic),
        )
        add(
            CompanionStat.ARMOUR,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.DefenceStab) +
                affixSum(EquipmentStat.DefenceSlash) +
                affixSum(EquipmentStat.DefenceCrush),
        )
        add(
            CompanionStat.MAX_HEALTH,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.MaximumHealth),
        )
        add(
            CompanionStat.HEALING_POWER,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.HealingPower),
        )
        add(
            CompanionStat.HEALING_POWER_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            (affixSum(EquipmentStat.LifeSteal) + affixSum(EquipmentStat.EffectiveHealth)) * 100,
        )
        add(
            CompanionStat.DAMAGE_REDUCTION_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.DamageReduction) * 100,
        )
        add(
            CompanionStat.MELEE_RESISTANCE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AbsorbMelee) * 100,
        )
        add(
            CompanionStat.RANGED_RESISTANCE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AbsorbRanged) * 100,
        )
        add(
            CompanionStat.MAGIC_RESISTANCE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AbsorbMagic) * 100,
        )
        add(
            CompanionStat.THREAT_GENERATION_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.ThreatGeneration) * 100,
        )
        add(
            CompanionStat.CRITICAL_CHANCE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.CriticalRate) * 100,
        )
        add(
            CompanionStat.CRITICAL_DAMAGE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.CriticalDamage) * 100,
        )
        add(
            CompanionStat.LIFESTEAL_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.LifeSteal) * 100,
        )
        add(
            CompanionStat.BOSS_DAMAGE_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.BossDamage) * 100,
        )
        val cooldownAffix = affixSum(EquipmentStat.Cooldown)
        add(
            CompanionStat.COOLDOWN_REDUCTION_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            cooldownAffix * 100,
        )
        add(
            CompanionStat.ATTACK_SPEED_BPS,
            CompanionStatSource.EQUIPMENT,
            "Affixes",
            affixSum(EquipmentStat.AttackSpeedPercent),
        )

        // --- item evolution branch tags (stage-3+ specialization, data-driven) ---
        for (item in items) {
            val branch = item.evolutionBranch?.let(ItemEvolutionCatalog.branchesById::get)
            if (branch == null) continue
            for (tag in branch.statTags) {
                when (tag) {
                    "offensive" ->
                        add(
                            CompanionStat.DAMAGE_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    "critical" -> {
                        add(
                            CompanionStat.CRITICAL_CHANCE_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                        add(
                            CompanionStat.CRITICAL_DAMAGE_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    }
                    "penetration" ->
                        add(
                            CompanionStat.RANGED_PENETRATION_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            1_000,
                        )
                    "defensive" ->
                        add(
                            CompanionStat.DAMAGE_REDUCTION_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    "health" ->
                        add(
                            CompanionStat.MAX_HEALTH,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            50,
                        )
                    "mitigation" ->
                        add(
                            CompanionStat.DAMAGE_REDUCTION_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    "utility" ->
                        add(
                            CompanionStat.ABILITY_POWER_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    "cooldown" ->
                        add(
                            CompanionStat.COOLDOWN_REDUCTION_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                    "support" ->
                        add(
                            CompanionStat.HEALING_POWER_BPS,
                            CompanionStatSource.EQUIPMENT,
                            branch.name,
                            500,
                        )
                }
            }
        }

        // --- talents (keyed by catalog effectKey so new talent branches feed the sheet) ---
        for (talent in companion.talents) {
            val definition = CompanionTalentCatalog.byId[talent.definitionId] ?: continue
            val ranks = talent.ranks
            val label =
                talent.definitionId.split('-').joinToString(" ") {
                    it.replaceFirstChar(Char::uppercase)
                }
            when (definition.effectKey) {
                "max-health-percent" ->
                    add(CompanionStat.MAX_HEALTH, CompanionStatSource.TALENT, label, ranks * 10)
                "damage-reduction-percent" -> {
                    add(CompanionStat.MAX_HEALTH, CompanionStatSource.TALENT, label, ranks * 5)
                    add(
                        CompanionStat.DAMAGE_REDUCTION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 200,
                    )
                }
                "taunt-radius" ->
                    add(
                        CompanionStat.THREAT_GENERATION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 500,
                    )
                "owner-damage-shield" ->
                    add(CompanionStat.MAX_HEALTH, CompanionStatSource.TALENT, label, ranks * 15)
                "boss-damage-reduction" ->
                    add(
                        CompanionStat.DAMAGE_REDUCTION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "death-prevention" ->
                    add(CompanionStat.MAX_HEALTH, CompanionStatSource.TALENT, label, ranks * 50)
                "heal-power" ->
                    add(
                        CompanionStat.HEALING_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 500,
                    )
                "owner-stat-buff" ->
                    add(
                        CompanionStat.HEALING_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "shield-power" -> {
                    add(
                        CompanionStat.HEALING_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 400,
                    )
                    add(
                        CompanionStat.SHIELD_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 400,
                    )
                }
                "heal-over-time" ->
                    add(CompanionStat.HEALTH_REGEN, CompanionStatSource.TALENT, label, ranks)
                "ability-cooldown" ->
                    add(
                        CompanionStat.COOLDOWN_REDUCTION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 200,
                    )
                "owner-death-save" ->
                    add(
                        CompanionStat.HEALING_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 2_500,
                    )
                "damage-percent" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 500)
                "low-health-damage" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 400)
                "attack-speed" ->
                    add(
                        CompanionStat.COOLDOWN_REDUCTION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 1_000,
                    )
                "critical-rate" ->
                    add(
                        CompanionStat.CRITICAL_CHANCE_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 200,
                    )
                "ability-power" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 300)
                "life-steal" ->
                    add(CompanionStat.LIFESTEAL_BPS, CompanionStatSource.TALENT, label, ranks * 200)
                "proc-chance" ->
                    add(
                        CompanionStat.CRITICAL_CHANCE_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 100,
                    )
                "burst-ability" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 2_500)
                // extended talent branches
                "armour-percent" ->
                    add(CompanionStat.ARMOUR, CompanionStatSource.TALENT, label, ranks * 3)
                "block-chance" ->
                    add(
                        CompanionStat.BLOCK_CHANCE_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 200,
                    )
                "frontline-damage" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 200)
                "shield-recharge" ->
                    add(
                        CompanionStat.SHIELD_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        200 * ranks,
                    )
                "passive-healing" ->
                    add(CompanionStat.HEALTH_REGEN, CompanionStatSource.TALENT, label, ranks)
                "single-target-heal" ->
                    add(
                        CompanionStat.HEALING_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "group-stat-buff" ->
                    add(
                        CompanionStat.BUFF_POWER_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "recovery-speed" ->
                    add(CompanionStat.HEALTH_REGEN, CompanionStatSource.TALENT, label, ranks)
                "shared-vitality" ->
                    add(
                        CompanionStat.INCOMING_HEALING_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "cooldown-weaving" ->
                    add(
                        CompanionStat.COOLDOWN_REDUCTION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 200,
                    )
                "accuracy-percent" -> {
                    add(CompanionStat.MELEE_ACCURACY, CompanionStatSource.TALENT, label, ranks * 3)
                    add(CompanionStat.RANGED_ACCURACY, CompanionStatSource.TALENT, label, ranks * 3)
                    add(CompanionStat.MAGIC_ACCURACY, CompanionStatSource.TALENT, label, ranks * 3)
                }
                "target-damage" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 200)
                "siphoning-blows" ->
                    add(CompanionStat.LIFESTEAL_BPS, CompanionStatSource.TALENT, label, ranks * 200)
                "exposed-weakness" ->
                    add(
                        CompanionStat.RANGED_PENETRATION_BPS,
                        CompanionStatSource.TALENT,
                        label,
                        ranks * 300,
                    )
                "damage-vs-wounded" ->
                    add(CompanionStat.DAMAGE_BPS, CompanionStatSource.TALENT, label, ranks * 200)
                "weapon-mastery" -> {
                    add(CompanionStat.MELEE_POWER, CompanionStatSource.TALENT, label, ranks * 2)
                    add(CompanionStat.RANGED_POWER, CompanionStatSource.TALENT, label, ranks * 2)
                    add(CompanionStat.MAGIC_POWER, CompanionStatSource.TALENT, label, ranks * 2)
                }
            }
        }

        // --- evolution stage ---
        val evolution = CompanionEvolutionCatalog.stage(companion.evolutionStage)
        if (evolution != null) {
            add(
                CompanionStat.DAMAGE_BPS,
                CompanionStatSource.EVOLUTION,
                evolution.title,
                evolution.statBonusPercent * 100,
            )
            add(
                CompanionStat.HEALING_POWER_BPS,
                CompanionStatSource.EVOLUTION,
                evolution.title,
                evolution.statBonusPercent * 100,
            )
            add(
                CompanionStat.MAX_HEALTH,
                CompanionStatSource.EVOLUTION,
                evolution.title,
                companion.maximumHitpoints * evolution.statBonusPercent / 100,
            )
        }

        // --- set bonuses (after flat sources, before caps) ---
        val activeSets = CompanionSetCatalog.activations(items)
        for (activation in activeSets) {
            for (bonus in activation.activeBonuses) {
                add(
                    bonus.stat,
                    CompanionStatSource.SET,
                    "${activation.definition.name} (${bonus.pieces})",
                    bonus.value,
                )
            }
        }

        for (augmenter in augmenters) {
            augmenter.augment(companion) { stat, value, label ->
                add(stat, CompanionStatSource.FRAGMENT, label, value)
            }
        }

        // --- caps (single choke point for multiplicative stats) ---
        totals[CompanionStat.DAMAGE_REDUCTION_BPS] =
            (totals[CompanionStat.DAMAGE_REDUCTION_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_DAMAGE_REDUCTION_BPS,
            )
        totals[CompanionStat.COOLDOWN_REDUCTION_BPS] =
            (totals[CompanionStat.COOLDOWN_REDUCTION_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_COOLDOWN_REDUCTION_BPS,
            )
        totals[CompanionStat.CRITICAL_CHANCE_BPS] =
            (totals[CompanionStat.CRITICAL_CHANCE_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_CRITICAL_CHANCE_BPS,
            )
        totals[CompanionStat.LIFESTEAL_BPS] =
            (totals[CompanionStat.LIFESTEAL_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_LIFESTEAL_BPS,
            )
        totals[CompanionStat.BLOCK_CHANCE_BPS] =
            (totals[CompanionStat.BLOCK_CHANCE_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_BLOCK_CHANCE_BPS,
            )
        for (stat in
            listOf(
                CompanionStat.MELEE_RESISTANCE_BPS,
                CompanionStat.RANGED_RESISTANCE_BPS,
                CompanionStat.MAGIC_RESISTANCE_BPS,
            )) {
            totals[stat] =
                (totals[stat] ?: 0).coerceIn(0, CompanionStatCaps.MAX_STYLE_RESISTANCE_BPS)
        }
        totals[CompanionStat.RANGED_PENETRATION_BPS] =
            (totals[CompanionStat.RANGED_PENETRATION_BPS] ?: 0).coerceIn(
                0,
                CompanionStatCaps.MAX_PENETRATION_BPS,
            )

        val damageBps = totals[CompanionStat.DAMAGE_BPS] ?: 10_000
        val healBps = totals[CompanionStat.HEALING_POWER_BPS] ?: 10_000
        val cdrBps = totals[CompanionStat.COOLDOWN_REDUCTION_BPS] ?: 0

        val combat =
            CompanionCombatStats(
                maximumHitpoints = (totals[CompanionStat.MAX_HEALTH] ?: 1).coerceAtLeast(1),
                damageMultiplier = (damageBps / 10_000.0).coerceAtLeast(0.1),
                supportPower = (healBps / 10_000.0).coerceAtLeast(0.0),
                attackDelay =
                    (4 - cdrBps / 1_000).coerceIn(
                        CompanionStatCaps.MIN_ATTACK_DELAY_TICKS,
                        CompanionStatCaps.MAX_ATTACK_DELAY_TICKS,
                    ),
            )

        return CompanionStatSheet(
            companion.id,
            totals,
            breakdown.mapValues { it.value.toList() },
            combat,
            activeSets,
        )
    }
}

/**
 * Memoizes [CompanionStatCalculator] output per companion id. Because [Companion] is an immutable
 * data class, any change to level, gear, talents or evolution produces a different key and the
 * sheet is recomputed - no manual invalidation calls are needed.
 *
 * Equipped item *contents* are part of the key too: Forge upgrades/reforges mutate an instance in
 * place (same `instanceId`, new `revision`), so the companion record alone would keep serving the
 * stale sheet. Folding each equipped instance's revision into the key makes such mutations
 * invalidate immediately.
 */
public class CompanionStatCache(
    private val augmenters: Set<CompanionStatAugmenter> = emptySet(),
) {
    private data class Key(
        val companion: Companion,
        val skillLevels: Map<CompanionSkill, Int>,
        val gearStamp: String,
        val augmenterStamp: String,
    )

    private val sheets = HashMap<Long, Pair<Key, CompanionStatSheet>>()

    public fun sheet(
        companion: Companion,
        registry: EquipmentInstanceRegistry,
        skillLevels: Map<CompanionSkill, Int>,
    ): CompanionStatSheet {
        val gearStamp =
            companion.gearInstanceIds.joinToString(",") { id ->
                "$id:${registry[id]?.revision ?: -1L}"
            }
        val augmenterStamp = augmenters.joinToString("|") { it.cacheStamp(companion) }
        val key = Key(companion, skillLevels, gearStamp, augmenterStamp)
        val cached = sheets[companion.id]
        if (cached != null && cached.first == key) {
            return cached.second
        }
        val sheet = CompanionStatCalculator.calculate(companion, registry, skillLevels, augmenters)
        sheets[companion.id] = key to sheet
        return sheet
    }

    public fun clear() {
        sheets.clear()
    }
}
