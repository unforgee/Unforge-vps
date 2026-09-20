package org.rsmod.content.other.commands

import org.rsmod.api.combat.commons.ability.AbilityVisual
import org.rsmod.api.combat.commons.ability.AbilityVisualImpact
import org.rsmod.api.combat.commons.ability.AbilityVisualOrigin
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.synths
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

/**
 * Centralized ability-presentation library for companion role spells and talent abilities.
 *
 * Every definition is built from named revision-239 cache assets (the same `seqs`/`spotanims`/
 * `synths`/`projanims` references the authentic spell content uses) - no raw ids are scattered
 * through combat code, and no asset is guessed: each entry names a real cache type.
 *
 * Tier rules: role spells come in five tiers (`levelReq` 1/20/40/60/80 -> tier 0..4), so the
 * presentation escalates with the spell exactly like the OSRS strike -> surge spell families.
 *
 * Origin classification follows the audit contract: [AbilityVisualOrigin.ORIGINAL] only when the
 * ability is literally the original OSRS spell/attack; [AbilityVisualOrigin.ADAPTED] when real
 * assets from a related source are reused; [AbilityVisualOrigin.CUSTOM] for coherent custom
 * combinations that are still built from real cache assets.
 */
public object CompanionAbilityVisuals {

    /** Spell tier index from a role spell's level requirement (1/20/40/60/80 -> 0..4). */
    internal fun tierOf(spell: RoleSpell): Int =
        when {
            spell.levelReq >= 80 -> 4
            spell.levelReq >= 60 -> 3
            spell.levelReq >= 40 -> 2
            spell.levelReq >= 20 -> 1
            else -> 0
        }

    public fun forRoleSpell(spell: RoleSpell): AbilityVisual =
        when (spell.type) {
            RoleSpellType.HEAL -> healVisual(tierOf(spell))
            RoleSpellType.BUFF -> buffVisual(tierOf(spell))
            RoleSpellType.DEFENSIVE -> defensiveVisual(tierOf(spell))
            RoleSpellType.TAUNT -> tauntVisual(tierOf(spell))
            RoleSpellType.DAMAGE_BURST -> burstVisual(tierOf(spell))
            RoleSpellType.EXECUTION -> executionVisual(tierOf(spell))
        }

    public fun forTalentAbility(abilityId: String): AbilityVisual? = TALENT_ABILITY[abilityId]

    // --- SUPPORT ---

    /**
     * Lunar-style heal: the caster chants `human_castheal` with the `heal_casting` gfx, a gargoyle
     * healing sphere travels to the target and bursts into its impact gfx. Sphere size grows with
     * the spell tier. ADAPTED: real heal assets, composed for a companion spell.
     */
    internal fun healVisual(tier: Int): AbilityVisual {
        val (travel, impact, sound) = HEAL_SPHERES[tier.coerceIn(0, HEAL_SPHERES.lastIndex)]
        return AbilityVisual(
            castSeq = seqs.human_castheal,
            castSpot = spotanims.heal_casting,
            travelSpot = travel,
            projanim = projanims.magic_spell,
            impactSpot = impact,
            impactSpotHeight = 96,
            castSound = synths.selfheal,
            hitSound = sound,
            origin = AbilityVisualOrigin.ADAPTED,
        )
    }

    /**
     * Stat buff: a quick `human_castzap` cast, then the leagues rampage sigil buff gfx lands on the
     * owner - one gfx per tier (`sigil_of_rampage_buff_01..05`).
     */
    internal fun buffVisual(tier: Int): AbilityVisual =
        AbilityVisual(
            castSeq = seqs.human_castzap,
            castSpot = spotanims.sigil_of_rampage_buff_01,
            impactSpot = RAMPAGE_BUFFS[tier.coerceIn(0, RAMPAGE_BUFFS.lastIndex)],
            impactSpotHeight = 96,
            castSound = synths.rampage,
            hitSound = synths.rampage,
            origin = AbilityVisualOrigin.ADAPTED,
        )

    // --- TANK ---

    /**
     * Defensive ward: shield-block stance plus an equipment/shield gfx that escalates from the
     * elemental shield equip flash to the Kephri shield form at the top tier.
     */
    internal fun defensiveVisual(tier: Int): AbilityVisual =
        AbilityVisual(
            castSeq = seqs.league_5_spear_shield_block,
            castSpot = DEFENSIVE_SHIELDS[tier.coerceIn(0, DEFENSIVE_SHIELDS.lastIndex)],
            castSpotHeight = 96,
            castSound = synths.human_block_1,
            origin = AbilityVisualOrigin.ADAPTED,
        )

