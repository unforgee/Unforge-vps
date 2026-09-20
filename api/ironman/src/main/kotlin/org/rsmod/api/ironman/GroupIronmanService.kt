package org.rsmod.api.ironman

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.config.refs.invs
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.util.setNullableString
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.Inventory
import org.rsmod.game.ironman.GameMode
import org.rsmod.game.ironman.GroupRank
import org.rsmod.game.ironman.groupObserverId
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.util.UncheckedType

/**
 * Server-side Group Ironman membership and shared-storage service.
 *
 * Group rows live in `ironman_groups`; membership lives on the `characters` row
 * (`group_id`/`group_rank`). Storage contents are rows in `ironman_group_storage` and every
 * mutation is recorded in `ironman_group_audit`. Structural changes (create/join/leave/kick) go
 * through [GameDbManager] on the db thread; the shared storage inventory is kept in memory per
 * group and flushed inside the character save transaction by [IronmanDataPipeline], so a member's
 * periodic/logout save persists storage + audit rows atomically with their own state.
 */
@OptIn(UncheckedType::class)
@Singleton
public class GroupIronmanService
@Inject
constructor(
    private val invTypes: InvTypeList,
    private val objTypes: ObjTypeList,
    private val playerList: PlayerList,
    private val dbManager: GameDbManager,
    private val gameModeService: Provider<GameModeService>,
) {
    private class GroupState(val id: Int, val storage: Inventory) {
        val pendingAudit = mutableListOf<AuditEntry>()
        var loaded = false
        var dirty = false
    }

    private class AuditEntry(
        val characterId: Int,
        val action: String,
        val obj: Int?,
        val count: Int?,
        val detail: String?,
    )

    private class Invite(val groupId: Int, val fromCharacterId: Int)

    private class StorageRow(val slot: Int, val obj: Int, val count: Int, val vars: Int)

    private val groups = ConcurrentHashMap<Int, GroupState>()
    private val invites = ConcurrentHashMap<Int, Invite>()

    public fun isMember(player: Player): Boolean =
        player.gameMode == GameMode.GROUP_IRONMAN && player.groupId != null

    // --- Membership -------------------------------------------------------------

    /**
     * Creates a new group with [player] as leader. Only Group Ironman accounts without a group may
     * create one. The result is delivered asynchronously; [onDone] runs on the game thread.
     */
    public fun createGroup(player: Player, name: String, onDone: (String) -> Unit) {
        if (player.gameMode != GameMode.GROUP_IRONMAN) {
            onDone("Only Group Ironman accounts can create a group.")
            return
        }
        if (player.groupId != null) {
            onDone("You are already in a group.")
            return
        }
        dbManager.request(
            request = { connection ->
                val groupId =
                    connection
                        .prepareStatement(
                            "INSERT INTO ironman_groups (realm_id, name, leader_character_id) " +
                                "VALUES ((SELECT realm_id FROM characters WHERE id = ?), ?, ?)",
                            java.sql.Statement.RETURN_GENERATED_KEYS,
                        )
                        .use {
                            it.setInt(1, player.characterId)
                            it.setNullableString(2, name.ifBlank { null })
                            it.setInt(3, player.characterId)
                            it.executeUpdate()
                            val keys = it.generatedKeys
                            if (keys.next()) keys.getInt(1) else -1
                        }
                if (groupId > 0) {
                    connection
                        .prepareStatement(
                            "UPDATE characters SET group_id = ?, group_rank = ?, " +
                                "group_joined_at = ?, group_settings_version = ? WHERE id = ?"
                        )
                        .use {
                            it.setInt(1, groupId)
                            it.setString(2, GroupRank.LEADER.dbName)
                            it.setString(3, LocalDateTime.now().toString())
                            it.setInt(4, 1)
                            it.setInt(5, player.characterId)
                            it.executeUpdate()
                        }
                    insertAudit(
                        connection,
                        groupId,
                        player.characterId,
                        "GROUP_CREATE",
                        null,
                        null,
                        name.ifBlank { null },
                    )
                }
                GameDbResult.Ok(groupId)
            },
            response = { result ->
                val groupId = if (result is GameDbResult.Ok) result.value else -1
                if (groupId <= 0) {
                    onDone("Could not create the group. Please try again.")
                    return@request
                }
                joinLocal(player, groupId, GroupRank.LEADER)
                onDone("Group #$groupId created. You are the leader.")
            },
        )
    }

    /** Invites [target] (must be an online Group Ironman without a group) into [player]'s group. */
    public fun invite(player: Player, target: Player, onDone: (String) -> Unit) {
        val groupId = player.groupId
        if (!isMember(player) || groupId == null || player.groupRank != GroupRank.LEADER) {
            onDone("Only a group leader can invite new members.")
            return
        }
        if (target.gameMode != GameMode.GROUP_IRONMAN || target.groupId != null) {
            onDone("${target.username} cannot join your group.")
            return
        }
        countMembers(groupId) { members ->
            if (members >= MAX_GROUP_SIZE) {
                onDone("Your group is full. (max $MAX_GROUP_SIZE members)")
                return@countMembers
            }
            invites[target.characterId] = Invite(groupId, player.characterId)
            target.mes(
                "${player.username} has invited you to join their ironman group. " +
                    "Use ::groupaccept to join."
            )
            onDone("Invitation sent to ${target.username}.")
        }
    }

    /** Accepts a pending group invitation. */
    public fun acceptInvite(player: Player, onDone: (String) -> Unit) {
        if (player.gameMode != GameMode.GROUP_IRONMAN || player.groupId != null) {
            onDone("You cannot join a group.")
            return
        }
        val invite = invites[player.characterId]
        if (invite == null) {
            onDone("You have no pending group invitation.")
            return
        }
        countMembers(invite.groupId) { members ->
            if (members >= MAX_GROUP_SIZE) {
                invites.remove(player.characterId)
                onDone("That group is now full.")
                return@countMembers
            }
            dbManager.request(
                request = { connection ->
                    val updated =
                        connection
                            .prepareStatement(
                                "UPDATE characters SET group_id = ?, group_rank = ?, " +
                                    "group_joined_at = ?, group_settings_version = ? WHERE id = ?"
                            )
                            .use {
                                it.setInt(1, invite.groupId)
                                it.setString(2, GroupRank.MEMBER.dbName)
                                it.setString(3, LocalDateTime.now().toString())
                                it.setInt(4, 1)
                                it.setInt(5, player.characterId)
                                it.executeUpdate()
                            }
                    if (updated > 0) {
                        insertAudit(
                            connection,
                            invite.groupId,
                            player.characterId,
                            "GROUP_JOIN",
                            null,
                            null,
                            null,
                        )
                    }
                    GameDbResult.Ok(updated > 0)
                },
                response = { result ->
                    if (result is GameDbResult.Ok && result.value) {
                        invites.remove(player.characterId)
                        joinLocal(player, invite.groupId, GroupRank.MEMBER)
                        onDone("You have joined group #${invite.groupId}.")
                    } else {
                        onDone("Could not join the group. Please try again.")
                    }
                },
            )
        }
    }

    /** Removes [player] from their group. A leaving leader dissolves the whole group. */
    public fun leaveGroup(player: Player, onDone: (String) -> Unit) {
        val groupId = player.groupId
        if (groupId == null) {
            onDone("You are not in a group.")
            return
        }
        if (player.groupRank == GroupRank.LEADER) {
            dissolveGroup(player, groupId, onDone)
            return
        }
        removeMember(player, groupId, "GROUP_LEAVE") { ok ->
            onDone(if (ok) "You have left your group." else "Could not leave the group.")
        }
    }

    /** Leader-only: removes [target] from [player]'s group. */
    public fun kickMember(player: Player, target: Player, onDone: (String) -> Unit) {
        val groupId = player.groupId
        if (groupId == null || player.groupRank != GroupRank.LEADER) {
            onDone("Only a group leader can remove members.")
            return
        }
        if (target.groupId != groupId || target == player) {
            onDone("${target.username} is not a member of your group.")
            return
        }
        removeMember(target, groupId, "GROUP_KICK") { ok ->
            if (ok) {
                target.mes("You have been removed from your ironman group.")
            }
            onDone(
                if (ok) "Removed ${target.username} from the group." else "Could not remove member."
            )
        }
    }

    // --- Shared storage -----------------------------------------------------------

    /**
     * Deposits [count] of the obj in `player.inv[slot]` into the group's shared storage. The
     * mutation is auditable and is flushed to `ironman_group_storage` inside the next character
     * save transaction (also triggered immediately via [GameModeService.persist]).
     */
    public fun deposit(player: Player, invSlot: Int, count: Int, onDone: (String) -> Unit) {
        val state = storageFor(player) ?: return onDone("You are not in a group.")
        val obj = player.inv[invSlot] ?: return onDone("There is nothing in that slot.")
        withStorageLoaded(player, state) {
            val capped = minOf(count, obj.count)
            val result =
                player.invTransfer(
                    from = player.inv,
                    fromSlot = invSlot,
                    count = capped,
                    into = state.storage,
                )
            if (result.failure) {
                onDone("The group storage is full.")
                return@withStorageLoaded
            }
            audit(state, player.characterId, "STORAGE_DEPOSIT", obj.id, capped, null)
            markDirtyAndPersist(player, state)
            onDone("Deposited ${capped}x ${objTypes[obj].name} into the group storage.")
        }
    }

    /** Withdraws [count] of the obj in group storage slot [slot] into the player's inventory. */
    public fun withdraw(player: Player, slot: Int, count: Int, onDone: (String) -> Unit) {
        val state = storageFor(player) ?: return onDone("You are not in a group.")
        withStorageLoaded(player, state) {
            val obj = state.storage[slot] ?: return@withStorageLoaded onDone("Empty slot.")
            val capped = minOf(count, obj.count)
            val result =
                player.invTransfer(
                    from = state.storage,
                    fromSlot = slot,
                    count = capped,
                    into = player.inv,
                )
            if (result.failure) {
                onDone("You do not have enough inventory space.")
                return@withStorageLoaded
            }
            audit(state, player.characterId, "STORAGE_WITHDRAW", obj.id, capped, null)
            markDirtyAndPersist(player, state)
            onDone("Withdrew ${capped}x ${objTypes[obj].name} from the group storage.")
        }
    }

    /** Sends a chatbox listing of the group's shared storage contents. */
    public fun listStorage(player: Player) {
        val state = storageFor(player) ?: return player.mes("You are not in a group.")
        withStorageLoaded(player, state) {
            val entries =
                state.storage.indices
                    .mapNotNull { state.storage[it] }
                    .joinToString { "${objTypes[it].name} x${it.count}" }
            player.mes(
                if (entries.isEmpty()) "Group storage is empty." else "Group storage: $entries"
            )
        }
    }

    // --- Persistence (called by IronmanDataPipeline) --------------------------------

    /** Flushes dirty group storage + audit rows inside the member's save transaction. */
    public fun savePlayerGroupData(
        connection: DatabaseConnection,
        player: Player,
        characterId: Int,
    ) {
        val groupId = player.groupId ?: return
        val state = groups[groupId] ?: return
        synchronized(state) {
            if (state.dirty) {
                connection
                    .prepareStatement("DELETE FROM ironman_group_storage WHERE group_id = ?")
                    .use {
                        it.setInt(1, groupId)
                        it.executeUpdate()
                    }
                connection
                    .prepareStatement(
                        "INSERT INTO ironman_group_storage (group_id, slot, obj, count, vars) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    )
                    .use { stmt ->
                        for (slot in state.storage.indices) {
                            val obj = state.storage[slot] ?: continue
                            stmt.setInt(1, groupId)
                            stmt.setInt(2, slot)
                            stmt.setInt(3, obj.id)
                            stmt.setInt(4, obj.count)
                            stmt.setInt(5, obj.vars)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                state.dirty = false
            }
            if (state.pendingAudit.isNotEmpty()) {
                connection
                    .prepareStatement(
                        "INSERT INTO ironman_group_audit " +
                            "(group_id, character_id, action, obj, count, detail) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
                    )
                    .use { stmt ->
                        for (entry in state.pendingAudit) {
                            stmt.setInt(1, groupId)
                            stmt.setInt(2, entry.characterId)
                            stmt.setString(3, entry.action)
                            if (entry.obj != null) stmt.setInt(4, entry.obj)
                            else stmt.setNull(4, java.sql.Types.INTEGER)
                            if (entry.count != null) stmt.setInt(5, entry.count)
                            else stmt.setNull(5, java.sql.Types.INTEGER)
                            stmt.setNullableString(6, entry.detail)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                state.pendingAudit.clear()
            }
        }
    }

    // --- Internals ----------------------------------------------------------------

    private fun joinLocal(player: Player, groupId: Int, rank: GroupRank) {
        player.groupId = groupId
        player.groupRank = rank
        player.groupJoinedAt = LocalDateTime.now()
        player.groupSettingsVersion = 1
        player.observerUUID = groupObserverId(groupId)
    }

    private fun clearLocal(player: Player) {
        player.groupId = null
        player.groupRank = null
        player.groupJoinedAt = null
        player.groupSettingsVersion = 0
        player.observerUUID = player.uuid
    }

    private fun removeMember(
        member: Player,
        groupId: Int,
        action: String,
        onDone: (Boolean) -> Unit,
    ) {
        dbManager.request(
            request = { connection ->
                val updated =
                    connection
                        .prepareStatement(
                            "UPDATE characters SET group_id = NULL, group_rank = NULL, " +
                                "group_joined_at = NULL, group_settings_version = 0 WHERE id = ?"
                        )
                        .use {
                            it.setInt(1, member.characterId)
                            it.executeUpdate()
                        }
                if (updated > 0) {
                    insertAudit(connection, groupId, member.characterId, action, null, null, null)
                }
                GameDbResult.Ok(updated > 0)
            },
            response = { result ->
                val ok = result is GameDbResult.Ok && result.value
                if (ok) {
                    clearLocal(member)
                    gameModeService.get().persist(member)
                }
                onDone(ok)
            },
        )
    }

    private fun dissolveGroup(leader: Player, groupId: Int, onDone: (String) -> Unit) {
        dbManager.request(
            request = { connection ->
                connection
                    .prepareStatement(
                        "UPDATE characters SET group_id = NULL, group_rank = NULL, " +
                            "group_joined_at = NULL, group_settings_version = 0 WHERE group_id = ?"
                    )
                    .use {
                        it.setInt(1, groupId)
                        it.executeUpdate()
                    }
                connection
                    .prepareStatement("DELETE FROM ironman_group_storage WHERE group_id = ?")
                    .use {
                        it.setInt(1, groupId)
                        it.executeUpdate()
                    }
                insertAudit(
                    connection,
                    groupId,
                    leader.characterId,
                    "GROUP_DISSOLVE",
                    null,
                    null,
                    null,
                )
                connection.prepareStatement("DELETE FROM ironman_groups WHERE id = ?").use {
                    it.setInt(1, groupId)
                    it.executeUpdate()
                }
                GameDbResult.Ok(true)
            },
            response = { result ->
                if (result !is GameDbResult.Ok) {
                    onDone("Could not dissolve the group.")
                    return@request
                }
                groups.remove(groupId)
                for (other in playerList) {
                    if (other.groupId == groupId) {
                        clearLocal(other)
                        other.mes("Your ironman group has been disbanded.")
                    }
                }
                gameModeService.get().persist(leader)
                onDone("Your group has been disbanded.")
            },
        )
    }

    private fun countMembers(groupId: Int, onDone: (Int) -> Unit) {
        dbManager.request(
            request = { connection ->
                val count =
                    connection
                        .prepareStatement("SELECT COUNT(*) FROM characters WHERE group_id = ?")
                        .use {
                            it.setInt(1, groupId)
                            val rs = it.executeQuery()
                            if (rs.next()) rs.getInt(1) else 0
                        }
                GameDbResult.Ok(count)
            },
            response = { result ->
                onDone(if (result is GameDbResult.Ok) result.value else MAX_GROUP_SIZE)
            },
        )
    }

    private fun storageFor(player: Player): GroupState? {
        val groupId = player.groupId ?: return null
        if (player.gameMode != GameMode.GROUP_IRONMAN) {
            return null
        }
        return groups.getOrPut(groupId) { GroupState(groupId, newStorageInv()) }
    }

    private fun withStorageLoaded(player: Player, state: GroupState, action: () -> Unit) {
        if (state.loaded) {
            action()
            return
        }
        // Not yet loaded from disk (first access before any member relog loaded it): fetch lazily.
        dbManager.request(
            request = { connection ->
                connection
                    .prepareStatement(
                        "SELECT slot, obj, count, vars FROM ironman_group_storage WHERE group_id = ?"
                    )
                    .use {
                        it.setInt(1, state.id)
                        val rs = it.executeQuery()
                        val rows = mutableListOf<StorageRow>()
                        while (rs.next()) {
                            rows +=
                                StorageRow(
                                    rs.getInt("slot"),
                                    rs.getInt("obj"),
                                    rs.getInt("count"),
                                    rs.getInt("vars"),
                                )
                        }
                        GameDbResult.Ok(rows)
                    }
            },
            response = { result ->
                if (result !is GameDbResult.Ok) {
                    player.mes("Could not open the group storage. Please try again.")
                    return@request
                }
                synchronized(state) {
                    for (row in result.value) {
                        if (row.slot in state.storage.indices) {
                            state.storage[row.slot] =
                                org.rsmod.game.inv.InvObj(row.obj, row.count, row.vars)
                        }
                    }
                    state.loaded = true
                }
                action()
            },
        )
    }

    private fun markDirtyAndPersist(player: Player, state: GroupState) {
        synchronized(state) { state.dirty = true }
        gameModeService.get().persist(player)
    }

    private fun audit(
        state: GroupState,
        characterId: Int,
        action: String,
        obj: Int?,
        count: Int?,
        detail: String?,
    ) {
        synchronized(state) {
            state.pendingAudit += AuditEntry(characterId, action, obj, count, detail)
        }
    }

    private fun insertAudit(
        connection: DatabaseConnection,
        groupId: Int,
        characterId: Int,
        action: String,
        obj: Int?,
        count: Int?,
        detail: String?,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO ironman_group_audit " +
                    "(group_id, character_id, action, obj, count, detail) VALUES (?, ?, ?, ?, ?, ?)"
            )
            .use {
                it.setInt(1, groupId)
                it.setInt(2, characterId)
                it.setString(3, action)
                if (obj != null) it.setInt(4, obj) else it.setNull(4, java.sql.Types.INTEGER)
                if (count != null) it.setInt(5, count) else it.setNull(5, java.sql.Types.INTEGER)
                it.setNullableString(6, detail)
                it.executeUpdate()
            }
    }

    private fun newStorageInv(): Inventory = Inventory.create(invTypes[invs.bank])

    public companion object {
        public const val MAX_GROUP_SIZE: Int = 5
        private val logger = InlineLogger()
    }
}
