package org.rsmod.content.other.special.attacks

import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.combat.effects.StatusService
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.timers
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.api.specials.instant.InstantSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.stat.StatType

/**
 * OSRS-accurate special attacks: stat drains, freezes, healing and weapon-specific effects.
 *
 * Registrations are guarded by [SpecialAttackManager.getSpecialEnergyRequirement] - weapons without
 * a `sa_energy_requirements` cache enum entry are skipped (e.g. bounty-ruins variants that are not
 * in the enum).
 */
class OsrsWeaponSpecialAttacks
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val ammunition: RangedAmmoManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
    private val npcList: NpcList,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        // Godswords
        melee(manager, objs.zamorak_godsword, ZamorakGodsword(manager, attackManager, statuses))
        melee(manager, objs.bandos_godsword, BandosGodsword(manager, attackManager))
        val sacrifice = AncientGodsword(manager, attackManager)
        melee(manager, objs.ancient_godsword, sacrifice)
        melee(manager, objs.ancient_godsword_br, sacrifice)

        // Defence-draining crush weapons (percent of the target's *current* defence).
        melee(manager, objs.dragon_warhammer, DefenceDrain(manager, attackManager, 1.0, 1.50, 30))
        val elderMaul = DefenceDrain(manager, attackManager, 1.25, 1.0, 35)
        melee(manager, objs.elder_maul, elderMaul)
        melee(manager, objs.elder_maul_ornament, elderMaul)
        melee(manager, objs.br_elder_maul, elderMaul)
        melee(manager, objs.statius_warhammer, StatiusSmash(manager, attackManager, 70))
        melee(manager, objs.statius_warhammer_bh, StatiusSmash(manager, attackManager, 75))
        val anchor = AnchorSmash(manager, attackManager)
        melee(manager, objs.brain_anchor, anchor)
        melee(manager, objs.brain_anchor_imbue, anchor)

        // Daggers.
        val boneDagger = BoneDagger(manager, attackManager)
        melee(manager, objs.bone_dagger, boneDagger)
        melee(manager, objs.bone_dagger_poison, boneDagger)
        melee(manager, objs.bone_dagger_poison_plus, boneDagger)
        melee(manager, objs.bone_dagger_poison_plus_plus, boneDagger)
        val abyssalDagger = AbyssalPuncture(manager, attackManager)
        melee(manager, objs.abyssal_dagger, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p_plus, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p_plus_plus, abyssalDagger)
        melee(manager, objs.abyssal_dagger_imbue, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p_imbue, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p_plus_imbue, abyssalDagger)
        melee(manager, objs.abyssal_dagger_p_plus_plus_imbue, abyssalDagger)

        // Swords.
        melee(manager, objs.dragon_scimitar, Sever(manager, attackManager))
        val lightning = SaradominLightning(manager, attackManager)
        melee(manager, objs.saradomin_sword, lightning)
        melee(manager, objs.saradomin_blessed_sword, lightning)
        melee(manager, objs.saradomin_blessed_sword_degraded, lightning)
        val disrupt = VoidwakerDisrupt(manager, attackManager)
        melee(manager, objs.voidwaker, disrupt)
        melee(manager, objs.voidwaker_br, disrupt)
        melee(manager, objs.voidwaker_deadman, disrupt)
        melee(manager, objs.voidwaker_deadman_blighted, disrupt)
        val weaken = Weaken(manager, attackManager, demonMultiplier = 2)
        melee(manager, objs.darklight, weaken)
        melee(manager, objs.arclight, weaken)
        melee(manager, objs.emberlight, Weaken(manager, attackManager, demonMultiplier = 3))

        // Spears ("Shove" stuns the target briefly; the knockback tile is approximated by the
        // movement lock).
        val shove = Shove(manager, attackManager, statuses)
        melee(manager, objs.dragon_spear, shove)
        melee(manager, objs.dragon_spear_p, shove)
        melee(manager, objs.dragon_spear_kp, shove)
        melee(manager, objs.zamorak_spear, shove)
        melee(manager, objs.zamorak_hasta, shove)

        // Other heavy melee.
        val quickSmash = QuickSmash(manager, attackManager)
        melee(manager, objs.granite_maul, quickSmash)
        melee(manager, objs.granite_maul_upgrade, quickSmash)
        melee(manager, objs.dragon_2h_sword, Powerstab(manager, attackManager, npcList))
        melee(manager, objs.dragon_halberd, HalberdSweep(manager, attackManager, npcList))
        melee(manager, objs.burning_claws, BurningClaws(manager, attackManager, statuses))
        melee(manager, objs.dual_macuahuitl, BloodInfusion(manager, attackManager))
        melee(manager, objs.abyssal_whip, EnergyDrain(manager, attackManager))
        melee(manager, objs.abyssal_whip_lava, EnergyDrain(manager, attackManager))
        melee(manager, objs.abyssal_whip_ice, EnergyDrain(manager, attackManager))
        melee(manager, objs.abyssal_tentacle, BindingTentacle(manager, attackManager, statuses))
        melee(manager, objs.abyssal_bludgeon, Penance(manager, attackManager))
        melee(manager, objs.ursine_chainmace, UrsineBearDown(manager, attackManager, statuses))
        melee(manager, objs.dinhs_bulwark, ShieldBash(manager, attackManager, npcList))
        val spearWall = VestaSpearWall(manager, attackManager)
        melee(manager, objs.vestas_spear, spearWall)
        melee(manager, objs.vestas_spear_bh, spearWall)
        val feint = VestaFeint(manager, attackManager)
        melee(manager, objs.vestas_longsword, feint)
        melee(manager, objs.vestas_longsword_bh, feint)

        // Instant specials.
        instant(manager, objs.dragon_battleaxe, InstantSpecialAttack(::rampage))

        // Ranged.
        ranged(
            manager,
            objs.magic_shortbow_i,
            OsrsRapidShot(manager, attackManager, ammunition, objTypes, 2, 1.43, 1.0),
        )
        val powershot = Powershot(manager, attackManager, ammunition, objTypes)
        ranged(manager, objs.magic_longbow, powershot)
        ranged(manager, objs.magic_composite_bow, powershot)
        val crossbowSpec = OsrsHeavyShot(manager, attackManager, ammunition, objTypes, 2.0, 1.0)
        ranged(manager, objs.armadyl_crossbow, crossbowSpec)
        ranged(manager, objs.armadyl_crossbow_br, crossbowSpec)
        ranged(manager, objs.zaryte_crossbow, crossbowSpec)
        ranged(manager, objs.zaryte_crossbow_br, crossbowSpec)
        val annihilate = Annihilate(manager, attackManager, ammunition, objTypes, npcList)
        ranged(manager, objs.dragon_crossbow, annihilate)
        ranged(manager, objs.dragon_crossbow_br, annihilate)
        ranged(
            manager,
            objs.webweaver_bow,
            WebweaverSwarm(manager, attackManager, ammunition, objTypes),
        )
        ranged(
            manager,
            objs.bone_crossbow,
            BoneCrossbow(manager, attackManager, ammunition, objTypes),
        )
        ranged(manager, objs.seercull, Seercull(manager, attackManager, ammunition, objTypes))
        ranged(
            manager,
            objs.scorching_bow,
            ScorchingShackles(manager, attackManager, ammunition, objTypes, statuses),
        )
        val duality = OsrsRapidShot(manager, attackManager, ammunition, objTypes, 2, 1.0, 1.0)
        ranged(manager, objs.dragon_knife, duality)
        ranged(manager, objs.dragon_knife_p, duality)
        ranged(manager, objs.dragon_knife_p_plus, duality)
        ranged(manager, objs.dragon_knife_p_plus_plus, duality)
        ranged(manager, objs.dragon_knife_br, duality)
        ranged(manager, objs.dragon_dart, duality)
        ranged(manager, objs.dragon_dart_p, duality)
        ranged(manager, objs.dragon_dart_p_plus, duality)
        ranged(manager, objs.dragon_dart_p_plus_plus, duality)
        val momentum = MomentumThrow(manager, attackManager, ammunition, objTypes)
        ranged(manager, objs.dragon_thrownaxe, momentum)
        ranged(manager, objs.dragon_thrownaxe_br, momentum)
        val hamstring = Hamstring(manager, attackManager, ammunition, objTypes)
        ranged(manager, objs.morrigans_thrownaxe, hamstring)
        ranged(manager, objs.morrigans_thrownaxe_bh, hamstring)
        val phantom = PhantomStrike(manager, attackManager, ammunition, objTypes)
        ranged(manager, objs.morrigans_javelin, phantom)
        ranged(manager, objs.morrigans_javelin_bh, phantom)

        // Magic.
        val condemn = AccursedCondemn(manager)
        magic(manager, objs.accursed_sceptre, condemn)
        magic(manager, objs.accursed_sceptre_a, condemn)
        magic(manager, objs.nightmare_staff_eldritch, EldritchInvocate(manager))
        magic(manager, objs.nightmare_staff_volatile, VolatileImmolate(manager))
        magic(manager, objs.zuriels_staff_bh, StaffBolt(manager, 1.25))
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

    /**
     * Dragon battleaxe "Rampage": drains the wielder's Attack, Defence, Ranged and Magic by 10% of
     * their current levels and boosts Strength by 10 plus a quarter of the drained levels.
     */
    private fun rampage(access: ProtectedAccess): Boolean {
        var drained = 0
        for (stat in listOf(stats.attack, stats.defence, stats.ranged, stats.magic)) {
            val loss = access.player.stat(stat) / 10
            if (loss > 0) {
                access.player.statSub(stat, loss, 0)
                drained += loss
            }
        }
        access.player.statBoost(stats.strength, constant = 10 + drained / 4, percent = 0)
        access.mes("Raarrrggghh!")
        return true
    }
}

