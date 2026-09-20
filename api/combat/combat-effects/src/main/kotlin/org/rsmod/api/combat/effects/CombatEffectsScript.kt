package org.rsmod.api.combat.effects

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.StatusEffect
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.timers
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.support.AbilityProcProviders
import org.rsmod.api.player.support.StatusProviders
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drives the periodic lifecycle of the unified status system ([StatusService]).
 * - The `toxins` player timer keeps its legacy self-rescheduling behavior: each fire advances the
 *   player's poison by a full [CombatEffects.POISON_INTERVAL] through [StatusService.tickEffect].
 * - The [GameLifecycle.LateCycle] event advances every other status for all players and every
 *   status (including poison) for all npcs, one cycle at a time.
 * - Player `VULNERABLE`/`WEAKEN` stacks are published through [StatusProviders] so the hit
 *   processors can apply their damage modifiers without a module dependency cycle.
 */
internal class CombatEffectsScript
@Inject
constructor(
    private val effects: CombatEffects,
    private val procApplier: AbilityProcApplier,
    private val npcList: NpcList,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val statusService = effects.statusService

        StatusProviders.incomingDamageBps = { entity ->
            statusService.getStacks(entity, StatusEffect.VULNERABLE) * VULNERABLE_BPS_PER_STACK
        }
        StatusProviders.outgoingDamageReductionBps = { entity ->
            statusService.getStacks(entity, StatusEffect.WEAKEN) * WEAKEN_BPS_PER_STACK
        }
        // Worn-instance ability riders (ailments, AoE splash) route through here so the hit
        // processors don't need a module dependency on this module.
        AbilityProcProviders.onHit = procApplier::onHit

        onPlayerTimer(timers.toxins) { applyPoisonTick() }

        onEvent<GameLifecycle.LateCycle> {
            playerList.forEach { player ->
                statusService.tick(
                    player,
                    exclude = StatusEffect.POISON,
                    onDamage = ::applyStatusDamage,
                )
            }
            npcList.forEach { npc -> statusService.tick(npc, onDamage = ::applyStatusDamage) }
        }

        onPlayerLogout { statusService.clearOnLogout(player) }
    }

    private fun ProtectedAccess.applyPoisonTick() {
        val statusService = effects.statusService
        if (!statusService.has(player, StatusEffect.POISON)) {
            player.timerMap.remove(timers.toxins)
            return
        }
        statusService.tickEffect(
            target = player,
            effect = StatusEffect.POISON,
            cycles = CombatEffects.POISON_INTERVAL,
            onDamage = ::applyStatusDamage,
        )
    }

    private fun applyStatusDamage(target: PathingEntity, effect: StatusEffect, damage: Int) {
        val group =
            when (effect) {
                StatusEffect.POISON -> hitmark_groups.poison_damage
                StatusEffect.VENOM -> hitmark_groups.venom
                StatusEffect.BLEED -> hitmark_groups.bleed
                StatusEffect.BURN -> hitmark_groups.burn
                else -> hitmark_groups.regular_damage
            }
        when (target) {
            is Player ->
                target.queueHit(
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    hitmark = group,
                )
            is Npc -> {
                val applied = min(damage, target.hitpoints)
                if (applied <= 0) {
                    return
                }
                target.hitpoints -= applied
                target.showHitmark(
                    Hitmark.fromNoSource(
                        self = group.lit.id,
                        source = group.lit.id,
                        public = group.tint?.id,
                        damage = applied,
                        delay = 0,
                    )
                )
                if (target.hitpoints == 0 && queues.death !in target.queueList) {
                    target.queue(queues.death, 1)
                }
            }
        }
    }

    private companion object {
        /** Incoming-damage bonus granted per `VULNERABLE` stack (+5%). */
        const val VULNERABLE_BPS_PER_STACK: Int = 500

        /** Outgoing-damage reduction applied per `WEAKEN` stack (-5%). */
        const val WEAKEN_BPS_PER_STACK: Int = 500
    }
}
