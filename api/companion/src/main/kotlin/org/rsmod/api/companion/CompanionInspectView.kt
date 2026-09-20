package org.rsmod.api.companion

/**
 * Server-side view model for the `companion_inspect` Hub screen. Every displayed value is derived
 * from the canonical [CompanionStatSheet] plus persisted progression data - the interface builder
 * only lays out text rows; nothing combat-facing is computed client-side.
 */
public class CompanionInspectView(
    public val subtitle: String,
    public val identityLines: List<String>,
    public val evolutionLines: List<String>,
    public val skillLines: List<String>,
    public val setLines: List<String>,
    public val statLines: List<String>,
    public val abilityLines: List<String>,
    public val talentLines: List<String>,
    public val status: String,
)

/** Builds [CompanionInspectView] rows from canonical domain state. Pure - unit-testable. */
public object CompanionInspectPresenter {
    /** Stat rows shown in the inspect view, in display order. */
    private val STAT_ROWS: List<Pair<String, CompanionStat>> =
        listOf(
            "Damage" to CompanionStat.DAMAGE_BPS,
            "Attack speed" to CompanionStat.ATTACK_SPEED_BPS,
            "Crit chance" to CompanionStat.CRITICAL_CHANCE_BPS,
            "Penetration" to CompanionStat.RANGED_PENETRATION_BPS,
            "Max health" to CompanionStat.MAX_HEALTH,
            "Armour" to CompanionStat.ARMOUR,
            "Dmg reduction" to CompanionStat.DAMAGE_REDUCTION_BPS,
            "Block chance" to CompanionStat.BLOCK_CHANCE_BPS,
            "Healing power" to CompanionStat.HEALING_POWER_BPS,
            "Threat gen" to CompanionStat.THREAT_GENERATION_BPS,
            "Cooldown red" to CompanionStat.COOLDOWN_REDUCTION_BPS,
            "Lifesteal" to CompanionStat.LIFESTEAL_BPS,
        )

    /** Short source tags used inside stat breakdown brackets. */
    private fun sourceTag(source: CompanionStatSource): String =
        when (source) {
            CompanionStatSource.BASE -> "base"
            CompanionStatSource.LEVEL -> "lvl"
            CompanionStatSource.PROGRESSION -> "skill"
            CompanionStatSource.EQUIPMENT -> "gear"
            CompanionStatSource.SET -> "set"
            CompanionStatSource.TALENT -> "tal"
            CompanionStatSource.EVOLUTION -> "evo"
            CompanionStatSource.BUFF -> "buff"
            CompanionStatSource.FRAGMENT -> "frag"
        }

    private fun formatValue(stat: CompanionStat, raw: Int): String =
        when (stat.format) {
            CompanionStatFormat.PERCENT_BPS -> "${raw / 100}%"
            CompanionStatFormat.FLAT -> "$raw"
        }

    /**
     * "Damage: 195% {lvl+40 gear+25 set+10 tal+5 evo+15}" - the total plus every non-base
     * contribution grouped by source so the whole pipeline is auditable from one line.
     */
    private fun statLine(label: String, stat: CompanionStat, sheet: CompanionStatSheet): String {
        val total = sheet.value(stat)
        val contributions = sheet.breakdown(stat)
        val suffix =
            contributions
                .groupBy { it.source }
                .filterKeys { it != CompanionStatSource.BASE }
                .entries
                .joinToString(" ") { (source, rows) ->
                    val sum = rows.sumOf { it.value }
                    "${sourceTag(source)}${formatValue(stat, sum)}"
                }
        return if (suffix.isEmpty()) {
            "$label: ${formatValue(stat, total)}"
        } else {
            "$label: ${formatValue(stat, total)} {$suffix}"
        }
    }