/** Drains [amount] flat levels from [target]'s [stat] (no-op for prayer vs npcs). */
internal fun drainStat(target: PathingEntity, stat: StatType, amount: Int) {
    if (amount <= 0) {
        return
    }
    when (target) {
        is Player -> target.statSub(stat, amount, 0)
        is Npc -> drainNpcStat(target, stat, amount)
    }
}

/** Drains [percent] of [target]'s *current* [stat] level. */
internal fun drainStatByCurrent(target: PathingEntity, stat: StatType, percent: Int) {
    drainStat(target, stat, currentLevel(target, stat) * percent / 100)
}

/**
 * Drains [amount] levels cascading through [order]: when a stat reaches 0 before all of [amount] is
 * accounted for, the remainder drains the next stat in the list (BGS/anchor pattern).
 */
internal fun drainCascade(target: PathingEntity, order: List<StatType>, amount: Int) {
    var remaining = amount
    for (stat in order) {
        if (remaining <= 0) {
            return
        }
        val level = currentLevel(target, stat)
        if (level <= 0) {
            continue
        }
        val drained = min(level, remaining)
        drainStat(target, stat, drained)
        remaining -= drained
    }
}

internal fun currentLevel(target: PathingEntity, stat: StatType): Int =
    when (target) {
        is Player -> target.stat(stat)
        is Npc -> npcLevel(target, stat)
    }

