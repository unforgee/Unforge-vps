package org.rsmod.content.areas.unforge.editor

import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.areas.unforge.teleports.TeleCat
import org.rsmod.content.areas.unforge.teleports.TeleDest
import org.rsmod.content.areas.unforge.teleports.TeleGroup
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Admin-only Unforge content editor.
 *
 * NPC editing:
 * - `::npcedit [filter]` toggles edit mode and opens a spawnable NPC catalog. Catalog entries
 *   tagged with their bound shop when one exists.
 * - While edit mode is ON, using any item on a ported Unforge npc opens an action menu: delete
 *   this spawn (live + npcs.toml), copy the npc type, or teleport to its spawn point.
 * - `::npcpaste` spawns the copied npc at the player's tile and persists it.
 *
 * Teleport editing:
 * - `::teleadd <page> <name>` stores the player's tile under a (possibly new) page in
 *   `.data/Unforge/custom_teleports.txt`.
 * - `::telepage <name>` creates an empty page; `::teledel` removes entries.
 * - Custom pages appear in the teleport menu under the "Custom" book.
 */
class UnforgeEditor
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    private val editMode = hashSetOf<Player>()
    private val clipboard = hashMapOf<Player, String>()

    override fun ScriptContext.startup() {
        onCommand("npcedit") {
            desc = "Toggle the npc editor (opt: name filter)"
            modLevel = modlevels.admin
            cheat(::npcEdit)
        }
        onCommand("npcpaste") {
            desc = "Spawn the copied npc at your tile"
            modLevel = modlevels.admin
            cheat(::npcPaste)
        }
        onCommand("teleadd") {
            desc = "Add current tile: ::teleadd <page> <name>"
            modLevel = modlevels.admin
            invalidArgs = "Use as ::teleadd <page> <name>"
            cheat(::teleAdd)
        }
        onCommand("teledel") {
            desc = "Delete a custom teleport"
            modLevel = modlevels.admin
            cheat(::teleDel)
        }
        onCommand("telepage") {
            desc = "Add an empty teleport page: ::telepage <name>"
            modLevel = modlevels.admin
            invalidArgs = "Use as ::telepage <name>"
            cheat(::telePage)
        }
        for (type in UnforgeEditorNpcs.editable) {
            onOpNpcU(type) { npcUse(it) }
        }
    }

    private fun Player.isAdmin(): Boolean = modLevel.hasAccessTo(modlevels.admin)

    // ------------------------------------------------------------------ npcs

    private fun npcEdit(cheat: Cheat) =
        with(cheat) {
            if (!player.isAdmin()) {
                player.mes("Only administrators can use the npc editor.")
                return@with
            }
            val filter = args.joinToString(" ").takeIf { it.isNotBlank() }?.lowercase()
            if (player in editMode && filter == null) {
                editMode -= player
                player.mes("NPC editor disabled.")
                return@with
            }
            editMode += player
            player.mes("NPC editor enabled. Use any item on a npc to delete or copy it.")
            protectedAccess.launch(player) { openCatalog(filter) }
        }

    private fun npcPaste(cheat: Cheat) =
        with(cheat) {
            if (!player.isAdmin()) {
                player.mes("Only administrators can use the npc editor.")
                return@with
            }
            val sym = clipboard[player]
            if (sym == null) {
                player.mes("Nothing copied. Use an item on a npc in edit mode to copy it.")
                return@with
            }
            spawnNpc(player, sym)
        }

    private suspend fun ProtectedAccess.openCatalog(filter: String?) {
        val shops = UnforgeEditorData.shopNames
        var entries = UnforgeEditorData.symbols
        if (filter != null) {
            entries =
                entries.filter {
                    it.contains(filter) || (shops[it]?.lowercase()?.contains(filter) == true)
                }
        }
        if (entries.isEmpty()) {
            player.mes("No Unforge npcs match '${filter ?: ""}'.")
            return
        }
        startDialogue {
            val labelled =
                entries.map { sym ->
                    val shop = shops[sym]
                    if (shop != null) "$sym ($shop)" to sym else sym to sym
                }
            val sym =
                pick("Spawn which npc? (${entries.size} listed)", labelled) ?: return@startDialogue
            val ok = choice2("Spawn here", true, "Cancel", false, title = sym)
            if (ok) {
                spawnNpc(player, sym)
            }
        }
    }

    private suspend fun ProtectedAccess.npcUse(event: NpcUDefaultEvents.OpType) {
        val npc = event.npc
        if (!player.isAdmin() || player !in editMode) {
            player.mes("Nothing interesting happens.")
            return
        }
        val sym = npc.type.internalName ?: return
        startDialogue {
            val action =
                choice4(
                    "Delete this spawn",
                    0,
                    "Copy npc type",
                    1,
                    "Teleport to spawn point",
                    2,
                    "Cancel",
                    3,
                    title = "Edit ${npc.type.name}",
                )
            when (action) {
                0 -> deleteSpawn(player, npc)
                1 -> {
                    clipboard[player] = sym
                    player.mes("Copied '$sym'. Use ::npcpaste on the target tile.")
                }
                2 -> {
                    access.teleport(npc.spawnCoords)
                    player.mes("Spawn point: ${npc.spawnCoords}")
                }
            }
        }
    }

    private fun deleteSpawn(player: Player, npc: Npc) {
        val sym = npc.type.internalName ?: return
        val c = npc.spawnCoords
        try {
            npcRepo.del(npc, Int.MAX_VALUE)
        } catch (e: IllegalStateException) {
            player.mes("Could not delete npc: ${e.message}")
            return
        }
        val removed = removeSpawnFromToml(sym, c)
        player.mes(
            "Deleted '$sym' @ $c" +
                if (removed) " (removed from npcs.toml)" else " (live only - not in npcs.toml)"
        )
    }

    private fun spawnNpc(player: Player, sym: String) {
        val ref = UnforgeEditorNpcs.symToRef[sym]
        val type = ref?.let { npcTypes[it] }
        if (type == null) {
            player.mes("Unknown npc symbol: $sym")
            return
        }
        val npc = Npc(type, player.coords)
        npc.mode = NpcMode.None
        npc.respawns = true
        npcRepo.add(npc, Int.MAX_VALUE)
        appendSpawn(sym, player.coords)
        player.mes("Spawned '$sym' at ${player.coords} (persisted to npcs.toml).")
    }

    // ------------------------------------------------------------- toml i/o

    private val spawnToml: Path =
        Path.of(
            "content/areas/unforge/src/main/resources/org/rsmod/content/areas/unforge/npcs.toml"
        )

    private fun coordKey(c: CoordGrid): String =
        "${c.level}_${c.x shr 6}_${c.z shr 6}_${c.x and 63}_${c.z and 63}"

    private fun appendSpawn(sym: String, c: CoordGrid) {
        runCatching {
            val block = "\n[[spawn]]\nnpc = '$sym'\ncoords = '${coordKey(c)}'\n"
            Files.writeString(
                spawnToml,
                block,
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE,
            )
        }
    }

    private fun removeSpawnFromToml(sym: String, c: CoordGrid): Boolean {
        if (!Files.exists(spawnToml)) return false
        return runCatching {
                val src = Files.readString(spawnToml)
                val pat =
                    Regex(
                        "\\[\\[spawn]]\\s*npc = '${Regex.escape(sym)}'\\s*coords = '${Regex.escape(coordKey(c))}'.*?(?=\\n\\[|\\z)",
                        RegexOption.DOT_MATCHES_ALL,
                    )
                val out = pat.replaceFirst(src, "")
                if (out != src) {
                    Files.writeString(spawnToml, out)
                    true
                } else false
            }
            .getOrDefault(false)
    }

    // ------------------------------------------------------------- teleports

    private fun teleAdd(cheat: Cheat) =
        with(cheat) {
            if (!player.isAdmin()) {
                player.mes("Only administrators can edit teleports.")
                return@with
            }
            val page = args[0]
            val name = args.drop(1).joinToString(" ")
            val c = player.coords
            UnforgeCustomTeles.append(page, name, c)
            player.mes("Added teleport '$name' to page '$page' @ ${c.x},${c.z},${c.level}.")
        }

    private fun telePage(cheat: Cheat) =
        with(cheat) {
            if (!player.isAdmin()) {
                player.mes("Only administrators can edit teleports.")
                return@with
            }
            val name = args.joinToString(" ")
            UnforgeCustomTeles.appendPage(name)
            player.mes("Teleport page '$name' created. Fill it with ::teleadd $name <dest>.")
        }

    private fun teleDel(cheat: Cheat) =
        with(cheat) {
            if (!player.isAdmin()) {
                player.mes("Only administrators can edit teleports.")
                return@with
            }
            protectedAccess.launch(player) { deleteTeleportMenu() }
        }

    private suspend fun ProtectedAccess.deleteTeleportMenu() {
        val entries = UnforgeCustomTeles.entries()
        if (entries.isEmpty()) {
            player.mes("No custom teleports stored. Use ::teleadd <page> <name>.")
            return
        }
        startDialogue {
            val entry =
                pick("Delete which teleport?", entries.map { "${it.page}: ${it.name}" to it })
                    ?: return@startDialogue
            val title = if (entry.isPageMarker) "page '${entry.page}'" else "'${entry.name}'"
            val ok = choice2("Delete $title", true, "Cancel", false, title = "Confirm")
            if (ok) {
                UnforgeCustomTeles.remove(entry)
                player.mes("Removed teleport $title.")
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private suspend fun <T> Dialogue.pick(title: String, options: List<Pair<String, T>>): T? {
        var page = 0
        while (true) {
            val slice = options.drop(page * 4).take(4)
            val more = options.size > (page + 1) * 4
            val labels = slice.map { it.first } + if (more) "More..." else "Cancel"
            val idx: Int =
                when (labels.size) {
                    2 -> choice2(labels[0], 0, labels[1], -1, title = title)
                    3 -> choice3(labels[0], 0, labels[1], 1, labels[2], -1, title = title)
                    4 ->
                        choice4(
                            labels[0],
                            0,
                            labels[1],
                            1,
                            labels[2],
                            2,
                            labels[3],
                            -1,
                            title = title,
                        )
                    else ->
                        choice5(
                            labels[0],
                            0,
                            labels[1],
                            1,
                            labels[2],
                            2,
                            labels[3],
                            3,
                            labels[4],
                            -1,
                            title = title,
                        )
                }
            if (idx == -1) {
                if (more) page++ else return null
            } else return options[page * 4 + idx].second
        }
    }
}

/** Runtime-editable teleport entries persisted under `.data/Unforge/`. */
object UnforgeCustomTeles {
    data class Entry(
        val page: String,
        val name: String,
        val dest: CoordGrid,
        val isPageMarker: Boolean = false,
    )

    private val file: Path = Path.of(".data/Unforge/custom_teleports.txt")

    fun entries(): List<Entry> {
        if (!Files.exists(file)) return emptyList()
        return runCatching {
                Files.readAllLines(file).mapNotNull { line ->
                    val l = line.trim()
                    if (l.isEmpty() || l.startsWith("#")) return@mapNotNull null
                    val p = l.split("|")
                    if (p[0] == "!page") {
                        Entry(p.getOrElse(1) { "Page" }, "", CoordGrid(0, 0), true)
                    } else if (p.size >= 5) {
                        Entry(p[0], p[1], CoordGrid(p[2].toInt(), p[3].toInt(), p[4].toInt()))
                    } else null
                }
            }
            .getOrDefault(emptyList())
    }

    fun append(page: String, name: String, c: CoordGrid) {
        Files.createDirectories(file.parent)
        val line = "$page|$name|${c.x}|${c.z}|${c.level}\n"
        Files.writeString(file, line, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
    }

    fun appendPage(name: String) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            "!page|$name\n",
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE,
        )
    }

    fun remove(target: Entry) {
        if (!Files.exists(file)) return
        val kept =
            if (target.isPageMarker) {
                // Deleting a page also removes its entries.
                entries().filter { it.page != target.page }
            } else {
                entries().filter { it != target }
            }
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            kept.joinToString("") { e ->
                if (e.isPageMarker) "!page|${e.page}\n"
                else "${e.page}|${e.name}|${e.dest.x}|${e.dest.z}|${e.dest.level}\n"
            },
        )
    }

    /** Teleport book for `UnforgeTeleports`: one category per page. */
    fun asBook(): List<TeleCat> {
        val real = entries().filter { !it.isPageMarker }
        if (real.isEmpty()) return emptyList()
        return real
            .groupBy { it.page }
            .map { (page, es) ->
                TeleCat(page, listOf(TeleGroup(page, es.map { TeleDest(it.name, it.dest) })))
            }
    }
}
