package org.rsmod.content.other.league.weapons

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.combat.effects.CombatEffects
import org.rsmod.api.combat.effects.StatusService
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.other.league.configs.league_combat_spots
import org.rsmod.content.other.league.configs.league_objs
import org.rsmod.content.other.league.configs.league_varps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid

/**
 * Standard-attack (passive) behaviour for the League/Echo weapons that is not covered by their
 * special attacks:
 * - `weapon_of_sol` builds a sunlight stack on every damaging hit (max 7, spent by Sol Slam).
 * - `thunder_khopesh` (+ deadman variant) has a 20% chance to call a delayed lightning bolt onto a
 *   ~10x10 area on successful hits, dealing up to 50% of the player's max hit.
 * - `tangled_lizard_charged` has a chance to bind the target on successful hits.
 * - `drygore_blowpipe` rolls accuracy twice per shot (keeps the best roll) and has a 25% chance to
 *   inflict burn.
 * - `venator_bow` (+ echo ornament) ricochets to up to two nearby npcs at reduced damage.
 * - `league_executioner_weapon` (Sage's axe) instantly kills npcs below 20% hitpoints.
 *
 * Uncharged variants refuse to attack.
 */
class LeagueWeapons
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val npcList: NpcList,
    private val ammunition: RangedAmmoManager,
    private val statuses: StatusService,
    private val effects: CombatEffects,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register(league_objs.weapon_of_sol, SunlightSpearWeapon(manager))

        val khopesh = ThunderKhopeshWeapon(manager, npcList)
        register(league_objs.thunder_khopesh, khopesh)
        register(league_objs.deadman_thunder_khopesh, khopesh)

        register(league_objs.tangled_lizard_charged, TangledLizardWeapon(manager, effects))
        register(
            league_objs.tangled_lizard_uncharged,
            UnchargedMeleeWeapon(manager, "Your tangled lizard has no charges!"),
        )

        val drygore = DrygoreBlowpipeWeapon(manager, objTypes, ammunition, statuses)
        register(league_objs.drygore_blowpipe, drygore)
        register(league_objs.drygore_blowpipe_loaded, drygore)

        val venator = VenatorBowWeapon(manager, objTypes, npcList, ammunition)
        register(objs.venator_bow, venator)
        register(league_objs.venator_bow_ornament, venator)

        val unchargedVenator = UnchargedRangedWeapon(manager, "Your venator bow has no charges!")
        register(objs.venator_bow_uncharged, unchargedVenator)
        register(league_objs.venator_bow_ornament_uncharged, unchargedVenator)

        register(league_objs.league_executioner_weapon, ExecutionerAxeWeapon(manager, objTypes))
    }

    /**
     * Replicates the standard melee attack flow so passives can be layered on top without
     * duplicating it in every weapon class.
     */
    private abstract class DefaultMeleeWeapon(protected val manager: WeaponAttackManager) :
        MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            standardAttack(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            standardAttack(target, attack)
            return true
        }

        protected fun ProtectedAccess.standardAttack(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            manager.playWeaponFx(this, attack)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                onHit(target, attack, damage)
            }
            manager.continueCombat(this, target)
        }

        protected open fun ProtectedAccess.onHit(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            damage: Int,
        ) {}
    }

    /** Weapon of Sol: each damaging hit builds a sunlight stack (max 7). */
    private class SunlightSpearWeapon(manager: WeaponAttackManager) : DefaultMeleeWeapon(manager) {
        private var Player.sunlightStacks by intVarp(league_varps.weapon_of_sol_stacks)

        override fun ProtectedAccess.onHit(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            damage: Int,
        ) {
            player.sunlightStacks = min(MAX_STACKS, player.sunlightStacks + 1)
        }

        private companion object {
            private const val MAX_STACKS = 7
        }
    }

    /** Thunder khopesh: 20% chance to drop a delayed ~10x10 lightning bolt on successful hits. */
    private class ThunderKhopeshWeapon(manager: WeaponAttackManager, private val npcList: NpcList) :
        DefaultMeleeWeapon(manager) {
        override fun ProtectedAccess.onHit(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            damage: Int,
        ) {
            if (random.of(0 until LIGHTNING_ROLL_SIDES) != 0) {
                return
            }
            val center = target.coords
            val maxHit =
                manager.rollMeleeMaxHit(this, target, attack.type, attack.style, LIGHTNING_MAX_MULT)
            target.spotanim(league_combat_spots.lightning)
            forEachNpcWithin(npcList, center, radius = LIGHTNING_RADIUS) { other ->
                manager.queueMeleeHit(this, other, random.of(0..maxHit), delay = LIGHTNING_DELAY)
            }
        }

        private companion object {
            private const val LIGHTNING_ROLL_SIDES = 5
            private const val LIGHTNING_MAX_MULT = 0.5
            private const val LIGHTNING_DELAY = 2
            private const val LIGHTNING_RADIUS = 5
        }
    }

    /** Tangled lizard: 20% chance to bind the target on successful hits. */
    private class TangledLizardWeapon(
        manager: WeaponAttackManager,
        private val effects: CombatEffects,
    ) : DefaultMeleeWeapon(manager) {
        override fun ProtectedAccess.onHit(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            damage: Int,
        ) {
            if (random.of(0 until BIND_ROLL_SIDES) == 0) {
                effects.bind(target, BIND_CYCLES)
            }
        }

        private companion object {
            private const val BIND_ROLL_SIDES = 5
            private const val BIND_CYCLES = 8
        }
    }

    /**
     * Drygore blowpipe: two independent accuracy rolls per shot (either can hit) and a 25% chance
     * to burn the target on a successful hit.
     */
    private class DrygoreBlowpipeWeapon(
        private val manager: WeaponAttackManager,
        private val objTypes: ObjTypeList,
        private val ammunition: RangedAmmoManager,
        private val statuses: StatusService,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged) {
            val weaponType = objTypes[attack.weapon]
            val quiver = player.quiver
            val quiverType = objTypes.getOrNull(quiver)

            if (!ammunition.attemptAmmoUsage(player, weaponType, quiverType)) {
                manager.stopCombat(this)
                return
            }

            val ammoType = quiverType ?: weaponType
            val projanimType = weaponType.paramOrNull(params.proj_type)
            val travelSpotanim = ammoType.paramOrNull(params.proj_travel)
            if (projanimType == null || travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return
            }

            manager.playWeaponFx(this, attack)
            val launchSpotanim = ammoType.paramOrNull(params.proj_launch)
            spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)

            val projanim = manager.spawnProjectile(this, target, travelSpotanim, projanimType)
            val (serverDelay, clientDelay) = projanim.durations

            if (quiverType != null) {
                ammunition.useQuiverAmmo(player, quiverType, target.coords, dropDelay = serverDelay)
            }

            // Passive: two independent accuracy rolls - the shot hits if either succeeds.
            val hits =
                manager.rollRangedAccuracy(
                    this,
                    target,
                    attack.type,
                    attack.style,
                    attack.type,
                    1.0,
                ) ||
                    manager.rollRangedAccuracy(
                        this,
                        target,
                        attack.type,
                        attack.style,
                        attack.type,
                        1.0,
                    )

            val damage =
                if (hits) {
                    manager.rollRangedMaxHit(
                        this,
                        target,
                        attack.type,
                        attack.style,
                        multiplier = 1.0,
                        boltSpecDamage = 0,
                    )
                } else {
                    0
                }

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, quiverType, damage, clientDelay)
            if (damage > 0 && random.of(0 until BURN_ROLL_SIDES) == 0) {
                statuses.apply(target, StatusEffect.BURN, duration = 40, potency = 1)
            }
            manager.continueCombat(this, target)
        }

        private companion object {
            private const val BURN_ROLL_SIDES = 4
        }
    }

    /**
     * Venator bow (+ echo ornament): arrows ricochet to up to two additional npcs near the primary
     * target, dealing reduced damage on each bounce.
     */
    private class VenatorBowWeapon(
        private val manager: WeaponAttackManager,
        private val objTypes: ObjTypeList,
        private val npcList: NpcList,
        private val ammunition: RangedAmmoManager,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged) {
            val weaponType = objTypes[attack.weapon]
            val quiver = player.quiver
            val quiverType = objTypes.getOrNull(quiver)

            if (!ammunition.attemptAmmoUsage(player, weaponType, quiverType)) {
                manager.stopCombat(this)
                return
            }

            val ammoType = quiverType ?: weaponType
            val projanimType = weaponType.paramOrNull(params.proj_type)
            val travelSpotanim = ammoType.paramOrNull(params.proj_travel)
            if (projanimType == null || travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return
            }

            manager.playWeaponFx(this, attack)
            val launchSpotanim = ammoType.paramOrNull(params.proj_launch)
            spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)

            val projanim = manager.spawnProjectile(this, target, travelSpotanim, projanimType)
            val (serverDelay, clientDelay) = projanim.durations

            if (quiverType != null) {
                ammunition.useQuiverAmmo(player, quiverType, target.coords, dropDelay = serverDelay)
            }

            val damage = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, quiverType, damage, clientDelay)

            ricochet(target, attack, quiverType, clientDelay)
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.ricochet(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            ammo: ObjType?,
            clientDelay: Int,
        ) {
            val multipliers = RICOCHET_MULTIPLIERS.iterator()
            forEachNpcWithin(npcList, target.coords, radius = RICOCHET_RADIUS) { other ->
                if (other === target || !multipliers.hasNext()) {
                    return@forEachNpcWithin
                }
                val multiplier = multipliers.next()
                val damage =
                    manager.rollRangedDamage(this, other, attack, maxHitMultiplier = multiplier)
                manager.giveCombatXp(this, other, attack, damage)
                manager.queueRangedDamage(this, other, ammo, damage, clientDelay + 1)
            }
        }

        private companion object {
            private const val RICOCHET_RADIUS = 3
            private val RICOCHET_MULTIPLIERS = listOf(2.0 / 3.0, 1.0 / 3.0)
        }
    }

    /**
     * Sage's axe (league executioner weapon): thrown ranged weapon that instantly kills npcs with
     * less than 20% of their hitpoints remaining. The axe is not consumed when thrown.
     */
    private class ExecutionerAxeWeapon(
        private val manager: WeaponAttackManager,
        private val objTypes: ObjTypeList,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged) {
            val weaponType = objTypes[attack.weapon]
            val projanimType = weaponType.paramOrNull(params.proj_type)
            val travelSpotanim = weaponType.paramOrNull(params.proj_travel)

            manager.playWeaponFx(this, attack)

            val clientDelay =
                if (projanimType != null && travelSpotanim != null) {
                    manager
                        .spawnProjectile(this, target, travelSpotanim, projanimType)
                        .durations
                        .clientDelay
                } else {
                    0
                }

            val damage =
                if (target is Npc && target.hitpoints * 100 < target.baseHitpointsLvl * 20) {
                    target.hitpoints
                } else {
                    manager.rollRangedDamage(this, target, attack)
                }

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay)
            manager.continueCombat(this, target)
        }
    }

    private class UnchargedMeleeWeapon(
        private val manager: WeaponAttackManager,
        private val message: String,
    ) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            terminate()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            terminate()
            return true
        }

        private fun ProtectedAccess.terminate() {
            mes(message)
            manager.stopCombat(this)
        }
    }

    private class UnchargedRangedWeapon(
        private val manager: WeaponAttackManager,
        private val message: String,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            terminate()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            terminate()
            return true
        }

        private fun ProtectedAccess.terminate() {
            mes(message)
            manager.stopCombat(this)
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
