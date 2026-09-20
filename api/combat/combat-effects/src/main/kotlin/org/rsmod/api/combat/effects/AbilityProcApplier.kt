package org.rsmod.api.combat.effects

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStatus
import org.rsmod.api.equipment.instance.AbilityStatusProc
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark

/**
 * Applies the non-damage riders of worn-instance ability procs ([AbilityProc.statuses] and AoE
 * splash) to hit targets. Installed into [org.rsmod.api.player.support.AbilityProcProviders.onHit]
 * at startup so the npc and PvP hit processors can reach the status service without a module cycle.
 *
 * Mimics the source boss mechanics: a "venom cloud" proc venoms every npc in the area, a "poison
 * slam" poisons its target, freeze/snare abilities lock movement, and vulnerability / weakening
 * riders stack through the existing `VULNERABLE`/`WEAKEN` damage modifiers. Boss abilities
 * additionally gain wider AoE and slightly stronger strikes (see `EquipmentAbilityProcs.procFor`).
 *
 * Npc `poison_immunity`/`venom_immunity`/`burn_immunity` params are honored: `1` halves the
 * ailment's potency and duration, `2`+ blocks it entirely.
 */
@Singleton
public class AbilityProcApplier @Inject constructor(private val effects: CombatEffects) {
    private val statusService
        get() = effects.statusService

    /** Applies [proc]'s on-hit riders to [target] and splashes AoE onto nearby npcs. */
    public fun onHit(source: PathingEntity, target: PathingEntity, proc: AbilityProc, damage: Int) {
        val alive =
            when (target) {
                is Npc -> target.hitpoints > 0
                is Player -> target.hitpoints > 0
            }
        if (damage <= 0 || !alive) {
            return
        }
        applyStatuses(target, proc)
        if (proc.aoeRadius > 0 && proc.aoeDamageBps > 0 && target is Npc) {
            splashArea(target, proc, damage)
        }
    }

    private fun applyStatuses(target: PathingEntity, proc: AbilityProc) {
        for (statusProc in proc.statuses) {
            val adjusted = adjustForImmunity(target, statusProc) ?: continue
            statusService.apply(
                target = target,
                effect = adjusted.status.toStatusEffect(),
                duration = adjusted.cycles,
                stacks = adjusted.stacks,
                potency = adjusted.potency,
            )
        }
    }

    /**
     * Returns the [statusProc] scaled down for a partially-immune npc, or `null` when the target is
     * fully immune.
     */
    private fun adjustForImmunity(
        target: PathingEntity,
        statusProc: AbilityStatusProc,
    ): AbilityStatusProc? {
        if (target !is Npc) {
            return statusProc
        }
        val immunity =
            when (statusProc.status) {
                AbilityStatus.Poison -> target.paramOrNull(params.poison_immunity) ?: 0
                AbilityStatus.Venom -> target.paramOrNull(params.venom_immunity) ?: 0
                AbilityStatus.Burn -> target.paramOrNull(params.burn_immunity) ?: 0
                else -> 0
            }
        return when {
            immunity >= IMMUNITY_FULL -> null
            immunity >= IMMUNITY_PARTIAL ->
                statusProc.copy(
                    potency = statusProc.potency / 2,
                    cycles = (statusProc.cycles / 2).coerceAtLeast(1),
                )
            else -> statusProc
        }
    }

    /**
     * Splashes [proc]'s AoE onto npcs within [AbilityProc.aoeRadius] of [target], mirroring boss
     * area attacks. Secondary victims take a fraction of the original hit and receive the same
     * ailment riders - a venom cloud venoms everything it touches.
     */
    private fun splashArea(target: Npc, proc: AbilityProc, damage: Int) {
        val splash = (damage.toLong() * proc.aoeDamageBps / 10_000).coerceAtMost(MAX_SPLASH).toInt()
        if (splash <= 0) {
            return
        }
        effects
            .npcsInArea(target.coords, proc.aoeRadius)
            .filter { victim ->
                victim != target &&
                    victim.isSlotAssigned &&
                    !victim.isInvisible &&
                    victim.hitpoints > 0
            }
            .take(MAX_AOE_TARGETS)
            .forEach { victim ->
                dealDamage(victim, splash)
                applyStatuses(victim, proc)
            }
    }

    private fun dealDamage(victim: PathingEntity, damage: Int) {
        when (victim) {
            is Player ->
                victim.queueHit(
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    hitmark = hitmark_groups.regular_damage,
                )
            is Npc -> {
                val applied = min(damage, victim.hitpoints)
                if (applied <= 0) {
                    return
                }
                victim.hitpoints -= applied
                val group = hitmark_groups.regular_damage
                victim.showHitmark(
                    Hitmark.fromNoSource(
                        self = group.lit.id,
                        source = group.lit.id,
                        public = group.tint?.id,
                        damage = applied,
                        delay = 0,
                    )
                )
                if (victim.hitpoints == 0 && queues.death !in victim.queueList) {
                    victim.queue(queues.death, 1)
                }
            }
        }
    }

    private fun AbilityStatus.toStatusEffect(): StatusEffect =
        when (this) {
            AbilityStatus.Poison -> StatusEffect.POISON
            AbilityStatus.Venom -> StatusEffect.VENOM
            AbilityStatus.Burn -> StatusEffect.BURN
            AbilityStatus.Bleed -> StatusEffect.BLEED
            AbilityStatus.Freeze -> StatusEffect.FROZEN
            AbilityStatus.Vulnerable -> StatusEffect.VULNERABLE
            AbilityStatus.Weaken -> StatusEffect.WEAKEN
        }

    private companion object {
        /** Npc immunity param value granting partial resistance (halved potency/duration). */
        const val IMMUNITY_PARTIAL: Int = 1

        /** Npc immunity param value granting full immunity. */
        const val IMMUNITY_FULL: Int = 2

        /** Maximum secondary victims splashed by one AoE proc. */
        const val MAX_AOE_TARGETS: Int = 5

        /** Splash damage ceiling per secondary victim. */
        const val MAX_SPLASH: Long = 2_000
    }
}
