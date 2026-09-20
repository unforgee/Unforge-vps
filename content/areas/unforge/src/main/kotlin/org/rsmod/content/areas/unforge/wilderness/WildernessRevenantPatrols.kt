package org.rsmod.content.areas.unforge.wilderness

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.api.area.checker.isWildernessSafeZone
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid

public class PatrolMember(
    public val npcId: Int,
    public val roleName: String,
    public var npc: Npc? = null,
    public var isLeader: Boolean = false,
    public var respawnTicks: Int = 0,
)

public class PatrolGroup(
    public val groupName: String,
    public val waypoints: List<CoordGrid>,
    public val members: MutableList<PatrolMember> = mutableListOf(),
    public var currentWaypointIndex: Int = 0,
    public var lastActionTick: Int = 0,
) {
    public val leader: PatrolMember?
        get() = members.firstOrNull { it.isLeader }
}

@Singleton
public class WildernessPatrolManager
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val players: PlayerList,
    private val mapClock: MapClock,
    private val launcher: ProtectedAccessLauncher,
) {
    private val logger = InlineLogger()
    private val activePatrols = mutableListOf<PatrolGroup>()

    public fun initialize() {
        // Patrol 1: Deep Wild North
        val northWaypoints =
            listOf(
                CoordGrid(3156, 3944), // Deserted Keep
                CoordGrid(3286, 3932), // Rogue's Castle
                CoordGrid(3288, 3886), // Demonic Ruins
                CoordGrid(3070, 3858), // Lava Maze
                CoordGrid(2966, 3934), // Frozen Waste
            )
        val northGroup = PatrolGroup("Northern Vanguard", northWaypoints)
        northGroup.members += PatrolMember(11246, "Revenant Maledictus", isLeader = true)
        northGroup.members += PatrolMember(7940, "Revenant Dragon")
        northGroup.members += PatrolMember(7939, "Revenant Knight")
        northGroup.members += PatrolMember(7938, "Revenant Dark Beast")
        activePatrols += northGroup

        // Patrol 2: Mid Wild
        val midWaypoints =
            listOf(
                CoordGrid(3036, 3696), // Bandit Camp
                CoordGrid(3168, 3672), // Graveyard of Shadows
                CoordGrid(2978, 3754), // Forgotten Cemetery
                CoordGrid(3070, 3858), // Lava Maze
            )
        val midGroup = PatrolGroup("Central Strike Team", midWaypoints)
        midGroup.members += PatrolMember(7940, "Revenant Dragon", isLeader = true)
        midGroup.members += PatrolMember(7939, "Revenant Knight")
        midGroup.members += PatrolMember(7937, "Revenant Ork")
        midGroup.members += PatrolMember(7936, "Revenant Demon")
        activePatrols += midGroup

        // Patrol 3: South Wild
        val southWaypoints =
            listOf(
                CoordGrid(3020, 3632), // Dark Warriors' Fortress
                CoordGrid(3236, 3638), // Chaos Temple
                CoordGrid(3168, 3672), // Graveyard of Shadows
                CoordGrid(3036, 3696), // Bandit Camp
            )
        val southGroup = PatrolGroup("Southern Reavers", southWaypoints)
        southGroup.members += PatrolMember(7939, "Revenant Knight", isLeader = true)
        southGroup.members += PatrolMember(7937, "Revenant Ork")
        southGroup.members += PatrolMember(7935, "Revenant Hellhound")
        southGroup.members += PatrolMember(7934, "Revenant Cyclops")
        activePatrols += southGroup

        for (patrol in activePatrols) {
            spawnPatrol(patrol)
        }

        logger.info {
            "Wilderness Patrol Manager initialized with ${activePatrols.size} patrol units."
        }
    }

    private fun spawnPatrol(patrol: PatrolGroup) {
        val startCoord = patrol.waypoints.first()
        for ((idx, member) in patrol.members.withIndex()) {
            val npcType = npcTypes[member.npcId] ?: continue
            val offsetCoord = startCoord.translate(idx % 2, idx / 2)
            val npc = Npc(npcType, offsetCoord)
            npc.mode = NpcMode.Wander
            npc.respawns = false
            npcRepo.add(npc, Int.MAX_VALUE)
            member.npc = npc
            member.respawnTicks = 0
        }
    }

    public fun process() {
        for (patrol in activePatrols) {
            val leader = patrol.leader?.npc

            // Check respawns for dead members
            for (member in patrol.members) {
                val npc = member.npc
                if (npc == null || !npc.isValidTarget()) {
                    member.respawnTicks++
                    if (member.respawnTicks >= 150) { // ~90s respawn
                        val respawnCoord =
                            leader?.coords ?: patrol.waypoints[patrol.currentWaypointIndex]
                        val type = npcTypes[member.npcId]
                        if (type != null) {
                            val newNpc = Npc(type, respawnCoord)
                            newNpc.mode = NpcMode.Wander
                            newNpc.respawns = false
                            npcRepo.add(newNpc, Int.MAX_VALUE)
                            member.npc = newNpc
                            member.respawnTicks = 0
                        }
                    }
                }
            }

            if (leader == null || !leader.isValidTarget()) {
                continue
            }

            // Check for nearby players to assault
            val target = findNearbyTarget(leader.coords, 12)
            if (target != null && !target.coords.isWildernessSafeZone()) {
                // Engage target!
                leader.walk(target.coords)
                for (member in patrol.members) {
                    member.npc?.walk(target.coords)
                }

                if (mapClock.cycle - patrol.lastActionTick >= 5) {
                    patrol.lastActionTick = mapClock.cycle
                    launcher.launch(target) {
                        mes(
                            "<col=ff0000>[Revenant Patrol]</col> The ${patrol.groupName} closes in!"
                        )
                        spotanim(spotanims.smokepuff, height = 0)
                        if (Random.nextInt(4) == 0) {
                            mes("<col=68007F>A magical freeze locks your feet into place!</col>")
                        }
                    }
                }
            } else {
                // Roam to next waypoint every 12 ticks
                if (mapClock.cycle % 12 == 0) {
                    patrol.currentWaypointIndex =
                        (patrol.currentWaypointIndex + 1) % patrol.waypoints.size
                    val nextWp = patrol.waypoints[patrol.currentWaypointIndex]
                    leader.walk(nextWp)

                    // Followers follow the leader
                    for (member in patrol.members) {
                        if (
                            !member.isLeader && member.npc != null && member.npc!!.isValidTarget()
                        ) {
                            member.npc!!.walk(leader.coords)
                        }
                    }
                }
            }
        }
    }

    private fun findNearbyTarget(coords: CoordGrid, maxDist: Int): Player? {
        for (player in players) {
            if (player.coords.level != coords.level) continue
            if (player.coords.isWildernessSafeZone()) continue
            val dist = maxOf(abs(coords.x - player.coords.x), abs(coords.z - player.coords.z))
            if (dist <= maxDist) {
                return player
            }
        }
        return null
    }
}