    /**
     * Taunt: the `specialattack_unleash` shout with the anger glow, and from tier 2 up the anger
     * projectile flies onto the taunted npc so the pull is visible on the target.
     */
    internal fun tauntVisual(tier: Int): AbilityVisual =
        AbilityVisual(
            castSeq = seqs.specialattack_unleash,
            castSpot = spotanims.anger_special_spotanim,
            castSpotHeight = 96,
            travelSpot = if (tier >= 2) spotanims.anger_special_projectile else null,
            projanim = if (tier >= 2) projanims.magic_spell else null,
            impactSpot = if (tier >= 2) spotanims.anger_special_spotanim else null,
            impactSpotHeight = 96,
            castSound = synths.cleave,
            hitSound = synths.clobber,
            origin = AbilityVisualOrigin.ADAPTED,
        )

    // --- DPS ---

    /**
     * Damage burst: the companion casts a real elemental spell presentation that escalates through
     * the OSRS families - strike -> bolt -> blast -> wave -> surge. These are the exact
     * casting/travel/impact gfx and synths the player's own spellbook uses.
     */
    internal fun burstVisual(tier: Int): AbilityVisual {
        val e = ELEMENTAL[tier.coerceIn(0, ELEMENTAL.lastIndex)]
        return AbilityVisual(
            castSeq = e.seq,
            castSpot = e.casting,
            travelSpot = e.travel,
            projanim = projanims.magic_spell,
            impactSpot = e.impact,
            castSound = e.castSound,
            hitSound = e.hitSound,
            origin = AbilityVisualOrigin.ORIGINAL,
        )
    }

    /**
     * Execution: melee finisher presentation built from real weapon-special assets (dragon claws ->
     * abyssal-style -> elder maul -> emberlight -> voidwaker as the tier climbs).
     */
    internal fun executionVisual(tier: Int): AbilityVisual {
        val e = EXECUTIONS[tier.coerceIn(0, EXECUTIONS.lastIndex)]
        return AbilityVisual(
            castSeq = e.seq,
            castSpot = e.launch,
            castSpotHeight = 96,
            impactSpot = e.impact,
            impactSpotHeight = 96,
            castSound = synths.cleave,
            hitSound = synths.clobber,
            extraImpacts = e.extra,
            origin = AbilityVisualOrigin.ADAPTED,
        )
    }

    // --- talent abilities ---

