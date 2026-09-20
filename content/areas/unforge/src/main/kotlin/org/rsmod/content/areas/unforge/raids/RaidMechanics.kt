package org.rsmod.content.areas.unforge.raids

import kotlin.math.max
import kotlin.random.Random
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Identifies the scripted mechanic attached to a room. */
enum class RaidMechanicId {
    NONE,
    PRESSURE,
    MAIDEN,
    SWARM,
    SOTETSEG,
    XARPUS,
    BLOAT,
    VERZIK,
    TEKTON,
    VESPULA,
    VANGUARDS,
    MUTTADILE,
    MYSTICS,
    ICEDEMON,
    TIGHTROPE,
    OLM,
}

/**
 * Engine-facing facade handed to room mechanics every cycle. Wraps the services a mechanic needs so
 * the mechanic classes themselves stay small and inject-free.
 */
class RaidIo(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
    val random: Random,
) {
    fun resolve(type: NpcType): UnpackedNpcType = npcTypes[type]

    /**
     * Spawns a raid-owned npc inside [room]'s pad, scaled by the run's difficulty and [statScale],
     * and tracks it in [RaidRoomState.alive] when [countsForClear] is true.
     */
    fun spawn(
        run: RaidRun,
        room: RaidRoomState,
        type: NpcType,
        localX: Int,
        localZ: Int,
        statScale: Double = 1.0,
        countsForClear: Boolean = true,
    ): Npc {
        val unpacked = npcTypes[type]
        val npc = Npc(unpacked, room.local(localX, localZ))
        npc.mode = NpcMode.Wander
        val mult = run.core.difficulty.statMultiplier * statScale
        npc.baseAttackLvl = (unpacked.attack * mult).toInt()
        npc.baseStrengthLvl = (unpacked.strength * mult).toInt()
        npc.baseDefenceLvl = (unpacked.defence * mult).toInt()
        npc.baseHitpointsLvl = (unpacked.hitpoints * mult).toInt()
        npc.baseRangedLvl = (unpacked.ranged * mult).toInt()
        npc.baseMagicLvl = (unpacked.magic * mult).toInt()
        npc.attackLvl = npc.baseAttackLvl
        npc.strengthLvl = npc.baseStrengthLvl
        npc.defenceLvl = npc.baseDefenceLvl
        npc.hitpoints = npc.baseHitpointsLvl
        npc.rangedLvl = npc.baseRangedLvl
        npc.magicLvl = npc.baseMagicLvl
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        if (countsForClear) {
            room.alive += npc
        }
        return npc
    }

    fun remove(npc: Npc) {
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    fun transmog(npc: Npc, type: NpcType) {
        npc.transmog(npcTypes[type], Int.MAX_VALUE)
    }

    fun telejump(player: Player, dest: CoordGrid) {
        PathingEntityCommon.telejump(player, collision, dest)
    }

    /** Queues a delayed npc-sourced hit on [player]. */
    fun hit(player: Player, source: Npc, type: HitType, damage: Int, delay: Int = 1) {
        player.queueHit(source = source, delay = delay, type = type, damage = damage)
    }

    /** Queues a source-less (environment) hit on [player]. */
    fun hit(player: Player, type: HitType, damage: Int, delay: Int = 1) {
        player.queueHit(delay = delay, type = type, damage = damage)
    }

    /** Mechanic damage scaled by the run's difficulty. */
    fun scaledDamage(run: RaidRun, base: Int): Int =
        max(1, (base * run.core.difficulty.damageMultiplier).toInt())

    /** Mechanic cooldown in cycles, shortened on Expert. */
    fun cooldown(run: RaidRun, baseCycles: Int): Int =
        max(1, baseCycles * 10_000 / run.core.difficulty.mechanicSpeedBps)

    fun msg(player: Player, text: String) = player.mes(text)

    fun msgAll(run: RaidRun, text: String) {
        for (member in run.players.values) {
            member.mes(text)
        }
    }

    /** Alive members currently standing inside [room]'s pad. */
    fun membersInRoom(run: RaidRun, room: RaidRoomState): List<Player> =
        run.aliveMembers().filter { inRoom(room, it.coords) }

    fun inRoom(room: RaidRoomState, coords: CoordGrid): Boolean =
        coords.x in room.origin.x..room.origin.x + room.widthTiles - 1 &&
            coords.z in room.origin.z..room.origin.z + room.lengthTiles - 1

    fun randomAlive(run: RaidRun, room: RaidRoomState): Player? =
        membersInRoom(run, room).randomOrNull(random)

    fun randomRoomTile(room: RaidRoomState, margin: Int = 2): CoordGrid {
        val width = (room.widthTiles - margin * 2).coerceAtLeast(1)
        val length = (room.lengthTiles - margin * 2).coerceAtLeast(1)
        return room.local(margin + random.nextInt(width), margin + random.nextInt(length))
    }

    fun bossOf(room: RaidRoomState): Npc? = room.alive.firstOrNull()

    /** Fraction (0-100) of the npc's remaining hitpoints. */
    fun hpPercent(npc: Npc): Int =
        if (npc.baseHitpointsLvl <= 0) 0 else npc.hitpoints * 100 / npc.baseHitpointsLvl

    companion object {
        /** Room pads are 2x2 zones = 16x16 tiles. */
        const val PAD_TILES = 16
    }
}

/** Scripted per-room behaviour, ticked once per game cycle by the raid driver. */
interface RaidRoomMechanic {
    /** Called once when the room is activated (initial npcs already spawned). */
    fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {}

    /** Called every cycle while the room is active and uncleared. */
    fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {}

    /** Called when a room npc dies, before the cleared check runs. */
    fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {}

    /**
     * Return `true` while the room must not clear even when [RaidRoomState.alive] is empty - e.g. a
     * wave room that still has waves to spawn.
     */
    fun blocksClear(): Boolean = false

    /**
     * Hook applied to incoming hits on room npcs (via `onModifyNpcHit` in the raid script). Return
     * the damage actually allowed through - e.g. the Olm head shrugs most damage off while its
     * hands live.
     */
    fun modifyIncomingDamage(
        io: RaidIo,
        run: RaidRun,
        room: RaidRoomState,
        npc: Npc,
        damage: Int,
    ): Int = damage
}

private class Hazard(val tile: CoordGrid, val radius: Int, var warmup: Int, var ticksLeft: Int)

/** Shared timed ground-hazard helper: telegraph, then periodic damage inside [radius]. */
private class HazardPool(
    private val warmupCycles: Int,
    private val activeCycles: Int,
    private val hitEvery: Int,
) {
    private val hazards = mutableListOf<Hazard>()
    private var cycle = 0

    fun add(tile: CoordGrid, radius: Int) {
        hazards += Hazard(tile, radius, warmupCycles, activeCycles)
    }

    fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState, damage: Int) {
        cycle++
        val iterator = hazards.iterator()
        while (iterator.hasNext()) {
            val hazard = iterator.next()
            if (hazard.warmup > 0) {
                hazard.warmup--
                continue
            }
            hazard.ticksLeft--
            if (cycle % hitEvery == 0) {
                for (member in io.membersInRoom(run, room)) {
                    if (member.coords.chebyshevDistance(hazard.tile) <= hazard.radius) {
                        io.hit(member, HitType.Typeless, io.scaledDamage(run, damage))
                    }
                }
            }
            if (hazard.ticksLeft <= 0) {
                iterator.remove()
            }
        }
    }
}

