package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.ability.AbilityVisual
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.seq.SeqType

/**
 * Shared ability-presentation playback for players and companion bots (both are [Player] entities,
 * so one implementation covers §56 of the ability contract).
 * - Cast phase plays immediately: skeleton-aware animation, cast gfx on the caster, cast sound as
 *   an **area** sound (bot clients are stubs - `Player.soundSynth` on a bot reaches nobody, so
 *   sounds are emitted through `soundArea` at the caster's coords).
 * - Projectile phase uses [PlayerAttackManager.spawnProjectile]; the returned `clientDelay` is the
 *   authoritative landing time - the impact gfx, hit sound and any queued hit share it so damage
 *   can never visibly precede the projectile (§61).
 * - Impact spotanims carry the client-side delay via `spotanim(delay=...)`, and
 *   [AbilityVisual.extraImpacts] supply multi-hit follow-up beats (§62).
 *
 * NPC/transmog compatibility (§57): when the caster is transmogged into an npc, [AbilityVisual]'s
 * `npcSeq` override is preferred; otherwise the transmog npc's own `attack_anim` param is used, so
 * a companion wearing a boss model animates with that model's real attack instead of a deforming
 * player seq.
 */
@Singleton
public class CompanionAbilityPresenter
@Inject
constructor(private val manager: PlayerAttackManager) {

    /** The seq to play on [caster], honouring npc-skeleton overrides for transmogged bots. */
    public fun castSeqFor(caster: Player, visual: AbilityVisual): SeqType? {
        val transmog = caster.appearance.transmog ?: return visual.castSeq
        return visual.npcSeq ?: transmog.paramOrNull(params.attack_anim)
    }

    /** Cast phase only: animation + cast gfx + cast sound. For self/aura abilities. */
    public fun playCast(caster: Player, visual: AbilityVisual) {
        castSeqFor(caster, visual)?.let(caster::anim)
        visual.castSpot?.let {
            caster.spotanim(
                it,
                delay = 0,
                height = visual.castSpotHeight,
                slot = constants.spotanim_slot_combat,
            )
        }
        visual.castSound?.let {
            manager.soundArea(
                source = caster.coords,
                synth = it,
                delay = 0,
                loops = 1,
                radius = visual.soundRadius,
                size = 0,
            )
        }
    }

    /**
     * Full cast -> projectile -> impact presentation on [target].
     *
     * @return the client delay (20ms cycles) at which the impact lands - `0` when there is no
     *   projectile. Queue gameplay hits with `hitDelay = 1 + clientDelay / 30` to keep damage in
     *   sync with the impact gfx.
     */
    public fun playAtTarget(caster: Player, target: PathingEntity, visual: AbilityVisual): Int {
        playCast(caster, visual)

        var clientDelay = 0
        val travel = visual.travelSpot
        val projanim = visual.projanim
        if (travel != null && projanim != null) {
            clientDelay =
                manager.spawnProjectile(caster, target, travel, projanim).durations.clientDelay
        }

        visual.impactSpot?.let {
            target.spotanim(
                it,
                delay = clientDelay,
                height = visual.impactSpotHeight,
                slot = constants.spotanim_slot_combat,
            )
        }
        visual.hitSound?.let {
            manager.soundArea(
                source = target.coords,
                synth = it,
                delay = clientDelay,
                loops = 1,
                radius = visual.soundRadius,
                size = 0,
            )
        }
        for (extra in visual.extraImpacts) {
            extra.spot?.let {
                target.spotanim(
                    it,
                    delay = clientDelay + extra.delayCycles,
                    height = extra.spotHeight,
                    slot = constants.spotanim_slot_combat,
                )
            }
            extra.sound?.let {
                manager.soundArea(
                    source = target.coords,
                    synth = it,
                    delay = clientDelay + extra.delayCycles,
                    loops = 1,
                    radius = visual.soundRadius,
                    size = 0,
                )
            }
        }
        return clientDelay
    }

    /**
     * Same presentation on a self/ally target that is a player (owner or another companion bot) -
     * identical code path, kept as a named entry point for readability at call sites.
     */
    public fun playOnAlly(caster: Player, target: Player, visual: AbilityVisual): Int =
        playAtTarget(caster, target, visual)

    /**
     * AoE presentation: the cast plays once on [caster], then every [target] gets the projectile /
     * impact beat. The server supplies the authoritative target list, so visual coverage can never
     * disagree with the real radius (§63).
     */
    public fun playAtTargets(
        caster: Player,
        targets: Collection<PathingEntity>,
        visual: AbilityVisual,
    ) {
        playCast(caster, visual)
        for (target in targets) {
            if (!target.isSlotAssigned) continue
            var clientDelay = 0
            val travel = visual.travelSpot
            val projanim = visual.projanim
            if (travel != null && projanim != null) {
                clientDelay =
                    manager.spawnProjectile(caster, target, travel, projanim).durations.clientDelay
            }
            visual.impactSpot?.let {
                target.spotanim(
                    it,
                    delay = clientDelay,
                    height = visual.impactSpotHeight,
                    slot = constants.spotanim_slot_combat,
                )
            }
        }
        visual.hitSound?.let {
            manager.soundArea(
                source = caster.coords,
                synth = it,
                delay = 30,
                loops = 1,
                radius = visual.soundRadius,
                size = 0,
            )
        }
    }
}
