package org.rsmod.api.talents

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Server-authoritative company (clan/team) talent-tree service.
 *
 * Membership lives in [memberships] keyed by `character_id` and mirrors the `company_members`
 * table: rows are written immediately on the db thread for structural changes
 * (create/join/leave/kick/dissolve), and the character-data pipeline restores the map at login. The
 * shared tree state ([companies]) is the authoritative in-memory copy of
 * `companies.talent_points` + `company_talents`; rank/point mutations mark the state dirty and the
 * next member's character save flushes them inside that member's transaction - the same contract
 * `GroupIronmanService` uses for shared storage.
 *
 * Effect scope is explicit: [effectBps] returns `0` the moment a character has no membership row,
 * so removed members lose company bonuses and new members gain exactly what the tree currently
 * grants - nothing leaks into the player tree (it reads `talent_rk_*` varps only).
 */
@Singleton
public class CompanyService
@Inject
constructor(private val playerList: PlayerList, private val dbManager: GameDbManager) {
    private val memberships = ConcurrentHashMap<Int, CompanyMembership>()
    private val companies = ConcurrentHashMap<Int, CompanyState>()
    private val invites = ConcurrentHashMap<Int, Invite>()

    private class Invite(val companyId: Int, val fromCharacterId: Int)

    // ---- Lookups ------------------------------------------------------------------

    public fun membership(characterId: Int): CompanyMembership? = memberships[characterId]

    public fun membership(player: Player): CompanyMembership? = memberships[player.characterId]

    public fun companyFor(player: Player): CompanyState? =
        membership(player)?.let { companies[it.companyId] }

    /** Online members of [companyId] (used for broadcasts and last-member flushes). */
    public fun onlineMembers(companyId: Int): List<Player> =
        playerList.filter { memberships[it.characterId]?.companyId == companyId }

    /**
     * Effective basis-point bonus of [key] granted by the player's company's trained ranks. `0` for
     * company-less players - and `0` the moment a member leaves, which is exactly the "effects stop
     * with membership" contract.
     */
    public fun effectBps(player: Player, key: TalentEffectKey): Int {
        val state = companyFor(player) ?: return 0
        synchronized(state) {
            return TalentCatalog.company
                .filter { it.effectKey == key }
                .sumOf { (state.ranks[it.id] ?: 0) * it.effectBpsPerRank }
        }
    }

    // ---- Character-data pipeline hooks ----------------------------------------------

    /**
     * Restores membership + company state at login. The company state is installed only when not
     * already cached - the in-memory copy is authoritative once any member is online, so a later
     * member's (staler) db row must not overwrite live progress.
     */
    public fun restore(player: Player, membership: CompanyMembership?, company: CompanyState?) {
        if (membership != null) {
            memberships[player.characterId] = membership
            if (company != null) {
                companies.putIfAbsent(company.id, company)
            }
        }
    }

    /** Flushes the player's company state inside the member's save transaction. */
    public fun saveCompanyState(connection: DatabaseConnection, player: Player) {
        val companyId = membership(player)?.companyId ?: return
        val state = companies[companyId] ?: return
        synchronized(state) {
            if (state.dirty || state.pendingAudit.isNotEmpty()) {
                CompanyRepository(connection).saveState(state)
            }
        }
    }

    // ---- Membership operations --------------------------------------------------------

    /** Creates a company with [player] as leader. Any company-less account may create one. */
    public fun createCompany(player: Player, name: String, onDone: (String) -> Unit) {
        if (membership(player) != null) {
            onDone("You are already in a company.")
            return
        }
        dbManager.request(
            request = { connection ->
                val repository = CompanyRepository(connection)
                val companyId =
                    repository.insertCompany(
                        realmId =
                            connection
                                .prepareStatement("SELECT realm_id FROM characters WHERE id = ?")
                                .use {
                                    it.setInt(1, player.characterId)
                                    it.executeQuery().use { rows ->
                                        if (rows.next()) rows.getInt(1) else 0
                                    }
                                },
                        name = name,
                        leaderCharacterId = player.characterId,
                    )
                if (companyId > 0) {
                    repository.insertMember(player.characterId, companyId, CompanyRole.LEADER)
                    repository.insertAudit(
                        companyId,
                        player.characterId,
                        "COMPANY_CREATE",
                        name.ifBlank { null },
                    )
                }
                GameDbResult.Ok(companyId)
            },
            response = { result ->
                val companyId = if (result is GameDbResult.Ok) result.value else -1
                if (companyId <= 0) {
                    onDone("Could not create the company. Please try again.")
                    return@request
                }
                joinLocal(player, companyId, CompanyRole.LEADER, name.ifBlank { null })
                onDone("Company #$companyId created. You are the leader.")
            },
        )
    }

    /** Invites [target] (online, company-less) into [player]'s company. Leader only. */
    public fun invite(player: Player, target: Player, onDone: (String) -> Unit) {
        val membership = membership(player)
        if (membership == null || membership.role != CompanyRole.LEADER) {
            onDone("Only a company leader can invite new members.")
            return
        }
        if (membership(target) != null) {
            onDone("${target.username} is already in a company.")
            return
        }
        dbManager.request(
            request = { connection ->
                GameDbResult.Ok(CompanyRepository(connection).countMembers(membership.companyId))
            },
            response = { result ->
                val members = if (result is GameDbResult.Ok) result.value else MAX_MEMBERS
                if (members >= MAX_MEMBERS) {
                    onDone("Your company is full. (max $MAX_MEMBERS members)")
                    return@request
                }
                invites[target.characterId] = Invite(membership.companyId, player.characterId)
                target.mes(
                    "${player.username} has invited you to join their company. " +
                        "Use ::companyaccept to join."
                )
                onDone("Invitation sent to ${target.username}.")
            },
        )
    }

    /** Accepts a pending company invitation. */
    public fun acceptInvite(player: Player, onDone: (String) -> Unit) {
        if (membership(player) != null) {
            onDone("You are already in a company.")
            return
        }
        val invite = invites[player.characterId]
        if (invite == null) {
            onDone("You have no pending company invitation.")
            return
        }
        dbManager.request(
            request = { connection ->
                val repository = CompanyRepository(connection)
                val members = repository.countMembers(invite.companyId)
                val joined =
                    members < MAX_MEMBERS &&
                        repository.insertMember(
                            player.characterId,
                            invite.companyId,
                            CompanyRole.MEMBER,
                        )
                if (joined) {
                    repository.insertAudit(
                        invite.companyId,
                        player.characterId,
                        "COMPANY_JOIN",
                        null,
                    )
                }
                GameDbResult.Ok(joined)
            },
            response = { result ->
                if (result is GameDbResult.Ok && result.value) {
                    invites.remove(player.characterId)
                    joinLocal(player, invite.companyId, CompanyRole.MEMBER, null)
                    onDone("You have joined company #${invite.companyId}.")
                } else {
                    invites.remove(player.characterId)
                    onDone("Could not join the company.")
                }
            },
        )
    }

    /** Removes [player] from their company. A leaving leader dissolves the whole company. */
    public fun leaveCompany(player: Player, onDone: (String) -> Unit) {
        val membership = membership(player)
        if (membership == null) {
            onDone("You are not in a company.")
            return
        }
        if (membership.role == CompanyRole.LEADER) {
            dissolveCompany(player, membership.companyId, onDone)
            return
        }
        removeMember(player, membership.companyId, "COMPANY_LEAVE") { ok ->
            onDone(if (ok) "You have left your company." else "Could not leave the company.")
        }
    }

    /** Leader-only: removes the online member [target] from [player]'s company. */
    public fun kickMember(player: Player, target: Player, onDone: (String) -> Unit) {
        val membership = membership(player)
        if (membership == null || membership.role != CompanyRole.LEADER) {
            onDone("Only a company leader can remove members.")
            return
        }
        val targetMembership = membership(target)
        if (
            targetMembership == null ||
                targetMembership.companyId != membership.companyId ||
                target == player
        ) {
            onDone("${target.username} is not a member of your company.")
            return
        }
        removeMember(target, membership.companyId, "COMPANY_KICK") { ok ->
            if (ok) {
                target.mes("You have been removed from your company.")
            }
            onDone(
                if (ok) "Removed ${target.username} from the company."
                else "Could not remove the member."
            )
        }
    }

    // ---- Talent tree -------------------------------------------------------------------

    /**
     * Attempts to buy one rank of a company talent for [player]'s company.
     *
     * The check + mutation run on the game thread against the authoritative in-memory state, so a
     * spend is atomic: a second click re-validates the already-updated ranks/balance and cannot
     * double-spend. The new state flushes inside the next member save.
     */
    public fun train(player: Player, definition: TalentDefinition): TrainResult {
        val membership = membership(player) ?: return TrainResult.NotMember
        val state = companies[membership.companyId] ?: return TrainResult.NotMember
        synchronized(state) {
            val ranks = state.rankSnapshot()
            val result =
                TalentRules.checkTrain(
                    definition = definition,
                    tree = TalentTree.COMPANY,
                    balance = state.points,
                    spent = TalentRules.spent(TalentTree.COMPANY, ranks),
                    ranks = ranks,
                    role = membership.role,
                )
            if (result != TrainResult.Ok) {
                return result
            }
            state.points -= definition.pointsPerRank
            val newRank = (ranks[definition.id] ?: 0) + 1
            state.ranks[definition.id] = newRank
            state.dirty = true
            audit(state, player.characterId, "TALENT_TRAIN", "${definition.id} rank $newRank")
            return TrainResult.Ok
        }
    }

    /**
     * Leader-only company respec: refunds every spent point and zeroes all ranks.
     *
     * @return the refunded amount, or `-1` when [player] may not reset (non-member/non-leader).
     */
    public fun reset(player: Player): Int {
        val membership = membership(player) ?: return -1
        if (membership.role != CompanyRole.LEADER) {
            return -1
        }
        val state = companies[membership.companyId] ?: return -1
        synchronized(state) {
            val refund = TalentRules.spent(TalentTree.COMPANY, state.rankSnapshot())
            state.ranks.clear()
            state.points += refund
            state.dirty = true
            if (refund > 0) {
                audit(state, player.characterId, "TALENT_RESET", "refund $refund")
            }
            return refund
        }
    }

    /** +1 talent point to [player]'s company for a boss kill by a member. No-op otherwise. */
    public fun awardMemberBossKill(player: Player) {
        val state = companyFor(player) ?: return
        synchronized(state) {
            state.points += BOSS_KILL_POINTS
            state.dirty = true
        }
    }

    /** Grants [amount] talent points to [player]'s company pool (admin tooling/tests). */
    public fun grantPoints(player: Player, amount: Int) {
        val state = companyFor(player) ?: return
        if (amount <= 0) {
            return
        }
        synchronized(state) {
            state.points += amount
            state.dirty = true
        }
    }

    // ---- Internals ----------------------------------------------------------------------

    private fun joinLocal(player: Player, companyId: Int, role: CompanyRole, name: String?) {
        memberships[player.characterId] = CompanyMembership(player.characterId, companyId, role)
        companies.putIfAbsent(companyId, CompanyState(companyId, name))
    }

    private fun removeMember(
        member: Player,
        companyId: Int,
        action: String,
        onDone: (Boolean) -> Unit,
    ) {
        dbManager.request(
            request = { connection ->
                val removed = CompanyRepository(connection).deleteMember(member.characterId)
                if (removed) {
                    CompanyRepository(connection)
                        .insertAudit(companyId, member.characterId, action, null)
                }
                GameDbResult.Ok(removed)
            },
            response = { result ->
                val ok = result is GameDbResult.Ok && result.value
                if (ok) {
                    memberships.remove(member.characterId)
                    flushIfLastMember(companyId)
                }
                onDone(ok)
            },
        )
    }

    private fun dissolveCompany(leader: Player, companyId: Int, onDone: (String) -> Unit) {
        dbManager.request(
            request = { connection ->
                val repository = CompanyRepository(connection)
                repository.deleteMembersOf(companyId)
                repository.insertAudit(companyId, leader.characterId, "COMPANY_DISSOLVE", null)
                repository.deleteCompany(companyId)
                GameDbResult.Ok(true)
            },
            response = { result ->
                if (result !is GameDbResult.Ok) {
                    onDone("Could not dissolve the company.")
                    return@request
                }
                companies.remove(companyId)
                for (member in onlineMembers(companyId)) {
                    memberships.remove(member.characterId)
                    member.mes("Your company has been dissolved.")
                }
                memberships.remove(leader.characterId)
                onDone("Your company has been dissolved.")
            },
        )
    }

    /**
     * When the last online member leaves, the cached company state has nobody left to carry the
     * next save - flush it eagerly so progress survives.
     */
    private fun flushIfLastMember(companyId: Int) {
        if (onlineMembers(companyId).isNotEmpty()) {
            return
        }
        val state = companies.remove(companyId) ?: return
        synchronized(state) {
            if (state.dirty || state.pendingAudit.isNotEmpty()) {
                dbManager.request(
                    request = { connection ->
                        CompanyRepository(connection).saveState(state)
                        GameDbResult.Ok(true)
                    },
                    response = {},
                )
            }
        }
    }

    private fun audit(state: CompanyState, characterId: Int, action: String, detail: String?) {
        state.pendingAudit += CompanyAuditEntry(characterId, action, detail)
    }

    private companion object {
        /** Talent points a member's boss kill mints for the company pool. */
        private const val BOSS_KILL_POINTS = 1

        /** Soft cap mirroring the GIM group-size contract. */
        private const val MAX_MEMBERS = 5
    }
}
