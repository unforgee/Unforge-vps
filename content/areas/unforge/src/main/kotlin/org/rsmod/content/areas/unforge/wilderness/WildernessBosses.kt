package org.rsmod.content.areas.unforge.wilderness

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.area.checker.isWildernessSafeZone
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public enum class BossState {
    SPAWNING,
    ROAMING,
    HUNTING,
    FIGHTING,
    RETURNING,
    MIGRATING,
    DYING,
    RESPAWNING,
}

public enum class RoamingBossType(
    public val npcId: Int,
    public val bossName: String,
    public val isApex: Boolean,
    public val combatLevel: Int,
    public val maxHp: Int,
    public val attackRate: Int,
) {
    CALLISTO(6503, "Callisto the Ursine", false, 470, 1050, 4),
    VENENATIS(6504, "Venenatis the Broodmother", false, 464, 980, 4),
    VETION(6611, "Vet'ion the Reborn", false, 454, 1100, 4),
    CHAOS_ELEMENTAL(2054, "Chaos Titan", false, 305, 850, 5),
    KING_BLACK_DRAGON(239, "Blackwing the Dread", false, 276, 920, 4),
    ABYSSAL_SIRE(5886, "Abyssal Behemoth", false, 350, 1200, 4),
    CERBERUS(5862, "Cerberus the Hellhound", false, 318, 1000, 4),
    ALCHEMICAL_HYDRA(8615, "Alchemical Hydra", false, 426, 1150, 4),
    CORPOREAL_BEAST(319, "Corporeal Beast", false, 785, 2000, 4),
    SOTETSEG(8388, "Nightmare of the Wastes", true, 895, 3500, 4),
    // Apex Boss
}

public class RoamingBossInstance(
    public val type: RoamingBossType,
    public var currentHotspot: WildernessHotspot,
    public var state: BossState = BossState.SPAWNING,
    public var npc: Npc? = null,
    public var waypointIndex: Int = 0,
    public var lastEngagedTick: Int = 0,
    public var ticksInCurrentState: Int = 0,
    public var stuckTicks: Int = 0,
    public var lastCoords: CoordGrid = currentHotspot.center,
    public var respawnDelayTicks: Int = 0,
)

