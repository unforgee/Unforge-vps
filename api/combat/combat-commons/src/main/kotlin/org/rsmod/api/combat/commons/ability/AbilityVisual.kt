package org.rsmod.api.combat.commons.ability

import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

/**
 * Where an ability presentation comes from, so audits can tell authentic recreations apart from
 * cache-based custom work.
 */
public enum class AbilityVisualOrigin {
    /** Faithful reproduction of an original OSRS action (same spell/spec/npc attack assets). */
    ORIGINAL,

    /**
     * Real cache assets taken from a related source (e.g. a weapon special used as a companion
     * ability).
     */
    ADAPTED,

    /** A custom combination of existing cache assets - never claimed to be an original ability. */
    CUSTOM,
}

/**
 * An additional impact beat in a multi-hit presentation, played on the target [delayCycles] client
 * cycles (20ms each) after the main impact.
 */
public data class AbilityVisualImpact(
    public val spot: SpotanimType? = null,
    public val spotHeight: Int = 124,
    public val sound: SynthType? = null,
    public val delayCycles: Int = 0,
)

/**
 * The presentation half of an ability: animations, spotanims, projectile and sounds plus the timing
 * metadata needed to keep them in sync with the server-authoritative result.
 *
 * Companion bots are `Player` entities, so the same definition drives both player abilities and
 * companion abilities through the same playback code - `PLAYER -> AbilityVisual <- COMPANION`.
 *
 * Animation compatibility: [castSeq] plays on the caster's normal (player-skeleton) model. When the
 * caster is rendered as an npc (companion `transmog`), playback prefers [npcSeq] and falls back to
 * the transmog npc's own `attack_anim` param, so non-humanoid companions never deform under a
 * player animation.
 *
 * Timing: when [travelSpot] + [projanim] are set, the projectile's metadata provides the
 * client-side landing delay; [impactSpot]/[hitSound] are scheduled at exactly that delay so the
 * visual impact and any hit queued with `hitDelay = 1 + clientDelay / 30` land together.
 */
public data class AbilityVisual(
    public val castSeq: SeqType? = null,
    public val npcSeq: SeqType? = null,
    public val castSpot: SpotanimType? = null,
    public val castSpotHeight: Int = 92,
    public val travelSpot: SpotanimType? = null,
    public val projanim: ProjAnimType? = null,
    public val impactSpot: SpotanimType? = null,
    public val impactSpotHeight: Int = 124,
    public val castSound: SynthType? = null,
    public val hitSound: SynthType? = null,
    public val soundRadius: Int = 8,
    public val extraImpacts: List<AbilityVisualImpact> = emptyList(),
    public val origin: AbilityVisualOrigin,
) {
    init {
        require(castSpotHeight in 0..255 && impactSpotHeight in 0..255)
        require(soundRadius in 0..32)
        require((travelSpot == null) == (projanim == null)) {
            "Projectile visuals need both a travel spotanim (model) and a projanim (trajectory)."
        }
        require(extraImpacts.none { it.delayCycles < 0 })
    }

    /** `true` when the definition carries a real projectile flight. */
    public val hasProjectile: Boolean
        get() = travelSpot != null && projanim != null
}