/** Tracks how long each member has stood still; anti-AFK pressure shared by several bosses. */
private class StationaryTracker(private val graceCycles: Int) {
    private val last = mutableMapOf<Int, CoordGrid>()
    private val still = mutableMapOf<Int, Int>()

    /** @return members that have not moved for [graceCycles]. */
    fun offenders(run: RaidRun, room: RaidRoomState, io: RaidIo): List<Player> {
        val offenders = mutableListOf<Player>()
        for (member in io.membersInRoom(run, room)) {
            val id = member.slotId
            if (last[id] == member.coords) {
                val cycles = (still[id] ?: 0) + 1
                still[id] = cycles
                if (cycles == graceCycles) {
                    offenders += member
                }
            } else {
                last[id] = member.coords
                still[id] = 0
            }
        }
        return offenders
    }
}

/** Generic light pressure used by plain combat rooms. */
private class PressureMechanic(private val base: Int = 4) : RaidRoomMechanic {
    private var cooldown = 0

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (--cooldown > 0) return
        cooldown = io.cooldown(run, 10)
        val target = io.randomAlive(run, room) ?: return
        val source = room.alive.randomOrNull(io.random) ?: return
        io.hit(target, source, HitType.Melee, io.scaledDamage(run, base))
    }
}

/** Maiden-style boss: telegraphed ground hazards, hp thresholds spawning nylocas, anti-camp. */
private class MaidenMechanic : RaidRoomMechanic {
    private val hazards = HazardPool(warmupCycles = 4, activeCycles = 12, hitEvery = 3)
    private val tracker = StationaryTracker(graceCycles = 30)
    private var hazardCooldown = 0
    private var spawn70 = false
    private var spawn40 = false

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        val hp = io.hpPercent(boss)
        if (!spawn70 && hp <= 70) {
            spawn70 = true
            io.msgAll(run, "<col=ff3333>The Maiden calls her brood!</col>")
            io.spawn(run, room, RaidNpcs.tobNylocasMelee, 4, 8, statScale = 0.5)
            io.spawn(run, room, RaidNpcs.tobNylocasRanged, 11, 8, statScale = 0.5)
        }
        if (!spawn40 && hp <= 40) {
            spawn40 = true
            io.msgAll(run, "<col=ff3333>The Maiden bleeds - the arena erupts!</col>")
            io.spawn(run, room, RaidNpcs.tobNylocasMagic, 7, 12, statScale = 0.5)
            repeat(2) { hazards.add(io.randomRoomTile(room, margin = 2), radius = 1) }
        }
        if (--hazardCooldown <= 0) {
            hazardCooldown = io.cooldown(run, 22)
            val anchor = io.randomAlive(run, room)
            val tile =
                anchor?.coords?.translate(io.random.nextInt(7) - 3, io.random.nextInt(7) - 3)
                    ?: io.randomRoomTile(room, margin = 2)
            hazards.add(tile, radius = 1)
        }
        hazards.tick(io, run, room, damage = 12)
        for (camper in tracker.offenders(run, room, io)) {
            io.msg(camper, "<col=ff3333>Blood pools beneath your feet!</col>")
            io.hit(camper, boss, HitType.Magic, io.scaledDamage(run, 10))
        }
    }
}

