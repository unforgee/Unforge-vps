package org.rsmod.content.pvmprogression.encounter

/** Why an encounter ended. Used by the challenge manager to distinguish success from failure. */
enum class AbandonReason {
    BOSS_KILLED,
    PLAYER_DIED,
    TELEPORTED,
    IDLE,
    TIMEOUT,
    LOGOUT,
    INSTANCE_DESTROYED,
    SWITCHED_TARGET,
}
