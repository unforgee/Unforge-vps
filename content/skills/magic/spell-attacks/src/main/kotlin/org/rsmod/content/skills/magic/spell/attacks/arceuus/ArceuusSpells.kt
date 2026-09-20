package org.rsmod.content.skills.magic.spell.attacks.arceuus

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.effects.CombatEffects
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
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
 * The Arceuus combat spells.
 * - `demonbane` spells deal bonus damage to demons, scaled down for npcs flagged with the
 *   `demonbane_resistant` param.
 * - `grasp` spells bind the target in place and heal the caster for a portion of the damage dealt.
 *
 * The remaining Arceuus spells are either utility casts or thrall summons that do not deal direct
 * spell damage and are therefore not registered here.
 */
class ArceuusSpells
@Inject
constructor(private val objTypes: ObjTypeList, private val effects: CombatEffects) :
    SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        registerDemonbanes(manager)
        registerGrasps(manager)
    }

    private fun SpellAttackRepository.registerDemonbanes(manager: SpellAttackManager) {
        register(
            objs.spell_inferior_demonbane,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = null,
                launch = spotanims.inferior_demonbane_cast_spotanim,
                impact = spotanims.inferior_demonbane_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = INFERIOR_DEMONBANE_BONUS,
            bindCycles = 0,
        )
        register(
            objs.spell_superior_demonbane,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = null,
                launch = spotanims.superior_demonbane_cast_spotanim,
                impact = spotanims.superior_demonbane_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = SUPERIOR_DEMONBANE_BONUS,
            bindCycles = 0,
        )
        register(
            objs.spell_dark_demonbane,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                launch = spotanims.dark_demonbane_cast_spotanim,
                impact = spotanims.dark_demonbane_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = DARK_DEMONBANE_BONUS,
            bindCycles = 0,
        )
    }

    private fun SpellAttackRepository.registerGrasps(manager: SpellAttackManager) {
        register(
            objs.spell_ghostly_grasp,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = null,
                launch = spotanims.ghostly_grasp_cast_spotanim,
                impact = spotanims.ghostly_grasp_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = 0,
            bindCycles = GRASP_GHOSTLY_BIND,
        )
        register(
            objs.spell_skeletal_grasp,
            manager,
            Fx(
                anim = seqs.human_caststrike,
                staffAnim = seqs.human_caststrike_staff,
                travel = null,
                launch = spotanims.skeletal_grasp_cast_spotanim,
                impact = spotanims.skeletal_grasp_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = 0,
            bindCycles = GRASP_SKELETAL_BIND,
        )
        register(
            objs.spell_undead_grasp,
            manager,
            Fx(
                anim = seqs.human_castwave,
                staffAnim = seqs.human_castwave_staff,
                travel = null,
                launch = spotanims.undead_grasp_cast_spotanim,
                impact = spotanims.undead_grasp_hit_spotanim,
                castSound = null,
                hitSound = null,
            ),
            bonusPercent = 0,
            bindCycles = GRASP_UNDEAD_BIND,
        )
    }

    private fun SpellAttackRepository.register(
        spell: ObjType,
        manager: SpellAttackManager,
        fx: Fx,
        bonusPercent: Int,
        bindCycles: Int,
    ) {
        register(
            spell = spell,
            attack =
                ArceuusSpellAttack(
                    manager = manager,
                    objTypes = objTypes,
                    effects = effects,
                    fx = fx,
                    bonusPercent = bonusPercent,
                    bindCycles = bindCycles,
                ),
        )
    }

    private class ArceuusSpellAttack(
        private val manager: SpellAttackManager,
        private val objTypes: ObjTypeList,
        private val effects: CombatEffects,
        private val fx: Fx,
        private val bonusPercent: Int,
        private val bindCycles: Int,
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
            spotanim(fx.launch, height = 92)

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay = 0, fx.castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, attack.spell.obj, clientDelay = 0)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val baseMaxHit = attack.spell.maxHit
            val damage = rollDamage(target, attack, castResult, baseMaxHit)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = 0,
                castSound = fx.castSound,
                soundRadius = 8,
                hitSpot = fx.impact,
                hitSpotHeight = 124,
                hitSound = fx.hitSound,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, attack.spell.obj, damage, clientDelay = 0)

            if (bindCycles > 0) {
                effects.bind(target, bindCycles)
                healFromDamage(damage)
            }

            manager.continueCombatIfAutocast(this, target)
        }

        /**
         * [manager.rollMaxHit] already applies magic damage bonuses and prayer modifiers. The
         * demonbane multiplier is applied on top of that as a final percentage boost, and is
         * reduced for npcs flagged as demonbane-resistant.
         */
        private fun ProtectedAccess.rollDamage(
            target: PathingEntity,
            attack: CombatAttack.Spell,
            castResult: org.rsmod.api.combat.manager.MagicRuneManager.CastResult,
            baseMaxHit: Int,
        ): Int {
            val rolled = manager.rollMaxHit(this, target, attack, castResult, baseMaxHit)
            if (bonusPercent <= 0 || target !is Npc || !target.isDemon()) {
                return rolled
            }
            val resistance = target.visType.paramOrNull(params.demonbane_resistant) ?: 0
            val applied = (bonusPercent - resistance).coerceAtLeast(0)
            return rolled + (rolled * applied / 100)
        }

        private fun Npc.isDemon(): Boolean = (visType.paramOrNull(params.demon) ?: 0) > 0

        private fun ProtectedAccess.healFromDamage(damage: Int) {
            val healed = damage * GRASP_HEAL_PERCENT / 100
            if (healed > 0) {
                statHeal(stats.hitpoints, healed, 0)
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
        val launch: SpotanimType,
        val impact: SpotanimType,
        val castSound: SynthType?,
        val hitSound: SynthType?,
    )

    private companion object {
        const val INFERIOR_DEMONBANE_BONUS: Int = 25
        const val SUPERIOR_DEMONBANE_BONUS: Int = 50
        const val DARK_DEMONBANE_BONUS: Int = 100

        /** Bind durations, in ticks (1 tick = 0.6s). */
        const val GRASP_GHOSTLY_BIND: Int = 8
        const val GRASP_SKELETAL_BIND: Int = 16
        const val GRASP_UNDEAD_BIND: Int = 24

        const val GRASP_HEAL_PERCENT: Int = 10
    }
}