/** Pestilent/nylocas-style wave room: sequential waves, all must die. */
private class SwarmMechanic : RaidRoomMechanic {
    private var wave = 0
    private var spawnCooldown = 0

    /** Three extra waves after the initial spawn. */
    private val pendingWaves
        get() = TOTAL_WAVES - wave

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        wave = 1 // spec.spawns already seeded wave 1
        io.msgAll(run, "<col=ffb84d>Wave 1/$TOTAL_WAVES - the nylocas swarm!</col>")
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (room.alive.isNotEmpty() || pendingWaves <= 0) return
        if (++spawnCooldown < io.cooldown(run, 15)) return
        spawnCooldown = 0
        wave++
        io.msgAll(run, "<col=ffb84d>Wave $wave/$TOTAL_WAVES - more nylocas pour in!</col>")
        val styles =
            listOf(RaidNpcs.tobNylocasMelee, RaidNpcs.tobNylocasRanged, RaidNpcs.tobNylocasMagic)
        val count = 3 + (wave - 1)
        repeat(count) { index ->
            val type = styles[index % styles.size]
            io.spawn(
                run,
                room,
                type,
                3 + io.random.nextInt(10),
                3 + io.random.nextInt(10),
                statScale = 0.45 + wave * 0.1,
            )
        }
        if (wave == TOTAL_WAVES) {
            io.msgAll(run, "<col=ff3333>A pestilent behemoth leads the final wave!</col>")
            io.spawn(run, room, RaidNpcs.tobNylocasBoss, 8, 11, statScale = 0.8)
        }
    }

    override fun blocksClear(): Boolean = pendingWaves > 0

    private companion object {
        const val TOTAL_WAVES = 3
    }
}