private fun npcLevel(npc: Npc, stat: StatType): Int =
    when {
        stat.isType(stats.attack) -> npc.attackLvl
        stat.isType(stats.strength) -> npc.strengthLvl
        stat.isType(stats.defence) -> npc.defenceLvl
        stat.isType(stats.ranged) -> npc.rangedLvl
        stat.isType(stats.magic) -> npc.magicLvl
        else -> 0
    }

/** True when [target] is an npc flagged with the `demon` npc-type param. */
internal fun isDemon(target: PathingEntity): Boolean =
    target is Npc && (target.visType.paramOrNull(params.demon) ?: 0) > 0

private fun drainNpcStat(npc: Npc, stat: StatType, amount: Int) {
    when {
        stat.isType(stats.attack) -> npc.attackLvl = max(0, npc.attackLvl - amount)
        stat.isType(stats.strength) -> npc.strengthLvl = max(0, npc.strengthLvl - amount)
        stat.isType(stats.defence) -> npc.defenceLvl = max(0, npc.defenceLvl - amount)
        stat.isType(stats.ranged) -> npc.rangedLvl = max(0, npc.rangedLvl - amount)
        stat.isType(stats.magic) -> npc.magicLvl = max(0, npc.magicLvl - amount)
    }
}

private fun PathingEntity.forEachNearbyNpc(npcList: NpcList, radius: Int, action: (Npc) -> Unit) {
    for (npc in npcList) {
        if (!npc.isSlotAssigned || npc.isInvisible || npc.hitpoints <= 0) {
            continue
        }
        if (npc === this || npc.coords.level != coords.level) {
            continue
        }
        if (npc.coords.chebyshevDistance(coords) <= radius) {
            action(npc)
        }
    }
}

/**
 * Abyssal dagger "Abyssal Puncture": two slashes sharing a single +25% accuracy roll - if the first
 * roll hits, so does the second. Each hit rolls damage up to 85% of the normal max hit.
 */
