package org.rsmod.content.areas.unforge.home

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Edgeville "home" extras:
 * - Spawns a prayer altar inside the Edgeville general store building.
 * - Binds the "Pray" op on common altar loc types so altars restore prayer points.
 */
object EdgevilleHomeLocs : LocReferences() {
    val altar = find("altar")
    val guthixAltar = find("guthix_altar")
    val chaosAltar = find("chaosaltar")
    val monksAltar = find("monks_altar")
    val zarosAltar = find("dt_zaros_altar")
    val clanwarsAltar = find("clanwars_tournament_altar")
    val elfAltar = find("elf_village_altar")
    val deviousAltar = find("devious_altar")

    val all =
        listOf(
            altar,
            guthixAltar,
            chaosAltar,
            monksAltar,
            zarosAltar,
            clanwarsAltar,
            elfAltar,
            deviousAltar,
        )
}

class EdgevilleHome
@Inject
constructor(private val locRegistry: LocRegistry, private val locTypes: LocTypeList) :
    PluginScript() {

    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        spawnHomeAltar()
        bindAltars()
    }

    private fun spawnHomeAltar() {
        val altar = locTypes[EdgevilleHomeLocs.altar]
        val loc =
            LocInfo(
                layer = 0,
                coords = CoordGrid(3080, 3503, 0),
                entity = LocEntity(altar.id, SHAPE_CENTREPIECE, 0),
            )
        locRegistry.add(loc)
        logger.info { "Spawned Edgeville home altar at 3080,3503." }
    }

    private fun ScriptContext.bindAltars() {
        for (ref in EdgevilleHomeLocs.all) {
            val type = locTypes[ref]
            for (slot in type.op.indices) {
                val label = type.op[slot] ?: continue
                if (label.contains("pray", ignoreCase = true)) {
                    bindOp(slot + 1, ref)
                }
            }
        }
    }

    private fun ScriptContext.bindOp(slot: Int, ref: LocType) {
        when (slot) {
            1 -> onOpLoc1(ref) { pray() }
            2 -> onOpLoc2(ref) { pray() }
            3 -> onOpLoc3(ref) { pray() }
            4 -> onOpLoc4(ref) { pray() }
            5 -> onOpLoc5(ref) { pray() }
        }
    }

    private fun ProtectedAccess.pray() {
        player.statRestore(stats.prayer)
        player.mes("You recharge your prayer points.")
    }

    private companion object {
        const val SHAPE_CENTREPIECE = 10
    }
}