/** Sotetseg-style boss: shadow-realm teleports and wrong-move punishment. */
private class SotetsegMechanic : RaidRoomMechanic {
    private var phase = 0
    private var specialCooldown = 0
    private var marked: Player? = null
    private var markedFrom: CoordGrid? = null
    private var markedCycles = 0

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        val hp = io.hpPercent(boss)
        if (phase < 1 && hp <= 66) {
            phase = 1
            io.msgAll(run, "<col=ff3333>Sotetseg sinks into the shadow realm!</col>")
        }
        if (phase < 2 && hp <= 33) {
            phase = 2
            io.msgAll(run, "<col=ff3333>Sotetseg's dark power swells!</col>")
        }
        val marked = this.marked
        if (marked != null) {
            markedCycles++
            if (markedCycles >= 12) {
                val from = markedFrom
                this.marked = null
                this.markedFrom = null
                if (from != null && marked.coords.chebyshevDistance(from) < 4) {
                    io.msg(marked, "<col=ff3333>The shadows punish your hesitation!</col>")
                    io.hit(marked, boss, HitType.Magic, io.scaledDamage(run, 22))
                } else {
                    io.msg(marked, "<col=66ccff>You escape the shadow realm.</col>")
                }
            }
            return
        }
        if (--specialCooldown <= 0) {
            specialCooldown = io.cooldown(run, if (phase >= 1) 45 else 60)
            val target = io.randomAlive(run, room) ?: return
            this.marked = target
            this.markedFrom = target.coords
            this.markedCycles = 0
            io.telejump(target, io.randomRoomTile(room, margin = 3))
            io.msg(target, "<col=ff3333>Sotetseg drags you into the shadow realm - run!</col>")
            for (other in io.membersInRoom(run, room)) {
                if (other !== target) {
                    io.hit(other, boss, HitType.Magic, io.scaledDamage(run, 9), delay = 3)
                }
            }
        }
    }
}

/** Xarpus-style boss: constant area pressure plus a gaze you must move against. */
private class XarpusMechanic : RaidRoomMechanic {
    private val hazards = HazardPool(warmupCycles = 3, activeCycles = 8, hitEvery = 2)
    private val tracker = StationaryTracker(graceCycles = 24)
    private var areaCooldown = 0
    private var pressureCooldown = 0
    private var gaze: Player? = null
    private var gazeFrom: CoordGrid? = null
    private var gazeCycles = 0

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        val gaze = this.gaze
        if (gaze != null) {
            if (++gazeCycles >= 12) {
                val from = gazeFrom
                this.gaze = null
                this.gazeFrom = null
                if (from != null && gaze.coords.chebyshevDistance(from) < 3) {
                    io.msg(gaze, "<col=ff3333>Xarpus's gaze catches you standing still!</col>")
                    io.hit(gaze, boss, HitType.Ranged, io.scaledDamage(run, 20))
                } else {
                    io.msg(gaze, "<col=66ccff>You break Xarpus's gaze.</col>")
                }
            }
        } else if (--areaCooldown <= 0) {
            areaCooldown = io.cooldown(run, 40)
            val target = io.randomAlive(run, room) ?: return
            this.gaze = target
            this.gazeFrom = target.coords
            this.gazeCycles = 0
            io.msg(target, "<col=ffb84d>Xarpus fixes its gaze on you - keep moving!</col>")
        }
        if (--pressureCooldown <= 0) {
            pressureCooldown = io.cooldown(run, 16)
            val target = io.randomAlive(run, room)
            if (target != null) {
                io.hit(target, boss, HitType.Ranged, io.scaledDamage(run, 6))
            }
            hazards.add(io.randomRoomTile(room, margin = 2), radius = 1)
        }
        hazards.tick(io, run, room, damage = 8)
        for (camper in tracker.offenders(run, room, io)) {
            io.msg(camper, "<col=ff3333>The ground beneath you withers!</col>")
            io.hit(camper, boss, HitType.Magic, io.scaledDamage(run, 9))
        }
    }
}

