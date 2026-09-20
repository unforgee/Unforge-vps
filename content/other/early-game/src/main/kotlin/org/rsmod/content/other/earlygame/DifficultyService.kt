package org.rsmod.content.other.earlygame

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.game.difficulty.Difficulty
import org.rsmod.game.entity.Player

/**
 * Server-authoritative writer for the first-login difficulty tier.
 *
 * Selection writes [Player.difficulty]/[Player.difficultySelected] and copies [Difficulty.xpRate]
 * into [Player.xpRate] (persisted through `xp_rate_in_hundreds`), then queues an atomic character
 * save so the choice cannot be re-made. The returned future completes on the account writer thread;
 * coroutine callers should poll [CompletableFuture.isDone] with `delay(1)`.
 */
@Singleton
public class DifficultyService @Inject constructor(private val accountManager: AccountManager) {
    private val pendingSaves = ConcurrentHashMap<Player, CompletableFuture<Boolean>>()

    /**
     * Applies the first-login difficulty selection to [player] and queues the save. Returns `null`
     * when the account already selected a tier (relog or crafted client input cannot re-select).
     */
    public fun selectDifficulty(player: Player, tier: Difficulty): CompletableFuture<Boolean>? {
        if (player.difficultySelected) {
            logger.warn {
                "Rejected duplicate difficulty selection for player=$player " +
                    "(existing=${player.difficulty}, attempted=$tier)."
            }
            return null
        }
        player.difficulty = tier
        player.difficultySelected = true
        player.difficultySelectedAt = LocalDateTime.now()
        player.xpRate = tier.xpRate
        logger.info {
            "Difficulty selected: player=${player.username} tier=${tier.dbName} " +
                "xpRate=${tier.xpRate} dmgBps=${tier.damageBoostBps} dropBps=${tier.dropBoostBps}"
        }
        return persist(player)
    }

    /** Queues an atomic save of [player]'s character row. */
    public fun persist(player: Player): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        pendingSaves[player] = future
        accountManager.save(player) { response ->
            when (response) {
                is AccountSaveResponse.Success -> future.complete(true)
                else -> {
                    logger.error {
                        "Could not persist difficulty for player=${player.username} " +
                            "characterId=${player.characterId}: response=$response"
                    }
                    future.complete(false)
                }
            }
            pendingSaves.remove(player, future)
        }
        return future
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
