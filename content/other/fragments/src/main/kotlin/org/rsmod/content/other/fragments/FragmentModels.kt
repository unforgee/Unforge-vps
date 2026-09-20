package org.rsmod.content.other.fragments

/** The only progression ceiling in the fragment system. There are no unlock trees or points. */
public object FragmentConfig {
    public const val MAX_LEVEL: Int = 10
    public const val SET_SIZE: Int = 5
    public const val DUPLICATE_XP: Int = 250
    public const val NPC_DROP_CHANCE_BPS: Int = 125
    public const val ELITE_DROP_CHANCE_BPS: Int = 350
    public const val BOSS_DROP_CHANCE_BPS: Int = 750
}

/** Effects are deliberately data, so new fragments can be added without new combat classes. */
public sealed interface FragmentEffect {
    public data class Stat(public val stat: FragmentStat, public val amount: Int) : FragmentEffect

    public data class Proc(
        public val kind: FragmentProc,
        public val chanceBps: Int,
        public val powerBps: Int,
        public val cooldownTicks: Int = 0,
    ) : FragmentEffect
}

/** Shared stat vocabulary for player, item-instance and companion integrations. */
public enum class FragmentStat {
    PLAYER_DAMAGE_BPS,
    PLAYER_ACCURACY_BPS,
    PLAYER_DEFENCE_BPS,
    PLAYER_CRITICAL_CHANCE_BPS,
    PLAYER_DROP_RATE_BPS,
    ITEM_PROC_CHANCE_BPS,
    ITEM_QUALITY_BPS,
    LEGENDARY_CHANCE_BPS,
    MYTHIC_CHANCE_BPS,
    COMPANION_DAMAGE_BPS,
    COMPANION_DEFENCE_BPS,
    COMPANION_ATTACK_SPEED_BPS,
    COMPANION_COOLDOWN_REDUCTION_BPS,
    COMPANION_ABILITY_POWER_BPS,
    COMPANION_ABILITY_PROC_CHANCE_BPS,
    COMPANION_INVENTORY_SLOTS,
}

/** Proc vocabulary covers the effect families requested for the first 250-fragment catalogue. */
public enum class FragmentProc {
    AOE_DAMAGE,
    BLEED,
    FREEZE,
    FORCED_MOVEMENT,
    COMPANION_EXTRA_ATTACK,
    COMPANION_ABILITY,
    ITEM_BONUS,
}

public data class FragmentDefinition(
    public val id: String,
    public val name: String,
    public val setId: String,
    public val slot: Int,
    public val effects: List<FragmentEffect>,
)

public data class FragmentSetBonus(
    public val setId: String,
    public val name: String,
    public val description: String,
    public val effects: List<FragmentEffect>,
)

public data class FragmentSetDefinition(
    public val id: String,
    public val name: String,
    public val fragments: List<FragmentDefinition>,
    public val bonus: FragmentSetBonus,
)

public data class FragmentProgress(public val xp: Int = 0, public val level: Int = 1)

public data class FragmentState(
    public val progress: Map<String, FragmentProgress> = emptyMap(),
    public val revision: Long = 0L,
)

public object FragmentXp {
    private val thresholds: IntArray =
        intArrayOf(0, 0, 250, 600, 1_050, 1_650, 2_400, 3_300, 4_350, 5_550, 6_900)

    public fun levelFor(xp: Int): Int {
        val safeXp = xp.coerceAtLeast(0)
        return thresholds.indexOfLast { it <= safeXp }.coerceIn(1, FragmentConfig.MAX_LEVEL)
    }

    public fun xpForLevel(level: Int): Int = thresholds[level.coerceIn(1, FragmentConfig.MAX_LEVEL)]

    public fun capXp(xp: Int): Int = xp.coerceIn(0, xpForLevel(FragmentConfig.MAX_LEVEL))

    public fun add(progress: FragmentProgress, amount: Int): FragmentProgress {
        val xp =
            (progress.xp.toLong() + amount.coerceAtLeast(0).toLong())
                .coerceAtMost(xpForLevel(FragmentConfig.MAX_LEVEL).toLong())
                .toInt()
        return FragmentProgress(xp, levelFor(xp))
    }
}

