package org.rsmod.api.player.hit.processor

import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.headbars
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.bonus.EquipmentCombatStats
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.headbar.InternalPlayerHeadbars
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.support.StatusProviders
import org.rsmod.api.player.support.SupportCombatState
import org.rsmod.api.player.support.SupportNpcRules
import org.rsmod.api.player.torso
import org.rsmod.api.random.GameRandom
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.synth.SynthType

public object StandardPlayerHitProcessor : QueuedPlayerHitProcessor {
    private val hitSoundsBodyA =
        listOf(synths.human_hit_1, synths.human_hit_2, synths.human_hit_3, synths.human_hit_4)

    private val hitSoundsBodyB = listOf(synths.female_hit_1, synths.female_hit_2)

    // Stateless service - safe to share the singleton-style instance from an object processor.
    private val perks = PerkService()

    override fun ProtectedAccess.process(initialHit: Hit) {
        if (!initialHit.isValid(this)) {
            return
        }
        var hit = initialHit
        val impact = PlayerHitEvents.GlobalImpact(player, hit)
        eventBus.publish(impact)
        if (impact.damage != hit.damage) {
            hit = hit.copy(hitmark = hit.hitmark.copy(damage = impact.damage.coerceAtLeast(0)))
        }
        preventLogout("You can't log out until 10 seconds after the end of combat.", 16)

        // TODO(combat): Process degradation, ring of recoil, retribution, etc.

        // Guardian reduces all incoming damage; the style wards and Last Stand only apply to
        // npc-originated hits so PvP stays unaffected. `VULNERABLE` stacks raise incoming damage
        // through a negative reduction; `WEAKEN` stacks on the attacker reduce its outgoing hit.
        var reductionBps =
            perks.incomingReductionBps(player) - StatusProviders.incomingDamageBps(player)
        if (hit.isFromNpc) {
            reductionBps += perks.styleReductionBps(player, hit.type)
            // `Iron Sanctuary` - npc-only (so PvP stays unaffected) and scoped by the attacking
            // npc's own support rules, so a boss can reduce or block it.
            val sourceNpc = findHitNpcSource(hit)
            if (sourceNpc != null) {
                reductionBps += StatusProviders.outgoingDamageReductionBps(sourceNpc)
            }
            val sourceType = sourceNpc?.visType
            if (sourceType != null) {
                reductionBps +=
                    SupportNpcRules.scale(
                        SupportCombatState.incomingReductionBps(player),
                        sourceType,
                    )
            }
            if (player.hitpoints * 4 < player.baseHitpointsLvl) {
                reductionBps += perks.lastStandReductionBps(player)
            }
            // Equipment `DamageReduction` affixes (tank gear) - npc hits only, so PvP stays
            // unaffected. Capped so gear can never create an immortal build.
            reductionBps +=
                (EquipmentCombatStats.snapshot(player)?.damageReductionBps ?: 0).coerceAtMost(
                    EquipmentCombatStats.MAX_DAMAGE_REDUCTION_BPS
                )
        } else if (hit.isFromPlayer) {
            // `WEAKEN` stacks on the attacking player reduce its outgoing hit in PvP as well.
            findHitPlayerSource(hit)?.let {
                reductionBps += StatusProviders.outgoingDamageReductionBps(it)
            }
        }
        // A negative `reductionBps` (e.g. `VULNERABLE` stacks) amplifies the hit instead.
        val reducedHit =
            if (reductionBps != 0 && hit.damage > 0) {
                val reduced = (hit.damage - hit.damage * reductionBps / 10_000).coerceAtLeast(0)
                val hitmark = hit.hitmark.copy(damage = reduced)
                hit.copy(hitmark = hitmark)
            } else {
                hit
            }

        // `Guardian's Grace` absorbs whatever is left of an npc-originated hit. Player (PvP) hits
        // never take this branch, so the shield can never mitigate another player.
        val finalHit = if (hit.isFromNpc) absorbShield(reducedHit) else reducedHit

        val damage = min(player.hitpoints, finalHit.damage)
        if (damage > 0) {
            statSub(stats.hitpoints, constant = damage, percent = 0)
            reflectThorns(finalHit)
        }

        playDefendSound(finalHit, random)

        val queueDeath = player.hitpoints == 0 && queues.death !in player.queueList
        if (queueDeath) {
            queueDeath()
        }

        player.showHitmark(finalHit.hitmark)

        val headbar = finalHit.createHeadbar(player.hitpoints, player.baseHitpointsLvl)
        player.showHeadbar(headbar)
    }