    private val TALENT_ABILITY: Map<String, AbilityVisual> =
        mapOf(
            // Tank talents
            "shield-bash" to
                AbilityVisual(
                    castSeq = seqs.league_5_spear_shield_special,
                    castSpot = spotanims.league_5_shieldslam_special,
                    castSpotHeight = 96,
                    impactSpot = spotanims.stunned,
                    castSound = synths.clobber,
                    hitSound = synths.stun_all,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "challenging-shout" to
                AbilityVisual(
                    castSeq = seqs.specialattack_unleash,
                    castSpot = spotanims.anger_special_spotanim,
                    castSpotHeight = 96,
                    castSound = synths.rampage,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "iron-bastion" to
                AbilityVisual(
                    castSeq = seqs.league_5_spear_shield_block,
                    castSpot = spotanims.kephri_shield_alive,
                    castSpotHeight = 96,
                    castSound = synths.human_block_1,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "unyielding-fortress" to
                AbilityVisual(
                    castSeq = seqs.league_5_spear_shield_block,
                    castSpot = spotanims.kephri_shield_alive,
                    impactSpot = spotanims.elysian_shield_defend_spotanim,
                    castSound = synths.human_block_1,
                    hitSound = synths.bind_all,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "intercept" to
                AbilityVisual(
                    castSeq = seqs.league_5_spear_shield_special,
                    impactSpot = spotanims.dov_dimintheis_receive_shield_spotanim,
                    impactSpotHeight = 96,
                    hitSound = synths.human_block_1,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            // Support talents
            "mending-wave" to
                AbilityVisual(
                    castSeq = seqs.human_castheal,
                    castSpot = spotanims.heal_casting,
                    impactSpot = spotanims.quest_lunar_spellbook_heal_group_spot_anim,
                    impactSpotHeight = 96,
                    castSound = synths.selfheal,
                    hitSound = synths.doubleheal,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "purifying-light" to
                AbilityVisual(
                    castSeq = seqs.human_castzap,
                    castSpot = spotanims.degrime_cast_spotanim,
                    impactSpot = spotanims.quest_lunar_spellbook_heal_group_spot_anim,
                    impactSpotHeight = 96,
                    castSound = synths.superheat_all,
                    hitSound = synths.heal,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "divine-intervention" to
                AbilityVisual(
                    castSeq = seqs.human_castheal,
                    castSpot = spotanims.sanguinesti_staff_heal,
                    impactSpot = spotanims.quest_lunar_spellbook_heal_group_spot_anim,
                    impactSpotHeight = 96,
                    castSound = synths.selfheal,
                    hitSound = synths.doubleheal,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "dispel-pulse" to
                AbilityVisual(
                    castSeq = seqs.human_castzap,
                    castSpot = spotanims.degrime_cast_spotanim,
                    impactSpot = spotanims.barbassault_penance_healer_heal,
                    impactSpotHeight = 96,
                    castSound = synths.superheat_all,
                    hitSound = synths.heal,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "rallying-call" to
                AbilityVisual(
                    castSeq = seqs.specialattack_unleash,
                    castSpot = spotanims.sigil_of_rampage_buff_03,
                    castSpotHeight = 96,
                    castSound = synths.rampage,
                    hitSound = synths.rampage,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "everlasting-aid" to
                AbilityVisual(
                    castSeq = seqs.human_castheal,
                    castSpot = spotanims.sanguinesti_staff_heal,
                    impactSpot = spotanims.leagues_5_gargboss_healing_sphere_large_impact,
                    impactSpotHeight = 96,
                    castSound = synths.selfheal,
                    hitSound = synths.doubleheal,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            // Dps talents
            "focused-volley" to
                AbilityVisual(
                    castSeq = seqs.human_bow,
                    castSpot = spotanims.acb_specialattack,
                    castSpotHeight = 96,
                    travelSpot = spotanims.crossbowbolt_travel,
                    projanim = projanims.bolt,
                    impactSpot = spotanims.godwars_armadyl_bolt_hit_spotanim,
                    castSound = synths.darkbow_fire,
                    hitSound = synths.darkbow_shadow_impact,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "crippling-strike" to
                AbilityVisual(
                    castSeq = seqs.human_castweaken,
                    castSpot = spotanims.weaken_casting,
                    travelSpot = spotanims.weaken_travel,
                    projanim = projanims.magic_spell,
                    impactSpot = spotanims.weaken_impact,
                    castSound = synths.weaken_all,
                    hitSound = synths.weaken_all,
                    origin = AbilityVisualOrigin.ORIGINAL,
                ),
            "death-mark" to
                AbilityVisual(
                    castSeq = seqs.human_castcurse,
                    castSpot = spotanims.curse_casting,
                    travelSpot = spotanims.curse_travel,
                    projanim = projanims.magic_spell,
                    impactSpot = spotanims.curse_impact,
                    castSound = synths.curse_cast_and_fire,
                    hitSound = synths.curse_hit,
                    origin = AbilityVisualOrigin.ORIGINAL,
                ),
            "perfect-execution" to
                AbilityVisual(
                    castSeq = seqs.human_elder_maul_spec,
                    castSpot = spotanims.spotanim_elder_maul_special,
                    castSpotHeight = 96,
                    impactSpot = spotanims.spotanim_elder_maul_special_impact,
                    impactSpotHeight = 96,
                    castSound = synths.cleave,
                    hitSound = synths.clobber,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "shadow-step" to
                AbilityVisual(
                    castSeq = seqs.human_castteleport,
                    castSpot = spotanims.abyssal_demon_teleport,
                    impactSpot = spotanims.abyssal_demon_teleport,
                    castSound = synths.teleport_all,
                    hitSound = synths.teleport_all,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "frenzy" to
                AbilityVisual(
                    castSeq = seqs.specialattack_unleash,
                    castSpot = spotanims.vfx_burning_claws_spec_02,
                    castSpotHeight = 96,
                    castSound = synths.rampage,
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
            "apex-predator" to
                AbilityVisual(
                    castSeq = seqs.the_godsword_special_attack,
                    castSpot = spotanims.dh_sword_update_bandos_special_spotanim,
                    castSpotHeight = 96,
                    impactSpot = spotanims.saradomin_lightning,
                    castSound = synths.cleave,
                    hitSound = synths.ice_barrage_impact,
                    extraImpacts =
                        listOf(
                            AbilityVisualImpact(
                                spot = spotanims.dragon_claws_spot,
                                delayCycles = 10,
                            )
                        ),
                    origin = AbilityVisualOrigin.ADAPTED,
                ),
        )

    // --- asset tables ---

    private data class ElementalSet(
        val seq: SeqType,
        val casting: SpotanimType,
        val travel: SpotanimType,
        val impact: SpotanimType,
        val castSound: SynthType,
        val hitSound: SynthType,
    )

    /** OSRS elemental escalation, one row per burst tier. */
    private val ELEMENTAL: List<ElementalSet> =
        listOf(
            ElementalSet(
                seqs.human_caststrike,
                spotanims.windstrike_casting,
                spotanims.windstrike_travel,
                spotanims.windstrike_impact,
                synths.windstrike_cast_and_fire,
                synths.windstrike_hit,
            ),
            ElementalSet(
                seqs.human_caststrike,
                spotanims.waterbolt_casting,
                spotanims.waterbolt_travel,
                spotanims.waterbolt_impact,
                synths.waterbolt_cast_and_fire,
                synths.waterbolt_hit,
            ),
            ElementalSet(
                seqs.human_castwave,
                spotanims.earthblast_casting,
                spotanims.earthblast_travel,
                spotanims.earthblast_impact,
                synths.earthblast_cast_and_fire,
                synths.earthblast_hit,
            ),
            ElementalSet(
                seqs.human_castwave,
                spotanims.firewave_casting,
                spotanims.firewave_travel,
                spotanims.firewave_impact,
                synths.firewave_cast_and_fire,
                synths.firewave_hit,
            ),
            ElementalSet(
                seqs.human_cast_surge,
                spotanims.firesurge_casting,
                spotanims.firesurge_travel,
                spotanims.firesurge_impact,
                synths.firesurge_cast_and_fire,
                synths.firesurge_hit,
            ),
        )

    private val HEAL_SPHERES: List<Triple<SpotanimType, SpotanimType, SynthType>> =
        listOf(
            Triple(
                spotanims.gargboss_healing_sphere_small,
                spotanims.gargboss_healing_sphere_small_impact,
                synths.heal,
            ),
            Triple(
                spotanims.gargboss_healing_sphere_small,
                spotanims.gargboss_healing_sphere_small_impact,
                synths.heal,
            ),
            Triple(
                spotanims.leagues_5_gargboss_healing_sphere_med,
                spotanims.leagues_5_gargboss_healing_sphere_med_impact,
                synths.heal,
            ),
            Triple(
                spotanims.leagues_5_gargboss_healing_sphere_med,
                spotanims.leagues_5_gargboss_healing_sphere_med_impact,
                synths.doubleheal,
            ),
            Triple(
                spotanims.leagues_5_gargboss_healing_sphere_large,
                spotanims.leagues_5_gargboss_healing_sphere_large_impact,
                synths.doubleheal,
            ),
        )

    private val RAMPAGE_BUFFS: List<SpotanimType> =
        listOf(
            spotanims.sigil_of_rampage_buff_01,
            spotanims.sigil_of_rampage_buff_02,
            spotanims.sigil_of_rampage_buff_03,
            spotanims.sigil_of_rampage_buff_04,
            spotanims.sigil_of_rampage_buff_05,
        )

    private val DEFENSIVE_SHIELDS: List<SpotanimType> =
        listOf(
            spotanims.elemental_shield_equip,
            spotanims.wyvern_shield_equip,
            spotanims.elysian_shield_defend_spotanim,
            spotanims.dov_dimintheis_receive_shield_spotanim,
            spotanims.kephri_shield_alive,
        )

    private data class ExecutionSet(
        val seq: SeqType,
        val launch: SpotanimType,
        val impact: SpotanimType,
        val extra: List<AbilityVisualImpact> = emptyList(),
    )

    private val EXECUTIONS: List<ExecutionSet> =
        listOf(
            ExecutionSet(
                seqs.human_ddagger_lunge,
                spotanims.dragon_claws_spot,
                spotanims.dragon_claws_spot,
                listOf(AbilityVisualImpact(spot = spotanims.dragon_claws_spot, delayCycles = 5)),
            ),
            ExecutionSet(
                seqs.specialattack_unleash,
                spotanims.dragon_claws_spot,
                spotanims.spell_blood_barrage_impact,
            ),
            ExecutionSet(
                seqs.ice_shatter_special_attack,
                spotanims.spotanim_elder_maul_special,
                spotanims.spotanim_elder_maul_special_impact,
            ),
            ExecutionSet(
                seqs.human_elder_maul_spec,
                spotanims.vfx_emberlight_spec_02,
                spotanims.spotanim_elder_maul_special_impact,
            ),
            ExecutionSet(
                seqs.human_special_voidwaker,
                spotanims.fx_voidwaker02_special,
                spotanims.shadow_barrage_impact,
            ),
        )
}