/** Bloat-style boss: stomps around the room, flattening anyone it walks over. */
private class BloatMechanic : RaidRoomMechanic {
    private var modeCycles = 0
    private var walking = true
    private var walkCooldown = 0
    private var stompCooldown = 0

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        modeCycles = 18
        io.msgAll(run, "<col=ffb84d>The Bloat is on the move - stay out of its path!</col>")
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        if (--modeCycles <= 0) {
            walking = !walking
            modeCycles = if (walking) 18 else 24
            io.msgAll(
                run,
                if (walking) "<col=ffb84d>The Bloat stomps around the room!</col>"
                else "<col=66ccff>The Bloat slows to a stop...</col>",
            )
        }
        if (!walking) return
        if (--walkCooldown <= 0) {
            walkCooldown = io.cooldown(run, 10)
            boss.walk(io.randomRoomTile(room, margin = 3))
        }
        if (--stompCooldown <= 0) {
            stompCooldown = io.cooldown(run, 4)
            for (member in io.membersInRoom(run, room)) {
                if (member.coords.chebyshevDistance(boss.coords) <= 2) {
                    io.hit(member, boss, HitType.Melee, io.scaledDamage(run, 11))
                }
            }
        }
    }
}

/** Verzik-style final boss: three phases with escalating attack patterns and adds. */
private class VerzikMechanic : RaidRoomMechanic {
    private var phase = 0
    private var attackCooldown = 0
    private var areaCooldown = 0
    private val hazards = HazardPool(warmupCycles = 3, activeCycles = 9, hitEvery = 3)

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        val hp = io.hpPercent(boss)
        if (phase < 1 && hp <= 66) {
            phase = 1
            io.transmog(boss, RaidNpcs.verzikP2)
            io.msgAll(run, "<col=ff3333>Verzik descends from her throne!</col>")
            io.spawn(run, room, RaidNpcs.verzikNylocasMelee, 4, 6, statScale = 0.5)
        }
        if (phase < 2 && hp <= 33) {
            phase = 2
            io.transmog(boss, RaidNpcs.verzikP3)
            io.msgAll(run, "<col=ff3333>Verzik erupts into her final form!</col>")
            io.spawn(run, room, RaidNpcs.verzikNylocasMelee, 4, 6, statScale = 0.6)
            io.spawn(run, room, RaidNpcs.verzikNylocasRanged, 11, 6, statScale = 0.6)
        }
        if (--attackCooldown <= 0) {
            attackCooldown = io.cooldown(run, if (phase == 2) 5 else 8 - phase)
            val target = io.randomAlive(run, room) ?: return
            val type =
                when {
                    phase == 0 -> HitType.Ranged
                    phase == 1 && io.random.nextBoolean() -> HitType.Magic
                    phase == 1 -> HitType.Ranged
                    io.random.nextInt(3) == 0 -> HitType.Melee
                    else -> HitType.Magic
                }
            val base = if (phase == 2) 14 else 9 + phase * 2
            io.hit(target, boss, type, io.scaledDamage(run, base))
        }
        if (phase >= 1 && --areaCooldown <= 0) {
            areaCooldown = io.cooldown(run, if (phase == 2) 18 else 26)
            hazards.add(io.randomRoomTile(room, margin = 2), radius = 1)
            if (phase == 2) {
                hazards.add(io.randomRoomTile(room, margin = 2), radius = 1)
            }
        }
        hazards.tick(io, run, room, damage = 10)
    }
}