internal class AbyssalPuncture(
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
        val accurate =
            manager.rollMeleeAccuracy(this, target, attack.type, attack.style, attack.type, 1.25)
        var total = 0
        repeat(2) { i ->
            val maxHit = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 0.85)
            val damage = if (accurate) random.of(0..maxHit) else 0
            manager.queueMeleeHit(this, target, damage, delay = 1 + i)
            total += damage
        }
        manager.giveCombatXp(this, target, attack, total)
        manager.continueCombat(this, target)
    }
}

/**
 * Vesta's spear "Spear Wall": a normal hit followed 2 cycles later by a second hit dealing 50-75%
 * of the first. A successful attack also reduces the next attack delay by one cycle. (The 8-cycle
 * melee/ranged immunity is not implemented.)
 */
internal class VestaSpearWall(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        val second = if (damage > 0) random.of((damage / 2)..max(damage / 2, damage * 3 / 4)) else 0
        manager.queueMeleeHit(this, target, damage)
        manager.queueMeleeHit(this, target, second, delay = 3)
        if (damage > 0) {
            manager.setNextAttackDelay(this, 4)
        }
        manager.giveCombatXp(this, target, attack, damage + second)
        manager.continueCombat(this, target)
    }
}

/**
 * Vesta's longsword "Feint": rolls accuracy against 25% of the target's Defence and deals damage
 * between 20% and 120% of the normal max hit.
 */
internal class VestaFeint(
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
            withReducedDefence(target) {
                val accurate =
                    manager.rollMeleeAccuracy(
                        this,
                        target,
                        attack.type,
                        attack.style,
                        attack.type,
                        1.0,
                    )
                val maxHit = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
                if (accurate) random.of((maxHit / 5)..max(maxHit / 5, maxHit * 6 / 5)) else 0
            }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }

    private inline fun withReducedDefence(target: PathingEntity, block: () -> Int): Int =
        when (target) {
            is Player -> {
                val original = target.stat(stats.defence)
                target.statSub(stats.defence, original - original / 4, 0)
                try {
                    block()
                } finally {
                    target.statAdd(stats.defence, original - target.stat(stats.defence), 0)
                }
            }
            is Npc -> {
                val original = target.defenceLvl
                target.defenceLvl = max(1, original / 4)
                try {
                    block()
                } finally {
                    target.defenceLvl = original
                }
            }
        }
}

/**
 * Zamorak godsword "Ice Cleave": double accuracy, +10% damage; a successful hit freezes the target
 * for ~20 seconds.
 */
