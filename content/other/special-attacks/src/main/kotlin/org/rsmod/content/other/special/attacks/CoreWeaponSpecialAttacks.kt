package org.rsmod.content.other.special.attacks

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.api.specials.instant.InstantSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Special attacks for the iconic weapons - godswords, dragon claws, ballistae, blowpipe, the
 * Nightmare staves and friends.
 *
 * Every registration is guarded by [SpecialAttackManager.getSpecialEnergyRequirement]: a weapon
 * whose `sa_energy_requirements` enum entry is missing from this cache is simply skipped instead of
 * crashing startup. Unregistered-but-mapped weapons still receive the generic fallback spec in
 * `api/combat/combat-scripts`.
 */
class CoreWeaponSpecialAttacks
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val ammunition: RangedAmmoManager,
    private val attackManager: PlayerAttackManager,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        // Godswords (zamorak and bandos godswords live in OsrsWeaponSpecialAttacks, which
        // implements their freeze and stat-drain effects).
        melee(manager, objs.armadyl_godsword, EmpoweredMelee(manager, attackManager, 2.00, 1.375))
        melee(manager, objs.saradomin_godsword, SgsStrike(manager, attackManager))

        // Daggers and claws
        val dagger = DoubleMelee(manager, attackManager, 1.25, 1.15)
        melee(manager, objs.dragon_dagger, dagger)
        melee(manager, objs.dragon_dagger_p, dagger)
        melee(manager, objs.dragon_dagger_p_plus, dagger)
        melee(manager, objs.dragon_dagger_p_plus_plus, dagger)
        melee(manager, objs.dragon_claws, ClawFlurry(manager, attackManager))
        melee(manager, objs.rune_claws, ClawFlurry(manager, attackManager))

        // Heavy melee
        melee(manager, objs.dragon_mace, EmpoweredMelee(manager, attackManager, 0.75, 1.50))

        // Instant
        instant(manager, objs.excalibur, InstantSpecialAttack(::sanctuary))

        // Ranged
        ranged(
            manager,
            objs.magic_shortbow,
            RapidShot(manager, attackManager, ammunition, objTypes, 2, 0.85),
        )
        ranged(
            manager,
            objs.heavy_ballista,
            HeavyShot(manager, attackManager, ammunition, objTypes, 1.25, 1.25),
        )
        ranged(
            manager,
            objs.light_ballista,
            HeavyShot(manager, attackManager, ammunition, objTypes, 1.25, 1.20),
        )
        val toxicSiphon = ToxicSiphon(manager, attackManager, ammunition, objTypes)
        ranged(manager, objs.toxic_blowpipe, toxicSiphon)
        ranged(manager, objs.toxic_blowpipe_loaded, toxicSiphon)

        // Magic staves
        val surge = StaffSurge(manager, damageMultiplier = 1.25, lifestealBps = 0)
        magic(manager, objs.nightmare_staff, surge)
        magic(manager, objs.nightmare_staff_harmonised, surge)
        magic(manager, objs.zuriels_staff, surge)
    }

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

    private fun SpecialAttackRepository.instant(
        manager: SpecialAttackManager,
        type: ObjType,
        spec: InstantSpecialAttack,
    ) {
        if (manager.getSpecialEnergyRequirement(type) != null) {
            registerInstant(type, spec)
        }
    }

    /** Excalibur "Sanctuary": +8 defence levels. */
    private fun sanctuary(access: ProtectedAccess): Boolean {
        access.statBoost(stats.defence, constant = 8, percent = 0)
        access.mes("You feel a wave of courage.")
        return true
    }
}

