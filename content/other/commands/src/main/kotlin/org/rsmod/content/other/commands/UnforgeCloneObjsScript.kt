package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import io.netty.buffer.Unpooled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import net.rsprot.protocol.api.js5.Js5Service
import org.openrs2.cache.Js5Compression
import org.openrs2.cache.Js5CompressionType
import org.openrs2.cache.Js5MasterIndex
import org.openrs2.cache.MasterIndexFormat
import org.openrs2.cache.Store
import org.openrs2.cache.VersionTrailer
import org.rsmod.annotations.Js5Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.Js5Configs
import org.rsmod.api.cache.types.model.ModelByteDefinition
import org.rsmod.api.net.rsprot.provider.Js5Store
import org.rsmod.api.player.output.mes
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.module.RuntimePaths
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Loads item-clone definitions written by the client-side "Unforge Item Clone" plugin from
 * `<data>/content/items/clones` dir (one `.json` per clone), registers them as real obj types, and
 * re-packs the obj group in both the game and js5 caches so connected clients download them.
 *
 * A clone record copies every field from its base type, then overrides name/desc/recolours. If `id
 * == base`, the base type is recoloured in place (no new item id is taken).
 *
 * ```json
 * { "id": 65001, "base": 4151, "name": "Golden whip", "internal": "clone_65001",
 *   "desc": "", "colors": [35338, 35339] }
 * ```
 *
 * Clones are also applied during type resolution (see [UnforgeCloneObjsLoader]) so `find()`
 * references resolve and the packed caches contain them; this script keeps runtime behaviour:
 * appending new symbol names, refreshing the in-memory js5 responses, and `::reloadclones` support
 * for clones added while the server is running.
 */
@Singleton
class UnforgeCloneObjsScript
@Inject
constructor(
    private val loader: UnforgeCloneObjsLoader,
    private val names: NameMapping,
    @Js5Cache private val js5Dir: Path,
    private val js5: Js5Store,
) : PluginScript() {
    private val logger = InlineLogger()

    private val cloneDir: Path
        get() = RuntimePaths.data.resolve("content/items/clones")

    private val objSym: Path
        get() = RuntimePaths.data.resolve("symbols/obj.sym")

    override fun ScriptContext.startup() {
        val clones = loader.loadClones()
        if (clones.isNotEmpty()) {
            loader.encodeJs5(clones)
            appendObjSymbols(clones)
            refreshJs5(loader.loadModelDefinitions())
            logger.info { "Applied ${clones.size} item clone(s) from $cloneDir." }
        }
        onCommand("reloadclones", "Reload item clone definitions", ::reloadClones)
    }

    private fun reloadClones(cheat: Cheat) =
        with(cheat) {
            val clones = loader.applyClones()
            if (clones.isNotEmpty()) {
                loader.encodeJs5(clones)
                appendObjSymbols(clones)
                refreshJs5(loader.loadModelDefinitions())
            }
            player.mes("Applied ${clones.size} item clone(s) - relog to download updated config.")
        }

    private fun appendObjSymbols(clones: Map<Int, UnpackedObjType>) {
        val existing =
            if (Files.exists(objSym)) {
                Files.readAllLines(objSym).map { it.substringAfter('\t').trim() }.toSet()
            } else {
                emptySet()
            }
        val lines =
            clones
                .mapNotNull { (id, clone) ->
                    val name = clone.internalName ?: return@mapNotNull null
                    if (name in existing || name in names.objs) null else "$id\t$name"
                }
                .sorted()
        if (lines.isNotEmpty()) {
            Files.write(objSym, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    /**
     * Rebuilds the in-memory js5 response for the obj config group and the master index so the next
     * client that connects downloads the updated definitions (the changed checksum also invalidates
     * the client's local js5 cache).
     */
    private fun refreshJs5(models: List<ModelByteDefinition>) {
        // Read through a fresh store: the shared js5 store's buffered file channels can hold
        // stale idx255 data after encodeJs5's writes earlier in this session.
        Store.open(js5Dir).use { store ->
            putResponse(store, Js5Archives.CONFIG, Js5Configs.OBJ)

            // Publish lazily-requested groups for custom item models - responses must exist
            // before a client can download them.
            for (model in models) {
                putResponse(store, Js5Archives.MODELS, model.type)
            }

            refreshMasterIndex(store)
        }
    }

    private fun putResponse(store: Store, archive: Int, group: Int) {
        val data = store.read(archive, group)
        VersionTrailer.strip(data)
        val out = Unpooled.buffer(data.readableBytes() + 8 + data.readableBytes() / 512)
        Js5Service.prepareJs5Buffer(archive, group, data, out)
        js5.responses[(archive shl 16) or group] = Unpooled.unreleasableBuffer(out)
        data.release()
    }

    private fun refreshMasterIndex(store: Store) {
        // Republish every archive-index group (255:N). `encodeJs5` rewrote obj-config and
        // model groups, so the responses cached by `Js5Store.from` for these indexes are
        // stale: if the served index data does not match the master-index checksums we
        // advertise below, clients fail their CRC check and abort with `error_game_js5crc`.
        for (indexGroup in store.list(Store.ARCHIVESET)) {
            val data = store.read(Store.ARCHIVESET, indexGroup)
            val out = Unpooled.buffer(data.readableBytes() + 8 + data.readableBytes() / 512)
            Js5Service.prepareJs5Buffer(Store.ARCHIVESET, indexGroup, data, out)
            js5.responses[(Store.ARCHIVESET shl 16) or indexGroup] =
                Unpooled.unreleasableBuffer(out)
            data.release()
        }
        val masterIndex = Js5MasterIndex.create(store)
        masterIndex.format = MasterIndexFormat.VERSIONED
        val indexBuf = Unpooled.buffer()
        masterIndex.write(indexBuf)
        val compressed = Js5Compression.compress(indexBuf, Js5CompressionType.UNCOMPRESSED)
        val masterOut =
            Unpooled.buffer(compressed.readableBytes() + 8 + compressed.readableBytes() / 512)
        Js5Service.prepareJs5Buffer(Store.ARCHIVESET, Store.ARCHIVESET, compressed, masterOut)
        js5.responses[(Store.ARCHIVESET shl 16) or Store.ARCHIVESET] =
            Unpooled.unreleasableBuffer(masterOut)
        compressed.release()
        masterIndex.entries.forEachIndexed { i, entry -> js5.crc[i] = entry.checksum }
    }
}
