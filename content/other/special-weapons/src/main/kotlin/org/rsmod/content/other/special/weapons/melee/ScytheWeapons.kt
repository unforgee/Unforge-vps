package org.rsmod.content.other.special.weapons.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Scythe of vitur: on targets that occupy at least a 2x2 area (and always versus players) the
 * scythe sweeps for three hits - full damage, then up to half, then up to a quarter of the normal
 * max hit. Uncharged variants cannot be used.
 */
class ScytheWeapons @Inject constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val scythe = Scythe(manager)
        register(objs.scythe_of_vitur, scythe)
        register(objs.scythe_of_vitur_or, scythe)
        register(objs.scythe_of_vitur_bl, scythe)
        register(objs.scythe_of_vitur_deadman, scythe)

        val uncharged = UnchargedScythe(manager)
        register(objs.scythe_of_vitur_uncharged, uncharged)
        register(objs.scythe_of_vitur_uncharged_or, uncharged)
        register(objs.scythe_of_vitur_uncharged_bl, uncharged)
        register(objs.scythe_of_vitur_deadman_uncharged, uncharged)
    }

    private class Scythe(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            sweep(target, attack, multiHit = target.type.size >= 2)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            sweep(target, attack, multiHit = true)
            return true
        }

        private fun ProtectedAccess.sweep(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            multiHit: Boolean,
        ) {
            manager.playWeaponFx(this, attack)

            val parts = if (multiHit) HIT_MULTIPLIERS else HIT_MULTIPLIERS.copyOfRange(0, 1)
            var total = 0
            parts.forEachIndexed { i, multiplier ->
                val damage =
                    manager.rollMeleeDamage(
                        this,
                        target,
                        attack,
                        accuracyMultiplier = 1.0,
                        maxHitMultiplier = multiplier,
                    )
                manager.queueMeleeHit(this, target, damage, delay = 1 + i)
                total += damage
            }

            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }

        private companion object {
            private val HIT_MULTIPLIERS = doubleArrayOf(1.0, 0.5, 0.25)
        }
    }

    private class UnchargedScythe(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            terminateAttack()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            terminateAttack()
            return true
        }

        private fun ProtectedAccess.terminateAttack() {
            mes(
                "Your scythe has no charges! You need to charge it with blood runes and vials of blood."
            )
            manager.stopCombat(this)
        }
    }
}
