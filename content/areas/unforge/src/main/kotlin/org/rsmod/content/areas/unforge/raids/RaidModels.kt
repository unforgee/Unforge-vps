package org.rsmod.content.areas.unforge.raids

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

/** The two supported raids. */
enum class RaidKind(val displayName: String) {
    THEATRE("Theatre of Blood"),
    CHAMBERS("Chambers of Xeric"),
}

enum class RaidDifficulty(
    val displayName: String,
    /** Multiplies npc attack/strength/defence/hitpoints/ranged/magic levels. */
    val statMultiplier: Double,
    /** Multiplies mechanic (scripted) damage dealt to players. */
    val damageMultiplier: Double,
    /** Basis-point scale applied to mechanic cooldowns (>10000 = faster cycles). */
    val mechanicSpeedBps: Int,
    /** Basis-point multiplier applied to raid score on completion. */
    val scoreMultiplierBps: Int,
    /** Extra common reward rolls granted by the reward chest. */
    val bonusChestRolls: Int,
) {
    NORMAL("Normal", 1.0, 1.0, 10_000, 10_000, 0),
    EXPERT("Expert", 1.5, 1.4, 13_000, 17_500, 1),
}

enum class RaidRoomKind {
    /** Straightforward combat room. */
    COMBAT,

    /** Multiple waves; every wave npc must die before advancing. */
    WAVE,

    /** Puzzle / resource-flavoured room (light combat). */
    PUZZLE,

    /** Stronger single-encounter room. */
    MINIBOSS,

    /** The final boss room; clearing it completes the raid. */
    BOSS,
}

/** How one npc should be spawned inside a room pad. */
class RaidSpawnSpec(
    val npc: NpcType,
    val localX: Int,
    val localZ: Int,
    val statScale: Double = 1.0,
)

/** Static description of one raid room. */
class RaidRoomSpec(
    val key: String,
    val displayName: String,
    val kind: RaidRoomKind,
    val spawns: List<RaidSpawnSpec>,
    val mechanic: RaidMechanicId,
    val layout: RaidRoomLayout? = null,
)

/** Mutable per-room state for an active [RaidRun]. */
class RaidRoomState(
    val spec: RaidRoomSpec,
    val origin: CoordGrid,
    val layout: RaidRoomLayout? = spec.layout ?: RaidMapLayouts.forKey(spec.key),
) {
    /** Npcs that must die for the room to clear. */
    val alive = mutableListOf<Npc>()

    /** Mechanic instance created when the room was activated. */
    var mechanic: RaidRoomMechanic? = null

    var cleared = false

    fun local(dx: Int, dz: Int): CoordGrid = origin.translate(dx, dz)

    val widthTiles: Int
        get() = layout?.widthTiles ?: RaidIo.PAD_TILES

    val lengthTiles: Int
        get() = layout?.lengthTiles ?: RaidIo.PAD_TILES
}

enum class RaidMemberState {
    ALIVE,
    DEAD,
}

enum class RaidRunState {
    /** Members teleported in, first room not yet engaged. */
    STARTING,
    ACTIVE,
    COMPLETE,
    FAILED,
    CLEANED,
}

/**
 * The engine-free half of a raid run: member ids, room progression, score, deaths and reward
 * claims. Everything here is deterministic and unit-testable without a live game world - the engine
 * wrapper ([RaidRun]) delegates to it.
 */
