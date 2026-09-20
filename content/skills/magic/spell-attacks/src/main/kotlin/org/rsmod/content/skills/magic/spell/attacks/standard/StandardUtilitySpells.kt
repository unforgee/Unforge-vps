package org.rsmod.content.skills.magic.spell.attacks.standard

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.effects.CombatEffects
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.statSub
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
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.synth.SynthType

/**
 * Standard spellbook utility and weapon-gated combat spells:
 * - Bind/Snare/Entangle freeze the target without dealing damage.
 * - Confuse/Weaken/Curse/Vulnerability/Enfeeble/Stun drain the target's combat stats.
 * - Crumble Undead only works on undead npcs.
 * - Magic Dart (slayer's staff) scales its max hit off the caster's magic level.
 * - Iban Blast, and the three god spells (Saradomin Strike, Claws of Guthix, Flames of Zamorak)
 *   deal damage with additional drain effects.
 * - Teleblock prevents the target player from teleporting.
 */
class StandardUtilitySpells
@Inject
constructor(private val objTypes: ObjTypeList, private val effects: CombatEffects) :
    SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        registerFreezes(manager)
        registerDebuffs(manager)
        registerWeaponSpells(manager)
    }

    private fun SpellAttackRepository.registerFreezes(manager: SpellAttackManager) {
        register(
            spell = objs.spell_bind,
            attack =
                FreezeSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    effects = effects,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = spotanims.entangle_casting,
                    travel = spotanims.entangle_travel,
                    impact = spotanims.bind_impact,
                    castSound = synths.bind_cast,
                    hitSound = synths.bind_impact,
                    freezeCycles = BIND_CYCLES,
                ),
        )
        register(
            spell = objs.spell_snare,
            attack =
                FreezeSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    effects = effects,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = spotanims.entangle_casting,
                    travel = spotanims.entangle_travel,
                    impact = spotanims.snare_impact,
                    castSound = synths.bind_cast,
                    hitSound = synths.bind_impact,
                    freezeCycles = SNARE_CYCLES,
                ),
        )
        register(
            spell = objs.spell_entangle,
            attack =
                FreezeSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    effects = effects,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = spotanims.entangle_casting,
                    travel = spotanims.entangle_travel,
                    impact = spotanims.entangle_impact,
                    castSound = synths.entangle_cast_and_fire,
                    hitSound = synths.entangle_hit,
                    freezeCycles = ENTANGLE_CYCLES,
                ),
        )
    }

    private fun SpellAttackRepository.registerDebuffs(manager: SpellAttackManager) {
        fun registerDebuff(
            spell: ObjType,
            launch: SpotanimType,
            travel: SpotanimType,
            impact: SpotanimType,
            castSound: SynthType,
            hitSound: SynthType,
            stat: StatType,
            percent: Int,
        ) {
            register(
                spell = spell,
                attack =
                    DebuffSpellAttack(
                        objTypes = objTypes,
                        manager = manager,
                        staffAnim = seqs.human_caststrike_staff,
                        unarmedAnim = seqs.human_caststrike,
                        launch = launch,
                        travel = travel,
                        impact = impact,
                        castSound = castSound,
                        hitSound = hitSound,
                        stat = stat,
                        drainPercent = percent,
                    ),
            )
        }

        registerDebuff(
            spell = objs.spell_confuse,
            launch = spotanims.confuse_casting,
            travel = spotanims.confuse_travel,
            impact = spotanims.confuse_impact,
            castSound = synths.confuse_cast_and_fire,
            hitSound = synths.confuse_hit,
            stat = stats.attack,
            percent = 5,
        )
        registerDebuff(
            spell = objs.spell_weaken,
            launch = spotanims.weaken_casting,
            travel = spotanims.weaken_travel,
            impact = spotanims.weaken_impact,
            castSound = synths.weaken_all,
            hitSound = synths.weaken_all,
            stat = stats.strength,
            percent = 5,
        )
        registerDebuff(
            spell = objs.spell_curse,
            launch = spotanims.curse_casting,
            travel = spotanims.curse_travel,
            impact = spotanims.curse_impact,
            castSound = synths.curse_cast_and_fire,
            hitSound = synths.curse_hit,
            stat = stats.defence,
            percent = 5,
        )
        registerDebuff(
            spell = objs.spell_vulnerability,
            launch = spotanims.vulnerability_casting,
            travel = spotanims.vulnerability_travel,
            impact = spotanims.vulnerability_impact,
            castSound = synths.vulnerability_all,
            hitSound = synths.vulnerability_all,
            stat = stats.defence,
            percent = 10,
        )
        registerDebuff(
            spell = objs.spell_enfeeble,
            launch = spotanims.enfeeble_casting,
            travel = spotanims.enfeeble_travel,
            impact = spotanims.enfeeble_impact,
            castSound = synths.enfeeble_cast_and_fire,
            hitSound = synths.enfeeble_hit,
            stat = stats.strength,
            percent = 10,
        )
        registerDebuff(
            spell = objs.spell_stun,
            launch = spotanims.stun_casting,
            travel = spotanims.stun_travel,
            impact = spotanims.stun_impact,
            castSound = synths.stun_all,
            hitSound = synths.stun_all,
            stat = stats.attack,
            percent = 10,
        )
    }

    private fun SpellAttackRepository.registerWeaponSpells(manager: SpellAttackManager) {
        register(
            spell = objs.spell_crumble_undead,
            attack =
                CrumbleUndeadAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = spotanims.crumbleundead_casting,
                    travel = spotanims.crumbleundead_travel,
                    impact = spotanims.crumbleundead_impact,
                    castSound = synths.crumble_cast_and_fire,
                    hitSound = synths.crumble_hit,
                ),
        )

        // Magic Dart: max hit scales with magic level (10 + magic / 10).
        register(
            spell = objs.spell_magic_dart,
            attack =
                DamageSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = null,
                    travel = spotanims.slayer_magicdart_travel,
                    impact = spotanims.slayer_magicdart_impact,
                    castSound = null,
                    hitSound = synths.magic_dart_hit,
                    baseMaxHit = { _, magicLvl -> magicLvl / 10 + 10 },
                ),
        )

        register(
            spell = objs.spell_iban_blast,
            attack =
                DamageSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_caststrike_staff,
                    unarmedAnim = seqs.human_caststrike,
                    launch = spotanims.ibansbolt,
                    travel = spotanims.ibansbolt,
                    impact = spotanims.ibansbolt,
                    castSound = null,
                    hitSound = null,
                    baseMaxHit = { attack, _ -> attack.spell.maxHit },
                ),
        )

        // Saradomin Strike drains the target player's prayer on a successful hit.
        register(
            spell = objs.spell_saradomin_strike,
            attack =
                DamageSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_castwave_staff,
                    unarmedAnim = seqs.human_castwave,
                    launch = null,
                    travel = null,
                    impact = spotanims.saradomin_lightning,
                    castSound = null,
                    hitSound = null,
                    baseMaxHit = { attack, _ -> attack.spell.maxHit },
                    onHit = { target, _ ->
                        if (target is Player) {
                            target.statSub(stats.prayer, constant = 1, percent = 0)
                        }
                    },
                ),
        )

        // Claws of Guthix drains 5% of the target's defence on a successful hit.
        register(
            spell = objs.spell_claws_of_guthix,
            attack =
                DamageSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_castwave_staff,
                    unarmedAnim = seqs.human_castwave,
                    launch = null,
                    travel = null,
                    impact = spotanims.guthix_claw_green,
                    castSound = null,
                    hitSound = null,
                    baseMaxHit = { attack, _ -> attack.spell.maxHit },
                    onHit = { target, _ -> drainStat(target, stats.defence, percent = 5) },
                ),
        )

        // Flames of Zamorak drains 5% of the target's magic on a successful hit.
        register(
            spell = objs.spell_flames_of_zamorak,
            attack =
                DamageSpellAttack(
                    objTypes = objTypes,
                    manager = manager,
                    staffAnim = seqs.human_castwave_staff,
                    unarmedAnim = seqs.human_castwave,
                    launch = null,
                    travel = null,
                    impact = spotanims.zamorak_flame,
                    castSound = null,
                    hitSound = null,
                    baseMaxHit = { attack, _ -> attack.spell.maxHit },
                    onHit = { target, _ -> drainStat(target, stats.magic, percent = 5) },
                ),
        )

        register(
            spell = objs.spell_tele_block,
            attack = TeleblockAttack(objTypes = objTypes, manager = manager, effects = effects),
        )
    }

    /** Damaging spell with an optional post-hit effect. Used by weapon-gated combat spells. */
    private open class DamageSpellAttack(
        private val objTypes: ObjTypeList,
        protected val manager: SpellAttackManager,
        private val staffAnim: SeqType,
        private val unarmedAnim: SeqType,
        private val launch: SpotanimType?,
        private val travel: SpotanimType?,
        private val impact: SpotanimType,
        private val castSound: SynthType?,
        private val hitSound: SynthType?,
        private val baseMaxHit: (CombatAttack.Spell, Int) -> Int,
        private val onHit: (PathingEntity, Int) -> Unit = { _, _ -> },
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        protected fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            val weaponType = objTypes.getOrNull(attack.weapon)
            val castAnim = if (weaponType.isStaff()) staffAnim else unarmedAnim

            anim(castAnim)
            if (launch != null) {
                spotanim(launch, height = 92)
            }

            val serverDelay: Int
            val clientDelay: Int
            if (travel != null) {
                val proj = manager.spawnProjectile(this, target, travel, projanims.magic_spell)
                serverDelay = proj.durations.serverDelay
                clientDelay = proj.durations.clientDelay
            } else {
                // Instant-impact spells (e.g. god spells) land with no travel time.
                serverDelay = 1
                clientDelay = 0
            }

            val spell = attack.spell.obj
            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val damage =
                manager.rollMaxHit(
                    this,
                    target,
                    attack,
                    castResult,
                    max(0, baseMaxHit(attack, player.magicLvl)),
                )
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = impact,
                hitSpotHeight = 124,
                hitSound = hitSound,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spell, damage, clientDelay, serverDelay)
            if (damage > 0) {
                onHit(target, damage)
            }
            manager.continueCombatIfAutocast(this, target)
        }

        protected fun UnpackedObjType?.isStaff(): Boolean =
            this != null && isCategoryType(categories.staff)
    }

    /** Non-damaging freeze spells (Bind/Snare/Entangle). */
    private class FreezeSpellAttack(
        private val objTypes: ObjTypeList,
        private val manager: SpellAttackManager,
        private val effects: CombatEffects,
        private val staffAnim: SeqType,
        private val unarmedAnim: SeqType,
        private val launch: SpotanimType,
        private val travel: SpotanimType,
        private val impact: SpotanimType,
        private val castSound: SynthType?,
        private val hitSound: SynthType?,
        private val freezeCycles: Int,
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
            anim(if (weaponType.isStaffLike()) staffAnim else unarmedAnim)
            spotanim(launch, height = 92)

            val proj = manager.spawnProjectile(this, target, travel, projanims.magic_spell)
            val (serverDelay, clientDelay) = proj.durations
            val spell = attack.spell.obj

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = impact,
                hitSpotHeight = 124,
                hitSound = hitSound,
            )
            manager.giveCombatXp(this, target, attack, 0)
            manager.queueMagicHit(this, target, spell, 0, clientDelay, serverDelay)
            effects.freeze(target, freezeCycles)
            manager.continueCombatIfAutocast(this, target)
        }

        private fun UnpackedObjType?.isStaffLike(): Boolean =
            this != null && isCategoryType(categories.staff)
    }

    /** Non-damaging curse spells that drain a single combat stat by a percentage of its base. */
    private class DebuffSpellAttack(
        private val objTypes: ObjTypeList,
        private val manager: SpellAttackManager,
        private val staffAnim: SeqType,
        private val unarmedAnim: SeqType,
        private val launch: SpotanimType,
        private val travel: SpotanimType,
        private val impact: SpotanimType,
        private val castSound: SynthType?,
        private val hitSound: SynthType?,
        private val stat: StatType,
        private val drainPercent: Int,
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
            anim(if (weaponType.isStaffLike()) staffAnim else unarmedAnim)
            spotanim(launch, height = 92)

            val proj = manager.spawnProjectile(this, target, travel, projanims.magic_spell)
            val (serverDelay, clientDelay) = proj.durations
            val spell = attack.spell.obj

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = impact,
                hitSpotHeight = 124,
                hitSound = hitSound,
            )
            manager.giveCombatXp(this, target, attack, 0)
            manager.queueMagicHit(this, target, spell, 0, clientDelay, serverDelay)
            when (target) {
                is Player -> target.statSub(stat, constant = 0, percent = drainPercent)
                is Npc -> drainNpcStat(target)
            }
            manager.continueCombatIfAutocast(this, target)
        }

        private fun drainNpcStat(npc: Npc) {
            val (current, base) =
                when (stat) {
                    stats.attack -> npc.attackLvl to npc.baseAttackLvl
                    stats.strength -> npc.strengthLvl to npc.baseStrengthLvl
                    stats.defence -> npc.defenceLvl to npc.baseDefenceLvl
                    stats.magic -> npc.magicLvl to npc.baseMagicLvl
                    else -> return
                }
            val drained = (current - base * drainPercent / 100).coerceAtLeast(0)
            when (stat) {
                stats.attack -> npc.attackLvl = drained
                stats.strength -> npc.strengthLvl = drained
                stats.defence -> npc.defenceLvl = drained
                stats.magic -> npc.magicLvl = drained
                else -> Unit
            }
        }

        private fun UnpackedObjType?.isStaffLike(): Boolean =
            this != null && isCategoryType(categories.staff)
    }

    /** Crumble Undead: only usable against undead npcs. */
    private class CrumbleUndeadAttack(
        objTypes: ObjTypeList,
        manager: SpellAttackManager,
        staffAnim: SeqType,
        unarmedAnim: SeqType,
        launch: SpotanimType,
        travel: SpotanimType,
        impact: SpotanimType,
        castSound: SynthType?,
        hitSound: SynthType?,
    ) :
        DamageSpellAttack(
            objTypes = objTypes,
            manager = manager,
            staffAnim = staffAnim,
            unarmedAnim = unarmedAnim,
            launch = launch,
            travel = travel,
            impact = impact,
            castSound = castSound,
            hitSound = hitSound,
            baseMaxHit = { attack, _ -> attack.spell.maxHit },
        ) {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            if (target.type.param(params.undead) == 0) {
                mes("You can only cast this spell on undead monsters.")
                manager.stopCombat(this)
                return
            }
            cast(target, attack)
        }
    }

    /** Teleblock: only meaningful against other players. */
    private class TeleblockAttack(
        private val objTypes: ObjTypeList,
        private val manager: SpellAttackManager,
        private val effects: CombatEffects,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            mes("This spell can only be cast on other players.")
            manager.stopCombat(this)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            val weaponType = objTypes.getOrNull(attack.weapon)
            anim(
                if (weaponType != null && weaponType.isCategoryType(categories.staff)) {
                    seqs.human_caststrike_staff
                } else {
                    seqs.human_caststrike
                }
            )

            val proj =
                manager.spawnProjectile(
                    this,
                    target,
                    spotanims.tele_block_travel_forfail,
                    projanims.magic_spell,
                )
            val (serverDelay, clientDelay) = proj.durations

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, synths.teleportblock_cast, 8)
                manager.queueSplashHit(this, target, attack.spell.obj, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = synths.teleportblock_cast,
                soundRadius = 8,
                hitSpot = spotanims.tele_block_impact,
                hitSpotHeight = 124,
                hitSound = synths.teleportblock_impact,
            )
            manager.giveCombatXp(this, target, attack, 0)
            manager.queueMagicHit(this, target, attack.spell.obj, 0, clientDelay, serverDelay)
            effects.teleblock(target, TELEBLOCK_CYCLES)
            manager.continueCombatIfAutocast(this, target)
        }
    }

    private companion object {
        private const val BIND_CYCLES = 8
        private const val SNARE_CYCLES = 16
        private const val ENTANGLE_CYCLES = 24
        private const val TELEBLOCK_CYCLES = 500
    }
}

private fun drainStat(target: PathingEntity, stat: StatType, percent: Int) {
    when (target) {
        is Player -> target.statSub(stat, constant = 0, percent = percent)
        is Npc -> {
            val (current, base) =
                when (stat) {
                    stats.attack -> target.attackLvl to target.baseAttackLvl
                    stats.strength -> target.strengthLvl to target.baseStrengthLvl
                    stats.defence -> target.defenceLvl to target.baseDefenceLvl
                    stats.magic -> target.magicLvl to target.baseMagicLvl
                    else -> return
                }
            val drained = (current - base * percent / 100).coerceAtLeast(0)
            when (stat) {
                stats.attack -> target.attackLvl = drained
                stats.strength -> target.strengthLvl = drained
                stats.defence -> target.defenceLvl = drained
                stats.magic -> target.magicLvl = drained
                else -> Unit
            }
        }
    }
}
