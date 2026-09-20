package org.rsmod.content.other.commands

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import org.openrs2.cache.Cache
import org.openrs2.cache.Store
import org.rsmod.annotations.EnrichedCache
import org.rsmod.annotations.GameCache
import org.rsmod.annotations.Js5Cache
import org.rsmod.api.cache.types.model.ModelByteDefinition
import org.rsmod.api.cache.types.model.ModelByteEncoder
import org.rsmod.api.cache.types.obj.ObjTypeEncoder
import org.rsmod.api.cache.util.EncoderContext
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.game.type.dbtable.DbTableTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.param.ParamTypeList
import org.rsmod.game.type.util.ParamMap
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.module.RuntimePaths

/**
 * Loads item-clone definitions from `<data>/content/items/clones` (one `.json` per clone) and
 * registers them as real obj types.
 *
 * This runs during type resolution (before references verify) so that `find()` references to clone
 * objs resolve, and encodes the clones into the `game`, `js5` and `enriched` caches so they survive
 * cache syncs and future `packCache` runs.
 */
@Singleton
class UnforgeCloneObjsLoader
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val names: NameMapping,
    private val paramTypes: ParamTypeList,
    private val varpTypes: VarpTypeList,
    private val dbTables: DbTableTypeList,
    @GameCache private val gameCache: Cache,
    @GameCache private val gameStore: Store,
    @Js5Cache private val js5Cache: Cache,
    @Js5Cache private val js5Store: Store,
    @EnrichedCache private val enrichedCache: Cache,
    @EnrichedCache private val enrichedStore: Store,
) {
    private val logger = InlineLogger()
    private val mapper = ObjectMapper()

    /** Clone ids registered by this loader - reload may overwrite these, nothing else. */
    private val loaded = mutableSetOf<Int>()

    private val cloneDir: Path
        get() = RuntimePaths.data.resolve("content/items/clones")

    private val modelDir: Path
        get() = RuntimePaths.data.resolve("content/items/models")

    /** Applies all clone definitions to [objTypes] and writes them into every packed cache. */
    fun applyClones(): Map<Int, UnpackedObjType> {
        val modelFiles = loadModelFiles()
        val ids = modelIds(modelFiles)
        val models =
            modelFiles.map { (name, bytes) -> ModelByteDefinition(ids.getValue(name), bytes) }
        val clones = loadClones(ids)
        if (clones.isEmpty()) {
            return clones
        }
        objTypes.types.putAll(clones)

        val params = paramTypes.filterTransmitKeys()
        val varps = varpTypes.filterTransmitKeys()
        val tables = dbTables.filterTransmitKeys()
        encode(gameCache, gameStore, clones, EncoderContext.server(params, varps, tables), models)
        encode(
            enrichedCache,
            enrichedStore,
            clones,
            EncoderContext.server(params, varps, tables),
            models,
        )
        encode(js5Cache, js5Store, clones, EncoderContext.client(params, varps, tables), models)
        return clones
    }

    /** Encodes clones into the js5 cache - call only after [Js5Store] responses are built. */
    fun encodeJs5(clones: Map<Int, UnpackedObjType>) {
        val params = paramTypes.filterTransmitKeys()
        val varps = varpTypes.filterTransmitKeys()
        val tables = dbTables.filterTransmitKeys()
        encode(
            js5Cache,
            js5Store,
            clones,
            EncoderContext.client(params, varps, tables),
            loadModelDefinitions(),
        )
    }

    private fun encode(
        cache: Cache,
        store: Store,
        clones: Map<Int, UnpackedObjType>,
        context: EncoderContext,
        models: List<ModelByteDefinition>,
    ) {
        try {
            ObjTypeEncoder.encodeAll(cache, clones.values, context)
            ModelByteEncoder.encodeAll(cache, models)
            cache.flush()
            // The store keeps pending writes in a buffered file channel; without this flush,
            // same-session reads (e.g. Js5MasterIndex.create) can observe torn data.
            store.flush()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to write item clones to cache." }
        }
    }

    /**
     * Loads raw model bytes from `<data>/content/items/models` (one `.dat` per model). The file
     * name is the model's symbolic key referenced by clone `models` fields.
     */
    fun loadModelDefinitions(): List<ModelByteDefinition> {
        val files = loadModelFiles()
        val ids = modelIds(files)
        return files.map { (name, bytes) -> ModelByteDefinition(ids.getValue(name), bytes) }
    }

    private fun loadModelFiles(): Map<String, ByteArray> {
        if (!modelDir.isDirectory()) {
            return emptyMap()
        }
        val files = mutableMapOf<String, ByteArray>()
        Files.list(modelDir).use { stream ->
            for (path in
                stream.filter { it.fileName.toString().endsWith(".dat") }.sorted().toList()) {
                try {
                    files[path.fileName.toString().removeSuffix(".dat")] = Files.readAllBytes(path)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to read model file: $path" }
                }
            }
        }
        return files
    }

    /**
     * Assigns model ids deterministically: sorted file names map to `CUSTOM_MODEL_ID_BASE + n`. The
     * vanilla models archive is densely packed (ids 0-61605 at time of writing) and js5 response
     * keys require group ids under 65536, so the custom block lives at 62000+.
     */
    private fun modelIds(files: Map<String, ByteArray>): Map<String, Int> =
        files.keys.sorted().mapIndexed { i, name -> name to CUSTOM_MODEL_ID_BASE + i }.toMap()

    fun loadClones(): Map<Int, UnpackedObjType> = loadClones(modelIds(loadModelFiles()))

    private fun loadClones(modelIds: Map<String, Int>): Map<Int, UnpackedObjType> {
        if (!cloneDir.isDirectory()) {
            return emptyMap()
        }
        val clones = mutableMapOf<Int, UnpackedObjType>()
        Files.list(cloneDir).use { stream ->
            for (path in
                stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList()) {
                try {
                    val (id, clone) = loadClone(path, modelIds) ?: continue
                    clones[id] = clone
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to load item clone: $path" }
                }
            }
        }
        return clones
    }

    private fun loadClone(path: Path, modelIds: Map<String, Int>): Pair<Int, UnpackedObjType>? {
        val json = mapper.readTree(Files.newBufferedReader(path))
        val id = json.required("id").asInt()
        val base = json.required("base").asInt()
        val internal = json["internal"]?.asText() ?: "clone_$id"
        val baseType =
            objTypes.types[base]
                ?: run {
                    logger.warn { "Clone $path skipped - base obj $base does not exist." }
                    return null
                }
        val existing = objTypes.types[id]
        if (id != base && existing != null && existing.internalName != internal && id !in loaded) {
            logger.warn { "Clone $path skipped - obj id $id already exists." }
            return null
        }

        val colors = json["colors"]?.map(JsonNode::asInt)?.map(Int::toShort)?.toShortArray()
        val srcColors = json["srccolors"]?.map(JsonNode::asInt)?.map(Int::toShort)?.toShortArray()
        val recolS = srcColors ?: baseType.recolS
        val recolD = colors ?: baseType.recolD
        require(recolS.size == recolD.size) {
            "Clone $path has ${recolS.size} src colours but ${recolD.size} dst colours."
        }

        val paramOverrides = mutableMapOf<Int, Any>()
        json["melee_strength"]?.let { paramOverrides[paramId("melee_strength")] = it.asInt() }
        json["ranged_strength"]?.let { paramOverrides[paramId("ranged_strength")] = it.asInt() }
        json["magic_damage"]?.let { paramOverrides[paramId("magic_damage")] = it.asInt() }
        json["attackrate"]?.let { paramOverrides[paramId("attackrate")] = it.asInt() }
        val mergedParams =
            if (paramOverrides.isEmpty()) {
                baseType.paramMap
            } else {
                val base = baseType.paramMap
                ParamMap(
                    (base?.primitiveMap ?: emptyMap()) + paramOverrides,
                    (base?.typedMap ?: emptyMap()) + paramOverrides,
                )
            }

        // Optional custom models: `"models": {"model": "File_12345", "manwear": "File_12346",
        // ...}`.
        // Values are file base names under `<data>/content/items/models`. When the node is
        // present, wear-model fields default to -1 (rather than the base type's models) so a
        // clone never mixes base-gear geometry with its own; woman* fields fall back to their
        // man* counterpart so items stay visible on female characters.
        val modelsNode = json["models"]
        val hasCustomModels = modelsNode != null && modelsNode.isObject

        fun modelRef(field: String): Int {
            val file = modelsNode?.get(field)?.asText() ?: return -1
            return modelIds[file]
                ?: run {
                    logger.warn { "Clone $path: model file '$file.dat' not found in $modelDir." }
                    -1
                }
        }

        val manwear = modelRef("manwear")
        val manwear2 = modelRef("manwear2")
        val manwear3 = modelRef("manwear3")
        val manhead = modelRef("manhead")
        val manhead2 = modelRef("manhead2")

        fun jsonInt(field: String): Int? = json[field]?.takeIf { it.isInt }?.asInt()

        val clone =
            baseType.copy(
                internalId = id,
                internalName = internal,
                name = json["name"]?.asText() ?: baseType.name,
                desc = json["desc"]?.asText()?.takeUnless(String::isBlank) ?: baseType.desc,
                // Unforge custom content is available to every account, including non-members.
                // Do not inherit the membership flag from the vanilla base object.
                members = false,
                recolS = recolS,
                recolD = recolD,
                paramMap = mergedParams,
                model =
                    if (hasCustomModels) modelRef("model").takeIf { it != -1 } ?: baseType.model
                    else baseType.model,
                zoom2d = jsonInt("zoom2d") ?: baseType.zoom2d,
                xan2d = jsonInt("xan2d") ?: baseType.xan2d,
                yan2d = jsonInt("yan2d") ?: baseType.yan2d,
                zan2d = jsonInt("zan2d") ?: baseType.zan2d,
                xof2d = jsonInt("xof2d") ?: baseType.xof2d,
                yof2d = jsonInt("yof2d") ?: baseType.yof2d,
                cost = jsonInt("cost") ?: baseType.cost,
                stackable = json["stackable"]?.asBoolean() ?: baseType.stackable,
                tradeable = json["tradeable"]?.asBoolean() ?: baseType.tradeable,
                wearpos1 = jsonInt("wearpos1") ?: baseType.wearpos1,
                wearpos2 = jsonInt("wearpos2") ?: baseType.wearpos2,
                wearpos3 = jsonInt("wearpos3") ?: baseType.wearpos3,
                manwear = if (hasCustomModels) manwear else baseType.manwear,
                manwear2 = if (hasCustomModels) manwear2 else baseType.manwear2,
                manwear3 = if (hasCustomModels) manwear3 else baseType.manwear3,
                womanwear =
                    if (hasCustomModels) modelRef("womanwear").takeIf { it != -1 } ?: manwear
                    else baseType.womanwear,
                womanwear2 =
                    if (hasCustomModels) modelRef("womanwear2").takeIf { it != -1 } ?: manwear2
                    else baseType.womanwear2,
                womanwear3 =
                    if (hasCustomModels) modelRef("womanwear3").takeIf { it != -1 } ?: manwear3
                    else baseType.womanwear3,
                manhead = if (hasCustomModels) manhead else baseType.manhead,
                manhead2 = if (hasCustomModels) manhead2 else baseType.manhead2,
                womanhead =
                    if (hasCustomModels) modelRef("womanhead").takeIf { it != -1 } ?: manhead
                    else baseType.womanhead,
                womanhead2 =
                    if (hasCustomModels) modelRef("womanhead2").takeIf { it != -1 } ?: manhead2
                    else baseType.womanhead2,
                manwearOff = jsonInt("manwearOff") ?: baseType.manwearOff,
                womanwearOff = jsonInt("womanwearOff") ?: baseType.womanwearOff,
                op = json["op"]?.map { it.asText(null) }?.toTypedArray() ?: baseType.op,
                iop = json["iop"]?.map { it.asText(null) }?.toTypedArray() ?: baseType.iop,
            )
        loaded += id
        return id to clone
    }

    private fun paramId(internal: String): Int =
        requireNotNull(names.params[internal]) { "Param '$internal' not defined in param.sym." }

    private companion object {
        /**
         * First model id in the custom-item block. The vanilla models archive is densely packed
         * (ids 0-61605 at time of writing); js5 response keys also require group ids below 65536.
         */
        const val CUSTOM_MODEL_ID_BASE: Int = 62000
    }
}