@Singleton
public class WildernessBossManager
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val players: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val launcher: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
) {
    private val logger = InlineLogger()
    private val activeBosses = mutableListOf<RoamingBossInstance>()

    public fun initialize() {
        val hotspots = WildernessHotspot.entries.toMutableList()
        hotspots.shuffle()

        // 1 Apex Boss
        val apexType = RoamingBossType.SOTETSEG
        val apexHotspot = hotspots.removeAt(0)
        val apexInstance = RoamingBossInstance(apexType, apexHotspot)
        activeBosses.add(apexInstance)
        spawnBossNpc(apexInstance)

        // 3 Regular Bosses
        val regularTypes = RoamingBossType.entries.filter { !it.isApex }.shuffled().take(3)
        for (type in regularTypes) {
            val hotspot = hotspots.removeAt(0)
            val instance = RoamingBossInstance(type, hotspot)
            activeBosses.add(instance)
            spawnBossNpc(instance)
        }

        logger.info { "Wilderness Boss Manager initialized: 1 Apex and 3 Regular bosses spawned." }
    }

    public fun getActiveBosses(): List<RoamingBossInstance> = activeBosses

    /**
     * Handles only kills belonging to this manager. Normal NPC death has already resolved drops
     * before [NpcKilledEvent] is published, so this method records KC and schedules the same clean
     * respawn path used for a naturally invalidated boss without rolling a second drop.
     */
    public fun onNpcKilled(event: NpcKilledEvent) {
        val instance = activeBosses.firstOrNull { it.npc === event.npc } ?: return
        val type = instance.type
        val kc = WildernessBossKillCounts.increment(event.killer, type)
        instance.npc = null
        instance.state = BossState.RESPAWNING
        instance.respawnDelayTicks = RESPAWN_DELAY_TICKS
        instance.lastEngagedTick = mapClock.cycle
        launcher.launch(event.killer) {
            if (kc > 0) {
                mes("<col=66ff66>${type.bossName} defeated. Wilderness KC: $kc.</col>")
            } else {
                mes(
                    "<col=66ff66>${type.bossName} defeated. KC is not available for this custom apex entry.</col>"
                )
            }
        }
    }

    private fun spawnBossNpc(instance: RoamingBossInstance) {
        val npcType = npcTypes[instance.type.npcId] ?: return
        val spawnCoords = instance.currentHotspot.center
        val npc = Npc(npcType, spawnCoords)
        npc.mode = NpcMode.Wander
        npc.respawns = false

        npcRepo.add(npc, Int.MAX_VALUE)
        instance.npc = npc
        instance.state = BossState.ROAMING
        instance.lastCoords = spawnCoords
        instance.stuckTicks = 0
        instance.ticksInCurrentState = 0
        instance.lastEngagedTick = mapClock.cycle

        broadcastMessage(
            "<col=ff0000>[Wilderness Danger]</col> ${instance.type.bossName} has emerged at ${instance.currentHotspot.displayName}!"
        )
    }

    public fun process() {
        for (boss in activeBosses) {
            boss.ticksInCurrentState++
            val npc = boss.npc

            if (npc == null || !npc.isValidTarget()) {
                if (boss.state != BossState.RESPAWNING) {
                    boss.state = BossState.RESPAWNING
                    boss.respawnDelayTicks = RESPAWN_DELAY_TICKS // ~1 minute respawn
                } else {
                    boss.respawnDelayTicks--
                    if (boss.respawnDelayTicks <= 0) {
                        // Pick a random hotspot
                        val occupied = activeBosses.map { it.currentHotspot }.toSet()
                        val available = WildernessHotspot.entries.filter { it !in occupied }
                        boss.currentHotspot =
                            available.randomOrNull() ?: WildernessHotspot.entries.random()
                        spawnBossNpc(boss)
                    }
                }
                continue
            }

            // Check stuck condition
            if (
                npc.coords == boss.lastCoords &&
                    (boss.state == BossState.ROAMING || boss.state == BossState.RETURNING)
            ) {
                boss.stuckTicks++
                if (boss.stuckTicks > 20) {
                    // Unstick: telejump to hotspot center
                    npc.telejump(collision, boss.currentHotspot.center)
                    boss.stuckTicks = 0
                    boss.state = BossState.ROAMING
                }
            } else {
                boss.stuckTicks = 0
                boss.lastCoords = npc.coords
            }

            // Check distance from center (leash of 25 tiles)
            val distFromCenter = distance(npc.coords, boss.currentHotspot.center)
            if (distFromCenter > 25) {
                npc.walk(boss.currentHotspot.center)
                boss.state = BossState.RETURNING
                continue
            }

            // If returning and reached near center, resume roaming
            if (boss.state == BossState.RETURNING) {
                if (distFromCenter <= 5) {
                    boss.state = BossState.ROAMING
                } else {
                    npc.walk(boss.currentHotspot.center)
                    continue
                }
            }

            // Migration check: every 1500 ticks (~15 min) if not in combat
            if (mapClock.cycle - boss.lastEngagedTick > 1500) {
                val occupied = activeBosses.map { it.currentHotspot }.toSet()
                val available = WildernessHotspot.entries.filter { it !in occupied }
                if (available.isNotEmpty()) {
                    val newHotspot = available.random()
                    boss.currentHotspot = newHotspot
                    boss.lastEngagedTick = mapClock.cycle
                    npc.telejump(collision, newHotspot.center)
                    boss.state = BossState.ROAMING
                    broadcastMessage(
                        "<col=ff0000>[Wilderness Danger]</col> ${boss.type.bossName} has migrated to ${newHotspot.displayName}!"
                    )
                    continue
                }
            }

            // Check for nearby players to hunt
            if (boss.state == BossState.ROAMING) {
                val nearbyPlayer = findNearbyPlayer(npc.coords, 14)
                if (nearbyPlayer != null) {
                    boss.state = BossState.HUNTING
                    boss.lastEngagedTick = mapClock.cycle
                    npc.facePlayer(nearbyPlayer)
                    npc.walk(nearbyPlayer.coords)
                    applyBossMechanic(boss, nearbyPlayer)
                } else {
                    // Roam along waypoints
                    if (boss.ticksInCurrentState % 8 == 0) {
                        val waypoints = boss.currentHotspot.waypoints
                        boss.waypointIndex = (boss.waypointIndex + 1) % waypoints.size
                        npc.walk(waypoints[boss.waypointIndex])
                    }
                }
            } else if (boss.state == BossState.HUNTING || boss.state == BossState.FIGHTING) {
                val nearbyPlayer = findNearbyPlayer(npc.coords, 16)
                if (nearbyPlayer == null || nearbyPlayer.coords.isWildernessSafeZone()) {
                    boss.state = BossState.ROAMING
                } else {
                    boss.lastEngagedTick = mapClock.cycle
                    npc.facePlayer(nearbyPlayer)
                    if (boss.ticksInCurrentState % boss.type.attackRate == 0) {
                        boss.state = BossState.FIGHTING
                        applyBossMechanic(boss, nearbyPlayer)
                    }
                }
            }
        }
    }

    private fun applyBossMechanic(boss: RoamingBossInstance, player: Player) {
        val npc = boss.npc ?: return
        launcher.launch(player) {
            // This is the shared first-pass E2E attack path. Protection prayers are applied by
            // StandardPlayerHitModifier inside queueHit; keep damage bounded until each boss has
            // a verified, cache-backed combat profile and phase machine.
            val damage =
                when (boss.type) {
                    RoamingBossType.CALLISTO -> 18
                    RoamingBossType.VENENATIS -> 16
                    RoamingBossType.VETION -> 20
                    RoamingBossType.CHAOS_ELEMENTAL -> 14
                    RoamingBossType.KING_BLACK_DRAGON -> 22
                    RoamingBossType.ABYSSAL_SIRE -> 18
                    RoamingBossType.CERBERUS -> 20
                    RoamingBossType.ALCHEMICAL_HYDRA -> 19
                    RoamingBossType.CORPOREAL_BEAST -> 24
                    RoamingBossType.SOTETSEG -> 26
                }
            queueHit(source = npc, delay = 1, type = HitType.Magic, damage = damage)
            when (boss.type) {
                RoamingBossType.CALLISTO -> {
                    mes("${boss.type.bossName} lets out an ear-splitting roar!")
                    spotanim(spotanims.sp_attackglow_red, height = 0)
                }
                RoamingBossType.VENENATIS -> {
                    mes("${boss.type.bossName} sprays debilitating venom web!")
                    spotanim(spotanims.smokepuff, height = 0)
                }
                RoamingBossType.VETION -> {
                    mes("${boss.type.bossName} calls down wrath from the skies!")
                    spotanim(spotanims.sp_attackglow_red, height = 0)
                }
                RoamingBossType.CHAOS_ELEMENTAL -> {
                    mes("${boss.type.bossName} destabilizes reality around you!")
                    spotanim(spotanims.smokepuff, height = 50)
                }
                RoamingBossType.KING_BLACK_DRAGON -> {
                    mes("${boss.type.bossName} engulfs you in ancient dragonfire!")
                    spotanim(spotanims.sp_attackglow_red, height = 30)
                }
                RoamingBossType.ABYSSAL_SIRE -> {
                    mes("${boss.type.bossName} spews noxious abyssal miasma!")
                    spotanim(spotanims.smokepuff, height = 0)
                }
                RoamingBossType.CERBERUS -> {
                    mes("${boss.type.bossName} snarls with demonic ferocity!")
                    spotanim(spotanims.sp_attackglow_red, height = 0)
                }
                RoamingBossType.ALCHEMICAL_HYDRA -> {
                    mes("${boss.type.bossName} spits corrosive alchemical acid!")
                    spotanim(spotanims.smokepuff, height = 20)
                }
                RoamingBossType.CORPOREAL_BEAST -> {
                    mes("${boss.type.bossName} stomps the ground, rattling your bones!")
                    spotanim(spotanims.smokepuff, height = 0)
                }
                RoamingBossType.SOTETSEG -> {
                    mes(
                        "<col=ff0000>${boss.type.bossName} launches a massive crimson shadow sphere!</col>"
                    )
                    spotanim(spotanims.sp_attackglow_red, height = 50)
                }
            }
        }
    }

    private fun findNearbyPlayer(coords: CoordGrid, maxDist: Int): Player? {
        for (player in players) {
            if (player.coords.level != coords.level) continue
            if (player.coords.isWildernessSafeZone()) continue
            val dist = distance(coords, player.coords)
            if (dist <= maxDist) {
                return player
            }
        }
        return null
    }

    private fun distance(a: CoordGrid, b: CoordGrid): Int {
        val dx = abs(a.x - b.x)
        val dz = abs(a.z - b.z)
        return maxOf(dx, dz)
    }

    private fun broadcastMessage(message: String) {
        for (player in players) {
            launcher.launch(player) { mes(message) }
        }
    }

    private companion object {
        private const val RESPAWN_DELAY_TICKS = 100
    }
}
