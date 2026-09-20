package org.rsmod.api.ironman

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.util.setNullableString
import org.rsmod.game.entity.Player
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.HardcoreStatus

/**
 * Server-authoritative writer for all game mode state changes.
 *
 * Every mutation of [Player.gameMode]/[Player.hardcoreStatus]/group fields goes through this
 * service, and every mutation is followed by a save request so the change is persisted before the
 * player is allowed to continue. The returned [CompletableFuture] completes on the account writer
 * thread; coroutine callers should poll [CompletableFuture.isDone] with `delay(1)`.
 */
@Singleton
public class GameModeService
@Inject
constructor(private val accountManager: AccountManager, private val dbManager: GameDbManager) {
    private val pendingSaves = ConcurrentHashMap<Player, CompletableFuture<Boolean>>()

    /**
     * Applies the first-login game mode selection to [player] and queues an atomic save of the
     * character row. Returns `null` when the account has already selected a mode (relog or crafted
     * client input cannot re-select).
     */
    public fun selectGameMode(player: Player, mode: GameMode): CompletableFuture<Boolean>? {
        if (player.gameModeSelected) {
            logger.warn {
                "Rejected duplicate game mode selection for player=$player " +
                    "(existing=${player.gameMode}, attempted=$mode)."
            }
            return null
        }
        applyMode(player, mode)
        player.gameModeSelectedAt = LocalDateTime.now()
        return persist(player)
    }

    /**
     * Protected admin path for changing an already-selected game mode. Applies the change
     * in-memory, writes a `game_mode_audit` row, and persists the character. The target must be
     * online; offline changes are intentionally not supported (documented limitation).
     */
    public fun adminChangeGameMode(
        admin: Player,
        target: Player,
        mode: GameMode,
        reason: String,
    ): CompletableFuture<Boolean> {
        val oldMode = target.gameMode
        applyMode(target, mode)
        auditGameModeChange(
            characterId = target.characterId,
            actorCharacterId = admin.characterId,
            oldMode = oldMode,
            newMode = mode,
            reason = reason,
        )
        logger.info {
            "Admin game mode change: admin=${admin.username} target=${target.username} " +
                "old=$oldMode new=$mode reason=$reason"
        }
        return persist(target)
    }

    /**
     * Consumes the hardcore status of a freshly-killed hardcore account. Returns the save future
     * when a demotion was applied, `null` when the account is not an active hardcore.
     */
    public fun demoteHardcore(player: Player): CompletableFuture<Boolean>? {
        val mode = player.gameMode
        if (mode == null || !mode.isHardcore || player.hardcoreStatus != HardcoreStatus.ACTIVE) {
            return null
        }
        player.hardcoreStatus = HardcoreStatus.DEMOTED
        player.hardcoreDeathCount += 1
        player.gameMode = mode.demotedMode
        auditGameModeChange(
            characterId = player.characterId,
            actorCharacterId = null,
            oldMode = mode,
            newMode = mode.demotedMode,
            reason = "hardcore_death",
        )
        return persist(player)
    }

    /** Queues an atomic save of [player]'s character row. Safe to call for any mutation. */
    public fun persist(player: Player): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        pendingSaves[player] = future
        accountManager.save(player) { response ->
            when (response) {
                is AccountSaveResponse.Success -> future.complete(true)
                else -> {
                    logger.error {
                        "Could not persist game mode for player=${player.username} " +
                            "characterId=${player.characterId}: response=$response"
                    }
                    future.complete(false)
                }
            }
            pendingSaves.remove(player, future)
        }
        return future
    }

    /** The most recent save future queued for [player] through this service, if any. */
    public fun pendingSave(player: Player): CompletableFuture<Boolean>? = pendingSaves[player]

    private fun applyMode(player: Player, mode: GameMode) {
        player.gameMode = mode
        player.gameModeSelected = true
        player.hardcoreStatus =
            if (mode.isHardcore) HardcoreStatus.ACTIVE else HardcoreStatus.DISABLED
        if (!mode.isGroup) {
            player.groupId = null
            player.groupRank = null
            player.groupJoinedAt = null
            player.groupSettingsVersion = 0
            player.observerUUID = player.uuid
        }
    }

    private fun auditGameModeChange(
        characterId: Int,
        actorCharacterId: Int?,
        oldMode: GameMode?,
        newMode: GameMode,
        reason: String,
    ) {
        dbManager.request(
            request = { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO game_mode_audit " +
                            "(character_id, actor_character_id, old_mode, new_mode, reason) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    )
                    .use {
                        it.setInt(1, characterId)
                        if (actorCharacterId != null) {
                            it.setInt(2, actorCharacterId)
                        } else {
                            it.setNull(2, java.sql.Types.INTEGER)
                        }
                        it.setNullableString(3, oldMode?.dbName)
                        it.setString(4, newMode.dbName)
                        it.setString(5, reason)
                        it.executeUpdate()
                    }
                GameDbResult.Ok(true)
            },
            response = { result ->
                if (result !is GameDbResult.Ok) {
                    logger.error { "Failed to write game_mode_audit row: $result" }
                }
            },
        )
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