/** Single empowered melee hit: [accuracyMultiplier]x accuracy, [damageMultiplier]x max hit. */
private class EmpoweredMelee(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val accuracyMultiplier: Double,
    private val damageMultiplier: Double,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        strike(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        strike(target, attack)
        return true
    }

    private fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        val damage =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = accuracyMultiplier,
                maxHitMultiplier = damageMultiplier,
            )
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/** Two melee swings in quick succession (dragon dagger / halberd / macuahuitl style). */
private class DoubleMelee(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val accuracyMultiplier: Double,
    private val damageMultiplier: Double,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        strike(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        strike(target, attack)
        return true
    }

    private fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        var total = 0
        repeat(2) { i ->
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = accuracyMultiplier,
                    maxHitMultiplier = damageMultiplier,
                )
            manager.queueMeleeHit(this, target, damage, delay = 1 + i)
            total += damage
        }
        manager.giveCombatXp(this, target, attack, total)
        manager.continueCombat(this, target)
    }
}

/** Claw spec: one roll split into four rapid hits - 100% / 50% / 25% / 25%. */
private class ClawFlurry(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        flurry(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        flurry(target, attack)
        return true
    }

    private fun ProtectedAccess.flurry(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        val rolled =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 1.25,
                maxHitMultiplier = 1.00,
            )
        val parts = intArrayOf(rolled, rolled / 2, rolled / 4, rolled / 4)
        parts.forEachIndexed { i, damage ->
            manager.queueMeleeHit(this, target, damage, delay = 1 + i / 2)
        }
        manager.giveCombatXp(this, target, attack, parts.sum())
        manager.continueCombat(this, target)
    }
}

/** Saradomin godsword: boosted hit that heals 50% of the damage and restores 25% to prayer. */
private class SgsStrike(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        strike(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        strike(target, attack)
        return true
    }

    private fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        val damage =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.00,
                maxHitMultiplier = 1.10,
            )
        if (damage > 0) {
            player.statBoost(stats.hitpoints, constant = damage / 2, percent = 0)
            player.statBoost(stats.prayer, constant = damage / 4, percent = 0)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/** Shared ranged spec pipeline: fires the loaded ammunition with boosted rolls. */
private abstract class BaseRangedSpec(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
    private val objTypes: ObjTypeList,
) : RangedSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Ranged): Boolean =
        fire(target, attack)

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Ranged,
    ): Boolean = fire(target, attack)

    protected abstract val shots: Int
    protected abstract val accuracyMultiplier: Double
    protected abstract val damageMultiplier: Double

    protected open fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) = Unit

    private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged): Boolean {
        val righthandType = objTypes[attack.weapon]
        val quiverType = objTypes.getOrNull(player.quiver)
        val usingThrown = righthandType.isCategoryType(categories.throwing_weapon)

        val weaponType: UnpackedObjType? = if (usingThrown) righthandType else quiverType
        if (weaponType == null || !ammunition.attemptAmmoUsage(player, righthandType, quiverType)) {
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

private class RapidShot(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    override val shots: Int,
    override val damageMultiplier: Double,
) : BaseRangedSpec(manager, attackManager, ammunition, objTypes) {
    override val accuracyMultiplier: Double = 1.0
}

private class HeavyShot(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    private val acc: Double,
    override val damageMultiplier: Double,
) : BaseRangedSpec(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = acc
}

/** Toxic blowpipe "Toxic siphon": two quick darts, healing half the damage dealt. */
private class ToxicSiphon(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : BaseRangedSpec(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 2
    override val accuracyMultiplier: Double = 1.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        if (damage > 0) {
            player.statBoost(stats.hitpoints, constant = damage / 2, percent = 0)
        }
    }
}

/** Powered-staff bolt scaled off the caster's magic level; optional lifesteal. */
private class StaffSurge(
    private val manager: SpecialAttackManager,
    private val damageMultiplier: Double,
    private val lifestealBps: Int,
) : MagicSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Staff): Boolean {
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
        val accurate = manager.rollStaffAccuracy(this, target, attack.style, 1.25)
        val baseMaxHit = 1 + player.stat(stats.magic) / 4
        val damage =
            if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, damageMultiplier) else 0
        if (damage > 0 && lifestealBps > 0) {
            val heal = (damage.toLong() * lifestealBps / 10_000).toInt()
            if (heal > 0) {
                player.statBoost(stats.hitpoints, constant = heal, percent = 0)
            }
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}
