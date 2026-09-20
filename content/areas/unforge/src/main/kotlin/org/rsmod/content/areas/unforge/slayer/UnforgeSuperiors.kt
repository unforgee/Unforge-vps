package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.npc.attackOp
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object UnforgeSuperiorNpcs : NpcReferences() {
    val crawlingHand = find("superior_crawling_hand")
    val caveCrawler = find("superior_cave_crawler")
    val banshee = find("superior_banshee")
    val rockslug = find("superior_rockslug")
    val cockatrice = find("superior_cockatrice")
    val pyrefiend = find("superior_pyrefiend")
    val basilisk = find("superior_basilisk")
    val infernalMage = find("superior_infernal_mage")
    val bloodveld = find("superior_bloodveld")
    val jelly = find("superior_jelly")
    val caveHorror = find("superior_cave_horror")
    val aberrantSpectre = find("superior_abberant_spectre")
    val dustDevil = find("superior_dustdevil")
    val kurask = find("superior_kurask")
    val smokeDevil = find("superior_smoke_devil")
    val gargoyle = find("superior_gargoyle")
    val darkBeast = find("superior_dark_beast")
    val abyssalDemon = find("superior_abyssal_demon")
    val nechryael = find("superior_nechryael")
    val turoth = find("superior_turoth")
    val wyrm = find("superior_wyrm_dark")
    val drake = find("superior_drake")
    val hydra = find("superior_hydra")
}

/**
 * Task-name -> superior variant lookups, resolved against the cache npc types on first use.
 *
 * Kept separate from [UnforgeSuperiors] so [UnforgeSlayer] can credit superior kills towards
 * the matching task without a hard dependency on the spawn script.
 */
internal object CwSuperiorData {
    private val byTask: Map<String, NpcType> =
        mapOf(
            "Crawling Hands" to UnforgeSuperiorNpcs.crawlingHand,
            "Cave Crawlers" to UnforgeSuperiorNpcs.caveCrawler,
            "Banshees" to UnforgeSuperiorNpcs.banshee,
            "Rockslugs" to UnforgeSuperiorNpcs.rockslug,
            "Cockatrice" to UnforgeSuperiorNpcs.cockatrice,
            "Pyrefiends" to UnforgeSuperiorNpcs.pyrefiend,
            "Basilisks" to UnforgeSuperiorNpcs.basilisk,
            "Infernal Mages" to UnforgeSuperiorNpcs.infernalMage,
            "Bloodveld" to UnforgeSuperiorNpcs.bloodveld,
            "Jellies" to UnforgeSuperiorNpcs.jelly,
            "Cave Horrors" to UnforgeSuperiorNpcs.caveHorror,
            "Aberrant Spectres" to UnforgeSuperiorNpcs.aberrantSpectre,
            "Dust Devils" to UnforgeSuperiorNpcs.dustDevil,
            "Kurask" to UnforgeSuperiorNpcs.kurask,
            "Smoke Devils" to UnforgeSuperiorNpcs.smokeDevil,
            "Gargoyles" to UnforgeSuperiorNpcs.gargoyle,
            "Dark Beasts" to UnforgeSuperiorNpcs.darkBeast,
            "Abyssal Demons" to UnforgeSuperiorNpcs.abyssalDemon,
            "Nechryael" to UnforgeSuperiorNpcs.nechryael,
            "Turoth" to UnforgeSuperiorNpcs.turoth,
            "Wyrms" to UnforgeSuperiorNpcs.wyrm,
            "Drakes" to UnforgeSuperiorNpcs.drake,
            "Hydras" to UnforgeSuperiorNpcs.hydra,
        )

    private var byId: Map<Int, String>? = null

    private fun idMap(npcTypes: NpcTypeList): Map<Int, String> =
        byId ?: byTask.map { (name, ref) -> npcTypes[ref].id to name }.toMap().also { byId = it }

    /** The task name this superior npc type id credits, or null for non-superior types. */
    fun taskNameFor(npcTypes: NpcTypeList, npcTypeId: Int): String? = idMap(npcTypes)[npcTypeId]

    /** The superior variant for the given task, or null when the task has none. */
    fun superiorFor(npcTypes: NpcTypeList, taskName: String): UnpackedNpcType? =
        byTask.entries
            .firstOrNull { it.key.equals(taskName, ignoreCase = true) }
            ?.let { npcTypes[it.value] }
}

/**
 * Slayer superior creatures, gated behind the `Bigger and Badder` reward-shop unlock.
 *
 * Each kill of an npc that counts towards the player's current task rolls a 1/200 chance to spawn
 * that task's superior variant on the victim's tile, aggroed onto the killer. Only one superior can
 * be active per player at a time; a superior kill never rolls another superior (the roll only
 * accepts npcs linked through `unforge_slayer_npcs.txt`, not superior ids). Kill credit itself
 * is handled by [UnforgeSlayer] via [CwSuperiorData].
 */
class UnforgeSuperiors
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val playerInteractions: AiPlayerInteractions,
    private val random: GameRandom,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    /** player -> their live superior; weak keys drop the entry once the player is gone. */
    private val activeSuperiors = WeakHashMap<Player, Npc>()

    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> { onKill(this) }
    }

    private fun onKill(event: NpcKilledEvent) {
        val killer = event.killer
        if (activeSuperiors[killer] === event.npc) {
            activeSuperiors.remove(killer)
            return
        }
        // Only npcs linked to a task can roll - superior ids never chain another roll.
        val names = CwSlayerData.npcTasks[event.npc.type.id] ?: return
        val task = killer.cwCurrentTask() ?: return
        if (names.none { it.equals(task.name, ignoreCase = true) }) {
            return
        }
        if (!killer.hasSlayerUnlock(CwSlayerUnlock.BIGGER_AND_BADDER)) {
            return
        }
        if (activeSuperior(killer) != null) {
            return
        }
        val superiorType = CwSuperiorData.superiorFor(npcTypes, task.name) ?: return
        if (random.of(SUPERIOR_ROLL) != 0) {
            return
        }
        spawnSuperior(killer, superiorType, event.npc.coords)
    }

    private fun activeSuperior(player: Player): Npc? =
        activeSuperiors[player]?.takeIf { it.isSlotAssigned }

    private fun spawnSuperior(killer: Player, type: UnpackedNpcType, coords: CoordGrid) {
        val npc = Npc(type, coords)
        npcRepo.add(npc, SUPERIOR_DURATION)
        activeSuperiors[killer] = npc
        playerInteractions.interactOp(npc, killer, npc.attackOp())
        launcher.launch(killer) { mes("<col=dc143c>A superior foe has appeared!</col>") }
    }

    private companion object {
        /** 1 in N chance per credited task kill. */
        private const val SUPERIOR_ROLL: Int = 200

        /** Auto-despawn after ~30 minutes (600ms cycles) so abandoned spawns clean up. */
        private const val SUPERIOR_DURATION: Int = 3000
    }
}
