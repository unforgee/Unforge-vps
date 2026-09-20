package org.rsmod.api.combat

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.player.GenericSpecials
import org.rsmod.api.combat.player.activateMagicSpecial
import org.rsmod.api.combat.player.activateMeleeSpecial
import org.rsmod.api.combat.player.activateRangedSpecial
import org.rsmod.api.combat.player.activateShieldSpecial
import org.rsmod.api.combat.player.specialAttackType
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStyle
import org.rsmod.api.equipment.instance.EquipmentAbilityEffects
import org.rsmod.api.npc.hasAttackOp
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.bonus.ArmourSetEffects
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.AbilityProcFx
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.attack
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.obj.ObjTypeList

internal class PvNCombat
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val speeds: WeaponSpeeds,
    private val specialsReg: SpecialAttackRegistry,
    private val specialEnergy: SpecialAttackEnergy,
    private val weaponsReg: WeaponRegistry,
    private val manager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
    private val spellsReg: SpellAttackRegistry,
    private val abilityEffects: EquipmentAbilityEffects,
    private val genericSpecs: GenericSpecials,
    private val armourSets: ArmourSetEffects,
) {
    suspend fun attack(access: ProtectedAccess, target: Npc, attack: CombatAttack.PlayerAttack) {
        when (attack) {
            is CombatAttack.Melee -> access.attackMelee(target, attack)
            is CombatAttack.Ranged -> access.attackRanged(target, attack)
            is CombatAttack.Spell -> access.attackMagicSpell(target, attack)
            is CombatAttack.Staff -> access.attackMagicStaff(target, attack)
        }
    }

    private suspend fun ProtectedAccess.attackMelee(npc: Npc, attack: CombatAttack.Melee) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = speeds.nextAttackDelay(player)
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing the special attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateMeleeSpecial(npc, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        // Important: Weapon attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getMelee(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        val damage = manager.rollMeleeDamage(player, npc, attack)
        manager.giveCombatXp(player, npc, attack, damage)
        manager.playWeaponFx(player, attack)
        manager.queueMeleeHit(player, npc, damage)
        rollAbilityStrikes(npc)
        manager.continueCombat(player, npc)
    }

    private suspend fun ProtectedAccess.attackRanged(npc: Npc, attack: CombatAttack.Ranged) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = speeds.nextAttackDelay(player)
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing the special attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateRangedSpecial(npc, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        val righthandType = objTypes[attack.weapon]

        // Important: Weapon attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getRanged(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        // `chargebows` are specialized and not worth trying to have as a generic system. As such,
        // they are required to be registered in `WeaponRegistry` and will return early if they
        // have reached this point (not handled by the previous `specializedWeapon` block).
        val usingChargeBow = righthandType.isCategoryType(categories.chargebow)
        if (usingChargeBow) {
            manager.stopCombat(player)
            mes("The bow refuses to fire.")
            return
        }

        val isCompanion = player.username.startsWith("companion_")
        val quiver = player.quiver
        val rawQuiverType = objTypes.getOrNull(quiver)
        val usingThrown = righthandType.isCategoryType(categories.throwing_weapon)

        val quiverType =
            if (isCompanion && rawQuiverType == null && !usingThrown) {
                if (righthandType.isCategoryType(categories.crossbow)) {
                    objTypes[objs.runite_bolts]
                } else {
                    objTypes[objs.bronze_arrow]
                }
            } else {
                rawQuiverType
            }

        val canUseAmmo =
            if (isCompanion && rawQuiverType == null && quiverType != null) {
                true
            } else {
                ammunition.attemptAmmoUsage(player, righthandType, quiverType)
            }
        if (!canUseAmmo) {
            manager.stopCombat(player)
            return
        }

        val weaponType = if (usingThrown) righthandType else quiverType
        checkNotNull(weaponType) {
            "Unexpected null weapon type: righthand=$righthandType, quiver=$quiverType"
        }

        val projanimType = righthandType.paramOrNull(params.proj_type)
        val travelSpotanim = weaponType.paramOrNull(params.proj_travel)

        // All valid ammunition requires a `proj_travel` spotanim type and `proj_type` projanim type
        // param so that the projectile can be created and referenced for its proper delays.
        if (projanimType == null || travelSpotanim == null) {
            manager.stopCombat(player)
            mes("You are unable to fire your ammunition.")
            return
        }

        // All valid ranged weapons require an `attack_anim_stance1` seq type param to be used in
        // combat.
        val playedAnim = manager.playWeaponFx(player, attack)
        if (!playedAnim) {
            manager.stopCombat(player)
            mes("The bow fails to fire.")
            return
        }

        // Official behavior: If the weapon (quiver or righthand, based on the thrown weapon flag)
        // has no `proj_launch` param, a "null" (-1) spotanim will still be sent in the same slot
        // and height as usual.
        val launchSpotanim = weaponType.paramOrNull(params.proj_launch)
        spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)

        val projanim = manager.spawnProjectile(player, npc, travelSpotanim, projanimType)
        val (serverDelay, clientDelay) = projanim.durations

        if (usingThrown) {
            ammunition.useThrownWeapon(player, righthandType, npc.coords, dropDelay = serverDelay)
        } else if (quiverType != null) {
            ammunition.useQuiverAmmo(player, quiverType, npc.coords, dropDelay = serverDelay)
        }

        val damage = manager.rollRangedDamage(player, npc, attack)
        manager.giveCombatXp(player, npc, attack, damage)

        val hitAmmoObj = if (usingThrown) null else quiverType
        manager.queueRangedHit(player, npc, hitAmmoObj, damage, clientDelay, serverDelay)
        rollAbilityStrikes(npc)

        if (usingThrown && player.righthand == null) {
            mes("That was your last one!")
            return
        }

        manager.continueCombat(player, npc)
    }

    private suspend fun ProtectedAccess.attackMagicSpell(npc: Npc, attack: CombatAttack.Spell) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc, attack.spell)
            return
        }

        val attackRate = MAGIC_SPELL_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        val spell = spellsReg[attack.spell.obj]
        if (spell != null) {
            spell.attack(this, npc, attack)
            return
        }

        // All magic spell attacks must be registered in `SpellAttackRegistry`.
        manager.stopCombat(player)
        mes("You attempt to cast the spell, but nothing happens.")
    }

    private suspend fun ProtectedAccess.attackMagicStaff(npc: Npc, attack: CombatAttack.Staff) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = MAGIC_STAFF_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing the special attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateMagicSpecial(npc, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        // Important: Weapon attack handlers are responsible for explicitly calling `opnpc2` (or a
        // helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getMagic(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        // Since most (if not all) powered staves require specialized attack handling logic, they
        // are expected to be registered as separate weapons in the `WeaponRegistry`. This ensures
        // their unique behavior is handled explicitly rather than relying on fallback logic.
        manager.stopCombat(player)
        mes("Your staff fails to respond.")
    }

    /**
     * Rolls every worn equipment-instance ability carrying a strike. A successful proc queues a
     * bonus hit resolved through that ability's own damage pipeline - e.g. a melee weapon whose
     * instance carries a `Ranged` strike rolls the player's ranged accuracy/strength against the
     * npc's ranged defence, and `Hybrid` strikes (e.g. fire breath) use whichever of the three
     * style pipelines currently yields the highest max hit.
     */
    private fun ProtectedAccess.rollAbilityStrikes(npc: Npc) {
        var strikes = 0
        for (resolved in abilityEffects.procs(player.worn) + armourSets.procs(player)) {
            val proc = resolved.proc
            if (proc.strikeStyle == AbilityStyle.None || proc.strikeMultiplierBps <= 0) {
                continue
            }
            if (random.of(10_000) >= resolved.chanceBps) {
                continue
            }
            if (strikes++ >= MAX_PROC_STRIKES) {
                break
            }
            AbilityProcFx.announce(player, listOf(resolved.abilityId))
            strike(npc, proc)
        }
    }

    private companion object {
        const val MAX_PROC_STRIKES = 3
    }

    private fun ProtectedAccess.strike(npc: Npc, proc: AbilityProc) {
        when (proc.strikeStyle) {
            AbilityStyle.Melee -> strikeMelee(npc, proc)
            AbilityStyle.Ranged -> strikeRanged(npc, proc)
            AbilityStyle.Magic -> strikeMagic(npc, proc)
            AbilityStyle.Hybrid -> strikeHybrid(npc, proc)
            AbilityStyle.None -> Unit
        }
    }

    private fun strikeMaxHit(styleCalc: Int, proc: AbilityProc): Int =
        styleCalc * proc.strikeMultiplierBps / 10_000 + proc.strikeBaseMaxHit

    private fun ProtectedAccess.strikeMelee(npc: Npc, proc: AbilityProc) {
        if (!manager.rollMeleeAccuracy(player, npc, null, null, null, multiplier = 1.0)) {
            return
        }
        val maxHit = strikeMaxHit(manager.calculateMeleeMaxHit(player, npc, null, null, 1.0), proc)
        manager.queueMeleeHit(player, npc, random.of(0..maxHit), delay = 1)
    }

    private fun ProtectedAccess.strikeRanged(npc: Npc, proc: AbilityProc) {
        if (!manager.rollRangedAccuracy(player, npc, null, null, null, multiplier = 1.0)) {
            return
        }
        val maxHit =
            strikeMaxHit(
                manager.calculateRangedMaxHit(player, npc, null, null, 1.0, boltSpecDamage = 0),
                proc,
            )
        manager.queueRangedHit(
            player,
            npc,
            ammo = null,
            damage = random.of(0..maxHit),
            clientDelay = 30,
            hitDelay = 2,
        )
    }

    private fun ProtectedAccess.strikeMagic(npc: Npc, proc: AbilityProc) {
        if (!manager.rollStaffAccuracy(player, npc, attackStyle = null, multiplier = 1.0)) {
            return
        }
        val maxHit =
            manager.calculateStaffMaxHit(
                player,
                npc,
                baseMaxHit = proc.strikeBaseMaxHit,
                multiplier = proc.strikeMultiplierBps / 10_000.0,
            )
        manager.queueMagicHit(
            player,
            npc,
            spell = null,
            damage = random.of(0..maxHit),
            clientDelay = 30,
            hitDelay = 2,
        )
    }

    private fun ProtectedAccess.strikeHybrid(npc: Npc, proc: AbilityProc) {
        val meleeMax =
            strikeMaxHit(manager.calculateMeleeMaxHit(player, npc, null, null, 1.0), proc)
        val rangedMax =
            strikeMaxHit(
                manager.calculateRangedMaxHit(player, npc, null, null, 1.0, boltSpecDamage = 0),
                proc,
            )
        val magicMax =
            manager.calculateStaffMaxHit(
                player,
                npc,
                baseMaxHit = proc.strikeBaseMaxHit,
                multiplier = proc.strikeMultiplierBps / 10_000.0,
            )
        when {
            rangedMax >= meleeMax && rangedMax >= magicMax -> strikeRanged(npc, proc)
            magicMax >= meleeMax -> strikeMagic(npc, proc)
            else -> strikeMelee(npc, proc)
        }
    }

    private fun ProtectedAccess.canAttack(npc: Npc): Boolean {
        if (!npc.isValidTarget()) {
            return false
        }

        if (!npc.hasAttackOp()) {
            mes("You can't attack this npc.")
            return false
        }

        return true
    }
}