    /**
     * @param skillExperience per-track stored xp (from `CompanionSkillService.experienceMap`).
     * @param abilityCooldownsMillis `abilityId -> remaining cooldown ms` for the loadout.
     * @param equippedCount number of worn gear instances.
     * @param bestGearRarity display name of the highest-rarity equipped item, or `null`.
     */
    public fun present(
        companion: Companion,
        sheet: CompanionStatSheet,
        skillExperience: Map<CompanionSkill, Long>,
        abilityCooldownsMillis: Map<String, Long>,
        equippedCount: Int,
        bestGearRarity: String?,
    ): CompanionInspectView {
        val raritySuffix = bestGearRarity?.let { " (best $it)" } ?: ""
        val identity =
            listOf(
                "Class: ${companion.companionClass.name}",
                "Style: ${companion.attackStyle.name}",
                "Mode: ${companion.combatMode.name}",
                "Gear: $equippedCount/${CompanionRules.MAX_GEAR_ITEMS}$raritySuffix",
            )

        val stage = CompanionEvolutionCatalog.stage(companion.evolutionStage)
        val next = CompanionEvolutionCatalog.nextStage(companion.evolutionStage)
        val evolution = buildList {
            add(
                "Stage: ${stage?.title ?: "Base"} " +
                    "(${companion.evolutionStage}/${CompanionEvolutionCatalog.MAX_STAGE})"
            )
            if (stage != null) {
                add("Bonus: +${stage.statBonusPercent}% dmg/heal/hp")
            }
            if (next == null) {
                add("Next: MAX STAGE")
            } else {
                add("Next: ${next.title} @ Lv${next.requiredLevel}")
                val missing = next.requiredLevel - companion.level
                add(
                    if (missing <= 0) "Status: READY - evolve now"
                    else "Status: need $missing more levels"
                )
                add(
                    "Gain: +${next.statBonusPercent}% dmg/heal/hp, " +
                        "+${next.talentPointsBonus} tp"
                )
            }
        }

        val skills =
            CompanionSkill.entries.map { skill ->
                val xp = skillExperience[skill] ?: 0L
                val level = CompanionSkillXp.levelFor(xp)
                if (level >= CompanionSkillXp.MAX_LEVEL) {
                    "${skill.name}  Lv$level MAX"
                } else {
                    // cumulative xp at `level`: sum_{i=1}^{level-1} 100*i = 50*level*(level-1)
                    val into = xp - 50L * level * (level - 1)
                    val need = CompanionRules.experienceToNextLevel(level)
                    val pct = (into * 100 / need).coerceIn(0, 99)
                    "${skill.name}  Lv$level  $pct% > ${level + 1}"
                }
            }

        val sets =
            sheet.activeSets.map { activation ->
                val def = activation.definition
                val active = activation.activeBonuses.maxOfOrNull { it.pieces } ?: 0
                if (active > 0) {
                    "${def.name} ${activation.equippedPieces}p T$active"
                } else {
                    val nextTier = def.bonuses.minOfOrNull { it.pieces } ?: 0
                    "${def.name} LOCKED ${activation.equippedPieces}/$nextTier"
                }
            }

        val stats =
            STAT_ROWS.map { (label, stat) -> statLine(label, stat, sheet) } +
                ("Resist M/R/M: " +
                    "${sheet.value(CompanionStat.MELEE_RESISTANCE_BPS) / 100}/" +
                    "${sheet.value(CompanionStat.RANGED_RESISTANCE_BPS) / 100}/" +
                    "${sheet.value(CompanionStat.MAGIC_RESISTANCE_BPS) / 100}%")

        val abilities =
            if (companion.abilityLoadout.isEmpty()) {
                listOf("No abilities equipped")
            } else {
                companion.abilityLoadout.map { id ->
                    val name = CompanionAbilityCatalog.byId[id]?.name ?: id
                    val cd = abilityCooldownsMillis[id] ?: 0L
                    if (cd <= 0L) "$name: READY" else "$name: ${(cd + 999) / 1000}s"
                }
            }

        val talents =
            if (companion.talents.isEmpty()) {
                listOf("No talents allocated")
            } else {
                companion.talents.take(2).map { "${it.definitionId} r${it.ranks}" } +
                    listOfNotNull(
                        (companion.talents.size - 2).takeIf { it > 0 }?.let { "+$it more" }
                    )
            }

        return CompanionInspectView(
            subtitle =
                "${companion.name} - ${companion.companionClass.name} " +
                    "Lv${companion.level} - ${companion.state.name}",
            identityLines = identity,
            evolutionLines = evolution,
            skillLines = skills,
            setLines = if (sets.isEmpty()) listOf("No set pieces equipped") else sets,
            statLines = stats,
            abilityLines = abilities,
            talentLines = talents,
            status = "All values live from the canonical stat sheet",
        )
    }
}
