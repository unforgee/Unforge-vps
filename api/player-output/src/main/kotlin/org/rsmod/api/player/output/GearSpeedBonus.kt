package org.rsmod.api.player.output

import kotlin.math.max
import org.rsmod.api.config.refs.params
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Deterministic attack-speed bonus (in basis points) granted by a wearable item.
 *
 * The bonus is a pure function of the template's gear tier - resolved from its level requirement /
 * combat bonuses by [requirementScore] - so it applies to every wearable whether or not it carries
 * an `EquipmentInstance`. Every worn piece contributes its own value and the pieces sum together;
 * see `org.rsmod.api.player.bonus.WornBonuses.attackSpeedModifierBps`.
 *
 * Lives in `api:player-output` because this module owns the item stat description contract
 * ([EquipmentInstanceDescribe], [EquipmentInstanceHoverSync]) *and* sits below `api:player`, so
 * both the hover payload and the worn-bonus aggregation share one implementation.
 *
 * Anchor values: rune armour, green d'hide, yew bows, rune arrows and ghostly robes grant 1 %,
 * end-game gear (ancestral, virtus, masori, twisted bow, torva) 7 %, and league reward items 10 %.
 * Everything between 1 % and 7 % is interpolated linearly by the requirement score.
 */
public object GearSpeedBonus {
    /** Low tier: rune armour, green d'hide, yew bows, rune arrows, ghostly robes. */
    public const val MIN_BPS: Int = 100

    /** End-game: ancestral, virtus, masori, twisted bow, torva. */
    public const val MAX_BPS: Int = 700

    /** League reward items. */
    public const val LEAGUE_BPS: Int = 1_000

    /** Requirement score that grants [MIN_BPS] (level-40 gear: rune, green d'hide, yew). */
    private const val FLOOR_SCORE: Int = 40

    /** Requirement score that grants [MAX_BPS] (level 75+: ancestral, virtus, masori, torva). */
    private const val CEIL_SCORE: Int = 75

    private const val SCORE_SPAN: Int = CEIL_SCORE - FLOOR_SCORE
    private const val BPS_SPAN: Int = MAX_BPS - MIN_BPS

    /**
     * Requirement-less end-game pieces that the score curve cannot place: they carry no level
     * requirement and their combat bonuses are too small to clear [FLOOR_SCORE]. Matched on the
     * internal name prefix so every reskin of the same slot counts (Ava's assembler and its trouver
     * / masori upgrades).
     */
    private val overrides: Map<String, Int> = mapOf("avas_assembler" to MAX_BPS)

    /**
     * League rewards that the name prefixes below do not catch. Kept explicit so the league tier is
     * never applied to an ordinary (non-league) ornament-kit reskin.
     */
    private val leagueNames: Set<String> =
        setOf(
            "thunder_khopesh",
            "weapon_of_sol",
            "drygore_blowpipe",
            "drygore_blowpipe_loaded",
            "tangled_lizard_charged",
            "tangled_lizard_uncharged",
            "dinhs_bulwark_ornament",
            "venator_bow_ornament",
            "venator_bow_ornament_uncharged",
            "toxic_blowpipe_ornament",
            "barrows_ahrim_weapon_ornament",
            "barrows_ahrim_weapon_ornament_100",
            "barrows_ahrim_weapon_ornament_75",
            "barrows_ahrim_weapon_ornament_50",
            "barrows_ahrim_weapon_ornament_25",
            "barrows_ahrim_weapon_ornament_broken",
        )

    public fun bps(type: UnpackedObjType): Int {
        if (isLeagueItem(type)) {
            return LEAGUE_BPS
        }
        val key = key(type)
        overrides.entries
            .firstOrNull { key.startsWith(it.key) }
            ?.let {
                return it.value
            }
        val score = requirementScore(type)
        return when {
            score <= FLOOR_SCORE -> MIN_BPS
            score >= CEIL_SCORE -> MAX_BPS
            else -> MIN_BPS + (score - FLOOR_SCORE) * BPS_SPAN / SCORE_SPAN
        }
    }

    /** League rewards are recognised by their `league_`/`trailblazer`/`echo_` names or by id. */
    public fun isLeagueItem(type: UnpackedObjType): Boolean {
        val key = key(type)
        return key.startsWith("league_") ||
            key.startsWith("trailblazer") ||
            key.startsWith("echo_") ||
            key in leagueNames
    }

    /**
     * The raw tier signal for [type]: the highest of its level requirements, or a gear score
     * derived from its combat bonuses when it declares none.
     *
     * Callers that need the discrete ten-band label use `EquipmentTierResolver` (`api:player`),
     * which derives its `EquipmentTier` from this score.
     */
    public fun requirementScore(type: UnpackedObjType): Int {
        val requirement =
            maxOf(
                type.paramOrNull(params.levelrequire) ?: 0,
                type.paramOrNull(params.statreq1_level) ?: 0,
                type.paramOrNull(params.statreq2_level) ?: 0,
            )
        return if (requirement > 0) requirement else gearScore(type)
    }

    private fun key(type: UnpackedObjType): String =
        type.internalName?.lowercase() ?: type.name.lowercase()

    private fun gearScore(type: UnpackedObjType): Int {
        fun p(param: org.rsmod.game.type.param.ParamType<Int>): Int = type.paramOrNull(param) ?: 0

        val attack =
            maxOf(
                p(params.attack_stab),
                p(params.attack_slash),
                p(params.attack_crush),
                p(params.attack_magic),
                p(params.attack_ranged),
            )
        val defence =
            maxOf(
                p(params.defence_stab),
                p(params.defence_slash),
                p(params.defence_crush),
                p(params.defence_magic),
                p(params.defence_ranged),
            )
        val strength = max(p(params.melee_strength), p(params.ranged_strength))
        val magicDamage = p(params.magic_damage)
        return max(attack, defence) / 2 + strength / 3 + magicDamage * 5
    }
}
