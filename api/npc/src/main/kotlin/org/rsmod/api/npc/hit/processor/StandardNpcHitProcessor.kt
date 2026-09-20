package org.rsmod.api.npc.hit.processor

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.EquipmentAbilityEffects
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hasAttackOp
import org.rsmod.api.npc.headbar.InternalNpcHeadbars
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.bonus.ArmourSetEffects
import org.rsmod.api.player.output.AbilityProcFx
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.support.AbilityProcProviders
import org.rsmod.api.player.support.StatusProviders
import org.rsmod.api.player.support.SupportCombatState
import org.rsmod.api.player.support.SupportNpcRules
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.type.headbar.HeadbarType
import org.rsmod.game.type.obj.Wearpos

public class StandardNpcHitProcessor
@Inject
constructor(
    private val playerList: PlayerList,
    private val npcList: NpcList,
    private val eventBus: EventBus,
    private val abilityEffects: EquipmentAbilityEffects,
    private val perks: PerkService,
    private val armourSets: ArmourSetEffects,
    private val threat: ThreatService,
) : NpcHitProcessor {
    private var isAoESpreading: Boolean = false

    override fun StandardNpcAccess.process(hit: Hit) {
        // TODO(combat): Show ironman_blocked hitmark if source is an ironman and target has been
        // damaged by other sources.

        val playerSource = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        val hasGodRing = playerSource?.worn?.get(Wearpos.Ring.slot)?.id == 773
        // Each worn instance's abilities roll their own proc chance (scaled by the instance's
        // rarity and item level); only the successful procs contribute.
        val proc =
            if (playerSource == null) {
                AbilityProc()
            } else {
                var rolled = AbilityProc()
                val procced = ArrayList<String>()
                val resolvedProcs =
                    abilityEffects.procs(playerSource.worn) + armourSets.procs(playerSource)
                for (resolved in resolvedProcs) {
                    if (random.of(10_000) < resolved.chanceBps) {
                        rolled += resolved.proc
                        procced += resolved.abilityId
                    }
                }
                if (hit.damage > 0) {
                    AbilityProcFx.announce(playerSource, procced)
                }
                EquipmentAbilityProcs.cap(rolled)
            }

        // Perk damage bonuses stack additively on top of the rolled ability proc bonus. All of
        // these only apply to player-originated hits, so PvP is unaffected by definition.
        var perkDamageBps = 0
        var flatBonus = 0
        var crit = false
        if (playerSource != null && hit.damage > 0) {
            perkDamageBps =
                perks.outgoingDamageBps(playerSource) +
                    perks.soloBossDamageBps(playerSource) +
                    perks.styleDamageBps(playerSource, hit.type) +
                    conditionalDamageBps(playerSource, npc) +
                    armourSets.outgoingDamageBps(playerSource) +
                    // `Battle Hymn` - folded into the same additive pool so it cannot multiply
                    // with perks, prayers or gear. Scaled by the target npc's support rules.
                    SupportNpcRules.scale(
                        SupportCombatState.outgoingDamageBps(playerSource),
                        npc.visType,
                    ) -
                    // `WEAKEN` stacks on the attacker reduce its outgoing hit.
                    StatusProviders.outgoingDamageReductionBps(playerSource)
            flatBonus = perks.flatBonusDamage(playerSource)
            crit = random.of(10_000) < perks.critChanceBps(playerSource)
        }
        // `VULNERABLE` stacks on the npc raise the damage it takes from any source; `WEAKEN`
        // stacks on an npc attacker lower the hit it deals.
        perkDamageBps += StatusProviders.incomingDamageBps(npc)
        if (hit.isFromNpc && hit.damage > 0) {
            hit.resolveNpcSource(npcList)?.let {
                perkDamageBps -= StatusProviders.outgoingDamageReductionBps(it)
            }
        }
        val totalBps = proc.outgoingDamageBps.toLong() + perkDamageBps
        // Negative `totalBps` (`WEAKEN`-driven) reduces the hit instead of boosting it.
        val abilityBoosted =
            if (hit.damage > 0 && (totalBps != 0L || flatBonus > 0 || crit)) {
                var boosted =
                    hit.damage.toLong() + hit.damage.toLong() * totalBps / 10000 + flatBonus
                if (crit) {
                    boosted += boosted * perks.critDamageBps() / 10_000
                }
                boosted = boosted.coerceIn(0, Hitmark.DAMAGE_BIT_MASK)
                hit.copy(hitmark = hit.hitmark.copy(damage = boosted.toInt()))
            } else {
                hit
            }

        val effectiveHit =
            if (hasGodRing) {
                val multipliedDamage =
                    (abilityBoosted.damage.toLong() * 100L)
                        .coerceAtMost(Hitmark.DAMAGE_BIT_MASK)
                        .toInt()
                val finalDamage = kotlin.math.max(100, multipliedDamage)
                val boostedHitmark = abilityBoosted.hitmark.copy(damage = finalDamage)
                abilityBoosted.copy(hitmark = boostedHitmark)
            } else {
                abilityBoosted
            }

        var changedDamage: Int? = null
        if (effectiveHit.damage > npc.hitpoints) {
            changedDamage = npc.hitpoints
        }

        val processedHit =
            if (changedDamage == 0) {
                val zeroDamageHitmark = hitmark_groups.zero_damage
                val modifiedHitmark =
                    effectiveHit.hitmark.copy(
                        self = zeroDamageHitmark.lit.id,
                        source = zeroDamageHitmark.lit.id,
                        public =
                            if (effectiveHit.hitmark.isPrivate) null
                            else zeroDamageHitmark.tint?.id,
                        damage = changedDamage,
                    )
                val modifiedHit = effectiveHit.copy(hitmark = modifiedHitmark)
                takeHit(modifiedHit)
                modifiedHit
            } else if (changedDamage != null) {
                val modifiedHitmark = effectiveHit.hitmark.copy(damage = changedDamage)
                val modifiedHit = effectiveHit.copy(hitmark = modifiedHitmark)
                takeHit(modifiedHit)
                modifiedHit
            } else {
                takeHit(effectiveHit)
                effectiveHit
            }

        // `Leech` perk healing stacks with ability `onHitHealBps` procs.
        val onHitHealBps =
            proc.onHitHealBps + (if (playerSource != null) perks.onHitHealBps(playerSource) else 0)
        if (onHitHealBps > 0 && playerSource != null && processedHit.damage > 0) {
            val heal = (processedHit.damage.toLong() * onHitHealBps / 10000).toInt()
            if (heal > 0) {
                playerSource.statBoost(stats.hitpoints, constant = heal, percent = 0)
            }
        }

        // Per-npc threat table: real damage dealt by a player feeds aggro. Runs after `takeHit`
        // so the recorded amount matches the damage actually applied.
        if (playerSource != null && processedHit.damage > 0) {
            threat.onDamage(npc, playerSource, processedHit.damage)
        }

        // Ability riders: ailments (poison/venom/burn/bleed/freeze/...) and AoE splash are
        // applied by `combat-effects` via the provider, keeping this module free of a status
        // service dependency.
        if (playerSource != null && processedHit.damage > 0 && proc.hasOnHitEffects) {
            AbilityProcProviders.onHit(playerSource, npc, proc, processedHit.damage)
        }

        // Combat XP is normally granted when the attack is queued, before equipment procs are
        // resolved. Grant the proc-only portion here as well, after the actual hit is capped by
        // the target's remaining HP. This applies equally to normal players and virtual
        // companion players; the companion runtime mirrors the bot's combat XP afterwards.
        if (playerSource != null && proc.outgoingDamageBps > 0 && processedHit.damage > 0) {
            val procDamage =
                (hit.damage.toLong() * proc.outgoingDamageBps / 10_000)
                    .toInt()
                    .coerceAtMost(processedHit.damage)
            if (procDamage > 0) {
                playerSource.statAdvance(stats.hitpoints, procDamage * 1.33)
            }
        }

        if (hasGodRing && !isAoESpreading) {
            try {
                isAoESpreading = true
                applyGodRingAoE(playerSource, processedHit)
            } finally {
                isAoESpreading = false
            }
        }
    }

    /**
     * Conditional perk damage bonuses resolved against the target npc: `Slayer` (bosses),
     * `Executioner` (target below 25% hp), `Punisher` (target above 75% hp), `Dominion` (target
     * outlevels the player) and `Bloodlust` (player below half health).
     */
    private fun conditionalDamageBps(source: Player, target: Npc): Int {
        var bps = 0
        if (perks.isBoss(target.type.vislevel)) {
            bps += perks.bossDamageBps(source)
        }
        val maxHp = target.baseHitpointsLvl.coerceAtLeast(1)
        if (target.hitpoints * 4 < maxHp) {
            bps += perks.executeDamageBps(source)
        } else if (target.hitpoints * 4 > maxHp * 3) {
            bps += perks.openerDamageBps(source)
        }
        if (target.type.vislevel > source.combatLevel) {
            bps += perks.dominionDamageBps(source)
        }
        if (source.hitpoints * 2 < source.baseHitpointsLvl) {
            bps += perks.bloodlustDamageBps(source)
        }
        return bps
    }

    private fun StandardNpcAccess.applyGodRingAoE(
        source: org.rsmod.game.entity.Player,
        sourceHit: Hit,
    ) {
        val center = npc.coords
        val damage = sourceHit.damage
        val hitmarkLit = sourceHit.hitmark.self
        val hitmarkTint = sourceHit.hitmark.public

        for (other in npcList) {
            if (other == npc) {
                continue
            }
            if (!other.isSlotAssigned || other.isInvisible || other.hitpoints <= 0) {
                continue
            }
            if (other.coords.level != center.level) {
                continue
            }
            if (other.coords.chebyshevDistance(center) > 10) {
                continue
            }
            if (!other.hasAttackOp()) {
                continue
            }

            val aoeAccess =
                StandardNpcAccess(
                    other,
                    coroutine = org.rsmod.coroutine.GameCoroutine(),
                    context = context,
                )
            val actualDmg = if (damage > other.hitpoints) other.hitpoints else damage
            val aoeHitmark =
                Hitmark.fromPlayerSource(
                    self = hitmarkLit,
                    source = hitmarkLit,
                    public = hitmarkTint,
                    damage = actualDmg,
                    delay = 0,
                    slotId = source.slotId,
                )
            val aoeHit =
                Hit(
                    type = sourceHit.type,
                    hitmark = aoeHitmark,
                    sourceUid = source.uid.packed,
                    righthandObj = null,
                    secondaryObj = null,
                )
            aoeAccess.heroPoints(source, actualDmg)
            aoeAccess.takeHit(aoeHit)
        }
    }

    private fun StandardNpcAccess.takeHit(hit: Hit, allowSecondary: Boolean = true) {
        check(hit.damage <= npc.hitpoints) {
            "Expected hit damage to be less than or equal to available hitpoints: " +
                "health=${npc.hitpoints}, hit=$hit"
        }
        // TODO(combat): Process recoils, retribution(?), etc.
        npc.hitpoints -= hit.damage

        playDefendSound(hit)

        val queueDeath = npc.hitpoints == 0 && queues.death !in npc.queueList
        if (queueDeath) {
            queueDeath()
        }

        npc.showHitmark(hit.hitmark)

        val visHeadbar = npc.visHeadbar(params.headbar)
        val headbar = hit.createHeadbar(npc.hitpoints, npc.baseHitpointsLvl, visHeadbar)
        npc.showHeadbar(headbar)

        val impact = npc.publishHitEvent(hit)
        if (allowSecondary && impact.secondaryDamage > 0 && npc.hitpoints > 0) {
            val secondaryDamage = min(impact.secondaryDamage, npc.hitpoints)
            takeHit(
                hit.copy(hitmark = hit.hitmark.copy(damage = secondaryDamage)),
                allowSecondary = false,
            )
        }
    }

    private fun StandardNpcAccess.playDefendSound(hit: Hit) {
        val source = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        if (source == null) {
            return
        }
        val defendSound = npc.visType.paramOrNull(params.defend_sound) ?: return
        source.soundSynth(defendSound)
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int, headbar: HeadbarType): Headbar =
        InternalNpcHeadbars.createFromHitmark(hitmark, currHp, maxHp, headbar)

    private fun Npc.publishHitEvent(hit: Hit): NpcHitEvents.GlobalImpact {
        val event = NpcHitEvents.Impact(this, hit)
        eventBus.publish(event)
        val global = NpcHitEvents.GlobalImpact(this, hit)
        eventBus.publish(global)
        return global
    }
}
