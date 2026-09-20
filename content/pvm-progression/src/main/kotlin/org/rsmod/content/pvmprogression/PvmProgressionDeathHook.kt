package org.rsmod.content.pvmprogression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.pvmprogression.challenge.CombatChallengeManager
import org.rsmod.content.pvmprogression.encounter.AbandonReason
import org.rsmod.content.pvmprogression.encounter.BossEncounterTracker
import org.rsmod.content.pvmprogression.streak.PvMStreakManager

/**
 * PlayerDeathHook for PvM Progression 2.0.
 *
 * When a player dies while inside a boss encounter, the encounter is abandoned as PLAYER_DIED
 * (which fails any active challenge) and the streak is reset. The hook never consumes the death -
 * it returns `false` so the standard death sequence still runs (respawn, item loss, etc.).
 *
 * Registered as a Guice set binding by [PvmProgressionModule].
 */
@Singleton
class PvmProgressionDeathHook
@Inject
constructor(
    private val tracker: BossEncounterTracker,
    private val streak: PvMStreakManager,
    private val challenges: CombatChallengeManager,
) : PlayerDeathHook {
    override suspend fun death(access: ProtectedAccess): Boolean {
        val player = access.player
        val encounter = tracker.current(player) ?: return false
        if (encounter.endReason == null) {
            val abandoned = tracker.abandon(player, AbandonReason.PLAYER_DIED)
            if (abandoned != null) {
                challenges.onBossKill(abandoned)
            }
        }
        streak.onBossDeath(player)
        return false
    }
}
