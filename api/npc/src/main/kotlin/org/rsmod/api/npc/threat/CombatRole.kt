package org.rsmod.api.npc.threat

import org.rsmod.game.entity.Player

/**
 * PvM party roles. A role determines **behavior** (who holds aggro, who heals), never the combat
 * style - melee, ranged and magic variants of every role are equally valid.
 */
public enum class CombatRole {
    TANK,
    SUPPORT,
    DAMAGE,
    HYBRID,
}

/** How an npc treats its threat table. */
public enum class ThreatMode {
    /** Threat is tracked and the npc retargets to the highest valid threat holder. */
    NORMAL,

    /** Threat is not tracked at all; the npc behaves exactly like legacy combat. */
    DISABLED,

    /**
     * Threat is tracked (for visibility/debugging) but never changes the npc's target on its own.
     * Boss scripts may still push temporary targets through [ThreatService.scriptedTarget].
     */
    SCRIPTED,
}

/** The kind of action that generated threat, for debug output. */
public enum class ThreatSource {
    DAMAGE,
    HEAL,
    SHIELD,
    TAUNT,
    SCRIPTED,
}

/**
 * Resolves the [CombatRole] of a player entity. Implemented by content modules that own the role
 * knowledge (e.g. companions map their bot entities to `CompanionClass`); registered through
 * [ThreatService.registerRoleResolver]. Return `null` when the resolver does not know the player.
 */
public fun interface ThreatRoleResolver {
    public fun roleOf(player: Player): CombatRole?
}
