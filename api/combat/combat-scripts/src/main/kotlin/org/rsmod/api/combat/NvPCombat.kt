package org.rsmod.api.combat

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.npc.attackingPlayer
import org.rsmod.api.combat.npc.lastAttack
import org.rsmod.api.combat.player.aggressiveNpc
import org.rsmod.api.combat.player.lastCombat
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjTypeList

internal class NvPCombat
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val objTypes: ObjTypeList,
    private val manager: PlayerAttackManager,
) {
    fun attack(access: StandardNpcAccess, target: Player, attack: CombatAttack.NpcAttack) {
        when (attack) {
            is CombatAttack.NpcMelee -> access.attackMelee(target, attack)
            is CombatAttack.NpcRanged -> access.attackRanged(target, attack)
            is CombatAttack.NpcMagic -> access.attackMagic(target, attack)
        }
    }

    private fun StandardNpcAccess.attackMelee(target: Player, attack: CombatAttack.NpcMelee) {
        if (!canAttack(target)) {
            resetMode()
            return
        }

        // Note: We do not need to explicitly call `opplayer2` because npcs will automatically
        // repeat their last interaction until it is canceled (e.g., by changing their `npcmode`).
        if (actionDelay > mapClock) {
            return
        }

        if (!npc.isInCombat()) {
            resetMode()
            return
        }

        val attackRate = npc.attackRate()
        actionDelay = mapClock + attackRate

        val attackAnim = npc.visType.param(params.attack_anim)
        val attackSound = npc.visType.paramOrNull(params.attack_sound)

        anim(attackAnim)
        attackSound?.let(target::soundSynth)

        val successfulHit = accuracy.rollMeleeAccuracy(npc, target, attack.type, random)

        val damage =
            if (successfulHit) {
                val maxHit = maxHits.getMeleeMaxHit(npc, target, attack.type)
                random.of(0..maxHit)
            } else {
                0
            }

        setAttackVars(target)

        // Note: Retaliation must be queued _before_ the hit. If queued after, every hit would
        // trigger the "speed-up" death mechanic, since the hit queues would no longer be the
        // last entries in the queue list at the time of processing.
        target.queueCombatRetaliate(npc)

        target.queueHit(npc, 1, HitType.Melee, damage)
        target.combatPlayDefendAnim(objTypes)
    }

    private fun StandardNpcAccess.attackRanged(target: Player, attack: CombatAttack.NpcRanged) {
        if (!beginAttack(target)) {
            return
        }

        val attackRate = npc.attackRate()
        actionDelay = mapClock + attackRate

        val attackAnim = npc.visType.param(params.attack_anim)
        val attackSound = npc.visType.paramOrNull(params.attack_sound)

        anim(attackAnim)
        attackSound?.let(target::soundSynth)

        val hitDelay = spawnProjectileFx(target)

        val successfulHit = accuracy.rollRangedAccuracy(npc, target, random)

        val damage =
            if (successfulHit) {
                val maxHit = maxHits.getRangedMaxHit(npc, target)
                random.of(0..maxHit)
            } else {
                0
            }

        setAttackVars(target)
        target.queueCombatRetaliate(npc)
        target.queueHit(npc, hitDelay, HitType.Ranged, damage)
    }

    private fun StandardNpcAccess.attackMagic(target: Player, attack: CombatAttack.NpcMagic) {
        if (!beginAttack(target)) {
            return
        }

        val attackRate = npc.attackRate()
        actionDelay = mapClock + attackRate

        val attackAnim = npc.visType.param(params.attack_anim)
        val attackSound = npc.visType.paramOrNull(params.attack_sound)

        anim(attackAnim)
        attackSound?.let(target::soundSynth)

        val hitDelay = spawnProjectileFx(target)

        val successfulHit = accuracy.rollMagicAccuracy(npc, target, random)

        val damage =
            if (successfulHit) {
                // `attack.maxHit` is resolved by the caller through `MaxHitFormulae.getMagicMaxHit`
                // before the attack is dispatched, so damage reductions are only rolled once.
                random.of(0..attack.maxHit)
            } else {
                0
            }

        setAttackVars(target)
        target.queueCombatRetaliate(npc)
        target.queueHit(npc, hitDelay, HitType.Magic, damage)
    }

    /**
     * Runs the guards shared by every npc attack. Returns `true` when the attack should proceed.
     */
    private fun StandardNpcAccess.beginAttack(target: Player): Boolean {
        if (!canAttack(target)) {
            resetMode()
            return false
        }

        if (actionDelay > mapClock) {
            return false
        }

        if (!npc.isInCombat()) {
            resetMode()
            return false
        }

        return true
    }

    /**
     * Spawns the travel projectile and launch spotanim for a ranged or magic npc attack, returning
     * the impact delay in cycles.
     *
     * Npcs without `proj_travel`/`proj_type` params still attack; they simply have no projectile
     * effect and fall back to a single-cycle impact delay.
     */
    private fun StandardNpcAccess.spawnProjectileFx(target: Player): Int {
        val travel = npc.visType.paramOrNull(params.proj_travel)
        val projType = npc.visType.paramOrNull(params.proj_type)
        if (travel == null || projType == null) {
            return 1
        }

        val launch = npc.visType.paramOrNull(params.proj_launch)
        if (launch != null) {
            spotanim(launch, height = 96, slot = constants.spotanim_slot_combat)
        }

        val projanim = manager.spawnProjectile(npc, target, travel, projType)
        val (serverDelay, _) = projanim.durations
        return serverDelay
    }

    private fun canAttack(target: Player): Boolean {
        return target.isValidTarget()
    }

    private fun StandardNpcAccess.setAttackVars(target: Player) {
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid
    }
}
