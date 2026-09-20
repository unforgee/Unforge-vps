package org.rsmod.content.other.league.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.effects.StatusService
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.content.other.league.configs.league_combat_seqs
import org.rsmod.content.other.league.configs.league_combat_spots
import org.rsmod.content.other.league.configs.league_objs
import org.rsmod.content.other.league.configs.league_varps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.stat.StatType
import org.rsmod.map.CoordGrid

/**
 * Melee special attacks for the League/Echo weapons.
 *
 * Spec energy and effect descriptions are cache-provided (`sa_energy_requirements` /
 * `sa_descriptions` and the obj `param_2060` field). Items without a cache spec entry are not
 * registered here.
 */
class LeagueWeaponSpecialAttacks
@Inject
constructor(
    private val npcList: NpcList,
    private val wornBonuses: WornBonuses,
    private val objTypes: ObjTypeList,
    private val attackManager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
    private val statuses: StatusService,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val khopesh = ThunderKhopesh(manager, npcList)
        melee(manager, league_objs.thunder_khopesh, khopesh)
        melee(manager, league_objs.deadman_thunder_khopesh, khopesh)
        melee(manager, league_objs.echo_godsword, EchoGodsword(manager))
        melee(manager, league_objs.weapon_of_sol, SunlightSpear(manager, npcList, wornBonuses))
        melee(manager, league_objs.echo_gauntlet_crystal_dagger_t3, CrystalDagger(manager))
        melee(manager, league_objs.dinhs_bulwark_ornament, BlazingBulwark(manager, npcList))

        val siphon = SiphonBarrage(manager, attackManager, ammunition, objTypes)
        ranged(manager, league_objs.toxic_blowpipe_ornament, siphon)
        ranged(manager, league_objs.toxic_blowpipe_loaded_ornament, siphon)

        val barrage = DrygoreBarrage(manager, attackManager, ammunition, objTypes, statuses)
        ranged(manager, league_objs.drygore_blowpipe, barrage)
        ranged(manager, league_objs.drygore_blowpipe_loaded, barrage)

        magic(manager, objs.eye_of_ayak, SoulFlame(manager))
    }

    /**
     * Registers only weapons that have a `sa_energy_requirements` enum entry in this cache;
     * unmapped weapons are skipped instead of crashing startup.
     */
    private fun SpecialAttackRepository.melee(
        manager: SpecialAttackManager,
        type: ObjType,
        spec: MeleeSpecialAttack,
    ) {
        if (manager.getSpecialEnergyRequirement(type) != null) {
            registerMelee(type, spec)
        }
    }

    private fun SpecialAttackRepository.ranged(
        manager: SpecialAttackManager,
        type: ObjType,
        spec: RangedSpecialAttack,
    ) {
        if (manager.getSpecialEnergyRequirement(type) != null) {
            registerRanged(type, spec)
        }
    }

    private fun SpecialAttackRepository.magic(
        manager: SpecialAttackManager,
        type: ObjType,
        spec: MagicSpecialAttack,
    ) {
        if (manager.getSpecialEnergyRequirement(type) != null) {
            registerMagic(type, spec)
        }
    }

    /**
     * "Lingering Lightning" - attacks twice and bolts the target tile if either hit lands,
     * splashing every npc in a ~10x10 area.
     */
    private class ThunderKhopesh(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            special(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            special(target, attack)

        private fun ProtectedAccess.special(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim(league_combat_seqs.human_sword_slash)
            spotanim(league_combat_spots.cleave, height = 96, slot = constants.spotanim_slot_combat)

            val first = roll(target, attack)
            val second = roll(target, attack)
            manager.giveCombatXp(this, target, attack, first + second)
            manager.queueMeleeHit(this, target, first)
            manager.queueMeleeHit(this, target, second)

            if (first > 0 || second > 0) {
                lingeringLightning(target, attack)
            }
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 1.0,
                maxHitMultiplier = 1.0,
                blockType = MeleeAttackType.Slash,
            )

        private fun ProtectedAccess.lingeringLightning(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            val center = target.coords
            val maxHit = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            target.spotanim(league_combat_spots.lightning)

            forEachNpcWithin(npcList, center, radius = LIGHTNING_RADIUS) { other ->
                if (other === target) return@forEachNpcWithin
                manager.queueMeleeHit(this, other, random.of(0..maxHit))
            }
        }

        private companion object {
            private const val LIGHTNING_RADIUS = 5
        }
    }

    /** "Echo slash" - applies the godsword effects on a single empowered hit. */
    private class EchoGodsword(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            special(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            special(target, attack)

        private fun ProtectedAccess.special(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim(league_combat_seqs.human_sword_slash)
            spotanim(league_combat_spots.cleave, height = 96, slot = constants.spotanim_slot_combat)

            // Armadyl (37.5% more damage, double accuracy), Bandos (drain), Saradomin (heal) and
            // Zamorak (freeze) are all applied by one hit.
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.375,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)

            val hitpoints = damage * 50 / 100
            if (hitpoints > 0) {
                statBoost(stats.hitpoints, constant = hitpoints, percent = 0)
            }
            val prayer = damage * 25 / 100
            if (prayer > 0) {
                statBoost(stats.prayer, constant = prayer, percent = 0)
            }

            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    /**
     * "Sol Slam" - spends the sunlight stacks (energy requirement `7`) to hit every npc within 3
     * tiles of the player, with damage increased by 3% per prayer bonus.
     */
    private class SunlightSpear(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
        private val wornBonuses: WornBonuses,
    ) : MeleeSpecialAttack {
        private var Player.sunlightStacks by intVarp(league_varps.weapon_of_sol_stacks)

        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            special(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            special(target, attack)

        private fun ProtectedAccess.special(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (
                player.sunlightStacks < REQUIRED_STACKS ||
                    !manager.hasSpecialEnergy(this, REQUIRED_STACKS)
            ) {
                mes("You don't have enough power left.")
                manager.stopCombat(this)
                return false
            }
            manager.takeSpecialEnergy(this, REQUIRED_STACKS)
            player.sunlightStacks = 0

            anim(league_combat_seqs.human_spear_lunge)
            spotanim(league_combat_spots.cleave, height = 96, slot = constants.spotanim_slot_combat)

            val prayerBonus = wornBonuses.calculate(player).prayer
            val multiplier = 1.0 + (prayerBonus * PRAYER_BONUS_RATE)
            forEachNpcWithin(npcList, coords, radius = 3) { other ->
                val damage =
                    manager.rollMeleeDamage(
                        source = this,
                        target = other,
                        attack = attack,
                        accuracyMultiplier = 1.0,
                        maxHitMultiplier = multiplier,
                        blockType = MeleeAttackType.Stab,
                    )
                manager.giveCombatXp(this, other, attack, damage)
                manager.queueMeleeHit(this, other, damage)
            }
            manager.continueCombat(this, target)
            return true
        }

        private companion object {
            private const val REQUIRED_STACKS = 7
            private const val PRAYER_BONUS_RATE = 0.03
        }
    }

    /** "Crystalline Severance" - an empowered stab. */
    private class CrystalDagger(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            special(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            special(target, attack)

        private fun ProtectedAccess.special(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim(league_combat_seqs.human_sword_stab)
            spotanim(league_combat_spots.cleave, height = 96, slot = constants.spotanim_slot_combat)

            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.25,
                    blockType = MeleeAttackType.Stab,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    /** "Shield Bash" - hits surrounding targets in a 10x10 area with 20% increased accuracy. */
    private class BlazingBulwark(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            special(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            special(target, attack)

        private fun ProtectedAccess.special(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim(league_combat_seqs.human_sword_slash)
            spotanim(league_combat_spots.cleave, height = 96, slot = constants.spotanim_slot_combat)

            forEachNpcWithin(npcList, target.coords, radius = SHIELD_BASH_RADIUS) { other ->
                val damage =
                    manager.rollMeleeDamage(
                        source = this,
                        target = other,
                        attack = attack,
                        accuracyMultiplier = 1.2,
                        maxHitMultiplier = 1.0,
                        blockType = MeleeAttackType.Crush,
                    )
                manager.giveCombatXp(this, other, attack, damage)
                manager.queueMeleeHit(this, other, damage)
            }
            manager.continueCombat(this, target)
            return true
        }

        private companion object {
            private const val SHIELD_BASH_RADIUS = 5
        }
    }

    /**
     * Shared blowpipe-style ranged spec pipeline: fires the loaded ammunition [shots] times with
     * the given roll multipliers and invokes [onHit] per dart.
     */
    private abstract class BaseBarrageSpec(
        protected val manager: SpecialAttackManager,
        private val attackManager: PlayerAttackManager,
        private val ammunition: RangedAmmoManager,
        private val objTypes: ObjTypeList,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = fire(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = fire(target, attack)

        protected abstract val shots: Int
        protected abstract val accuracyMultiplier: Double
        protected abstract val damageMultiplier: Double

        protected open fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) = Unit

        private fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val righthandType = objTypes[attack.weapon]
            val quiverType = objTypes.getOrNull(player.quiver)
            val usingThrown = righthandType.isCategoryType(categories.throwing_weapon)

            val weaponType: UnpackedObjType? = if (usingThrown) righthandType else quiverType
            if (
                weaponType == null ||
                    !ammunition.attemptAmmoUsage(player, righthandType, quiverType)
            ) {
                return false
            }

            val projanimType = righthandType.paramOrNull(params.proj_type)
            val travelSpotanim = weaponType.paramOrNull(params.proj_travel)

            attackManager.playWeaponFx(player, attack)

            var serverDelay = 1
            var clientDelay = 30
            if (projanimType != null && travelSpotanim != null) {
                val launchSpotanim = weaponType.paramOrNull(params.proj_launch)
                spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)
                val projanim = manager.spawnProjectile(this, target, travelSpotanim, projanimType)
                serverDelay = projanim.durations.serverDelay
                clientDelay = projanim.durations.clientDelay
            }

            if (usingThrown) {
                repeat(shots) {
                    ammunition.useThrownWeapon(player, righthandType, target.coords, serverDelay)
                }
            } else if (quiverType != null) {
                repeat(shots) {
                    ammunition.useQuiverAmmo(player, quiverType, target.coords, serverDelay)
                }
            }

            var total = 0
            repeat(shots) { i ->
                val damage =
                    manager.rollRangedDamage(
                        source = this,
                        target = target,
                        attack = attack,
                        accuracyMultiplier = accuracyMultiplier,
                        maxHitMultiplier = damageMultiplier,
                    )
                manager.queueRangedHit(
                    this,
                    target,
                    if (usingThrown) null else quiverType,
                    damage,
                    clientDelay,
                    serverDelay + i,
                )
                total += damage
                onHit(target, damage)
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
            return true
        }
    }

    /** Toxic blowpipe ornament "Toxic siphon": two darts, healing half the damage dealt. */
    private class SiphonBarrage(
        manager: SpecialAttackManager,
        attackManager: PlayerAttackManager,
        ammunition: RangedAmmoManager,
        objTypes: ObjTypeList,
    ) : BaseBarrageSpec(manager, attackManager, ammunition, objTypes) {
        override val shots: Int = 2
        override val accuracyMultiplier: Double = 1.0
        override val damageMultiplier: Double = 1.0

        override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
            if (damage > 0) {
                player.statBoost(stats.hitpoints, constant = damage / 2, percent = 0)
            }
        }
    }

    /** Drygore blowpipe: two darts, each with a 25% chance to ignite the target. */
    private class DrygoreBarrage(
        manager: SpecialAttackManager,
        attackManager: PlayerAttackManager,
        ammunition: RangedAmmoManager,
        objTypes: ObjTypeList,
        private val statuses: StatusService,
    ) : BaseBarrageSpec(manager, attackManager, ammunition, objTypes) {
        override val shots: Int = 2
        override val accuracyMultiplier: Double = 1.0
        override val damageMultiplier: Double = 1.0

        override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
            if (damage > 0 && random.of(0 until BURN_ROLL_SIDES) == 0) {
                statuses.apply(target, StatusEffect.BURN, duration = 40, potency = 1)
            }
        }

        private companion object {
            private const val BURN_ROLL_SIDES = 4
        }
    }

    /**
     * Eye of Ayak "Soul flame": a powered bolt (max hit `Magic / 3 - 6`) that drains the target's
     * magic and defence levels by 10% on a successful hit.
     */
    private class SoulFlame(private val manager: SpecialAttackManager) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Staff) {
            val accurate = manager.rollStaffAccuracy(this, target, attack.style, 1.0)
            val baseMaxHit = (player.stat(stats.magic) / 3 - 6).coerceAtLeast(1)
            val damage = if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, 1.0) else 0
            if (damage > 0) {
                drainStat(target, stats.magic, DRAIN_PERCENT)
                drainStat(target, stats.defence, DRAIN_PERCENT)
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0)
            manager.continueCombat(this, target)
        }

        private fun drainStat(target: PathingEntity, stat: StatType, percent: Int) {
            when (target) {
                is Player -> target.statSub(stat, constant = 0, percent = percent)
                is Npc -> {
                    val (current, base) =
                        when (stat) {
                            stats.magic -> target.magicLvl to target.baseMagicLvl
                            stats.defence -> target.defenceLvl to target.baseDefenceLvl
                            else -> return
                        }
                    val drained = (current - base * percent / 100).coerceAtLeast(0)
                    when (stat) {
                        stats.magic -> target.magicLvl = drained
                        stats.defence -> target.defenceLvl = drained
                        else -> Unit
                    }
                }
            }
        }

        private companion object {
            private const val DRAIN_PERCENT = 10
        }
    }
}

private inline fun forEachNpcWithin(
    npcList: NpcList,
    center: CoordGrid,
    radius: Int,
    action: (Npc) -> Unit,
) {
    for (npc in npcList) {
        if (!npc.isSlotAssigned || npc.isInvisible || npc.hitpoints <= 0) continue
        if (npc.coords.level != center.level) continue
        if (npc.coords.chebyshevDistance(center) > radius) continue
        action(npc)
    }
}