internal class ZamorakGodsword(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
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
        val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.10)
        if (damage > 0) {
            statuses.apply(target, StatusEffect.FROZEN, duration = 33)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Bandos godsword "Warstrike": double accuracy, +21% damage; drains the target's combat stats by
 * the damage dealt, cascading through Defence, Strength, Prayer, Attack, Magic and Ranged.
 */
internal class BandosGodsword(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.21)
        if (damage > 0) {
            val order =
                listOf(
                    stats.defence,
                    stats.strength,
                    stats.prayer,
                    stats.attack,
                    stats.magic,
                    stats.ranged,
                )
            drainCascade(target, order, damage)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Dragon warhammer / elder maul style: empowered hit that drains [defencePercent] of the target's
 * *current* Defence level on a successful strike.
 */
internal class DefenceDrain(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val accuracyMultiplier: Double,
    private val damageMultiplier: Double,
    private val defencePercent: Int,
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
            manager.rollMeleeDamage(this, target, attack, accuracyMultiplier, damageMultiplier)
        if (damage > 0) {
            drainStatByCurrent(target, stats.defence, defencePercent)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Statius' warhammer "Smash": damage rolled anywhere between 25% and 125% of the normal max hit; a
 * successful strike drains [defencePercent]% of the target's current Defence (70% base, 75% for the
 * bounty-hunter variant).
 */
internal class StatiusSmash(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val defencePercent: Int,
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
        val accurate =
            manager.rollMeleeAccuracy(this, target, attack.type, attack.style, attack.type, 1.0)
        val maxHit = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
        val damage =
            if (accurate) {
                random.of((maxHit * 25 / 100)..maxOf(maxHit * 25 / 100, maxHit * 125 / 100))
            } else {
                0
            }
        if (damage > 0) {
            drainStatByCurrent(target, stats.defence, defencePercent)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Barrelchest anchor "Sunder": double accuracy, +10% damage, and drains 10% of the damage dealt
 * from the first available stat of Defence, Attack, Ranged and Magic.
 */
internal class AnchorSmash(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.10)
        if (damage > 0) {
            drainCascade(
                target,
                listOf(stats.defence, stats.attack, stats.ranged, stats.magic),
                damage / 10,
            )
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Bone dagger "Backstab": in OSRS the hit is guaranteed when the wielder was not the last one to
 * attack the target. There is no last-attacker tracking here, so the strike is always treated as a
 * true strike; it drains Defence by the damage dealt.
 */
internal class BoneDagger(
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
        val maxHit = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
        val damage = random.of(0..maxHit)
        if (damage > 0) {
            drainStat(target, stats.defence, damage)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Dragon scimitar "Sever": +25% accuracy; versus players it also deactivates any active protection
 * prayer.
 */
internal class Sever(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
        if (damage > 0 && target is Player) {
            disableProtectionPrayers(target)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }

    private fun disableProtectionPrayers(target: Player) {
        var disabled = false
        for (varbit in
            listOf(
                varbits.protect_from_magic,
                varbits.protect_from_missiles,
                varbits.protect_from_melee,
            )) {
            if (target.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(target, varbit, 0)
                disabled = true
            }
        }
        if (!disabled) {
            return
        }
        if (constants.isOverhead(target.overheadIcon)) {
            target.overheadIcon = null
        }
        if (target.vars[varbits.enabled_prayers] == 0) {
            VarPlayerIntMapSetter.set(target, varbits.quickprayer_active, 0)
            target.clearSoftTimer(timers.prayer_drain)
        }
    }
}

/**
 * Saradomin sword / blessed variant "Saradomin's lightning": a melee hit with +10% damage plus a
 * magic lightning strike for 1-16 damage.
 */
internal class SaradominLightning(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.10)
        val lightning = if (damage > 0) random.of(1..16) else 0
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        if (lightning > 0) {
            manager.queueMagicHit(this, target, lightning, clientDelay = 0)
        }
        manager.continueCombat(this, target)
    }
}

/**
 * Voidwaker "Disrupt": a guaranteed hit of magic damage equal to 50-150% of the wielder's melee max
 * hit.
 */
internal class VoidwakerDisrupt(
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
        val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
        val damage = random.of((maxHit / 2)..max(maxHit / 2, maxHit * 3 / 2))
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}

/**
 * Ancient godsword "Blood Sacrifice": double accuracy, +10% damage; on a hit the target is marked
 * and, eight cycles later, takes 15% of its max hitpoints as further damage (capped at 25 vs npcs
 * and 15 vs players) while the wielder heals the same amount. The mark does not detonate if the
 * target has moved 5 or more tiles away from where it was marked.
 */
internal class AncientGodsword(
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

    private suspend fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.10)
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
        if (damage <= 0) {
            return
        }

        val marked = target.coords
        val cap = if (target is Player) 15 else 25
        val sacrifice = min(cap, maxHitpoints(target) * 15 / 100)

        delay(SACRIFICE_DELAY)
        if (!target.isAlive() || target.coords.chebyshevDistance(marked) >= 5) {
            return
        }
        manager.queueMeleeHit(this, target, sacrifice)
        player.statBoost(stats.hitpoints, constant = sacrifice, percent = 0)
    }

    private fun maxHitpoints(target: PathingEntity): Int =
        when (target) {
            is Player -> target.statBase(stats.hitpoints)
            is Npc -> target.baseHitpointsLvl
        }

    private fun PathingEntity.isAlive(): Boolean =
        when (this) {
            is Player -> stat(stats.hitpoints) > 0
            is Npc -> isSlotAssigned && hitpoints > 0
        }

    private companion object {
        private const val SACRIFICE_DELAY = 8
    }
}

/**
 * Darklight/arclight/emberlight "Weaken": drains the target's Attack, Strength and Defence by 5% of
 * their levels plus one on a successful hit. Demons take [demonMultiplier]x the drain (2x for
 * darklight and arclight, 3x for emberlight).
 */
internal class Weaken(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val demonMultiplier: Int,
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        if (damage > 0) {
            val multiplier = if (isDemon(target)) demonMultiplier else 1
            for (stat in listOf(stats.attack, stats.strength, stats.defence)) {
                drainStat(target, stat, baseLevel(target, stat) * multiplier / 20 + 1)
            }
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }

    private fun baseLevel(target: PathingEntity, stat: StatType): Int =
        when (target) {
            is Player -> target.statBase(stat)
            is Npc ->
                when {
                    stat.isType(stats.attack) -> target.baseAttackLvl
                    stat.isType(stats.strength) -> target.baseStrengthLvl
                    stat.isType(stats.defence) -> target.baseDefenceLvl
                    stat.isType(stats.ranged) -> target.baseRangedLvl
                    stat.isType(stats.magic) -> target.baseMagicLvl
                    else -> 0
                }
        }
}

/**
 * Spear family "Shove": always applies a ~3 second stun but never deals damage. (The one-tile
 * knockback is approximated by the movement lock.)
 */
internal class Shove(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
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
        statuses.apply(target, StatusEffect.FROZEN, duration = 5)
        manager.queueMeleeHit(this, target, 0)
        manager.continueCombat(this, target)
    }
}

/**
 * Granite maul "Quick smash": a normal-damage hit that lets the next attack come out on the
 * following cycle (approximation of the instant follow-up swing).
 */
internal class QuickSmash(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        manager.setNextAttackDelay(this, 1)
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Dragon 2h sword "Powerstab": hits up to 14 npcs within one tile of the *wielder* in a single
 * sweeping attack. Rolls accuracy and damage normally for each target.
 */
internal class Powerstab(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val npcList: NpcList,
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        var struck = 0
        player.forEachNearbyNpc(npcList, radius = 1) { other ->
            if (other !== target && ++struck <= 13) {
                val splash = manager.rollMeleeDamage(this, other, attack, 1.0, 1.0)
                manager.giveCombatXp(this, other, attack, splash)
                manager.queueMeleeHit(this, other, splash)
            }
        }
        manager.continueCombat(this, target)
    }
}

/** Burning claws: the four-part claw flurry plus a burn that ticks damage over time. */
internal class BurningClaws(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
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
        val rolled = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
        val parts = intArrayOf(rolled, rolled / 2, rolled / 4, rolled / 4)
        parts.forEachIndexed { i, damage ->
            manager.queueMeleeHit(this, target, damage, delay = 1 + i / 2)
        }
        if (parts.sum() > 0) {
            statuses.apply(target, StatusEffect.BURN, duration = 40, potency = 1)
        }
        manager.giveCombatXp(this, target, attack, parts.sum())
        manager.continueCombat(this, target)
    }
}

/**
 * Abyssal whip "Energy drain": +25% accuracy; versus players it also drains a small chunk of the
 * target's prayer points (standing in for the drained run energy, which has no engine stat).
 */
internal class EnergyDrain(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
        if (damage > 0 && target is Player) {
            drainStat(target, stats.prayer, max(1, target.stat(stats.prayer) / 10))
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/** Abyssal tentacle "Binding tentacle": a hit that binds the target in place and poisons it. */
internal class BindingTentacle(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        if (damage > 0) {
            statuses.apply(target, StatusEffect.FROZEN, duration = 5)
            statuses.apply(target, StatusEffect.POISON, duration = 40, potency = 4)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/** Abyssal bludgeon "Penance": restores prayer points equal to half the damage dealt. */
internal class Penance(
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
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
        if (damage > 0) {
            player.statBoost(stats.prayer, constant = damage / 2, percent = 0)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/** Shared ranged pipeline for ammunition-based special attacks. */
internal abstract class OsrsRangedSpecBase(
    protected val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
    protected val objTypes: ObjTypeList,
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

    /** True-strike specials (Powershot) skip the accuracy roll entirely. */
    protected open val guaranteed: Boolean = false

    protected open fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) = Unit

    /** Hook invoked once after all shots are queued (Momentum Throw's faster next attack). */
    protected open fun ProtectedAccess.onFired(target: PathingEntity, attack: CombatAttack.Ranged) =
        Unit

    protected open fun ProtectedAccess.rollShot(
        target: PathingEntity,
        attack: CombatAttack.Ranged,
    ): Int {
        if (guaranteed) {
            val maxHit =
                manager.calculateRangedMaxHit(
                    this,
                    target,
                    attack.type,
                    attack.style,
                    damageMultiplier,
                    0,
                )
            return random.of(0..maxHit)
        }
        return manager.rollRangedDamage(this, target, attack, accuracyMultiplier, damageMultiplier)
    }

    protected fun ProtectedAccess.fire(
        target: PathingEntity,
        attack: CombatAttack.Ranged,
    ): Boolean {
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
            val damage = rollShot(target, attack)
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
        onFired(target, attack)
        return true
    }
}

internal class OsrsRapidShot(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    override val shots: Int,
    private val acc: Double,
    override val damageMultiplier: Double,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val accuracyMultiplier: Double = acc
}

internal class OsrsHeavyShot(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    private val acc: Double,
    override val damageMultiplier: Double,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = acc
}

/**
 * Webweaver bow "Swarm": four rapid shots at double accuracy, each capped at 40% of the normal max
 * hit.
 */
internal class WebweaverSwarm(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 4
    override val accuracyMultiplier: Double = 2.0
    override val damageMultiplier: Double = 0.4
}

/**
 * Dragon crossbow "Annihilate": double-accuracy bolt that also splashes the same damage onto every
 * npc adjacent to the target.
 */
internal class Annihilate(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    private val npcList: NpcList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 2.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        if (damage <= 0) {
            return
        }
        target.forEachNearbyNpc(npcList, radius = 1) { other ->
            manager.queueMeleeHit(this, other, damage)
        }
    }
}

/** Dorgeshuun ("bone") crossbow: near-guaranteed hit that lowers Defence by the damage dealt. */
internal class BoneCrossbow(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 5.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        drainStat(target, stats.defence, damage)
    }
}

/** Seercull "Soulshot": near-guaranteed hit that lowers Magic by the damage dealt. */
internal class Seercull(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 10.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        drainStat(target, stats.magic, damage)
    }
}

/**
 * Accursed sceptre "Condemn": +50% accuracy and max hit; a hit drains up to 15% of the target's
 * current Defence and Magic.
 */
internal class AccursedCondemn(private val manager: SpecialAttackManager) : MagicSpecialAttack {
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
        val accurate = manager.rollStaffAccuracy(this, target, attack.style, 1.5)
        val baseMaxHit = 1 + player.stat(stats.magic) / 4
        val damage = if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, 1.5) else 0
        if (damage > 0) {
            drainStatByCurrent(target, stats.defence, 15)
            drainStatByCurrent(target, stats.magic, 15)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}

/** Eldritch nightmare staff "Invocate": +25% damage and restores prayer by half the damage. */
internal class EldritchInvocate(private val manager: SpecialAttackManager) : MagicSpecialAttack {
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
        val damage = if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, 1.25) else 0
        if (damage > 0) {
            player.statBoost(stats.prayer, constant = damage / 2, percent = 0)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}

/** Generic powered-staff bolt (Zuriel's staff and friends). */
internal class StaffBolt(
    private val manager: SpecialAttackManager,
    private val damageMultiplier: Double,
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
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}

/**
 * Dragon halberd "Sweep": strikes the main target at +10% damage and also sweeps every npc adjacent
 * to the wielder.
 */
internal class HalberdSweep(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val npcList: NpcList,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        sweep(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        sweep(target, attack)
        return true
    }

    private fun ProtectedAccess.sweep(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        var total = 0
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.1)
        manager.queueMeleeHit(this, target, damage)
        total += damage
        player.forEachNearbyNpc(npcList, radius = 1) { other ->
            if (other != target) {
                val splash = manager.rollMeleeDamage(this, other, attack, 1.0, 1.1)
                manager.queueMeleeHit(this, other, splash)
                total += splash
            }
        }
        manager.giveCombatXp(this, target, attack, total)
        manager.continueCombat(this, target)
    }
}

/**
 * Dual macuahuitl "Blood Infusion": costs 25% of the wielder's current Hitpoints (no cost below 4
 * HP), raises min and max hit by 25%, and guarantees the Bloodrager set effect on a successful hit
 * (next attack one tick earlier).
 */
internal class BloodInfusion(
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
        val hitpoints = player.stat(stats.hitpoints)
        if (hitpoints >= 4) {
            player.statSub(stats.hitpoints, hitpoints / 4, 1)
        }
        val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.25)
        val first = random.of(maxHit * 5 / 16..max(maxHit * 5 / 16, maxHit * 5 / 8))
        val second = random.of(maxHit * 5 / 16..max(maxHit * 5 / 16, maxHit * 5 / 8))
        manager.queueMeleeHit(this, target, first, delay = 1)
        manager.queueMeleeHit(this, target, second, delay = 1)
        if (first + second > 0) {
            manager.setNextAttackDelay(this, 3)
        }
        manager.giveCombatXp(this, target, attack, first + second)
        manager.continueCombat(this, target)
    }
}

/**
 * Ursine chainmace "Bear Down": doubled-accuracy hit that, on a successful strike, deals 20 damage
 * over six seconds, stops the target from running for six ticks (approximated by a short bind) and
 * drains 20 Agility levels.
 */
internal class UrsineBearDown(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val statuses: StatusService,
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
        val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.0)
        if (damage > 0) {
            statuses.apply(target, StatusEffect.FROZEN, duration = 6)
            drainStat(target, stats.agility, 20)
            manager.queueMeleeHit(this, target, 10, delay = 5)
            manager.queueMeleeHit(this, target, 10, delay = 10)
        }
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMeleeHit(this, target, damage)
        manager.continueCombat(this, target)
    }
}

