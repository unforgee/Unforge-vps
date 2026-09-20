package org.rsmod.api.companion

import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.game.entity.Player

public interface CompanionPlayerRegistry {
    public fun getPlayer(companionId: Long): Player?

    public fun getCompanionId(player: Player): Long?

    public fun getOwnerCharacterId(companionId: Long): Long?

    /** Returns the current runtime damage multiplier for a virtual companion player. */
    public fun getDamageMultiplier(player: Player): Double

    /** Updates the transient multiplier used by the shared player-vs-NPC combat pipeline. */
    public fun setDamageMultiplier(companionId: Long, multiplier: Double)

    public fun register(companionId: Long, ownerCharacterId: Long, player: Player)

    public fun unregister(companionId: Long): Player?

    public fun isCompanionPlayer(player: Player): Boolean

    public fun areAllied(playerA: Player, playerB: Player): Boolean
}

@Singleton
public class DefaultCompanionPlayerRegistry : CompanionPlayerRegistry {
    private val companionToPlayer = ConcurrentHashMap<Long, Player>()
    private val playerToCompanion = ConcurrentHashMap<Player, Long>()
    private val companionToOwner = ConcurrentHashMap<Long, Long>()
    private val companionDamageMultipliers = ConcurrentHashMap<Long, Double>()

    override fun getPlayer(companionId: Long): Player? = companionToPlayer[companionId]

    override fun getCompanionId(player: Player): Long? = playerToCompanion[player]

    override fun getOwnerCharacterId(companionId: Long): Long? = companionToOwner[companionId]

    override fun getDamageMultiplier(player: Player): Double =
        getCompanionId(player)?.let(companionDamageMultipliers::get) ?: 1.0

    override fun setDamageMultiplier(companionId: Long, multiplier: Double) {
        companionDamageMultipliers[companionId] = multiplier.coerceAtLeast(0.0)
    }

    override fun register(companionId: Long, ownerCharacterId: Long, player: Player) {
        companionToPlayer[companionId] = player
        playerToCompanion[player] = companionId
        companionToOwner[companionId] = ownerCharacterId
        companionDamageMultipliers.putIfAbsent(companionId, 1.0)
    }

    override fun unregister(companionId: Long): Player? {
        val player = companionToPlayer.remove(companionId)
        if (player != null) {
            playerToCompanion.remove(player)
        }
        companionToOwner.remove(companionId)
        companionDamageMultipliers.remove(companionId)
        return player
    }

    override fun isCompanionPlayer(player: Player): Boolean = playerToCompanion.containsKey(player)

    override fun areAllied(playerA: Player, playerB: Player): Boolean {
        if (playerA == playerB) return true
        val companionIdA = getCompanionId(playerA)
        val companionIdB = getCompanionId(playerB)
        if (
            companionIdA != null &&
                getOwnerCharacterId(companionIdA) == playerB.characterId.toLong()
        ) {
            return true
        }
        if (
            companionIdB != null &&
                getOwnerCharacterId(companionIdB) == playerA.characterId.toLong()
        ) {
            return true
        }
        if (
            companionIdA != null &&
                companionIdB != null &&
                getOwnerCharacterId(companionIdA) == getOwnerCharacterId(companionIdB)
        ) {
            return true
        }
        return false
    }
}
