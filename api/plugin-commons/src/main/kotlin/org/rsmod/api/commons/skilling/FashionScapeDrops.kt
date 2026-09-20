package org.rsmod.api.commons.skilling

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.config.constants
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.ModifierUnit
import org.rsmod.api.equipment.instance.SkillAffixRoll
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * FashionScape: wearable cosmetic equipment rolled as skilling-activity drops.
 *
 * Every successful skill action may roll a cosmetic drop (see [rollDrop]). The dropped item is a
 * full [org.rsmod.api.equipment.instance.EquipmentInstance] carrying [SkillAffixRoll]s - skilling
 * stats such as noted-chance, direct-bank, double/triple output and skill-specific bonuses - that
 * apply only while the piece is worn (see [org.rsmod.api.equipment.instance.WornSkillBonuses]) and
 * are shown in the item-instance hover.
 *
 * Pools are resolved lazily through [NameMapping]; cache symbols that do not exist are skipped, so
 * the catalog degrades gracefully instead of crashing startup.
 */
@Singleton
public class FashionScapeDrops
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val names: NameMapping,
    private val objRepo: ObjRepository,
    private val instances: EquipmentInstanceService,
    private val wornSkills: org.rsmod.api.equipment.instance.WornSkillBonuses,
    private val players: PlayerList,
    private val random: GameRandom,
) {
    private val genericPool: List<UnpackedObjType> by lazy { resolveAll(GENERIC_POOL) }
    private val skillPools: Map<String, List<UnpackedObjType>> by lazy {
        SKILL_POOLS.mapValues { (_, symbols) -> resolveAll(symbols) }
    }

    /**
     * Rolls a cosmetic drop after a successful [skill] action. Base chance is [BASE_DROP_BPS]; worn
     * gear raises it via `COSMETIC_DROP_CHANCE` and biases rarity via `RARE_COSMETIC_CHANCE`.
     */
    public fun rollDrop(access: ProtectedAccess, skill: String) {
        val player = access.player
        val chanceBps = BASE_DROP_BPS + wornSkills.value(player, skill, "COSMETIC_DROP_CHANCE")
        if (access.random.of(ROLL_BOUND) >= chanceBps.coerceAtMost(MAX_DROP_BPS)) {
            return
        }
        val rareBonusBps = wornSkills.value(player, skill, "RARE_COSMETIC_CHANCE")
        grant(player, skill, rareBonusBps)
    }

    /**
     * Immediately rolls and grants a FashionScape cosmetic to [player]. Exposed for admin/testing
     * (e.g. the `::fashiondrop` command) and for direct skill hooks.
     */
    public fun grant(player: Player, skill: String, rareBonusBps: Int = 0) {
        val type = pickType(skill) ?: return
        val rarity = pickRarity(rareBonusBps)
        val affixes = rollAffixes(skill, rarity)
        val uid = player.uid
        val coords = player.coords
        instances.rollAndPersist(
            type,
            source = "fashionscape:${skill.lowercase()}",
            tier = EquipmentTierResolver.resolve(type),
            itemLevel = rarity.ordinal + 1,
            skillAffixes = affixes,
            onFailure = { uid.resolve(players)?.invAddOrDrop(objRepo, type, count = 1) },
        ) { instance ->
            val target = uid.resolve(players) ?: return@rollAndPersist
            val added =
                target.invAdd(
                    target.inv,
                    type,
                    count = 1,
                    instanceId = instance.instanceId,
                    strict = false,
                )
            if (!added.success) {
                val invObj = InvObj(type, count = 1, instanceId = instance.instanceId)
                objRepo.add(
                    invObj,
                    coords,
                    target.lootDropDuration ?: constants.lootdrop_duration,
                    target,
                )
            }
            target.mes(
                "<col=ff66cc>FashionScape! You feel ${rarity.name.lowercase()} power in your " +
                    "${type.name.lowercase()}.</col>"
            )
        }
    }

    private fun pickType(skill: String): UnpackedObjType? {
        val skillPool = skillPools[skill].orEmpty()
        val pool =
            if (skillPool.isNotEmpty() && (genericPool.isEmpty() || random.of(5) < 3)) {
                skillPool
            } else {
                genericPool
            }
        return pool.takeIf { it.isNotEmpty() }?.let { it[random.of(it.size)] }
    }

    /** Weighted rarity roll; [rareBonusBps] shifts the roll towards the rarer end. */
    private fun pickRarity(rareBonusBps: Int): EquipmentRarity {
        val roll =
            (random.of(EquipmentRarity.TOTAL_WEIGHT_BASIS_POINTS) - rareBonusBps).coerceAtLeast(0)
        var cumulative = 0
        for (rarity in EquipmentRarity.entries.sortedByDescending { it.ordinal }) {
            cumulative += rarity.weightBasisPoints
            if (roll < cumulative) {
                return rarity
            }
        }
        return EquipmentRarity.Uncommon
    }

    private fun rollAffixes(skill: String, rarity: EquipmentRarity): List<SkillAffixRoll> {
        val pool = (UNIVERSAL_EFFECTS + SKILL_EFFECTS[skill].orEmpty()).distinct()
        val count =
            (rarity.affixMin + random.of(rarity.affixMax - rarity.affixMin + 1)).coerceIn(
                1,
                pool.size,
            )
        return pool.shuffled(Random(random.of(Int.MAX_VALUE))).take(count).mapIndexed { slot, effect
            ->
            SkillAffixRoll(
                slot = slot,
                skill = if (effect in UNIVERSAL_EFFECTS) "ALL" else skill,
                effect = effect,
                unit = ModifierUnit.BasisPoints,
                magnitude = magnitudeFor(rarity),
            )
        }
    }

    /** Magnitude range scales with rarity: ~0.5%-2% at Uncommon up to ~3%-14% at Jackpot. */
    private fun magnitudeFor(rarity: EquipmentRarity): Int =
        50 + rarity.ordinal * 50 + random.of(150 + rarity.ordinal * 250 + 1)

    private fun resolveAll(symbols: List<String>): List<UnpackedObjType> =
        symbols
            .mapNotNull { names.objs[it] }
            .mapNotNull { objTypes[it] }
            .filter { instances.isInstanceEligible(it) }

    public companion object {
        /** Basis-points roll bound for the drop chance (`10_000` = 100%). */
        public const val ROLL_BOUND: Int = 10_000

        /** Base FashionScape drop chance: 0.35% per successful skilling action. */
        public const val BASE_DROP_BPS: Int = 35

        /** Upper bound for the worn-gear-boosted drop chance (15%). */
        public const val MAX_DROP_BPS: Int = 1_500

        /** Effects every cosmetic can roll; tagged `ALL` so they apply to any skill. */
        public val UNIVERSAL_EFFECTS: List<String> =
            listOf(
                "NOTED_CHANCE",
                "DIRECT_BANK_CHANCE",
                "DOUBLE_OUTPUT_CHANCE",
                "TRIPLE_OUTPUT_CHANCE",
                "EXTRA_OUTPUT_CHANCE",
                "COSMETIC_DROP_CHANCE",
                "RARE_COSMETIC_CHANCE",
            )

        /** Skill-specific effects; tagged with their own skill so they only apply to it. */
        public val SKILL_EFFECTS: Map<String, List<String>> =
            mapOf(
                "WOODCUTTING" to listOf("WOODCUTTING_NEST_CHANCE"),
                "MINING" to listOf("MINING_GEM_CHANCE"),
                "FISHING" to listOf("FISHING_EXTRA_CATCH_CHANCE"),
                "COOKING" to listOf("COOK_SUCCESS_CHANCE"),
                "SMITHING" to listOf("SMITH_SAVE_CHANCE"),
                "FLETCHING" to listOf("FLETCHING_DOUBLE_OUTPUT_CHANCE"),
                "HERBLORE" to listOf("HERBLORE_SECONDARY_SAVE_CHANCE"),
                "RUNECRAFTING" to listOf("RUNECRAFTING_EXTRA_RUNE_CHANCE"),
                "THIEVING" to listOf("THIEVING_EXTRA_LOOT_CHANCE"),
                "FIREMAKING" to listOf("FIREMAKING_EXTRA_REWARD_CHANCE"),
                "PRAYER" to listOf("PRAYER_EXTRA_XP_CHANCE"),
                "CRAFTING" to listOf("CRAFTING_DOUBLE_OUTPUT_CHANCE"),
                "MAGIC" to listOf("MAGIC_RUNE_SAVE_CHANCE"),
            )

        /** Wearable cosmetics any skill can drop - every name verified against `obj.sym`. */
        public val GENERIC_POOL: List<String> =
            listOf(
                "graceful_hood",
                "graceful_cape",
                "graceful_top",
                "graceful_legs",
                "graceful_gloves",
                "graceful_boots",
                "zeah_graceful_hood_arceuus",
                "zeah_graceful_cape_arceuus",
                "zeah_graceful_top_arceuus",
                "zeah_graceful_legs_arceuus",
                "zeah_graceful_gloves_arceuus",
                "zeah_graceful_boots_arceuus",
                "zeah_graceful_hood_piscarilius",
                "zeah_graceful_cape_piscarilius",
                "zeah_graceful_top_piscarilius",
                "zeah_graceful_legs_piscarilius",
                "zeah_graceful_gloves_piscarilius",
                "zeah_graceful_boots_piscarilius",
                "zeah_graceful_hood_lovakengj",
                "zeah_graceful_cape_lovakengj",
                "zeah_graceful_top_lovakengj",
                "zeah_graceful_legs_lovakengj",
                "zeah_graceful_gloves_lovakengj",
                "zeah_graceful_boots_lovakengj",
                "zeah_graceful_hood_shayzien",
                "zeah_graceful_cape_shayzien",
                "zeah_graceful_hood_hosidius",
                "zeah_graceful_cape_hosidius",
                "zeah_graceful_hood_kourend",
                "zeah_graceful_cape_kourend",
                "graceful_hood_hallowed",
                "graceful_cape_hallowed",
                "graceful_top_hallowed",
                "graceful_legs_hallowed",
                "graceful_gloves_hallowed",
                "graceful_boots_hallowed",
                "graceful_hood_trailblazer",
                "graceful_cape_trailblazer",
                "graceful_top_trailblazer",
                "graceful_legs_trailblazer",
                "graceful_gloves_trailblazer",
                "graceful_boots_trailblazer",
                "graceful_hood_skillcapecolour",
                "graceful_cape_skillcapecolour",
                "evil_chicken_feet",
                "evil_chicken_wings",
                "evil_chicken_head",
                "evil_chicken_legs",
                "trail_gilded_dhide_coif",
                "trail_gilded_dhide_vambraces",
                "trail_gilded_dhide_top",
                "trail_gilded_dhide_chaps",
                "farmers_fork",
                "chefs_hat",
                "pirate_torso",
                "pirate_bandanna",
                "pirate_boots",
                "pirate_legs",
                "pirate_torso_red",
                "pirate_bandana_red",
                "pirate_legs_red",
                "pirate_torso_blue",
                "pirate_bandana_blue",
                "pirate_legs_blue",
                "cavalier_brown",
                "cavalier_dark",
                "cavalier_black",
                "cavalier_white",
                "cavalier_red",
                "cavalier_navy",
                "trail_pirate_hat",
                "pirate_hat_cavalier_highway_mask",
                "pirate_hat_berret_mime_mask",
                "hallowed_grapple",
                "hallowed_focus",
                "hallowed_symbol",
                "hallowed_hammer",
                "hallowed_ring",
                "shayzien_helm_1",
                "shayzien_body_1",
                "shayzien_legs_1",
                "shayzien_gloves_1",
                "shayzien_boots_1",
                "shayzien_helm_3",
                "shayzien_body_3",
                "shayzien_legs_3",
                "shayzien_gloves_3",
                "shayzien_boots_3",
            )

        /** Skilling-themed wearable pools, drawn ~60% of the time for that skill's drops. */
        public val SKILL_POOLS: Map<String, List<String>> =
            mapOf(
                "WOODCUTTING" to
                    listOf(
                        "ramble_lumberjack_hat",
                        "ramble_lumberjack_top",
                        "ramble_lumberjack_legs",
                        "ramble_lumberjack_boots",
                        "forestry_lumberjack_hat",
                        "forestry_lumberjack_top",
                        "forestry_lumberjack_legs",
                        "forestry_lumberjack_boots",
                        "forestry_pheasant_hat",
                        "forestry_pheasant_legs",
                        "forestry_pheasant_boots",
                        "forestry_pheasant_cape",
                        "forestry_gloves",
                        "trail_gilded_axe",
                        "trail_gilded_spade",
                    ),
                "MINING" to listOf("trail_gilded_pickaxe", "gauntlets_of_goldsmithing"),
                "FISHING" to
                    listOf(
                        "spirit_angler_hat",
                        "spirit_angler_top",
                        "spirit_angler_legs",
                        "spirit_angler_boots",
                    ),
                "FIREMAKING" to
                    listOf(
                        "pyromancer_hood",
                        "pyromancer_top",
                        "pyromancer_bottom",
                        "pyromancer_boots",
                        "pyromancer_gloves",
                    ),
                "SMITHING" to listOf("gauntlets_of_goldsmithing"),
                "COOKING" to listOf("chefs_hat"),
            )
    }
}