/** Tekton-style miniboss: advances on the nearest target, heavy melee, enrages at low hp. */
private class TektonMechanic : RaidRoomMechanic {
    private var enraged = false
    private var advanceCooldown = 0
    private var meleeCooldown = 0

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss = io.bossOf(room) ?: return
        if (!enraged && io.hpPercent(boss) <= 30) {
            enraged = true
            io.transmog(boss, RaidNpcs.tektonEnraged)
            io.msgAll(run, "<col=ff3333>Tekton overheats and enrages!</col>")
        }
        if (--advanceCooldown <= 0) {
            advanceCooldown = io.cooldown(run, if (enraged) 8 else 12)
            val target =
                io.membersInRoom(run, room).minByOrNull { it.coords.chebyshevDistance(boss.coords) }
            if (target != null && target.coords.chebyshevDistance(boss.coords) > 2) {
                boss.walk(target.coords)
            }
        }
        if (--meleeCooldown <= 0) {
            meleeCooldown = io.cooldown(run, if (enraged) 4 else 6)
            for (member in io.membersInRoom(run, room)) {
                if (member.coords.chebyshevDistance(boss.coords) <= 2) {
                    io.hit(
                        member,
                        boss,
                        HitType.Melee,
                        io.scaledDamage(run, if (enraged) 16 else 11),
                    )
                }
            }
        }
    }
}

/** Vespula-style room: a portal heals the boss until destroyed; spawns vespine adds. */
private class VespulaMechanic : RaidRoomMechanic {
    private var portalDown = false
    private var healCooldown = 0
    private var addCooldown = 0
    private var adds = 0

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        io.msgAll(run, "<col=ffb84d>Destroy the portal to stop Vespula's regeneration!</col>")
    }

    override fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {
        if (npc.type.id == io.resolve(RaidNpcs.vespulaPortal).id) {
            portalDown = true
            val boss = room.alive.firstOrNull()
            if (boss != null) {
                boss.baseDefenceLvl = (boss.baseDefenceLvl * 0.7).toInt()
                boss.defenceLvl = boss.baseDefenceLvl
            }
            io.msgAll(run, "<col=66ccff>The portal collapses - Vespula is vulnerable!</col>")
        }
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (portalDown) return
        val boss =
            room.alive.firstOrNull { it.type.id != io.resolve(RaidNpcs.vespulaPortal).id } ?: return
        if (--healCooldown <= 0) {
            healCooldown = io.cooldown(run, 30)
            if (boss.hitpoints < boss.baseHitpointsLvl) {
                boss.hitpoints =
                    (boss.hitpoints + boss.baseHitpointsLvl / 12).coerceAtMost(
                        boss.baseHitpointsLvl
                    )
            }
        }
        if (--addCooldown <= 0 && adds < 3) {
            addCooldown = io.cooldown(run, 40)
            adds++
            io.spawn(
                run,
                room,
                RaidNpcs.vespulaVespine,
                4 + io.random.nextInt(8),
                4,
                statScale = 0.5,
            )
            io.msgAll(run, "<col=ffb84d>A vespine hatches to defend the portal!</col>")
        }
    }
}

/** Vanguards-style room: melee/ranged/magic trio that empower each other on death. */
private class VanguardsMechanic : RaidRoomMechanic {
    private var attackCooldown = 0

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        io.msgAll(run, "<col=ffb84d>The Vanguards rise - fell them together!</col>")
    }

    override fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {
        for (survivor in room.alive) {
            survivor.baseStrengthLvl = (survivor.baseStrengthLvl * 1.15).toInt()
            survivor.strengthLvl = survivor.baseStrengthLvl
            survivor.baseAttackLvl = (survivor.baseAttackLvl * 1.15).toInt()
            survivor.attackLvl = survivor.baseAttackLvl
        }
        if (room.alive.isNotEmpty()) {
            io.msgAll(run, "<col=ff3333>The remaining Vanguards are empowered!</col>")
        }
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (--attackCooldown > 0) return
        attackCooldown = io.cooldown(run, 9)
        for (vanguard in room.alive) {
            val target = io.randomAlive(run, room) ?: break
            val style =
                when (vanguard.type.id) {
                    io.resolve(RaidNpcs.vanguardRanged).id -> HitType.Ranged
                    io.resolve(RaidNpcs.vanguardMagic).id -> HitType.Magic
                    else -> HitType.Melee
                }
            io.hit(target, vanguard, style, io.scaledDamage(run, 8))
        }
    }
}

/** Muttadile-style miniboss: junior form, then the big muttadile with relentless melee. */
private class MuttadileMechanic : RaidRoomMechanic {
    private var bigSpawned = false
    private var attackCooldown = 0

