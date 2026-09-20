package org.rsmod.api.combat

import jakarta.inject.Inject
import org.rsmod.api.area.checker.isWilderness
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.player.GenericSpecials
import org.rsmod.api.combat.player.activateMagicSpecial
import org.rsmod.api.combat.player.activateMeleeSpecial
import org.rsmod.api.combat.player.activateRangedSpecial
import org.rsmod.api.combat.player.activateShieldSpecial
import org.rsmod.api.combat.player.setPkVars
import org.rsmod.api.combat.player.specialAttackType
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStyle
import org.rsmod.api.equipment.instance.EquipmentAbilityEffects
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.player.bonus.ArmourSetEffects
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.AbilityProcFx
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.support.AbilityProcProviders
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.attack
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

internal class PvPCombat
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
    private val genericSpecs: GenericSpecials,
    private val abilityEffects: EquipmentAbilityEffects,
    private val perks: PerkService,
    private val armourSets: ArmourSetEffects,
) {
    suspend fun attack(access: ProtectedAccess, target: Player, attack: CombatAttack.PlayerAttack) {
        when (attack) {
            is CombatAttack.Melee -> access.attackMelee(target, attack)
            is CombatAttack.Ranged -> access.attackRanged(target, attack)
            is CombatAttack.Spell -> access.attackMagicSpell(target, attack)
            is CombatAttack.Staff -> access.attackMagicStaff(target, attack)
        }
    }

    private suspend fun ProtectedAccess.attackMelee(target: Player, attack: CombatAttack.Melee) {
        if (!canAttack(target)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, target)
            return
        }

        // TODO(combat): Add support for and check a target death flag varbit. `stopAction` if true.
        //  Applies to other combat types.

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = speeds.nextAttackDelay(player)
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opplayer2`
        // (or a helper function that does so) to re-engage in combat after performing the special
        // attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateMeleeSpecial(target, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(target, player.lefthand, specialsReg)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        // Important: Weapon attack handlers are responsible for explicitly calling `opplayer2` (or
        // a helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getMelee(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, target, attack)
            if (attackHandled) {
                setPkVars(target)
                return
            }
        }

        setPkVars(target)

        // Worn instance abilities, perks and armour sets apply in PvP exactly as in PvN.
        val (proc, procced, strikes) = rollWornProc()
        val damage = boostDamage(manager.rollMeleeDamage(player, target, attack), proc)
        if (damage > 0) {
            AbilityProcFx.announce(player, procced)
        }
        manager.giveCombatXp(player, target, attack, damage)
        manager.playWeaponFx(player, attack)
        manager.queueMeleeHit(player, target, damage)
        strikes.forEachIndexed { index, strike ->
            manager.queueMeleeHit(player, target, procDamage(damage, strike), delay = index + 1)
        }
        healFromProc(proc, damage)
        if (proc.hasOnHitEffects && damage > 0) {
            AbilityProcProviders.onHit(player, target, proc, damage)
        }
        manager.continueCombat(player, target)
    }

    private suspend fun ProtectedAccess.attackRanged(target: Player, attack: CombatAttack.Ranged) {
        if (!canAttack(target)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, target)
            return
        }

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = speeds.nextAttackDelay(player)
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opplayer2`
        // (or a helper function that does so) to re-engage in combat after performing the special
        // attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateRangedSpecial(target, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(target, player.lefthand, specialsReg)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        val righthandType = objTypes[attack.weapon]

        // Important: Weapon attack handlers are responsible for explicitly calling `opplayer2` (or
        // a helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getRanged(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, target, attack)
            if (attackHandled) {
                setPkVars(target)
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

        val quiver = player.quiver
        val quiverType = objTypes.getOrNull(quiver)

        val canUseAmmo = ammunition.attemptAmmoUsage(player, righthandType, quiverType)
        if (!canUseAmmo) {
            manager.stopCombat(player)
            return
        }

        // Note: Some weapons are categorized as `throwing_weapon` but do not behave like standard
        // throwing weapons. For example, the Toxic blowpipe falls under this category but requires
        // special handling. Such weapons should be managed via the `Weapon` system to ensure
        // correct behavior and avoid unintended side effects.
        val usingThrown = righthandType.isCategoryType(categories.throwing_weapon)

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

        setPkVars(target)

        // Official behavior: If the weapon (quiver or righthand, based on the thrown weapon flag)
        // has no `proj_launch` param, a "null" (-1) spotanim will still be sent in the same slot
        // and height as usual.
        val launchSpotanim = weaponType.paramOrNull(params.proj_launch)
        spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)

        val projanim = manager.spawnProjectile(player, target, travelSpotanim, projanimType)
        val (serverDelay, clientDelay) = projanim.durations

        if (usingThrown) {
            ammunition.useThrownWeapon(
                player,
                righthandType,
                target.coords,
                dropDelay = serverDelay,
            )
        } else if (quiverType != null) {
            ammunition.useQuiverAmmo(player, quiverType, target.coords, dropDelay = serverDelay)
        }

        val (proc, procced, strikes) = rollWornProc()
        val damage = boostDamage(manager.rollRangedDamage(player, target, attack), proc)
        if (damage > 0) {
            AbilityProcFx.announce(player, procced)
        }
        manager.giveCombatXp(player, target, attack, damage)

        val hitAmmoObj = if (usingThrown) null else quiverType
        manager.queueRangedHit(player, target, hitAmmoObj, damage, clientDelay, serverDelay)
        strikes.forEachIndexed { index, strike ->
            manager.queueRangedHit(
                player,
                target,
                hitAmmoObj,
                procDamage(damage, strike),
                clientDelay = clientDelay + index + 1,
                hitDelay = serverDelay + index + 1,
            )
        }
        healFromProc(proc, damage)
        if (proc.hasOnHitEffects && damage > 0) {
            AbilityProcProviders.onHit(player, target, proc, damage)
        }

        if (usingThrown && player.righthand == null) {
            mes("That was your last one!")
            return
        }

        manager.continueCombat(player, target)
    }

    private suspend fun ProtectedAccess.attackMagicSpell(
        target: Player,
        attack: CombatAttack.Spell,
    ) {
        if (!canAttack(target)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, target, attack.spell)
            return
        }

        val attackRate = MAGIC_SPELL_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        val spell = spellsReg[attack.spell.obj]
        if (spell != null) {
            setPkVars(target)
            spell.attack(this, target, attack)
            return
        }

        // All magic spell attacks must be registered in `SpellAttackRegistry`.
        manager.stopCombat(player)
        mes("You attempt to cast the spell, but nothing happens.")
    }

    private suspend fun ProtectedAccess.attackMagicStaff(
        target: Player,
        attack: CombatAttack.Staff,
    ) {
        if (!canAttack(target)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, target)
            return
        }

        // Set the next attack clock before executing any special attack, ensuring all attacks
        // default to the weapon's standard attack delay.
        val attackRate = MAGIC_STAFF_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        // Important: Special attack handlers are responsible for explicitly calling `opplayer2`
        // (or a helper function that does so) to re-engage in combat after performing the special
        // attack.
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec =
                activateMagicSpecial(target, attack, specialsReg, specialEnergy, genericSpecs)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(target, player.lefthand, specialsReg)
            if (activatedSpec) {
                setPkVars(target)
                return
            }
        }

        // Important: Weapon attack handlers are responsible for explicitly calling `opplayer2` (or
        // a helper function that does so) to re-engage in combat after performing their attack.
        val specializedWeapon = weaponsReg.getMagic(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, target, attack)
            if (attackHandled) {
                setPkVars(target)
                return
            }
        }

        // Since most (if not all) powered staves require specialized attack handling logic, they
        // are expected to be registered as separate weapons in the `WeaponRegistry`. This ensures
        // their unique behavior is handled explicitly rather than relying on fallback logic.
        manager.stopCombat(player)
        mes("Your staff fails to respond.")
    }

    private fun ProtectedAccess.canAttack(target: Player): Boolean =
        target.isValidTarget() && !player.coords.isWilderness() && !target.coords.isWilderness()

    /**
     * Rolls every worn-instance ability plus armour-set proc by its own chance, then caps the
     * combined result. Perk and set outgoing-damage bps are folded in as unconditional bonuses,
     * mirroring `StandardNpcHitProcessor`.
     */
    private fun ProtectedAccess.rollWornProc():
        Triple<AbilityProc, List<String>, List<AbilityProc>> {
        var rolled = AbilityProc()
        val procced = ArrayList<String>()
        val strikes = ArrayList<AbilityProc>(MAX_PROC_STRIKES)
        for (resolved in abilityEffects.procs(player.worn) + armourSets.procs(player)) {
            if (random.of(10_000) < resolved.chanceBps) {
                rolled += resolved.proc
                procced += resolved.abilityId
                if (resolved.proc.hasStrike && strikes.size < MAX_PROC_STRIKES) {
                    strikes += resolved.proc
                }
            }
        }
        return Triple(EquipmentAbilityProcs.cap(rolled), procced, strikes)
    }

    private fun procDamage(base: Int, proc: AbilityProc): Int =
        (base.toLong() * proc.strikeMultiplierBps / 10_000 + proc.strikeBaseMaxHit)
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    private fun ProtectedAccess.boostDamage(damage: Int, proc: AbilityProc): Int {
        if (damage <= 0) {
            return damage
        }
        val totalBps =
            proc.outgoingDamageBps +
                perks.outgoingDamageBps(player) +
                armourSets.outgoingDamageBps(player)
        val flat = perks.flatBonusDamage(player)
        val boosted = damage.toLong() + damage.toLong() * totalBps / 10_000 + flat
        return boosted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private val AbilityProc.hasStrike: Boolean
        get() = strikeStyle != AbilityStyle.None && strikeMultiplierBps > 0

    private companion object {
        const val MAX_PROC_STRIKES = 3
    }

    private fun ProtectedAccess.healFromProc(proc: AbilityProc, damage: Int) {
        if (proc.onHitHealBps > 0 && damage > 0) {
            val heal = (damage.toLong() * proc.onHitHealBps / 10_000).toInt()
            if (heal > 0) {
                player.statBoost(stats.hitpoints, constant = heal, percent = 0)
            }
        }
    }
}
