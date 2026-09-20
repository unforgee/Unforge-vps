package org.rsmod.api.commons.skilling

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.equipment.instance.WornSkillBonuses
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Applies worn skilling-gear effects ([WornSkillBonuses]) to skilling reward hooks.
 *
 * Skill scripts replace their bare `invAdd(inv, product)` calls with [grant]: the produced count is
 * multiplied by double/triple rolls, extended by extra-output rolls, converted to its noted (cert)
 * variant, or sent straight to the bank - all based on the skilling affixes carried by the
 * equipment instances currently worn. Every call then rolls the FashionScape cosmetic drop.
 *
 * All affix magnitudes are basis points: `250` = 2.5%. Affixes disappear the moment the item is
 * unequipped because [WornSkillBonuses] only reads the worn inventory.
 */
@Singleton
public class SkillingRewards
@Inject
constructor(
    private val wornSkills: WornSkillBonuses,
    private val fashionScape: FashionScapeDrops,
    private val names: NameMapping,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
) {
    private val nestPool: List<ObjType> by lazy { resolveAll(NEST_SYMBOLS) }
    private val gemPool: List<ObjType> by lazy { resolveAll(GEM_SYMBOLS) }
    private val firemakingReward: ObjType? by lazy { names.objs["ashes"]?.let(objTypes::get) }

    /**
     * Grants [count] of [product] after a successful [skill] action, applying every worn
     * skilling-gear effect, then rolling the FashionScape cosmetic drop.
     *
     * @return `true` when the product reached the inventory or the bank; `false` when the inventory
     *   was full and the product was dropped under the player instead - callers that loop while
     *   inventory space remains may want to stop.
     */
    public fun grant(
        access: ProtectedAccess,
        skill: String,
        product: ObjType,
        count: Int = 1,
    ): Boolean {
        val player = access.player
        var produced = count

        val tripleBps = value(player, skill, TRIPLE_OUTPUT)
        val doubleBps =
            value(player, skill, DOUBLE_OUTPUT) +
                DOUBLE_EXTRA[skill]?.let { value(player, skill, it) }.orZero()
        produced *=
            when {
                access.random.of(ROLL_BOUND) < tripleBps -> 3
                access.random.of(ROLL_BOUND) < doubleBps -> 2
                else -> 1
            }

        val extraBps =
            value(player, skill, EXTRA_OUTPUT) +
                EXTRA_EXTRA[skill]?.let { value(player, skill, it) }.orZero()
        if (extraBps > 0 && access.random.of(ROLL_BOUND) < extraBps) {
            produced += count
            access.mes(
                "<col=6699ff>Your gear yields extra ${objTypes[product].name.lowercase()}.</col>"
            )
        }

        val noted = access.random.of(ROLL_BOUND) < value(player, skill, NOTED)
        val toBank = access.random.of(ROLL_BOUND) < value(player, skill, DIRECT_BANK)

        val delivered: Boolean
        if (toBank) {
            val tx = access.invAdd(access.bank, product, produced, cert = noted, strict = false)
            delivered = tx.success
            if (delivered) {
                access.mes(
                    "<col=6699ff>Your gear sends ${produced}x " +
                        "${objTypes[product].name.lowercase()} straight to your bank.</col>"
                )
            }
        } else {
            val tx = access.invAdd(access.inv, product, produced, cert = noted, strict = false)
            delivered = tx.success || access.invAddOrDrop(objRepo, product, produced)
        }

        if (delivered) {
            rollSkillBonus(access, skill)
            fashionScape.rollDrop(access, skill)
        }
        return delivered
    }

    /**
     * Rolls the "free bonus item" effects for skills that have no produced output of their own
     * (firemaking) and the FashionScape cosmetic drop. Called once per successful action.
     */
    public fun bonusDrop(access: ProtectedAccess, skill: String) {
        val reward = firemakingReward
        if (
            skill == "FIREMAKING" &&
                reward != null &&
                access.random.of(ROLL_BOUND) < value(access.player, skill, FIREMAKING_EXTRA_REWARD)
        ) {
            access.invAddOrDrop(objRepo, reward, 1)
        }
        fashionScape.rollDrop(access, skill)
    }

    /** XP multiplier from worn `*_EXTRA_XP` style affixes (currently `PRAYER_EXTRA_XP_CHANCE`). */
    public fun xpMultiplier(player: Player, skill: String): Double =
        1.0 + value(player, skill, PRAYER_EXTRA_XP) / 10_000.0

    /** `true` when a `*_SAVE_CHANCE`-style worn effect rolls successfully. */
    public fun rollChance(access: ProtectedAccess, skill: String, effect: String): Boolean =
        access.random.of(ROLL_BOUND) < value(access.player, skill, effect)

    /** Sum of [effect] across all worn instances for [skill] (`ALL` affixes included). */
    public fun value(player: Player, skill: String, effect: String): Int =
        wornSkills.value(player, skill, effect)

    private fun Int?.orZero(): Int = this ?: 0

    /** Skill-specific free-item rolls triggered inside [grant] after a successful delivery. */
    private fun rollSkillBonus(access: ProtectedAccess, skill: String) {
        val player = access.player
        when (skill) {
            "WOODCUTTING" -> {
                if (
                    nestPool.isNotEmpty() &&
                        access.random.of(ROLL_BOUND) < value(player, skill, WOODCUTTING_NEST)
                ) {
                    access.invAddOrDrop(objRepo, nestPool[access.random.of(nestPool.size)], 1)
                    access.mes("<col=6699ff>A bird's nest falls out of the tree!</col>")
                }
            }
            "MINING" -> {
                if (
                    gemPool.isNotEmpty() &&
                        access.random.of(ROLL_BOUND) < value(player, skill, MINING_GEM)
                ) {
                    val gem = gemPool[access.random.of(gemPool.size)]
                    access.invAddOrDrop(objRepo, gem, 1)
                    access.mes("<col=6699ff>You find a ${objTypes[gem].name.lowercase()}!</col>")
                }
            }
        }
    }

    private fun resolveAll(symbols: List<String>): List<ObjType> =
        symbols.mapNotNull { names.objs[it] }.mapNotNull { objTypes[it] }

    public companion object {
        public const val ROLL_BOUND: Int = 10_000

        public const val NOTED: String = "NOTED_CHANCE"
        public const val DIRECT_BANK: String = "DIRECT_BANK_CHANCE"
        public const val DOUBLE_OUTPUT: String = "DOUBLE_OUTPUT_CHANCE"
        public const val TRIPLE_OUTPUT: String = "TRIPLE_OUTPUT_CHANCE"
        public const val EXTRA_OUTPUT: String = "EXTRA_OUTPUT_CHANCE"
        public const val WOODCUTTING_NEST: String = "WOODCUTTING_NEST_CHANCE"
        public const val MINING_GEM: String = "MINING_GEM_CHANCE"
        public const val FIREMAKING_EXTRA_REWARD: String = "FIREMAKING_EXTRA_REWARD_CHANCE"
        public const val PRAYER_EXTRA_XP: String = "PRAYER_EXTRA_XP_CHANCE"
        public const val COOK_SUCCESS: String = "COOK_SUCCESS_CHANCE"
        public const val SMITH_SAVE: String = "SMITH_SAVE_CHANCE"
        public const val HERBLORE_SAVE: String = "HERBLORE_SECONDARY_SAVE_CHANCE"
        public const val MAGIC_RUNE_SAVE: String = "MAGIC_RUNE_SAVE_CHANCE"

        /** Skill-specific effects folded into the generic double-output roll. */
        private val DOUBLE_EXTRA: Map<String, String> =
            mapOf(
                "FLETCHING" to "FLETCHING_DOUBLE_OUTPUT_CHANCE",
                "CRAFTING" to "CRAFTING_DOUBLE_OUTPUT_CHANCE",
            )

        /** Skill-specific effects folded into the generic extra-output roll. */
        private val EXTRA_EXTRA: Map<String, String> =
            mapOf(
                "FISHING" to "FISHING_EXTRA_CATCH_CHANCE",
                "THIEVING" to "THIEVING_EXTRA_LOOT_CHANCE",
                "RUNECRAFTING" to "RUNECRAFTING_EXTRA_RUNE_CHANCE",
            )

        private val NEST_SYMBOLS =
            listOf(
                "bird_nest_seeds",
                "bird_nest_egg_red",
                "bird_nest_egg_green",
                "bird_nest_egg_blue",
                "bird_nest_ring",
                "bird_nest_cheapseeds",
                "bird_nest_empty",
            )

        private val GEM_SYMBOLS =
            listOf(
                "uncut_sapphire",
                "uncut_emerald",
                "uncut_ruby",
                "uncut_diamond",
                "uncut_opal",
                "uncut_jade",
                "uncut_red_topaz",
                "uncut_dragonstone",
            )
    }
}