    override fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {
        if (!bigSpawned && npc.type.id == io.resolve(RaidNpcs.muttadileJunior).id) {
            bigSpawned = true
            io.msgAll(run, "<col=ff3333>The Muttadile erupts from the water!</col>")
            io.spawn(run, room, RaidNpcs.muttadile, 8, 10)
        }
    }

    override fun blocksClear(): Boolean = !bigSpawned

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val boss =
            room.alive.firstOrNull { it.type.id == io.resolve(RaidNpcs.muttadile).id }
                ?: room.alive.firstOrNull()
                ?: return
        val fast = io.hpPercent(boss) <= 50
        if (--attackCooldown <= 0) {
            attackCooldown = io.cooldown(run, if (fast) 5 else 7)
            for (member in io.membersInRoom(run, room)) {
                if (member.coords.chebyshevDistance(boss.coords) <= 3) {
                    io.hit(member, boss, HitType.Melee, io.scaledDamage(run, 12))
                }
            }
        }
    }
}

/** Mystics-style room: several magic-painteinen npcs rotating targets. */
private class MysticsMechanic : RaidRoomMechanic {
    private var attackCooldown = 0

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (--attackCooldown > 0) return
        attackCooldown = io.cooldown(run, 8)
        for (mystic in room.alive) {
            val target = io.randomAlive(run, room) ?: break
            io.hit(target, mystic, HitType.Magic, io.scaledDamage(run, 7))
        }
    }
}

/**
 * Ice demon-style room: kill [KINDLING_REQUIRED] icefiends to stoke the flames, which wakes the
 * dormant demon into its combat form.
 */
private class IceDemonMechanic : RaidRoomMechanic {
    private var kindled = 0
    private var fiendsSpawned = 0
    private var respawnCooldown = 0
    private var dormant: Npc? = null
    private var demonSpawned = false

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        dormant = room.alive.firstOrNull { it.type.id == io.resolve(RaidNpcs.iceDemonDormant).id }
        dormant?.let { room.alive.remove(it) }
        fiendsSpawned = room.alive.count { it.type.id == io.resolve(RaidNpcs.iceFiend).id }
        io.msgAll(
            run,
            "<col=ffb84d>The ice demon slumbers - slay $KINDLING_REQUIRED icefiends to stoke " +
                "the flames!</col>",
        )
    }

    override fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {
        if (npc.type.id == io.resolve(RaidNpcs.iceFiend).id && !demonSpawned) {
            kindled++
            if (kindled >= KINDLING_REQUIRED) {
                awaken(io, run, room)
            } else {
                io.msgAll(run, "<col=66ccff>The flames grow... ($kindled/$KINDLING_REQUIRED)</col>")
            }
        }
    }

    private fun awaken(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        demonSpawned = true
        dormant?.let {
            io.remove(it)
            room.alive.remove(it)
        }
        io.msgAll(run, "<col=ff3333>The ice demon awakens!</col>")
        io.spawn(run, room, RaidNpcs.iceDemon, 8, 10)
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        if (demonSpawned) return
        // Keep the kindling supply coming until the fire is lit.
        val fiendId = io.resolve(RaidNpcs.iceFiend).id
        val fiendsAlive = room.alive.count { it.type.id == fiendId }
        if (fiendsAlive == 0 && fiendsSpawned < KINDLING_REQUIRED + 2) {
            if (++respawnCooldown >= io.cooldown(run, 20)) {
                respawnCooldown = 0
                fiendsSpawned++
                io.spawn(
                    run,
                    room,
                    RaidNpcs.iceFiend,
                    3 + io.random.nextInt(10),
                    5,
                    statScale = 0.5,
                )
            }
        }
    }

    private companion object {
        const val KINDLING_REQUIRED = 4
    }
}

/**
 * Olm-style final boss: head plus two destructible hands. The head resists damage while hands live
 * (enforced via `onModifyNpcHit` in the raid script); killing both opens the burn phase.
 */
private class OlmMechanic : RaidRoomMechanic {
    private var phase = 0
    private var attackCooldown = 0
    private var areaCooldown = 0
    private val hazards = HazardPool(warmupCycles = 3, activeCycles = 10, hitEvery = 3)

