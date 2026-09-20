package org.rsmod.content.areas.unforge.raids

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The shared raid engine used by both `::tob` and `::cox`.
 *
 * One run owns a copied region: pad 0 is the lobby, each subsequent 16x16 pad hosts one room. A
 * world-queue loop drives the run (room activation, mechanic ticks, housekeeping, completion) - it
 * must not run on a player's [ProtectedAccess] coroutine, because a suspended coroutine keeps that
 * player access-protected and would block all their input. Member actions arrive through events
 * (kills, portal clicks, chest ops, logout, death hook) which all funnel into the same cleanup
 * path.
 */
@Singleton
class RaidService
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val statTypes: StatTypeList,
    private val pvmPoints: PvmPoints,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
) {
    private val io = RaidIo(npcTypes, npcRepo, collision, Random.Default)

    /** memberId -> run for every player currently inside a raid instance. */
    private val runs = mutableMapOf<Int, RaidRun>()

    /** memberId -> lobby party. A party exists only outside a run. */
    private val partyByPlayer = mutableMapOf<Int, RaidParty>()

    /** memberId -> pending lobby selection made through `::raid` args or the lobby ui. */
    private val lobbySelection = mutableMapOf<Int, LobbySelection>()

    class LobbySelection(var kind: RaidKind, var difficulty: RaidDifficulty)

    fun runOf(player: Player): RaidRun? = runs[player.slotId]

    /** Finds the run that owns [npc] (room combatants and portals alike). */
    fun runForNpc(npc: Npc): RaidRun? =
        runs.values.firstOrNull { run ->
            run.portals.contains(npc) || run.room?.alive?.contains(npc) == true
        }

    fun partyOf(player: Player): RaidParty? = partyByPlayer[player.slotId]

    fun lobbySelection(player: Player): LobbySelection =
        lobbySelection.getOrPut(player.slotId) {
            LobbySelection(RaidKind.THEATRE, RaidDifficulty.NORMAL)
        }

    /* ------------------------------------------------------------------ */
    /* Party management                                                    */
    /* ------------------------------------------------------------------ */

    fun createParty(player: Player) {
        if (runs.containsKey(player.slotId)) {
            player.mes("<col=ff3333>You cannot form a raid party inside a raid.</col>")
            return
        }
        if (partyByPlayer.containsKey(player.slotId)) {
            player.mes("<col=ff3333>You are already in a raid party.</col>")
            return
        }
        partyByPlayer[player.slotId] = RaidParty(player)
        player.mes(
            "<col=66ccff>Raid party formed. Invite with ::raidinvite <name>, " +
                "start with ::raidstart.</col>"
        )
    }

    fun invite(player: Player, targetName: String) {
        val party = partyByPlayer[player.slotId]
        if (party == null) {
            createParty(player)
            invite(player, targetName)
            return
        }
        if (party.leader !== player) {
            player.mes("<col=ff3333>Only the party leader can invite.</col>")
            return
        }
        val target = playerList.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
        if (target == null) {
            player.mes("<col=ff3333>Player '$targetName' is not online.</col>")
            return
        }
        if (runs.containsKey(target.slotId)) {
            player.mes("<col=ff3333>${target.username} is already inside a raid.</col>")
            return
        }
        if (party.members.contains(target)) {
            player.mes("<col=ff3333>${target.username} is already in your party.</col>")
            return
        }
        party.invites += target
        player.mes("<col=66ccff>Invited ${target.username} to your raid party.</col>")
        target.mes(
            "<col=ffb84d>${player.username} invited you to a raid party. " +
                "Type ::raidjoin ${player.username} to accept.</col>"
        )
    }

    fun joinParty(player: Player, leaderName: String) {
        if (runs.containsKey(player.slotId)) {
            player.mes("<col=ff3333>You are already inside a raid.</col>")
            return
        }
        val leader = playerList.firstOrNull { it.username.equals(leaderName, ignoreCase = true) }
        val party = leader?.let { partyByPlayer[it.slotId] }
        if (leader == null || party == null || party.leader !== leader) {
            player.mes("<col=ff3333>$leaderName does not lead a raid party.</col>")
            return
        }
        if (player !in party.invites) {
            player.mes("<col=ff3333>You have not been invited to that party.</col>")
            return
        }
        if (!party.canJoin()) {
            player.mes("<col=ff3333>That raid party is full.</col>")
            return
        }
        partyByPlayer[player.slotId]?.members?.remove(player)
        party.invites -= player
        party.members += player
        partyByPlayer[player.slotId] = party
        player.mes("<col=66ccff>You joined ${leader.username}'s raid party.</col>")
        for (member in party.members) {
            member.mes("<col=66ccff>${player.username} joined the raid party.</col>")
        }
    }

    fun leaveParty(player: Player) {
        val party = partyByPlayer.remove(player.slotId) ?: return
        party.members -= player
        party.invites -= player
        if (party.leader === player || party.members.isEmpty()) {
            for (member in party.members) {
                partyByPlayer.remove(member.slotId)
                member.mes("<col=ffb84d>Your raid party has disbanded.</col>")
            }
            player.mes("<col=ffb84d>Your raid party has disbanded.</col>")
        } else {
            player.mes("<col=ffb84d>You left the raid party.</col>")
            for (member in party.members) {
                member.mes("<col=ffb84d>${player.username} left the raid party.</col>")
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Run lifecycle                                                       */
    /* ------------------------------------------------------------------ */

    /** Entry point for `::tob`, `::cox`, `::raidstart` and the lobby Start button. */
    suspend fun ProtectedAccess.startRaid(kind: RaidKind, difficulty: RaidDifficulty) {
        if (runs.containsKey(player.slotId)) {
            mes("<col=ff3333>You are already inside a raid.</col>")
            return
        }
        val party = partyByPlayer[player.slotId]
        if (party != null && party.leader !== player) {
            mes("<col=ff3333>Only your party leader can start the raid.</col>")
            return
        }
        val members = party?.members?.toList() ?: listOf(player)
        if (members.any { runs.containsKey(it.slotId) }) {
            mes("<col=ff3333>A party member is already inside a raid.</col>")
            return
        }
        val rooms =
            when (kind) {
                RaidKind.THEATRE -> TobRaid.rooms
                RaidKind.CHAMBERS -> CoxRaid.rooms(io.random)
            }
        val template = RaidInstance.template(kind, rooms)
        val region = regionRepo.add(template)
        if (region == null) {
            mes("<col=ff3333>The raid instance pool is full. Try again shortly.</col>")
            return
        }
        val core = RaidRunCore(kind, difficulty, rooms.size, clock.cycle)
        val run = RaidRun(core, region, party, rooms, LinkedHashMap())
        for (member in members) {
            core.addMember(member.slotId)
            run.players[member.slotId] = member
            runs[member.slotId] = run
        }
        val lobby = RaidInstance.padOrigin(region.southWest, 0, kind)
        for (member in members) {
            PathingEntityCommon.telejump(member, collision, lobby.translate(8, 8))
            member.mes(
                "<col=ffb84d>${kind.displayName} (${difficulty.displayName}) - " +
                    "${rooms.size} rooms stand between you and the reward chest.</col>"
            )
        }
        spawnPortal(run, lobby)
        beginDrive(run)
    }

    /**
     * Starts the per-cycle world-queue drive loop. Runs on the world queue - never on a player
     * coroutine, which would keep that player access-protected for the entire raid.
     */
    private fun beginDrive(run: RaidRun) {
        run.core.activate()
        worldQueues.add(1) { raidTick(run) }
    }

    private fun raidTick(run: RaidRun) {
        when (run.core.state) {
            RaidRunState.ACTIVE -> stepActive(run)
            RaidRunState.COMPLETE -> stepComplete(run)
            else -> return
        }
        if (run.core.state == RaidRunState.ACTIVE || run.core.state == RaidRunState.COMPLETE) {
            worldQueues.add(1) { raidTick(run) }
        }
    }

    /** One cycle of ACTIVE raid: activate the room, tick its mechanic, resolve clearing. */
    private fun stepActive(run: RaidRun) {
        val room = run.room
        if (room == null) {
            val spec = run.rooms[run.core.roomIndex]
            activateRoom(run, spec)
            msgAll(
                run,
                "<col=ffb84d>Room ${run.core.roomIndex + 1}/${run.rooms.size}: " +
                    "${spec.displayName}</col>",
            )
            run.roomKillsBefore = run.core.kills
            run.roomCycle = 0
            return
        }
        room.mechanic?.tick(io, run, room)
        if (++run.roomCycle % 20 == 0) {
            housekeeping(run)
        }
        if (run.core.state != RaidRunState.ACTIVE || !room.cleared) {
            return
        }
        val points = RaidScore.roomPoints(room.spec.kind, run.core.kills - run.roomKillsBefore)
        run.core.addScore(points)
        msgAll(run, "<col=66ccff>Room cleared! +$points raid points.</col>")
        run.room = null
        if (run.core.isFinalRoom()) {
            complete(run)
        } else {
            run.core.advanceRoom() // also revives dead members for the next room
        }
    }

    /**
     * Post-completion watch: keeps the chest and region alive until every member has claimed or the
     * loot window expires, then teleports stragglers out and cleans up.
     */
    private fun stepComplete(run: RaidRun) {
        val allClaimed = run.players.keys.all { it in run.core.claimed }
        if (!allClaimed && ++run.lootWaited < LOOT_WINDOW_CYCLES && run.players.isNotEmpty()) {
            return
        }
        for (member in run.players.values.toList()) {
            PathingEntityCommon.telejump(member, collision, RaidInstance.exit)
            member.mes("<col=ffb84d>The raid instance collapses behind you.</col>")
        }
        cleanup(run)
    }

    private fun activateRoom(run: RaidRun, spec: RaidRoomSpec): RaidRoomState {
        val pad =
            RaidInstance.padOrigin(run.region.southWest, run.core.roomIndex + 1, run.core.kind)
        val room = RaidRoomState(spec, pad)
        run.room = room
        for (spawn in spec.spawns) {
            io.spawn(run, room, spawn.npc, spawn.localX, spawn.localZ, spawn.statScale)
        }
        spawnPortal(run, pad)
        for (member in run.players.values) {
            PathingEntityCommon.telejump(member, collision, room.local(8, 4))
        }
        room.mechanic = spec.mechanic.create()
        room.mechanic?.onStart(io, run, room)
        return room
    }

    private fun spawnPortal(run: RaidRun, pad: CoordGrid) {
        for (portal in run.portals) {
            npcRepo.del(portal, Int.MAX_VALUE)
        }
        run.portals.clear()
        val portal = Npc(npcTypes[RaidNpcs.exitPortal], pad.translate(1, 1))
        npcRepo.add(portal, Int.MAX_VALUE)
        portal.respawns = false
        run.portals += portal
    }

    /** Periodic checks: run timeout, members straying outside the region, portal tiles. */
    private fun housekeeping(run: RaidRun) {
        if (run.core.timedOut(clock.cycle, TIMEOUT_CYCLES)) {
            failRun(run, "The raid timed out.")
            return
        }
        for (member in run.players.values.toList()) {
            if (!run.contains(member.coords)) {
                removeMember(run, member, "left the raid area")
            } else if (
                run.room != null && run.core.members[member.slotId] == RaidMemberState.ALIVE
            ) {
                val room = run.room!!
                if (!io.inRoom(room, member.coords)) {
                    PathingEntityCommon.telejump(member, collision, room.local(8, 4))
                    member.mes("<col=ffb84d>You are returned to the active room.</col>")
                }
            }
            if (run.core.state != RaidRunState.ACTIVE) {
                return
            }
        }
        for (portal in run.portals) {
            for (member in run.players.values.toList()) {
                if (member.coords == portal.coords) {
                    leaveRaid(member)
                    break
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Events                                                              */
    /* ------------------------------------------------------------------ */

    fun onNpcKilled(event: NpcKilledEvent) {
        val run = runs[event.killer.slotId] ?: return
        if (run.portals.remove(event.npc)) {
            // The exit portal is attackable - replace it so nobody gets stranded.
            run.room?.let { spawnPortal(run, it.origin) }
            return
        }
        val room = run.room ?: return
        if (!room.alive.remove(event.npc)) {
            return
        }
        run.core.addKill()
        val awarded = pvmPoints.award(event.killer, event.npc.type.vislevel)
        if (awarded > 0) {
            event.killer.mes("<col=66ccff>+$awarded PvM points</col>")
        }
        room.mechanic?.onKill(io, run, room, event.npc)
        if (room.alive.isEmpty() && room.mechanic?.blocksClear() != true) {
            room.cleared = true
        }
    }

    /**
     * Death hook: consumes raid deaths. A solo death or a full party wipe fails the run; a party
     * member otherwise waits in the lobby pad until the room is cleared.
     */
    suspend fun handleDeath(access: ProtectedAccess): Boolean {
        val player = access.player
        val run = runs[player.slotId] ?: return false
        if (run.core.state != RaidRunState.ACTIVE) {
            return false
        }
        access.stopAction()
        access.combatClearQueue()
        access.anim(seqs.human_death)
        val wipe = run.core.markDead(player.slotId)
        access.statRestoreAll(statTypes.values)
        if (wipe) {
            msgAll(run, "<col=ff3333>Your party has been defeated!</col>")
            for (member in run.players.values.toList()) {
                PathingEntityCommon.telejump(member, collision, RaidInstance.exit)
            }
            cleanup(run)
        } else {
            val lobby = RaidInstance.padOrigin(run.region.southWest, 0, run.core.kind)
            access.telejump(lobby.translate(8, 8))
            player.mes(
                "<col=ff3333>You died! -8% raid score. Your party can still clear the room " +
                    "to revive you.</col>"
            )
        }
        return true
    }

    /** Lets a member (or the whole run, when the leader leaves) exit through the portal. */
    fun leaveRaid(player: Player) {
        val run = runs[player.slotId] ?: return
        if (run.leader() === player) {
            msgAll(run, "<col=ffb84d>${player.username} abandoned the raid.</col>")
            for (member in run.players.values.toList()) {
                PathingEntityCommon.telejump(member, collision, RaidInstance.exit)
            }
            cleanup(run)
            return
        }
        removeMember(run, player, "left the raid")
        PathingEntityCommon.telejump(player, collision, RaidInstance.exit)
        player.mes("<col=ffb84d>You left the raid.</col>")
    }

    private fun removeMember(run: RaidRun, player: Player, reason: String) {
        if (run.leader() === player) {
            msgAll(run, "<col=ffb84d>${player.username} $reason - the raid collapses.</col>")
            for (member in run.players.values.toList()) {
                PathingEntityCommon.telejump(member, collision, RaidInstance.exit)
            }
            cleanup(run)
            return
        }
        runs.remove(player.slotId)
        run.core.removeMember(player.slotId)
        run.players.remove(player.slotId)
        msgAll(run, "<col=ffb84d>${player.username} $reason.</col>")
        if (run.players.isEmpty()) {
            cleanup(run)
        }
    }

    fun onLogout(player: Player) {
        partyByPlayer[player.slotId]?.let { leaveParty(player) }
        runs[player.slotId]?.let { leaveRaid(player) }
    }

    /** `onModifyNpcHit` hook: lets the room mechanic reduce incoming damage (Olm head). */
    fun modifyNpcHit(npc: Npc, damage: Int): Int {
        val run = runForNpc(npc) ?: return damage
        val room = run.room ?: return damage
        return room.mechanic?.modifyIncomingDamage(io, run, room, npc, damage) ?: damage
    }

    /* ------------------------------------------------------------------ */
    /* Completion, rewards and cleanup                                     */
    /* ------------------------------------------------------------------ */

    private fun complete(run: RaidRun) {
        run.core.complete()
        val elapsed = clock.cycle - run.core.startCycle
        val score = run.core.finalScore(elapsed)
        val completionsVarp =
            when (run.core.kind) {
                RaidKind.THEATRE -> RaidVarps.tobCompletions
                RaidKind.CHAMBERS -> RaidVarps.coxCompletions
            }
        for (member in run.players.values) {
            VarPlayerIntMapSetter.set(
                member,
                RaidVarps.points,
                member.vars[RaidVarps.points] + score,
            )
            VarPlayerIntMapSetter.set(member, completionsVarp, member.vars[completionsVarp] + 1)
            member.mes(
                "<col=00ff00>${run.core.kind.displayName} complete!</col> " +
                    "<col=ffb84d>Final score: $score (${run.core.deaths} deaths).</col>"
            )
        }
        val room = run.room ?: return
        val chestType =
            when (run.core.kind) {
                RaidKind.THEATRE -> locTypes[RaidLocs.tobChestClosed]
                RaidKind.CHAMBERS -> locTypes[RaidLocs.coxChestClosed]
            }
        run.chest =
            locRepo.add(
                room.local(12, 3),
                chestType,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        msgAll(run, "<col=ffb84d>A reward chest has appeared!</col>")
    }

    /** Chest op handler. One claim per member, enforced by [RaidRunCore.claimChest]. */
    suspend fun ProtectedAccess.claimChest(coords: CoordGrid) {
        val run = runs[player.slotId] ?: return
        val chest = run.chest
        if (chest == null || chest.coords != coords) {
            mes("<col=ff3333>That chest is not yours to loot.</col>")
            return
        }
        if (!run.core.claimChest(player.slotId)) {
            mes("<col=ff3333>You have already looted this chest.</col>")
            return
        }
        val score = run.core.finalScore(clock.cycle - run.core.startCycle)
        val rewards = RaidRewards.rollChest(io.random, run.core.kind, score, run.core.difficulty)
        for ((type, count) in rewards) {
            invAddOrDrop(objRepo, objTypes[type], count)
        }
        val summary =
            rewards.joinToString(", ") { (type, count) -> "${count}x ${objTypes[type].name}" }
        mes("<col=66ccff>You loot the chest: $summary.</col>")
    }

    private fun failRun(run: RaidRun, reason: String) {
        if (run.core.state == RaidRunState.FAILED || run.core.state == RaidRunState.CLEANED) {
            return
        }
        run.core.fail()
        msgAll(run, "<col=ff3333>$reason The raid has failed.</col>")
        for (member in run.players.values.toList()) {
            PathingEntityCommon.telejump(member, collision, RaidInstance.exit)
        }
        cleanup(run)
    }

    /** Idempotent teardown shared by logout, death, timeout, completion and abandonment. */
    fun cleanup(run: RaidRun) {
        if (run.core.state == RaidRunState.CLEANED) {
            return
        }
        run.core.cleanup()
        run.room?.let { room ->
            for (npc in room.alive) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
            room.alive.clear()
        }
        for (portal in run.portals) {
            npcRepo.del(portal, Int.MAX_VALUE)
        }
        run.portals.clear()
        run.chest?.let { locRepo.del(it, Int.MAX_VALUE) }
        run.chest = null
        for (memberId in run.players.keys) {
            runs.remove(memberId)
        }
        run.party?.let { party ->
            for (member in party.members) {
                partyByPlayer.remove(member.slotId)
            }
            party.invites.clear()
        }
    }

    /* ------------------------------------------------------------------ */
    /* Status                                                              */
    /* ------------------------------------------------------------------ */

    fun statusText(run: RaidRun): String {
        val core = run.core
        val elapsed = ((clock.cycle - core.startCycle) * 0.6).toInt()
        val members = run.players.values.joinToString(", ") { it.username }
        return "<col=ffb84d>${core.kind.displayName}</col> | " +
            "${core.difficulty.displayName} | " +
            "Room ${core.roomIndex + 1}/${core.roomCount} | " +
            "Score ${core.score} | Deaths ${core.deaths} | " +
            "Time ${elapsed / 60}m ${elapsed % 60}s | " +
            "Party: $members"
    }

    private fun msgAll(run: RaidRun, text: String) {
        for (member in run.players.values) {
            member.mes(text)
        }
    }

    companion object {
        /** Raid timeout: 90 minutes at 0.6s per cycle. */
        const val TIMEOUT_CYCLES = 9_000

        /** Time the reward chest stays up after completion (~6 minutes). */
        const val LOOT_WINDOW_CYCLES = 600
    }
}