class RaidRunCore(
    val kind: RaidKind,
    val difficulty: RaidDifficulty,
    val roomCount: Int,
    val startCycle: Int,
) {
    var state: RaidRunState = RaidRunState.STARTING
        private set

    var roomIndex = 0
        private set

    var score = 0
        private set

    var kills = 0
        private set

    var deaths = 0
        private set

    /** memberId -> alive/dead. Linked map keeps join order for deterministic iteration. */
    val members = LinkedHashMap<Int, RaidMemberState>()

    /** memberIds that already looted the reward chest. */
    val claimed = mutableSetOf<Int>()

    fun addMember(memberId: Int) {
        members.putIfAbsent(memberId, RaidMemberState.ALIVE)
    }

    fun removeMember(memberId: Int) {
        members.remove(memberId)
    }

    fun isMember(memberId: Int): Boolean = memberId in members

    fun aliveCount(): Int = members.values.count { it == RaidMemberState.ALIVE }

    fun allDead(): Boolean = members.isNotEmpty() && aliveCount() == 0

    /**
     * Marks a member dead and returns `true` when the run wiped (no alive members left). Solo runs
     * always wipe on death.
     */
    fun markDead(memberId: Int): Boolean {
        if (members[memberId] != RaidMemberState.ALIVE) {
            return allDead()
        }
        members[memberId] = RaidMemberState.DEAD
        deaths++
        return allDead()
    }

    /** Revives every dead member (called when a room is cleared). */
    fun reviveAll() {
        members.replaceAll { _, state ->
            if (state == RaidMemberState.DEAD) RaidMemberState.ALIVE else state
        }
    }

    fun isFinalRoom(): Boolean = roomIndex >= roomCount - 1

    fun addScore(points: Int) {
        score = (score + points).coerceAtLeast(0)
    }

    fun addKill(count: Int = 1) {
        kills += count
    }

    fun activate() {
        if (state == RaidRunState.STARTING) {
            state = RaidRunState.ACTIVE
        }
    }

    /** Advances to the next room. @return `false` when the run was already on the last room. */
    fun advanceRoom(): Boolean {
        if (isFinalRoom() || state != RaidRunState.ACTIVE) {
            return false
        }
        roomIndex++
        reviveAll()
        return true
    }

    /** Final score with difficulty multiplier, death penalty and time bonus applied. */
    fun finalScore(elapsedCycles: Int): Int =
        RaidScore.finalScore(
            roomScore = score,
            kills = kills,
            deaths = deaths,
            difficulty = difficulty,
            elapsedCycles = elapsedCycles,
        )

    fun timedOut(nowCycle: Int, timeoutCycles: Int): Boolean = nowCycle - startCycle > timeoutCycles

    /**
     * Claims the reward chest for [memberId]. @return `true` exactly once per member - subsequent
     * calls (or non-members) return `false`.
     */
    fun claimChest(memberId: Int): Boolean = memberId in members && claimed.add(memberId)

    fun complete() {
        if (state == RaidRunState.ACTIVE) {
            state = RaidRunState.COMPLETE
        }
    }

    fun fail() {
        if (state == RaidRunState.STARTING || state == RaidRunState.ACTIVE) {
            state = RaidRunState.FAILED
        }
    }

    /** Idempotent: safe to call from logout, death, timeout and completion paths alike. */
    fun cleanup() {
        if (state != RaidRunState.CLEANED) {
            state = RaidRunState.CLEANED
        }
    }
}

/** The live raid run: [core] state plus engine-side entities owned by the run. */
class RaidRun(
    val core: RaidRunCore,
    val region: Region,
    val party: RaidParty?,
    val rooms: List<RaidRoomSpec>,
    /** memberId -> player for every member still in the run. */
    val players: LinkedHashMap<Int, Player>,
) {
    var room: RaidRoomState? = null

    /** Reward chest loc, spawned on completion only. */
    var chest: LocInfo? = null

    /** Exit portal npcs spawned per pad so players can bail mid-raid. */
    val portals = mutableListOf<Npc>()

    /** Kill counter snapshot taken when the active room was spawned. */
    var roomKillsBefore: Int = 0

    /** Cycles elapsed in the active room; housekeeping runs every 20. */
    var roomCycle: Int = 0

    /** Cycles elapsed in the post-completion loot window. */
    var lootWaited: Int = 0

    fun aliveMembers(): List<Player> =
        players.values.filter { core.members[it.slotId] == RaidMemberState.ALIVE }

    fun leader(): Player = players.values.first()

    fun contains(coords: CoordGrid): Boolean =
        coords.x in region.southWest.x..region.northEast.x &&
            coords.z in region.southWest.z..region.northEast.z
}

/** A lobby party: one leader plus up to [MAX_SIZE] members sharing a single raid run. */
class RaidParty(val leader: Player) {
    val members = LinkedHashSet<Player>()

    /** Players invited but not yet joined. */
    val invites = mutableSetOf<Player>()

    init {
        members += leader
    }

    fun size(): Int = members.size

    fun canJoin(): Boolean = members.size < MAX_SIZE

    companion object {
        const val MAX_SIZE = 5
    }
}
