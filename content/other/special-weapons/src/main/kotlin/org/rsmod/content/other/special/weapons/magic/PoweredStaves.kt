package org.rsmod.content.other.special.weapons.magic

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Powered staves with a built-in magic spell. Each staff scales its base max hit off the caster's
 * visible magic level using its own official formula. Uncharged variants cannot be used.
 *
 * Charge consumption is not tracked here - the charged/uncharged state is decided by which obj
 * variant the player is wielding.
 */
class PoweredStaves @Inject constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        // Warped sceptre: floor(((8 * magic) + 96) / 37) -> 16 at 62, 24 at 99.
        val warped = PoweredStaffBolt(manager) { magic -> (8 * magic + 96) / 37 }
        register(objs.warped_sceptre, warped)

        // Thammaron's sceptre: magic / 3 - 8 -> 25 at 99.
        val thammarons = PoweredStaffBolt(manager) { magic -> magic / 3 - 8 }
        register(objs.thammarons_sceptre, thammarons)
        register(objs.thammarons_sceptre_a, thammarons)

        // Accursed sceptre: magic / 3 - 6 -> 27 at 99.
        val accursed = PoweredStaffBolt(manager) { magic -> magic / 3 - 6 }
        register(objs.accursed_sceptre, accursed)
        register(objs.accursed_sceptre_a, accursed)

        // Eye of Ayak: magic / 3 - 6 -> 27 at 99 (attacks every 3 ticks instead of 4).
        val ayak = PoweredStaffBolt(manager) { magic -> magic / 3 - 6 }
        register(objs.eye_of_ayak, ayak)

        // Bone staff: magic / 3 + 5, rats only, +10 max hit against rats.
        register(objs.rat_bone_staff, BoneStaffBolt(manager))

        // Merfolk trident: trident-tier powered bolt, magic / 3 - 9 -> 16 at 75, 24 at 99.
        register(objs.merfolk_trident, PoweredStaffBolt(manager) { magic -> magic / 3 - 9 })

        // Crystal/corrupted gauntlet sceptres use a trident-tier formula while wielded.
        val gauntlet = PoweredStaffBolt(manager) { magic -> magic / 3 - 2 }
        register(objs.gauntlet_sceptre, gauntlet)
        register(objs.gauntlet_sceptre_hm, gauntlet)

        // Starter staff (deadman tutorial weapon): magic / 3 - 17.
        register(objs.deadman_starter_staff, PoweredStaffBolt(manager) { magic -> magic / 3 - 17 })

        val uncharged = UnchargedStaff(manager)
        register(objs.warped_sceptre_uncharged, uncharged)
        register(objs.thammarons_sceptre_u, uncharged)
        register(objs.thammarons_sceptre_au, uncharged)
        register(objs.accursed_sceptre_u, uncharged)
        register(objs.accursed_sceptre_au, uncharged)
        register(objs.eye_of_ayak_uncharged, uncharged)
    }

    private open class PoweredStaffBolt(
        protected val manager: WeaponAttackManager,
        private val baseMaxHit: (Int) -> Int,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack, bonusMaxHit = 0)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack, bonusMaxHit = 0)
            return true
        }

        protected fun ProtectedAccess.cast(
            target: PathingEntity,
            attack: CombatAttack.Staff,
            bonusMaxHit: Int,
        ) {
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
                val damage =
                    manager.rollStaffMaxHit(
                        this,
                        target,
                        max(0, baseMaxHit(player.stat(stats.magic)) + bonusMaxHit),
                    )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay = 0)
            }
            manager.continueCombat(this, target)
        }
    }

    /**
     * Bone staff: powered staff that can only be used against rat-type npcs. Adds a flat +10 to the
     * base max hit when fighting rats.
     */
    private class BoneStaffBolt(manager: WeaponAttackManager) :
        PoweredStaffBolt(manager, { magic -> magic / 3 + 5 }) {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!target.type.name.contains("rat", ignoreCase = true)) {
                mes("You can only use this weapon against rats.")
                manager.stopCombat(this)
                return true
            }
            cast(target, attack, bonusMaxHit = RAT_BONUS_MAX_HIT)
            return true
        }

        private companion object {
            private const val RAT_BONUS_MAX_HIT = 10
        }
    }

    private class UnchargedStaff(private val manager: WeaponAttackManager) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminate()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminate()
            return true
        }

        private fun ProtectedAccess.terminate() {
            mes("Your staff has no charges! You need to charge it to use it.")
            manager.stopCombat(this)
        }
    }
}