/** Aggregated, read-only view used by item, companion and future combat consumers. */
public class FragmentEffectSnapshot
internal constructor(
    public val stats: Map<FragmentStat, Int>,
    public val procs: List<FragmentEffect.Proc>,
    public val completeSets: Set<String>,
    public val ownedCount: Int,
) {
    public fun value(stat: FragmentStat): Int = stats[stat] ?: 0

    public fun hasProc(kind: FragmentProc): Boolean = procs.any { it.kind == kind }
}

private data class FragmentSetSpec(
    val id: String,
    val name: String,
    val stat: FragmentStat,
    val bonus: FragmentEffect,
    val suffixes: List<String> = listOf("Spark", "Core", "Edge", "Echo", "Crown"),
)

/** The complete server catalogue: exactly 50 sets x 5 fragments = 250 fragments. */
public object FragmentCatalog {
    private val specs =
        listOf(
            FragmentSetSpec(
                "ember",
                "Ember",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 1_000, 1_500),
            ),
            FragmentSetSpec(
                "frost",
                "Frost",
                FragmentStat.PLAYER_ACCURACY_BPS,
                FragmentEffect.Proc(FragmentProc.FREEZE, 850, 1_000, 8),
            ),
            FragmentSetSpec(
                "venom",
                "Venom",
                FragmentStat.PLAYER_CRITICAL_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.BLEED, 1_250, 1_200, 4),
            ),
            FragmentSetSpec(
                "storm",
                "Storm",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.FORCED_MOVEMENT, 700, 900, 6),
            ),
            FragmentSetSpec(
                "titan",
                "Titan",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DEFENCE_BPS, 1_000),
            ),
            FragmentSetSpec(
                "rift",
                "Rift",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 800, 2_000),
            ),
            FragmentSetSpec(
                "lunar",
                "Lunar",
                FragmentStat.PLAYER_DROP_RATE_BPS,
                FragmentEffect.Stat(FragmentStat.ITEM_QUALITY_BPS, 1_200),
            ),
            FragmentSetSpec(
                "solar",
                "Solar",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Stat(FragmentStat.LEGENDARY_CHANCE_BPS, 650),
            ),
            FragmentSetSpec(
                "void",
                "Void",
                FragmentStat.PLAYER_CRITICAL_CHANCE_BPS,
                FragmentEffect.Stat(FragmentStat.MYTHIC_CHANCE_BPS, 300),
            ),
            FragmentSetSpec(
                "iron",
                "Iron",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DEFENCE_BPS, 1_500),
            ),
            FragmentSetSpec(
                "hunter",
                "Hunter",
                FragmentStat.PLAYER_ACCURACY_BPS,
                FragmentEffect.Stat(FragmentStat.ITEM_PROC_CHANCE_BPS, 900),
            ),
            FragmentSetSpec(
                "executioner",
                "Executioner",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.BLEED, 1_000, 1_800, 5),
            ),
            FragmentSetSpec(
                "guardian",
                "Guardian",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Proc(FragmentProc.FORCED_MOVEMENT, 600, 800, 8),
            ),
            FragmentSetSpec(
                "oracle",
                "Oracle",
                FragmentStat.PLAYER_DROP_RATE_BPS,
                FragmentEffect.Stat(FragmentStat.MYTHIC_CHANCE_BPS, 240),
            ),
            FragmentSetSpec(
                "forge",
                "Forge",
                FragmentStat.ITEM_QUALITY_BPS,
                FragmentEffect.Stat(FragmentStat.ITEM_QUALITY_BPS, 1_800),
            ),
            FragmentSetSpec(
                "relic",
                "Relic",
                FragmentStat.LEGENDARY_CHANCE_BPS,
                FragmentEffect.Stat(FragmentStat.LEGENDARY_CHANCE_BPS, 900),
            ),
            FragmentSetSpec(
                "gambit",
                "Gambit",
                FragmentStat.PLAYER_CRITICAL_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.ITEM_BONUS, 750, 1_500),
            ),
            FragmentSetSpec(
                "precision",
                "Precision",
                FragmentStat.PLAYER_ACCURACY_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_ACCURACY_BPS, 1_400),
            ),
            FragmentSetSpec(
                "blood",
                "Blood",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.BLEED, 1_400, 1_000, 3),
            ),
            FragmentSetSpec(
                "bulwark",
                "Bulwark",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DEFENCE_BPS, 2_000),
            ),
            FragmentSetSpec(
                "familiar",
                "Familiar",
                FragmentStat.COMPANION_DAMAGE_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_DAMAGE_BPS, 1_200),
            ),
            FragmentSetSpec(
                "packmaster",
                "Packmaster",
                FragmentStat.COMPANION_INVENTORY_SLOTS,
                FragmentEffect.Stat(FragmentStat.COMPANION_INVENTORY_SLOTS, 8),
            ),
            FragmentSetSpec(
                "bond",
                "Bond",
                FragmentStat.COMPANION_ABILITY_POWER_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_ABILITY_POWER_BPS, 1_500),
            ),
            FragmentSetSpec(
                "hastened",
                "Hastened",
                FragmentStat.COMPANION_ATTACK_SPEED_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_ATTACK_SPEED_BPS, 1_200),
            ),
            FragmentSetSpec(
                "tactician",
                "Tactician",
                FragmentStat.COMPANION_COOLDOWN_REDUCTION_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_COOLDOWN_REDUCTION_BPS, 1_000),
            ),
            FragmentSetSpec(
                "conductor",
                "Conductor",
                FragmentStat.COMPANION_ABILITY_PROC_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.COMPANION_ABILITY, 1_250, 1_500, 10),
            ),
            FragmentSetSpec(
                "vanguard",
                "Vanguard",
                FragmentStat.COMPANION_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.COMPANION_EXTRA_ATTACK, 900, 1_000, 6),
            ),
            FragmentSetSpec(
                "mender",
                "Mender",
                FragmentStat.COMPANION_ABILITY_POWER_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_ABILITY_POWER_BPS, 1_800),
            ),
            FragmentSetSpec(
                "siege",
                "Siege",
                FragmentStat.COMPANION_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 850, 1_600),
            ),
            FragmentSetSpec(
                "chillguard",
                "Chillguard",
                FragmentStat.COMPANION_DEFENCE_BPS,
                FragmentEffect.Proc(FragmentProc.FREEZE, 700, 800, 8),
            ),
            FragmentSetSpec(
                "mythic",
                "Mythic",
                FragmentStat.MYTHIC_CHANCE_BPS,
                FragmentEffect.Stat(FragmentStat.MYTHIC_CHANCE_BPS, 500),
            ),
            FragmentSetSpec(
                "legend",
                "Legend",
                FragmentStat.LEGENDARY_CHANCE_BPS,
                FragmentEffect.Stat(FragmentStat.LEGENDARY_CHANCE_BPS, 1_100),
            ),
            FragmentSetSpec(
                "treasure",
                "Treasure",
                FragmentStat.PLAYER_DROP_RATE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DROP_RATE_BPS, 1_800),
            ),
            FragmentSetSpec(
                "scavenger",
                "Scavenger",
                FragmentStat.PLAYER_DROP_RATE_BPS,
                FragmentEffect.Stat(FragmentStat.ITEM_PROC_CHANCE_BPS, 1_200),
            ),
            FragmentSetSpec(
                "overcharge",
                "Overcharge",
                FragmentStat.ITEM_PROC_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.ITEM_BONUS, 1_400, 1_300),
            ),
            FragmentSetSpec(
                "assault",
                "Assault",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 650, 2_400),
            ),
            FragmentSetSpec(
                "ward",
                "Ward",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Proc(FragmentProc.FORCED_MOVEMENT, 550, 1_200, 10),
            ),
            FragmentSetSpec(
                "focus",
                "Focus",
                FragmentStat.PLAYER_ACCURACY_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_ACCURACY_BPS, 1_800),
            ),
            FragmentSetSpec(
                "rupture",
                "Rupture",
                FragmentStat.PLAYER_CRITICAL_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.BLEED, 1_100, 2_000, 5),
            ),
            FragmentSetSpec(
                "aegis",
                "Aegis",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DEFENCE_BPS, 2_400),
            ),
            FragmentSetSpec(
                "wildcall",
                "Wildcall",
                FragmentStat.COMPANION_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.COMPANION_EXTRA_ATTACK, 1_100, 1_200, 5),
            ),
            FragmentSetSpec(
                "arsenal",
                "Arsenal",
                FragmentStat.ITEM_PROC_CHANCE_BPS,
                FragmentEffect.Stat(FragmentStat.ITEM_PROC_CHANCE_BPS, 1_600),
            ),
            FragmentSetSpec(
                "chronicle",
                "Chronicle",
                FragmentStat.COMPANION_COOLDOWN_REDUCTION_BPS,
                FragmentEffect.Stat(FragmentStat.COMPANION_COOLDOWN_REDUCTION_BPS, 1_500),
            ),
            FragmentSetSpec(
                "ascendant",
                "Ascendant",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DAMAGE_BPS, 2_000),
            ),
            FragmentSetSpec(
                "cataclysm",
                "Cataclysm",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 500, 3_000),
            ),
            FragmentSetSpec(
                "tempest",
                "Tempest",
                FragmentStat.PLAYER_ACCURACY_BPS,
                FragmentEffect.Proc(FragmentProc.FORCED_MOVEMENT, 900, 1_000, 7),
            ),
            FragmentSetSpec(
                "hallowed",
                "Hallowed",
                FragmentStat.PLAYER_DEFENCE_BPS,
                FragmentEffect.Stat(FragmentStat.PLAYER_DEFENCE_BPS, 2_800),
            ),
            FragmentSetSpec(
                "nightfall",
                "Nightfall",
                FragmentStat.MYTHIC_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.ITEM_BONUS, 900, 2_000),
            ),
            FragmentSetSpec(
                "dawnfire",
                "Dawnfire",
                FragmentStat.LEGENDARY_CHANCE_BPS,
                FragmentEffect.Proc(FragmentProc.ITEM_BONUS, 1_000, 1_800),
            ),
            FragmentSetSpec(
                "worldeater",
                "World Eater",
                FragmentStat.PLAYER_DAMAGE_BPS,
                FragmentEffect.Proc(FragmentProc.AOE_DAMAGE, 400, 4_000),
            ),
        )

    public val sets: List<FragmentSetDefinition> =
        specs.map { spec ->
            val fragments =
                spec.suffixes.mapIndexed { index, suffix ->
                    val id = "${spec.id}-${index + 1}"
                    FragmentDefinition(
                        id = id,
                        name = "${spec.name} $suffix",
                        setId = spec.id,
                        slot = index + 1,
                        effects = listOf(FragmentEffect.Stat(spec.stat, 100 + index * 50)),
                    )
                }
            FragmentSetDefinition(
                id = spec.id,
                name = "${spec.name} Set",
                fragments = fragments,
                bonus =
                    FragmentSetBonus(
                        spec.id,
                        "${spec.name} Set Bonus",
                        "All five ${spec.name} fragments are collected.",
                        listOf(spec.bonus),
                    ),
            )
        }

    public val fragments: List<FragmentDefinition> = sets.flatMap(FragmentSetDefinition::fragments)
    public val fragmentsById: Map<String, FragmentDefinition> =
        fragments.associateBy(FragmentDefinition::id)
    public val setsById: Map<String, FragmentSetDefinition> =
        sets.associateBy(FragmentSetDefinition::id)

    init {
        check(sets.size == 50) { "Fragment catalogue must contain 50 sets" }
        check(fragments.size == 250) { "Fragment catalogue must contain 250 fragments" }
        check(
            fragments.groupingBy(FragmentDefinition::setId).eachCount().values.all {
                it == FragmentConfig.SET_SIZE
            }
        )
    }
}