/**
 * Dinh's bulwark "Shield Bash": slams every npc adjacent to the wielder for reduced damage (up to
 * ten targets).
 */
internal class ShieldBash(
    private val manager: SpecialAttackManager,
    private val attackManager: PlayerAttackManager,
    private val npcList: NpcList,
) : MeleeSpecialAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
        bash(target, attack)
        return true
    }

    override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean {
        bash(target, attack)
        return true
    }

    private fun ProtectedAccess.bash(target: PathingEntity, attack: CombatAttack.Melee) {
        attackManager.playWeaponFx(player, attack)
        var total = 0
        var targets = 0
        val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 0.5)
        manager.queueMeleeHit(this, target, damage)
        total += damage
        targets++
        player.forEachNearbyNpc(npcList, radius = 1) { other ->
            if (other != target && targets < 10) {
                val splash = manager.rollMeleeDamage(this, other, attack, 1.0, 0.5)
                manager.queueMeleeHit(this, other, splash)
                total += splash
                targets++
            }
        }
        manager.giveCombatXp(this, target, attack, total)
        manager.continueCombat(this, target)
    }
}

/** Magic longbow / composite bow "Powershot": a guaranteed hit (true strike). */
internal class Powershot(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 1.0
    override val damageMultiplier: Double = 1.0
    override val guaranteed: Boolean = true
}