    /**
     * Consumes shield points for an incoming npc hit, returning the hit with the absorbed damage
     * removed. The shield only ever absorbs ordinary damage; it cannot rescue a player from a
     * dedicated instakill/wipe mechanic, because those are not routed through this processor.
     */
    private fun ProtectedAccess.absorbShield(hit: Hit): Hit {
        val absorbed = SupportCombatState.absorb(player, hit.damage)
        if (absorbed <= 0) {
            return hit
        }
        return hit.copy(hitmark = hit.hitmark.copy(damage = hit.damage - absorbed))
    }

    /**
     * `Thorns` perk reflects a share of incoming npc damage back to the attacker. Reflected damage
     * never drops the npc below 1 hitpoint - kills must come from real hits.
     */
    private fun ProtectedAccess.reflectThorns(hit: Hit) {
        if (!hit.isFromNpc || hit.damage <= 0) {
            return
        }
        val bps = perks.reflectBps(player)
        if (bps <= 0) {
            return
        }
        val npc = findHitNpcSource(hit) ?: return
        val reflected = (hit.damage * bps / 10_000).coerceAtLeast(1)
        val applied = min(reflected, npc.hitpoints - 1)
        if (applied > 0) {
            npc.hitpoints -= applied
        }
    }

    private fun Hit.isValid(access: ProtectedAccess): Boolean {
        // Currently, we only have evidence of this validation being applied to hits dealt by npcs.
        if (!isFromNpc) {
            return true
        }
        // Only melee-based hits can be invalidated here.
        if (type != HitType.Melee) {
            return true
        }
        // If the npc that dealt the hit can no longer be found, the hit is invalidated. This can
        // occur when the npc's internal `uid` is reassigned (e.g., due to transmogrification).
        val npc = access.findHitNpcSource(this) ?: return false
        return npc.hitpoints > 0
    }

    private fun ProtectedAccess.playDefendSound(hit: Hit, random: GameRandom) {
        val lefthandType = player.lefthand?.let(::ocType)
        val torsoType = player.torso?.let(::ocType)
        val bodyType = player.appearance.bodyType

        val defendSound = resolveDefendSound(lefthandType, torsoType, hit.damage, bodyType, random)
        soundSynth(defendSound, delay = 20)

        val playerSource = if (hit.isFromPlayer) findHitPlayerSource(hit) else null
        playerSource?.soundSynth(defendSound)
    }

    private fun resolveDefendSound(
        lefthand: UnpackedObjType?,
        torso: UnpackedObjType?,
        damage: Int,
        bodyType: Int,
        random: GameRandom,
    ): SynthType =
        when {
            damage == 0 -> resolveBlockSound(lefthand, torso, random)
            bodyType == constants.bodytype_a -> random.pick(hitSoundsBodyA)
            bodyType == constants.bodytype_b -> random.pick(hitSoundsBodyB)
            else -> throw NotImplementedError("Sound for body type is not implemented: $bodyType")
        }

    private fun resolveBlockSound(
        lefthand: UnpackedObjType?,
        torso: UnpackedObjType?,
        random: GameRandom,
    ): SynthType {
        val lefthandSound = lefthand?.randomBlockSound(random)
        if (lefthandSound != null) {
            return lefthandSound
        }

        val torsoSound = torso?.randomBlockSound(random)
        if (torsoSound != null) {
            return torsoSound
        }

        return synths.human_block_1
    }

    private fun UnpackedObjType.randomBlockSound(random: GameRandom): SynthType? {
        val sounds =
            listOfNotNull(
                paramOrNull(params.item_block_sound1),
                paramOrNull(params.item_block_sound2),
                paramOrNull(params.item_block_sound3),
                paramOrNull(params.item_block_sound4),
                paramOrNull(params.item_block_sound5),
            )
        return random.pickOrNull(sounds)
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int): Headbar =
        InternalPlayerHeadbars.createFromHitmark(hitmark, currHp, maxHp, headbars.health_30)
}
