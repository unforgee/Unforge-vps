package org.rsmod.content.skills.magic.spell.attacks.ancient

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.effects.CombatEffects
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.synths
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

/**
 * The Ancient Magicks combat spellbook.
 *
 * Each spell is a plain damage spell that optionally applies an element effect:
 * - `ice` freezes the target in place.
 * - `smoke` poisons player targets.
 * - `blood` heals the caster for a portion of the damage dealt.
 * - `shadow` drains the target npc's attack level.
 *
 * The `burst` and `barrage` tiers additionally hit everything around the primary target when the
 * target is standing in a multi-way area.
 */
class AncientSpells
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val effects: CombatEffects,
    private val areaChecker: AreaChecker,
) : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        registerRushes(manager)
        registerBursts(manager)
        registerBlitzes(manager)
        registerBarrages(manager)
    }

    private fun SpellAttackRepository.registerRushes(manager: SpellAttackManager) {
        register(
            objs.spell_smoke_rush,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = spotanims.smoke_rush_travel,
                impact = spotanims.smoke_rush_impact,
                castSound = synths.smoke_cast,
                hitSound = synths.smoke_rush_impact,
            ),
            effect = ElementEffect.Smoke,
            aoe = false,
        )
        register(
            objs.spell_shadow_rush,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = spotanims.shadow_rush_travel,
                impact = spotanims.shadow_rush_impact,
                castSound = synths.shadow_cast,
                hitSound = synths.shadow_rush_impact,
            ),
            effect = ElementEffect.Shadow,
            aoe = false,
        )
        register(
            objs.spell_blood_rush,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = spotanims.blood_rush_travel,
                impact = spotanims.blood_rush_impact,
                castSound = synths.blood_cast,
                hitSound = synths.blood_rush_impact,
            ),
            effect = ElementEffect.Blood,
            aoe = false,
        )
        register(
            objs.spell_ice_rush,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = spotanims.ice_rush_travel,
                impact = spotanims.ice_rush_impact,
                castSound = synths.ice_cast,
                hitSound = synths.ice_rush_impact,
            ),
            effect = ElementEffect.Ice(FREEZE_RUSH),
            aoe = false,
        )
    }

    private fun SpellAttackRepository.registerBursts(manager: SpellAttackManager) {
        register(
            objs.spell_smoke_burst,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.smoke_burst_travel,
                impact = spotanims.smoke_burst_impact,
                castSound = synths.smoke_cast2,
                hitSound = synths.smoke_burst_impact,
            ),
            effect = ElementEffect.Smoke,
            aoe = true,
        )
        register(
            objs.spell_shadow_burst,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                impact = spotanims.shadow_burst_impact,
                castSound = synths.shadow_cast,
                hitSound = synths.shadow_burst_impact,
            ),
            effect = ElementEffect.Shadow,
            aoe = true,
        )
        register(
            objs.spell_blood_burst,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                impact = spotanims.spell_blood_burst_impact,
                castSound = synths.blood_cast,
                hitSound = synths.blood_burst_impact,
            ),
            effect = ElementEffect.Blood,
            aoe = true,
        )
        register(
            objs.spell_ice_burst,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.ice_burst_travel,
                impact = spotanims.ice_burst_impact,
                castSound = synths.ice_cast,
                hitSound = synths.ice_burst_impact,
            ),
            effect = ElementEffect.Ice(FREEZE_BURST),
            aoe = true,
        )
    }

    private fun SpellAttackRepository.registerBlitzes(manager: SpellAttackManager) {
        register(
            objs.spell_smoke_blitz,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.smoke_blitz_travel,
                impact = spotanims.smoke_blitz_impact,
                castSound = synths.smoke_cast,
                hitSound = synths.smoke_blitz_impact,
            ),
            effect = ElementEffect.Smoke,
            aoe = false,
        )
        register(
            objs.spell_shadow_blitz,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.shadow_blitz_travel,
                impact = spotanims.shadow_blitz_impact,
                castSound = synths.shadow_cast,
                hitSound = synths.shadow_blitz_impact,
            ),
            effect = ElementEffect.Shadow,
            aoe = false,
        )
        register(
            objs.spell_blood_blitz,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.blood_blitz_travel,
                impact = spotanims.blood_blitz_impact,
                castSound = synths.blood_cast,
                hitSound = synths.blood_blitz_impact,
            ),
            effect = ElementEffect.Blood,
            aoe = false,
        )
        register(
            objs.spell_ice_blitz,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.ice_blitz_travel,
                impact = spotanims.ice_blitz_impact,
                castSound = synths.ice_cast,
                hitSound = synths.ice_blitz_impact,
            ),
            effect = ElementEffect.Ice(FREEZE_BLITZ),
            aoe = false,
        )
    }

    private fun SpellAttackRepository.registerBarrages(manager: SpellAttackManager) {
        register(
            objs.spell_smoke_barrage,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.smoke_barrage_travel,
                impact = spotanims.smoke_barrage_impact,
                castSound = synths.smoke_cast2,
                hitSound = synths.smoke_barrage_impact,
            ),
            effect = ElementEffect.Smoke,
            aoe = true,
        )
        register(
            objs.spell_shadow_barrage,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                impact = spotanims.shadow_barrage_impact,
                castSound = synths.shadow_cast,
                hitSound = synths.shadow_barrage_impact,
            ),
            effect = ElementEffect.Shadow,
            aoe = true,
        )
        register(
            objs.spell_blood_barrage,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                impact = spotanims.spell_blood_barrage_impact,
                castSound = synths.blood_cast,
                hitSound = synths.blood_barrage_impact,
            ),
            effect = ElementEffect.Blood,
            aoe = true,
        )
        register(
            objs.spell_ice_barrage,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = spotanims.ice_barrage_travel,
                impact = spotanims.ice_barrage_impact,
                castSound = synths.ice_cast,
                hitSound = synths.ice_barrage_impact,
            ),
            effect = ElementEffect.Ice(FREEZE_BARRAGE),
            aoe = true,
        )
    }

    private fun SpellAttackRepository.register(
        spell: ObjType,
        manager: SpellAttackManager,
        fx: Fx,
        effect: ElementEffect,
        aoe: Boolean,
    ) {
        register(
            spell = spell,
            attack =
                AncientSpellAttack(
                    manager = manager,
                    objTypes = objTypes,
                    effects = effects,
                    areaChecker = areaChecker,
                    fx = fx,
                    effect = effect,
                    aoe = aoe,
                ),
        )
    }

    private class AncientSpellAttack(
        private val manager: SpellAttackManager,
        private val objTypes: ObjTypeList,
        private val effects: CombatEffects,
        private val areaChecker: AreaChecker,
        private val fx: Fx,
        private val effect: ElementEffect,
        private val aoe: Boolean,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }

            val weaponType = objTypes.getOrNull(attack.weapon)
            anim(weaponType.castAnim() ?: fx.anim)

            val travel = fx.travel
            val serverDelay: Int
            val clientDelay: Int
            if (travel != null) {
                val projanim = manager.spawnProjectile(this, target, travel, projanims.magic_spell)
                serverDelay = projanim.durations.serverDelay
                clientDelay = projanim.durations.clientDelay
            } else {
                // Burst and barrage tiers of blood and shadow share their rush-tier projectile
                // graphic and have no travel spotanim of their own in this cache.
                serverDelay = 1
                clientDelay = 0
            }

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, fx.castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, attack.spell.obj, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val baseMaxHit = attack.spell.maxHit
            val damage = manager.rollMaxHit(this, target, attack, castResult, baseMaxHit)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = fx.castSound,
                soundRadius = 8,
                hitSpot = fx.impact,
                hitSpotHeight = 124,
                hitSound = fx.hitSound,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, attack.spell.obj, damage, clientDelay, serverDelay)
            applySingleTargetEffect(target, damage)

            if (aoe && target is Npc) {
                applyAreaEffect(target, attack, castResult, clientDelay, baseMaxHit)
            }

            manager.continueCombatIfAutocast(this, target)
        }

        private fun ProtectedAccess.applySingleTargetEffect(target: PathingEntity, damage: Int) {
            when (effect) {
                is ElementEffect.Ice -> effects.freeze(target, effect.cycles)
                ElementEffect.Smoke -> {
                    if (target is Player) {
                        effects.poison(target, SMOKE_POISON_DAMAGE, SMOKE_POISON_DURATION)
                    }
                }
                ElementEffect.Blood -> healFromDamage(damage)
                ElementEffect.Shadow -> {
                    if (target is Npc) {
                        drainAttack(target)
                    }
                }
            }
        }

        private fun ProtectedAccess.healFromDamage(damage: Int) {
            val healed = damage * BLOOD_HEAL_PERCENT / 100
            if (healed > 0) {
                statHeal(org.rsmod.api.config.refs.stats.hitpoints, healed, 0)
            }
        }

        private fun drainAttack(npc: Npc) {
            val current = npc.attackLvl
            if (current <= 0) {
                return
            }
            npc.attackLvl =
                (current - (npc.baseAttackLvl * SHADOW_DRAIN_PERCENT / 100)).coerceAtLeast(0)
        }

        private fun ProtectedAccess.applyAreaEffect(
            primary: Npc,
            attack: CombatAttack.Spell,
            castResult: org.rsmod.api.combat.manager.MagicRuneManager.CastResult,
            clientDelay: Int,
            baseMaxHit: Int,
        ) {
            if (!primary.mapMultiway(areaChecker)) {
                return
            }

            for (npc in effects.npcsInArea(primary.coords)) {
                if (npc === primary || npc.hitpoints <= 0) {
                    continue
                }
                if (manager.rollSplash(this, npc, attack, castResult)) {
                    continue
                }
                val damage = manager.rollMaxHit(this, npc, attack, castResult, baseMaxHit)
                manager.giveCombatXp(this, npc, attack, damage)
                manager.queueMagicHit(this, npc, attack.spell.obj, damage, clientDelay, 1)
                applySingleTargetEffect(npc, damage)
            }
        }

        private fun UnpackedObjType?.castAnim(): SeqType? {
            if (this == null) {
                return null
            }
            return if (isCategoryType(categories.staff)) fx.staffAnim else fx.anim
        }
    }

    private data class Fx(
        val anim: SeqType,
        val staffAnim: SeqType,
        val travel: SpotanimType?,
        val impact: SpotanimType,
        val castSound: SynthType?,
        val hitSound: SynthType?,
    )

    private sealed interface ElementEffect {
        data class Ice(val cycles: Int) : ElementEffect

        data object Smoke : ElementEffect

        data object Blood : ElementEffect

        data object Shadow : ElementEffect
    }

    private companion object {
        /**
         * Freeze durations, in ticks (1 tick = 0.6s), matching the standard 5s/10s/15s/20s freeze
         * tiers of the rush/burst/blitz/barrage spells.
         */
        const val FREEZE_RUSH: Int = 8
        const val FREEZE_BURST: Int = 17
        const val FREEZE_BLITZ: Int = 25
        const val FREEZE_BARRAGE: Int = 33

        const val SMOKE_POISON_DAMAGE: Int = 2
        const val SMOKE_POISON_DURATION: Int = 100

        const val BLOOD_HEAL_PERCENT: Int = 25
        const val SHADOW_DRAIN_PERCENT: Int = 10
    }
}