/**
 * Scorching bow "Scorching shackles": empowered shot that ignites and briefly binds demonic
 * targets.
 */
internal class ScorchingShackles(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
    private val statuses: StatusService,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 1.3
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        if (damage > 0 && isDemon(target)) {
            statuses.apply(target, StatusEffect.BURN, duration = 40, potency = 1)
            statuses.apply(target, StatusEffect.FROZEN, duration = 20)
        }
    }
}

/**
 * Dragon thrownaxe "Momentum Throw": a quick throw that lets the wielder attack again one tick
 * sooner.
 */
internal class MomentumThrow(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 1.25
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onFired(target: PathingEntity, attack: CombatAttack.Ranged) {
        manager.setNextAttackDelay(this, 1)
    }
}

/**
 * Morrigan's throwing axe "Hamstring": a hit that also drains the target's run energy by the damage
 * dealt (players only).
 */
internal class Hamstring(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 1.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        if (damage > 0 && target is Player) {
            target.runEnergy = max(0, target.runEnergy - damage * 100)
        }
    }
}

/**
 * Morrigan's javelin "Phantom strike": after the initial javelin lands, phantom damage ticks hit
 * the target every three cycles (one tick of 10 damage per 40 damage dealt).
 */
internal class PhantomStrike(
    manager: SpecialAttackManager,
    attackManager: PlayerAttackManager,
    ammunition: RangedAmmoManager,
    objTypes: ObjTypeList,
) : OsrsRangedSpecBase(manager, attackManager, ammunition, objTypes) {
    override val shots: Int = 1
    override val accuracyMultiplier: Double = 1.0
    override val damageMultiplier: Double = 1.0

    override fun ProtectedAccess.onHit(target: PathingEntity, damage: Int) {
        val ticks = damage * 3 / 40
        repeat(ticks) { i ->
            manager.queueRangedHit(this, target, null, PHANTOM_DAMAGE, 30, 3 * (i + 1))
        }
    }

    private companion object {
        private const val PHANTOM_DAMAGE = 10
    }
}

/**
 * Volatile nightmare staff "Immolate": powered staff strike at +50% accuracy with a base max hit of
 * `max(49, Magic - 2)`.
 */
internal class VolatileImmolate(private val manager: SpecialAttackManager) : MagicSpecialAttack {
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
        val accurate = manager.rollStaffAccuracy(this, target, attack.style, 1.5)
        val baseMaxHit = max(49, player.stat(stats.magic) - 2)
        val damage = if (accurate) manager.rollStaffMaxHit(this, target, baseMaxHit, 1.0) else 0
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, damage, clientDelay = 0)
        manager.continueCombat(this, target)
    }
}
