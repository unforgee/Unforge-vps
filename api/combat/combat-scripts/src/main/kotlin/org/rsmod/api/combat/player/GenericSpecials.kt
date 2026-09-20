package org.rsmod.api.combat.player

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.stat.stat
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Generic special-attack fallback for weapons that have an `sa_attack` energy requirement in the
 * cache but no content-registered special.
 *
 * Without this, pressing the special-attack orb on an unregistered weapon silently does nothing.
 * The fallback mirrors the standard combat pipelines with a modest power boost - melee gets a
 * heavier hit, ranged fires the loaded ammunition at +30%, and staves hurl a magic bolt scaled off
 * the caster's magic level. Registered specials always take precedence.
 */
@Singleton
internal class GenericSpecials
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
) {
    fun meleeFor(weapon: InvObj?): SpecialAttack.Melee? {
        weapon ?: return null
        val type = objTypes[weapon]
        val energy = manager.getSpecialEnergyRequirement(type) ?: return null
        return SpecialAttack.Melee(energy, GenericMelee(manager, attackManager))
    }

    fun rangedFor(weapon: InvObj): SpecialAttack.Ranged? {
        val type = objTypes[weapon]
        val energy = manager.getSpecialEnergyRequirement(type) ?: return null
        return SpecialAttack.Ranged(
            energy,
            GenericRanged(manager, attackManager, ammunition, objTypes),
        )
    }

    fun magicFor(weapon: InvObj): SpecialAttack.Magic? {
        val type = objTypes[weapon]
        val energy = manager.getSpecialEnergyRequirement(type) ?: return null
        return SpecialAttack.Magic(energy, GenericMagic(manager))
    }

    private class GenericMelee(
        private val manager: SpecialAttackManager,
        private val attackManager: PlayerAttackManager,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
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
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.30,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    private class GenericRanged(
        private val manager: SpecialAttackManager,
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
                ammunition.useThrownWeapon(player, righthandType, target.coords, serverDelay)
            } else if (quiverType != null) {
                ammunition.useQuiverAmmo(player, quiverType, target.coords, serverDelay)
            }

            val damage =
                manager.rollRangedDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.30,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                this,
                target,
                if (usingThrown) null else quiverType,
                damage,
                clientDelay,
                serverDelay,
            )
            manager.continueCombat(this, target)
            return true
        }
    }

    private class GenericMagic(private val manager: SpecialAttackManager) : MagicSpecialAttack {
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
            val accurate = manager.rollStaffAccuracy(this, target, attack.style, 1.25)
            val baseMaxHit = 1 + player.stat(stats.magic) / 5
            val damage =
                if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, 1.30) else 0
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0)
            manager.continueCombat(this, target)
        }
    }
}