    fun handsAlive(io: RaidIo, room: RaidRoomState): Boolean =
        room.alive.any {
            val id = it.type.id
            id == io.resolve(RaidNpcs.olmHandLeft).id || id == io.resolve(RaidNpcs.olmHandRight).id
        }

    override fun onStart(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        io.msgAll(
            run,
            "<col=ffb84d>The Great Olm rises - destroy its hands to expose the head!</col>",
        )
    }

    override fun modifyIncomingDamage(
        io: RaidIo,
        run: RaidRun,
        room: RaidRoomState,
        npc: Npc,
        damage: Int,
    ): Int {
        val headId = io.resolve(RaidNpcs.olmHead).id
        if (npc.type.id == headId && handsAlive(io, room)) {
            return damage * HEAD_GUARDED_NUM / HEAD_GUARDED_DEN
        }
        return damage
    }

    override fun onKill(io: RaidIo, run: RaidRun, room: RaidRoomState, npc: Npc) {
        if (npc.type.id == io.resolve(RaidNpcs.olmHead).id) {
            // The head's death drags the hands down with it.
            val hands =
                room.alive.filter {
                    it.type.id == io.resolve(RaidNpcs.olmHandLeft).id ||
                        it.type.id == io.resolve(RaidNpcs.olmHandRight).id
                }
            for (hand in hands) {
                room.alive.remove(hand)
                io.remove(hand)
            }
            return
        }
        if (!handsAlive(io, room) && phase == 0) {
            phase = 1
            io.msgAll(run, "<col=66ccff>The Great Olm's head is exposed - burn it down!</col>")
        }
    }

    override fun tick(io: RaidIo, run: RaidRun, room: RaidRoomState) {
        val head =
            room.alive.firstOrNull { it.type.id == io.resolve(RaidNpcs.olmHead).id } ?: return
        if (phase == 1 && io.hpPercent(head) <= 50) {
            phase = 2
            io.msgAll(run, "<col=ff3333>The Great Olm writhes in fury!</col>")
        }
        if (--attackCooldown <= 0) {
            attackCooldown = io.cooldown(run, if (phase == 2) 5 else 9)
            val target = io.randomAlive(run, room) ?: return
            val type = if (io.random.nextBoolean()) HitType.Magic else HitType.Ranged
            io.hit(target, head, type, io.scaledDamage(run, if (phase == 2) 13 else 9))
        }
        if (--areaCooldown <= 0) {
            areaCooldown = io.cooldown(run, if (phase == 2) 16 else 26)
            hazards.add(io.randomRoomTile(room, margin = 2), radius = 1)
        }
        hazards.tick(io, run, room, damage = 9)
    }

    companion object {
        /** Damage multiplier applied to head hits while a hand is alive (20%). */
        const val HEAD_GUARDED_NUM = 1
        const val HEAD_GUARDED_DEN = 5
    }
}

/** Instantiates the scripted mechanic for a room spec. */
fun RaidMechanicId.create(): RaidRoomMechanic =
    when (this) {
        RaidMechanicId.NONE,
        RaidMechanicId.PRESSURE,
        RaidMechanicId.TIGHTROPE -> PressureMechanic()
        RaidMechanicId.MAIDEN -> MaidenMechanic()
        RaidMechanicId.SWARM -> SwarmMechanic()
        RaidMechanicId.SOTETSEG -> SotetsegMechanic()
        RaidMechanicId.XARPUS -> XarpusMechanic()
        RaidMechanicId.BLOAT -> BloatMechanic()
        RaidMechanicId.VERZIK -> VerzikMechanic()
        RaidMechanicId.TEKTON -> TektonMechanic()
        RaidMechanicId.VESPULA -> VespulaMechanic()
        RaidMechanicId.VANGUARDS -> VanguardsMechanic()
        RaidMechanicId.MUTTADILE -> MuttadileMechanic()
        RaidMechanicId.MYSTICS -> MysticsMechanic()
        RaidMechanicId.ICEDEMON -> IceDemonMechanic()
        RaidMechanicId.OLM -> OlmMechanic()
    }
