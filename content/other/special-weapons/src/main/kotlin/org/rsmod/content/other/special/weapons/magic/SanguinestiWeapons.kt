package org.rsmod.content.other.special.weapons.magic

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Sanguinesti staff: a powered staff whose bolt scales off the caster's magic level. Each damaging
 * hit has a one-in-six chance to heal the wielder for half of the damage dealt. Uncharged variants
 * cannot be used.
 */
class SanguinestiWeapons @Inject constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val sanguinesti = Sanguinesti(manager)
        register(objs.sanguinesti_staff, sanguinesti)
        register(objs.sanguinesti_staff_or, sanguinesti)

        val uncharged = UnchargedSanguinesti(manager)
        register(objs.sanguinesti_staff_uncharged, uncharged)
        register(objs.sanguinesti_staff_uncharged_or, uncharged)
    }

    private class Sanguinesti(private val manager: WeaponAttackManager) : MagicWeapon {
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
            anim(seqs.human_castwave_staff)

            if (manager.rollStaffSplash(this, target, attack)) {
                manager.playSplashFx(
                    this,
                    target,
                    clientDelay = 0,
                    castSound = null,
                    soundRadius = 10,
                )
                manager.queueSplashHit(this, target, clientDelay = 0)
            } else {
                val baseMaxHit = max(0, player.stat(stats.magic) / 3 - 1)
                val damage = manager.rollStaffMaxHit(this, target, baseMaxHit)
                if (damage > 0 && random.of(0 until HEAL_ROLL_SIDES) == 0) {
                    val heal = damage / 2
                    if (heal > 0) {
                        player.statBoost(stats.hitpoints, constant = heal, percent = 0)
                    }
                }
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay = 0)
            }
            manager.continueCombat(this, target)
        }

        private companion object {
            private const val HEAL_ROLL_SIDES = 6
        }
    }

    private class UnchargedSanguinesti(private val manager: WeaponAttackManager) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminateAttack()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminateAttack()
            return true
        }

        private fun ProtectedAccess.terminateAttack() {
            mes(
                "Your sanguinesti staff has no charges! You need to " +
                    "charge it with blood runes."
            )
            manager.stopCombat(this)
        }
    }
}
