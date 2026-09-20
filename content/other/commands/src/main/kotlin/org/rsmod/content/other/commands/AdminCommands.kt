package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.varps
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.type.symbols.name.NameMapping
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.game.GameUpdate
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.midi.MidiType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.WeaponCategory
import org.rsmod.game.type.proj.ProjAnimTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.spot.SpotanimTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.game.type.synth.SynthType
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.game.type.varbit.VarBitTypeList
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants
import org.simmetrics.metrics.StringMetrics

class AdminCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val statTypes: StatTypeList,
    private val seqTypes: SeqTypeList,
    private val spotTypes: SpotanimTypeList,
    private val locTypes: LocTypeList,
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val varpTypes: VarpTypeList,
    private val varBitTypes: VarBitTypeList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val projAnimTypes: ProjAnimTypeList,
    private val spawnStore: CustomSpawnRegistry,
    private val shopEdits: ShopEditRegistry,
    private val npcRegistry: NpcRegistry,
    private val names: NameMapping,
    private val update: GameUpdate,
    private val attackStyles: AttackStyles,
) : PluginScript() {
    private val logger = InlineLogger()

    private val levenshteinMetric = StringMetrics.levenshtein()

    override fun ScriptContext.startup() {
        onCommand("master", "Max out all stats", ::master)
        onCommand("reset", "Reset all stats", ::reset)
        onCommand("mypos", "Get current coordinates", ::mypos)
        onCommand("style", "Report the resolved combat stance and attack style", ::style)
        onCommand("tele", "Teleport to coordgrid", ::tele) {
            invalidArgs = "Use as ::tele level mx mz lx lz (ex: 0 50 50 0 0)"
        }
        onCommand("telezone", "Teleport to zone key", ::teleZone) {
            invalidArgs = "Use as ::telezone zoneX zoneY level (ex: 400 400 0)"
        }
        onCommand("anim", "Play animation", ::anim)
        onCommand("spot", "Play spotanim", ::spotanim) {
            invalidArgs = "Use as ::spot spotanimDebugNameOrId (ex: fx_emote_party01_active)"
        }
        onCommand("locadd", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"
        }
        onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }
        onCommand("locaddat", "Spawn loc at an exact coordinate", ::locAddAt) {
            invalidArgs =
                "Use as ::locaddat duration locDebugNameOrId level mapX mapZ localX localZ " +
                    "[angle] [shape]"
        }
        onCommand("npcadd", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"
        }
        onCommand("npcaddat", "Spawn npc at an exact coordinate", ::npcAddAt) {
            invalidArgs =
                "Use as ::npcaddat duration npcDebugNameOrId level mapX mapZ localX localZ " +
                    "(ex: 100 prison_pete 0 26 40 12 18)"
        }
        onCommand("npcdel", "Delete npc at an exact coordinate", ::npcDel) {
            invalidArgs =
                "Use as ::npcdel npcDebugNameOrId level mapX mapZ localX localZ " +
                    "(ex: goblin 0 26 40 12 18)"
        }
        onCommand("locdelat", "Delete loc at an exact coordinate", ::locDelAt) {
            invalidArgs =
                "Use as ::locdelat locDebugNameOrId level mapX mapZ localX localZ " +
                    "(ex: bankbooth 0 26 40 12 18)"
        }
        onCommand("locrotate", "Rotate loc 90 degrees at an exact coordinate", ::locRotate) {
            invalidArgs =
                "Use as ::locrotate locDebugNameOrId level mapX mapZ localX localZ [shape]"
        }
        onCommand("shopdel", "Remove an item from the open shop", ::shopDel) {
            invalidArgs = "Use as ::shopdel slot (slot = shop slot, 1-based)"
        }
        onCommand("shopadd", "Add an item to the open shop", ::shopAdd) {
            invalidArgs = "Use as ::shopadd objDebugNameOrId [count]"
        }
        onCommand("invadd", "Spawn obj into inv", ::invAdd)
        onCommand("item", "Spawn item into inventory", ::invAdd) {
            invalidArgs = "Use as ::item itemNameOrId [count] (ex: ::item 4151 1)"
        }
        onCommand("invclear", "Remove all objs from inv", ::invClear)
        onCommand("varp", "Set varp value", ::setVarp) {
            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"
        }
        onCommand("varbit", "Set varbit value", ::setVarBit) {
            invalidArgs = "Use as ::varbit debugNameOrId value (ex: emote_hotline_bling 1)"
        }
        onCommand("midi", "Play midi song by id", ::midi) {
            invalidArgs = "Use as ::midi songId (ex: 380)"
        }
        onCommand("sound", "Play synth sound by id", ::sound) {
            invalidArgs = "Use as ::sound synthId [loops] [delay] (ex: 62)"
        }
        onCommand("proj", "Fire projectile 2 tiles north", ::proj) {
            invalidArgs =
                "Use as ::proj projanimNameOrId [spotanimNameOrId] " +
                    "(ex: ::proj magic_spell windstrike_travel)"
        }
        onCommand("reboot", "Reboots the game world, applying packed changes", ::reboot)
        onCommand("slowreboot", "Reboots the game world, with a timer", ::slowReboot)
    }

    private fun master(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 99) }

    private fun reset(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 1) }

    private fun mypos(cheat: Cheat) =
        with(cheat) {
            player.mes("${player.coords}:")
            player.mes("  ${ZoneKey.from(player.coords)} - ${ZoneGrid.from(player.coords)}")
            player.mes(
                "  ${MapSquareKey.from(player.coords)} - ${MapSquareGrid.from(player.coords)}"
            )
            player.mes("  BuildArea(${player.buildArea})")
        }

    /**
     * Reports what the server believes the player's combat stance and attack style to be.
     *
     * `com_mode` is only readable through here: `::varp` can set a varp but never prints one, so
     * there is otherwise no way to tell a stance button that never reached the server from a weapon
     * whose category is missing from the `weapon_attack_styles` enum. Both leave the player on
     * `Stance1` and both look identical in game - every hit trains Attack, and Rapid never applies
     * its extra tick.
     */
    private fun style(cheat: Cheat) =
        with(cheat) {
            val stance = player.vars[varps.com_mode]
            val weaponType = objTypes.getOrNull(player.righthand)
            val category = WeaponCategory.getOrUnarmed(weaponType?.weaponCategory)
            val resolved = attackStyles.resolve(weaponType, stance ?: 0)
            player.mes("com_mode (stance index): ${stance ?: 0}")
            player.mes("  weapon: ${weaponType?.name ?: "unarmed"}")
            player.mes("  category: $category (id ${category.id})")
            player.mes("  resolved style: ${resolved ?: "null - no entry for this category"}")
            for (index in 0..3) {
                player.mes("  stance $index -> ${attackStyles.resolve(weaponType, index)}")
            }
        }

    private fun tele(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val level = args[0].toInt()
            val mx = args[1].toInt()
            val mz = args[2].toInt()
            val lx = args.getOrNull(3)?.toInt() ?: 0
            val lz = args.getOrNull(4)?.toInt() ?: 0
            val coords = CoordGrid(level, mx, mz, lx, lz)
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords)
            }
        }

    private fun teleZone(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val zoneX = args[0].toInt()
            val zoneZ = args[1].toInt()
            val level = args[2].toInt()
            val coords = ZoneKey(zoneX, zoneZ, level).toCoords()
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords)
            }
        }

    private fun anim(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args.asTypeName(), names.seqs)
            val typeId = resolveArgTypeId(resolvedName, names.seqs)
            if (typeId == null) {
                player.mes("There is no seq mapped to: '$resolvedName'")
                return
            }
            val type = seqTypes[typeId]
            if (type == null) {
                player.mes("That seq does not exist: $typeId")
                return
            }
            player.anim(type)
            player.mes("Anim: '${type.internalName}' (priority=${type.priority})")
            logger.debug { "Anim: $type" }
        }

    private fun spotanim(cheat: Cheat) =
        with(cheat) {
            val (typeName, heightArg) = args.asTypeNameAndNumber(defaultNumber = 0)
            val resolvedName = resolveTypeName(typeName, names.spotanims)
            val typeId = resolveArgTypeId(resolvedName, names.spotanims)
            if (typeId == null) {
                player.mes("There is no spotanim mapped to: '$resolvedName'")
                return
            }
            val type = spotTypes[typeId]
            if (type == null) {
                player.mes("That spotanim does not exist: $typeId")
                return
            }
            val height = min(heightArg.toInt(), Short.MAX_VALUE.toInt())
            player.spotanim(type, delay = 0, height = height, slot = 0)
            player.mes("Spotanim: '${type.internalName}' (height=$height)")
            logger.debug { "Spotanim: $type" }
        }

    /**
     * Fires a projectile from the player to a tile two squares north, so projectile archetypes
     * (arc, speed, heights) can be inspected in-game the same way `::anim`/`::spot`/`::sound` cover
     * the other visual types. The travel spotanim (the projectile's model) defaults to
     * `windstrike_travel` when omitted.
     */
    private fun proj(cheat: Cheat) =
        with(cheat) {
            val resolvedProjName = resolveTypeName(args[0], names.projanims)
            val projId = resolveArgTypeId(resolvedProjName, names.projanims)
            if (projId == null) {
                player.mes("There is no projanim mapped to: '$resolvedProjName'")
                return
            }
            val projType = projAnimTypes[projId]
            if (projType == null) {
                player.mes("That projanim does not exist: $projId")
                return
            }

            val spotArg = args.getOrNull(1) ?: "windstrike_travel"
            val resolvedSpotName = resolveTypeName(spotArg, names.spotanims)
            val spotId = resolveArgTypeId(resolvedSpotName, names.spotanims)
            val spotType = spotId?.let { spotTypes[it] }
            if (spotType == null) {
                player.mes("There is no spotanim mapped to: '$resolvedSpotName'")
                return
            }

            val destination = player.coords.translate(0, 2)
            val projAnim = worldRepo.projAnim(player, destination, spotType, projType)
            player.mes(
                "Proj: '${projType.internalName}' + '${spotType.internalName}' -> $destination " +
                    "(duration=${projAnim.durations.clientDelay} client cycles)"
            )
            logger.debug { "ProjAnim: $projAnim" }
        }

    private fun locAdd(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.locs)
            val typeId = resolveArgTypeId(resolvedName, names.locs)
            if (typeId == null) {
                player.mes("There is no loc mapped to name: '$resolvedName'")
                return
            }
            val type = locTypes[typeId]
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val angle = args.getOrNull(2)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(3)?.toInt() ?: LocShape.CentrepieceStraight.id
            spawnLocAt(type.id, player.coords, duration, shape, angle)
            player.mes("Spawned loc '${type.internalName}' (duration: $duration cycles)")
            logger.debug { "Spawned loc: type=$type, coords=${player.coords}" }
        }

    private fun locAddAt(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.locs)
            val typeId = resolveArgTypeId(resolvedName, names.locs)
            if (typeId == null) {
                player.mes("There is no loc mapped to name: '$resolvedName'")
                return
            }
            val type = locTypes[typeId]
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val level = args[2].toInt()
            val mapX = args[3].toInt()
            val mapZ = args[4].toInt()
            val localX = args[5].toInt()
            val localZ = args[6].toInt()
            val angle = args.getOrNull(7)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(8)?.toInt() ?: LocShape.CentrepieceStraight.id
            val coords = CoordGrid(level, mapX, mapZ, localX, localZ)
            spawnLocAt(type.id, coords, duration, shape, angle)
            player.mes("Spawned loc '${type.internalName}' at $coords (duration: $duration cycles)")
            logger.debug { "Spawned loc: type=$type, coords=$coords" }
        }

    private fun spawnLocAt(id: Int, coords: CoordGrid, duration: Int, shape: Int, angle: Int) {
        val layer = LocLayerConstants.of(shape)
        val loc = LocInfo(layer, coords, LocEntity(id, shape, angle))
        locRepo.add(loc, duration)
        if (duration == Int.MAX_VALUE) {
            spawnStore.recordLocSpawn(id, coords, shape, angle)
        }
    }

    private fun locDel(cheat: Cheat) =
        with(cheat) {
            val zone = ZoneKey.from(player.coords)
            val locs = locRepo.findAll(zone).filter { it.coords == player.coords }.toList()
            if (locs.isEmpty()) {
                player.mes("No loc found on ${player.coords}")
                return
            }
            val duration = args[0].toInt()
            val shape = args.getOrNull(1)?.toIntOrNull() ?: LocShape.CentrepieceStraight.id
            val loc = locs.firstOrNull { it.shapeId == shape }
            if (loc == null) {
                player.mes("No loc with shape `${LocShape[shape]}` found on ${player.coords}")
                return
            }
            val type = locTypes[loc]
            locRepo.del(loc, duration)
            if (duration == Int.MAX_VALUE) {
                spawnStore.recordLocDelete(loc.id, loc.coords, loc.shapeId, loc.angleId)
            }
            player.mes("Deleted loc `${type.internalName}` (duration: $duration cycles)")
            logger.debug { "Deleted loc: loc=$loc, type=$type" }
        }

    private fun npcAdd(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.npcs)
            val typeId = resolveArgTypeId(resolvedName, names.npcs)
            if (typeId == null) {
                player.mes("There is no npc mapped to name: '$resolvedName'")
                return
            }
            val type = npcTypes[typeId]
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            if (duration == Int.MAX_VALUE && restoreSuppressedNpc(type.id, player.coords)) {
                player.mes("Restored npc `${type.internalName}` at ${player.coords}")
                return
            }
            val npc = Npc(type, player.coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            if (duration == Int.MAX_VALUE) {
                spawnStore.recordNpcSpawn(type.id, npc.coords)
            }
            player.mes("Spawned npc `${type.internalName}` (duration: $duration cycles)")
        }

    private fun npcAddAt(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[1], names.npcs)
            val typeId = resolveArgTypeId(resolvedName, names.npcs)
            if (typeId == null) {
                player.mes("There is no npc mapped to name: '$resolvedName'")
                return
            }
            val type = npcTypes[typeId]
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val level = args[2].toInt()
            val mapX = args[3].toInt()
            val mapZ = args[4].toInt()
            val localX = args[5].toInt()
            val localZ = args[6].toInt()
            val coords = CoordGrid(level, mapX, mapZ, localX, localZ)
            if (duration == Int.MAX_VALUE && restoreSuppressedNpc(type.id, coords)) {
                player.mes("Restored npc `${type.internalName}` at $coords")
                return
            }
            val npc = Npc(type, coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            if (duration == Int.MAX_VALUE) {
                spawnStore.recordNpcSpawn(type.id, npc.coords)
            }
            player.mes("Spawned npc `${type.internalName}` at $coords (duration: $duration cycles)")
        }

    /**
     * If [id] at [coords] was previously deleted through the spawn editor, the map-spawned npc is
     * only hidden - reveal it again instead of stacking a second npc on the same tile.
     */
    @OptIn(InternalApi::class)
    private fun restoreSuppressedNpc(id: Int, coords: CoordGrid): Boolean {
        if (!spawnStore.isNpcDeleted(id, coords)) {
            return false
        }
        val npc =
            npcRepo.findAll(coords).firstOrNull { it.type.id == id && it.hidden } ?: return false
        npcRegistry.reveal(npc)
        spawnStore.liftNpcDelete(id, coords)
        return true
    }

    private fun npcDel(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.npcs)
            val typeId = resolveArgTypeId(resolvedName, names.npcs)
            if (typeId == null) {
                player.mes("There is no npc mapped to name: '$resolvedName'")
                return
            }
            val type = npcTypes[typeId]
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val level = args[1].toInt()
            val mapX = args[2].toInt()
            val mapZ = args[3].toInt()
            val localX = args[4].toInt()
            val localZ = args[5].toInt()
            val coords = CoordGrid(level, mapX, mapZ, localX, localZ)
            // The npc may have walked a tile or two off its spawn point between the
            // client click and this command; accept the nearest match in the zone.
            val zone = ZoneKey.from(coords)
            val npc =
                npcRepo
                    .findAll(zone)
                    .filter { it.type.id == type.id }
                    .minByOrNull {
                        kotlin.math.abs(it.coords.x - coords.x) +
                            kotlin.math.abs(it.coords.z - coords.z)
                    }
            if (npc == null) {
                player.mes("No npc `${type.internalName}` found near $coords")
                return
            }
            npcRepo.del(npc, Int.MAX_VALUE)
            spawnStore.recordNpcDelete(type.id, npc.spawnCoords)
            player.mes("Deleted npc `${type.internalName}` at ${npc.coords}")
            logger.debug { "Deleted npc via command: npc=$npc, coords=$coords" }
        }

    private fun locDelAt(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.locs)
            val typeId = resolveArgTypeId(resolvedName, names.locs)
            if (typeId == null) {
                player.mes("There is no loc mapped to name: '$resolvedName'")
                return
            }
            val type = locTypes[typeId]
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val level = args[1].toInt()
            val mapX = args[2].toInt()
            val mapZ = args[3].toInt()
            val localX = args[4].toInt()
            val localZ = args[5].toInt()
            val shape = args.getOrNull(6)?.toIntOrNull()
            val coords = CoordGrid(level, mapX, mapZ, localX, localZ)
            // Locs wider than 1x1 may register on the anchor tile of a neighbouring
            // tile; match on any loc of the type overlapping the clicked coord.
            val loc =
                locRepo.findAll(ZoneKey.from(coords)).firstOrNull {
                    it.id == type.id &&
                        it.coords == coords &&
                        (shape == null || it.shapeId == shape)
                }
                    ?: locRepo.findAll(ZoneKey.from(coords)).firstOrNull {
                        it.id == type.id && (shape == null || it.shapeId == shape)
                    }
                    ?: locRepo.findAll(ZoneKey.from(coords)).firstOrNull { it.id == type.id }
            if (loc == null) {
                player.mes("No loc `${type.internalName}` found near $coords")
                return
            }
            locRepo.del(loc, Int.MAX_VALUE)
            spawnStore.recordLocDelete(loc.id, loc.coords, loc.shapeId, loc.angleId)
            player.mes("Deleted loc `${type.internalName}` at ${loc.coords}")
            logger.debug { "Deleted loc via command: loc=$loc, coords=$coords" }
        }

    private fun locRotate(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.locs)
            val typeId = resolveArgTypeId(resolvedName, names.locs)
            if (typeId == null) {
                player.mes("There is no loc mapped to name: '$resolvedName'")
                return
            }
            val type = locTypes[typeId]
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val level = args[1].toInt()
            val mapX = args[2].toInt()
            val mapZ = args[3].toInt()
            val localX = args[4].toInt()
            val localZ = args[5].toInt()
            val shape = args.getOrNull(6)?.toIntOrNull()
            val coords = CoordGrid(level, mapX, mapZ, localX, localZ)
            val loc =
                locRepo.findAll(ZoneKey.from(coords)).firstOrNull {
                    it.id == type.id &&
                        it.coords == coords &&
                        (shape == null || it.shapeId == shape)
                }
                    ?: locRepo.findAll(ZoneKey.from(coords)).firstOrNull {
                        it.id == type.id && (shape == null || it.shapeId == shape)
                    }
            if (loc == null) {
                player.mes("No loc `${type.internalName}` found near $coords")
                return
            }
            val rotated = (loc.angleId + 1) % 4
            locRepo.del(loc, Int.MAX_VALUE)
            spawnStore.recordLocDelete(loc.id, loc.coords, loc.shapeId, loc.angleId)
            val layer = LocLayerConstants.of(loc.shapeId)
            val rotatedLoc = LocInfo(layer, loc.coords, LocEntity(loc.id, loc.shapeId, rotated))
            locRepo.add(rotatedLoc, Int.MAX_VALUE)
            spawnStore.recordLocSpawn(loc.id, loc.coords, loc.shapeId, rotated)
            player.mes("Rotated loc `${type.internalName}` at ${loc.coords} (angle: $rotated)")
            logger.debug { "Rotated loc via command: loc=$loc, newAngle=$rotated" }
        }

    private fun shopDel(cheat: Cheat) =
        with(cheat) {
            val shop = player.openedShop
            if (shop == null) {
                player.mes("No shop is currently open.")
                return
            }
            // Client sends the raw comsub; shop stock slots are comsub - 1.
            val slot = args[0].toInt() - 1
            val obj = shop.inv[slot]
            if (obj == null) {
                player.mes("No item in shop slot ${slot + 1}")
                return
            }
            val type = objTypes[obj]
            shop.inv[slot] = null
            shopEdits.recordSlotClear(shop.inv.type.id, slot)
            player.mes("Removed `${type.internalName}` from shop slot ${slot + 1} (persistent)")
            logger.debug { "Shop edit: cleared slot=$slot invType=${shop.inv.type.id}" }
        }

    @OptIn(UncheckedType::class)
    private fun shopAdd(cheat: Cheat) =
        with(cheat) {
            val shop = player.openedShop
            if (shop == null) {
                player.mes("No shop is currently open.")
                return
            }
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val resolvedName = resolveTypeName(typeName, names.objs)
            val typeId = resolveArgTypeId(resolvedName, names.objs)
            if (typeId == null) {
                player.mes("There is no obj mapped to name: '$resolvedName'")
                return
            }
            val type = objTypes[typeId]
            if (type == null) {
                player.mes("That obj does not exist: $typeId")
                return
            }
            val slot = shop.inv.indexOfFirst { it == null }
            if (slot < 0) {
                player.mes("Shop inventory is full.")
                return
            }
            val count = countArg.toLong().coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            shop.inv[slot] = InvObj(type.id, count)
            shopEdits.recordSlotSet(shop.inv.type.id, slot, type.id, count)
            player.mes("Added `${type.internalName}` x$count to shop slot ${slot + 1} (persistent)")
            logger.debug {
                "Shop edit: set slot=$slot obj=${type.id} count=$count " +
                    "invType=${shop.inv.type.id}"
            }
        }

    private fun invAdd(cheat: Cheat) =
        with(cheat) {
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val normalizedName = typeName.replace("cert_", "")
            val resolvedName = resolveTypeName(normalizedName, names.objs)
            val typeId = resolveArgTypeId(resolvedName, names.objs)
            if (typeId == null) {
                player.mes("There is no obj mapped to name: '$resolvedName'")
                return
            }
            val type = objTypes[typeId]
            if (type == null) {
                player.mes("That obj does not exist: $typeId")
                return
            }
            val spawnCert = typeName.startsWith("cert_")
            val resolvedType =
                if (spawnCert && type.canCert) objTypes.getValue(type.certlink) else type
            val count = countArg.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val objName = type.internalName ?: type.name
            val spawned = player.invAdd(player.inv, resolvedType, count, strict = false)
            if (spawned.err is TransactionResult.RestrictedDummyitem) {
                player.mes("You can't spawn this item!")
                return
            }
            player.mes("Spawned inv obj `$objName` x ${spawned.completed().formatAmount}")
        }

    private fun invClear(cheat: Cheat) = with(cheat) { player.invClear(player.inv) }

    private fun midi(cheat: Cheat) =
        with(cheat) {
            val songId = args[0].toInt()
            player.midiSong(MidiType(songId, "debug_midi_$songId"))
            player.mes("MidiSongV2 id=$songId")
        }

    private fun sound(cheat: Cheat) =
        with(cheat) {
            val synthId = args[0].toInt()
            val loops = args.getOrNull(1)?.toInt() ?: 1
            val delay = args.getOrNull(2)?.toInt() ?: 0
            player.soundSynth(
                SynthType(synthId, "debug_synth_$synthId"),
                loops = loops,
                delay = delay,
            )
            player.mes("SynthSound id=$synthId loops=$loops delay=$delay")
        }

    private fun setVarp(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.varps)
            val typeId = resolveArgTypeId(resolvedName, names.varps)
            if (typeId == null) {
                player.mes("There is no varp mapped to name: '$resolvedName'")
                return
            }
            val type = varpTypes[typeId]
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            player.vars.backing[type.id] = value
            player.resyncVar(type)
            player.mes("Set varp '${type.internalName}' to value: ${player.vars[type]}")
        }

    private fun setVarBit(cheat: Cheat) =
        with(cheat) {
            val resolvedName = resolveTypeName(args[0], names.varbits)
            val typeId = resolveArgTypeId(resolvedName, names.varbits)
            if (typeId == null) {
                player.mes("There is no varbit mapped to name: '$resolvedName'")
                return
            }
            val type = varBitTypes[typeId]
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            VarPlayerIntMapSetter.set(player, type, value)
            player.mes("Set varbit '${type.internalName}' to value: ${player.vars[type]}")
        }

    @OptIn(InternalApi::class)
    private fun Player.setStatLevels(level: Int) {
        val xp = PlayerSkillXPTable.getXPFromLevel(level)
        for (stat in statTypes.values) {
            val baseLevel = statMap.getBaseLevel(stat)
            val targetLevel = max(stat.minLevel, level)
            if (baseLevel > targetLevel) {
                statRevert(stat, targetLevel, xp)
                continue
            }
            val xpDelta = xp - statMap.getXP(stat)
            statMap.setCurrentLevel(stat, targetLevel.toByte())
            statAdvance(stat, xpDelta.toDouble(), rate = 1.0)
        }
    }

    // There is, by design, no helper function to decrease stat xp, as xp reduction is not a
    // standard operation in normal gameplay.
    @OptIn(InternalApi::class)
    private fun Player.statRevert(stat: StatType, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - targetLevel
        require(levelDelta > 0) { "This function can only be used to reduce stat levels." }
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    private fun reboot(cheat: Cheat) {
        logger.info { "Reboot initiated by '${cheat.player.username}'." }
        SafeServiceExit.terminate()
    }

    private fun slowReboot(cheat: Cheat) =
        with(cheat) {
            val cycles = min(args[0].toInt(), 65535)
            if (cycles <= 0) {
                update.clear()
                return@with
            }
            update.startCountdown(cycles)
            for (p in playerList) {
                MiscOutput.updateRebootTimer(p, cycles)
            }
        }

    private fun resolveArgTypeId(arg: String, names: Map<String, Int>): Int? {
        val argAsInt = arg.toIntOrNull()
        if (argAsInt != null) {
            return argAsInt
        }
        val sanitized = arg.replace("-", "_")
        return names[sanitized]
    }

    private fun resolveTypeName(name: String, names: Map<String, Int>): String =
        when {
            name in names -> name
            name.toIntOrNull() != null -> name
            else -> findClosestNameMatch(name, names.keys) ?: name
        }

    private fun List<String>.asTypeNameAndNumber(defaultNumber: Number): Pair<String, String> =
        if (size > 1 && last().toLongOrNull() != null) {
            dropLast(1).joinToString("_") to last()
        } else {
            joinToString("_") to defaultNumber.toString()
        }

    private fun List<String>.asTypeName(): String = joinToString("_")

    private fun findClosestNameMatch(input: String, names: Iterable<String>): String? {
        val normalizedInput = input.replace("_", " ")

        var bestMatchScore = 0.0f
        var bestMatchName: String? = null
        for (name in names) {
            val score = levenshteinMetric.compare(normalizedInput, name.replace("_", " "))
            if (score > bestMatchScore) {
                bestMatchScore = score
                bestMatchName = name
            }
        }

        return if (bestMatchScore >= 0.5) bestMatchName else null
    }
}
