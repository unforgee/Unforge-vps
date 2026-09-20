package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HunterScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val locTypes: LocTypeList,
    private val seqTypes: SeqTypeList,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val clock: MapClock,
    private val random: GameRandom,
    private val rewards: SkillingRewards,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private data class Trap(
        val owner: Player,
        val tool: Int,
        var loc: LocInfo,
        val created: Int,
        var catch: Quarry? = null,
        var failed: Boolean = false,
    )

    private val traps = mutableMapOf<CoordGrid, Trap>()

    override fun ScriptContext.startup() {
        for (npc in npcTypes.values) {
            val quarry = HunterData.byName[npc.name.lowercase()] ?: continue
            if (quarry.jar != null) onOpNpc1(npc) { catchNet(it.npc, quarry) }
        }
        for (tool in listOf(10006, 10008)) onOpHeld1(objTypes.getValue(tool)) { lay(tool) }
        val trapTypes =
            listOf(9345, 9344, 9380, 9385) +
                HunterData.quarry.map { it.fullTrap }.filter { it != 0 }
        for (id in trapTypes.distinct()) {
            onOpLoc1(locTypes.getValue(id)) { collect(it.loc.coords) }
            onOpLoc2(locTypes.getValue(id)) { collect(it.loc.coords) }
        }
        onPlayerSoftTimer(HunterTimers.traps) { tick(player) }
        onPlayerLogout {
            // Active traps are session objects. Return the reusable tool before account saving.
            for (trap in traps.values.filter { it.owner === player }.toList()) {
                remove(trap)
                player.invAddOrDrop(objRepo, objTypes.getValue(trap.tool), 1)
            }
        }
    }

    private fun ProtectedAccess.hasNet(): Boolean =
        listOf(10010, 11259).any {
            invTotal(inv, objTypes.getValue(it)) > 0 || player.righthand?.id == it
        }

    private suspend fun ProtectedAccess.catchNet(npc: Npc, quarry: Quarry) {
        if (player.stat(stats.hunter) < quarry.level) {
            mes("You need a Hunter level of ${quarry.level} to catch this creature.")
            return
        }
        if (!hasNet()) {
            mes("You need a butterfly net to catch this creature.")
            return
        }
        val jar = objTypes.getValue(checkNotNull(quarry.jar))
        if (invTotal(inv, jar) == 0) {
            mes("You need an empty ${jar.name.lowercase()}.")
            return
        }
        val origin = npc.coords
        try {
            anim(seqTypes.getValue(5209))
            delay(3)
            if (!npc.isSlotAssigned || npc.isInvisible || npc.coords != origin || !hasNet()) return
            if (player.stat(stats.hunter) < quarry.level || invTotal(inv, jar) == 0) return
            if (random.of(256) >= quarry.chance(player.stat(stats.hunter))) {
                mes("The creature escapes your net.")
                return
            }
            // A filled inventory is fine: the empty jar is replaced by the captured creature.
            if (!invDel(inv, jar, 1).success) return
            npcRepo.del(npc, quarry.respawn)
            rewards.grant(this, "HUNTER", objTypes.getValue(quarry.product))
            statAdvance(stats.hunter, quarry.xp * xpMods.get(player, stats.hunter))
            mes("You catch the ${quarry.name}.")
        } finally {
            resetAnim()
        }
    }

    private suspend fun ProtectedAccess.lay(tool: Int) {
        val level = player.stat(stats.hunter)
        if (tool == 10008 && level < 53) {
            mes("You need a Hunter level of 53 to trap chinchompas.")
            return
        }
        if (traps.values.count { it.owner === player } >= HunterData.trapLimit(level)) {
            mes("You already have as many traps as your Hunter level allows.")
            return
        }
        val coords = player.coords
        if (coords in traps || locRepo.findAll(coords).any { it.layer == 2 }) {
            mes("There is no room to lay a trap here.")
            return
        }
        val nearby =
            nearby(coords).any { npc ->
                val quarry = HunterData.byName[npc.type.name.lowercase()]
                quarry != null && quarry.tool == tool && level >= quarry.level
            }
        if (!nearby) {
            mes("Find a suitable hunting creature before laying your trap.")
            return
        }
        val item = objTypes.getValue(tool)
        if (invTotal(inv, item) == 0) return
        anim(seqTypes.getValue(5212))
        delay(3)
        resetAnim()
        if (
            player.coords != coords ||
                coords in traps ||
                locRepo.findAll(coords).any { it.layer == 2 }
        )
            return
        if (!invDel(inv, item, 1).success) return
        val loc = LocInfo(2, coords, LocEntity(HunterData.emptyTrap(tool), 10, 0))
        if (!locRepo.add(loc, Int.MAX_VALUE)) {
            invAdd(inv, item)
            return
        }
        traps[coords] = Trap(player, tool, loc, clock.cycle)
        player.softTimer(HunterTimers.traps, 5)
        mes("You set the trap. Check it after a creature has approached.")
    }

    private fun nearby(coords: CoordGrid): Sequence<Npc> =
        npcRepo.findAll(ZoneKey.from(coords), 1).filter {
            it.isSlotAssigned &&
                it.isVisible &&
                it.coords.level == coords.level &&
                kotlin.math.abs(it.coords.x - coords.x) <= 4 &&
                kotlin.math.abs(it.coords.z - coords.z) <= 4
        }

    private fun tick(player: Player) {
        for (trap in traps.values.filter { it.owner === player }.toList()) {
            val age = clock.cycle - trap.created
            if (age >= 200) {
                remove(trap)
                player.invAddOrDrop(objRepo, objTypes.getValue(trap.tool), 1)
                continue
            }
            if (trap.failed || trap.catch != null || age < 10) continue
            val prey =
                nearby(trap.loc.coords).firstOrNull {
                    val quarry = HunterData.byName[it.type.name.lowercase()]
                    quarry != null &&
                        quarry.tool == trap.tool &&
                        player.stat(stats.hunter) >= quarry.level
                } ?: continue
            val quarry = HunterData.byName.getValue(prey.type.name.lowercase())
            if (random.of(256) < quarry.chance(player.stat(stats.hunter))) {
                trap.catch = quarry
                npcRepo.del(prey, quarry.respawn)
                transform(trap, quarry.fullTrap)
            } else {
                trap.failed = true
                transform(trap, HunterData.failedTrap(trap.tool))
            }
        }
    }

    private fun transform(trap: Trap, id: Int) {
        locRepo.del(trap.loc, Int.MAX_VALUE)
        trap.loc = trap.loc.copy(entity = LocEntity(id, 10, 0))
        check(locRepo.add(trap.loc, Int.MAX_VALUE)) { "Could not transform hunter trap." }
    }

    private fun ProtectedAccess.collect(coords: CoordGrid) {
        val trap = traps[coords] ?: return
        if (trap.owner !== player) {
            mes("This is someone else's trap.")
            return
        }
        val quarry = trap.catch
        val slots = if (quarry == null) 1 else if (trap.tool == 10006) 4 else 2
        if (inv.freeSpace() < slots) {
            mes("You need $slots free inventory spaces to recover this trap.")
            return
        }
        remove(trap)
        invAdd(inv, objTypes.getValue(trap.tool))
        if (quarry != null) {
            rewards.grant(
                this,
                "HUNTER",
                objTypes.getValue(quarry.product),
                if (trap.tool == 10006) 5 else 1,
            )
            if (trap.tool == 10006) {
                invAdd(inv, objTypes.getValue(526))
                invAdd(inv, objTypes.getValue(9978))
            }
            statAdvance(stats.hunter, quarry.xp * xpMods.get(player, stats.hunter))
            mes("You retrieve your ${quarry.name} and recover the trap.")
        } else mes("You recover the trap.")
        anim(seqTypes.getValue(827))
    }

    private fun remove(trap: Trap) {
        traps.remove(trap.loc.coords)
        locRepo.del(trap.loc, Int.MAX_VALUE)
    }
}
