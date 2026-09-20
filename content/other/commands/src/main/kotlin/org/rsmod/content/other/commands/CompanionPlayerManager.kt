package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.onSuccess
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionPlayerRegistry
import org.rsmod.api.companion.CompanionSpellbook
import org.rsmod.api.companion.combatLevels
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.config.refs.baseanimsets
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.PlayerRegistryResult
import org.rsmod.game.client.Client
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.stat.StatType
import org.rsmod.map.CoordGrid

@Singleton
public class CompanionPlayerManager
@Inject
constructor(
    private val playerList: PlayerList,
    private val playerRegistry: PlayerRegistry,
    private val networkService: NetworkService<Player>,
    private val objTypes: ObjTypeList,
    private val npcTypes: NpcTypeList,
    private val equipmentInstances: EquipmentInstanceRegistry,
    private val companionPlayerRegistry: CompanionPlayerRegistry,
) {
    private val allocatedInfos = ConcurrentHashMap<Long, Infos>()
    private val companionPlayers = ConcurrentHashMap<Long, Player>()

    public fun ownerPlayer(companionId: Long): Player? {
        val ownerId = companionPlayerRegistry.getOwnerCharacterId(companionId) ?: return null
        return playerList.firstOrNull { it.characterId.toLong() == ownerId }
    }

    public fun spawn(owner: Player, companion: Companion): Player? {
        despawn(companion.id)

        val slot = playerList.nextFreeSlot() ?: return null
        val infos = networkService.infoProtocols.alloc(slot, OldSchoolClientType.DESKTOP)

        val cycle = CompanionPlayerCycle(infos, objTypes)
        @Suppress("UNCHECKED_CAST") val client = CompanionClient(infos) as Client<Any, Any>

        val bot =
            Player(client = client, clientCycle = cycle).apply {
                this.slotId = slot
                this.coords =
                    when (companion.slot) {
                        1 -> owner.coords.translateX(-1)
                        2 -> owner.coords.translateZ(-1)
                        3 -> owner.coords.translateX(1)
                        4 -> owner.coords.translateZ(1)
                        else -> owner.coords.translateX(-1)
                    }
                this.displayName = companion.name
                this.username = "companion_${companion.id}"
                val idLong = companion.id
                val idInt = (companion.id % 2_000_000_000L).toInt().coerceAtLeast(1)
                this.uuid = -idLong
                this.observerUUID = companionObserverId(owner, companion.id)
                this.userId = -idLong
                this.userHash = -idLong
                this.accountId = -idInt
                this.characterId = -idInt
                this.accountHash = -idLong
                this.appearance.bodyType = 0
                this.appearance.combatLevel = companion.level
                // Virtual players do not receive the normal client-side run/walk varp during
                // login. The movement processor otherwise resolves every server route against
                // MoveSpeed.Stationary, leaving companions visible but permanently standing.
                this.varMoveSpeed = MoveSpeed.Walk
                this.moveSpeed = MoveSpeed.Walk
                this.cachedMoveSpeed = MoveSpeed.Walk
            }

        val result = playerRegistry.add(bot)
        if (result !is PlayerRegistryResult.Add.Success) {
            networkService.infoProtocols.dealloc(infos)
            return null
        }

        allocatedInfos[companion.id] = infos
        companionPlayers[companion.id] = bot
        companionPlayerRegistry.register(companion.id, owner.characterId.toLong(), bot)

        syncEquipment(companion, bot)
        applyCombatState(owner, companion, bot, companion.maximumHitpoints)
        return bot
    }

    /**
     * Mirrors the persisted companion combat state onto its bot entity so the shared player
     * combat/spell pipelines can run unmodified:
     * - Combat stat levels (including Magic, which gates `MagicRuneManager` level checks) are
     *   forced to the companion's own level - the owner's stats can never substitute for it.
     * - `bot.inv` is aliased to the owner's companion pack, the documented Beast-of-Burden resource
     *   convention, so `MagicRuneManager` validates and consumes real stored runes.
     * - `bot.worn` still carries the companion's gear, so staff-based rune substitutes and worn
     *   spell requirements resolve against companion equipment exactly like a real player.
     * - The engine `spellbook` varbit mirrors the companion-owned book selection; it never touches
     *   the owner's spellbook.
     * - The domain hitpoints value is authoritative: damage suffered by the bot is mirrored into
     *   `Companion.hitpoints` by the runtime script, which then writes the domain value straight
     *   back through here.
     */
    public fun applyCombatState(
        owner: Player,
        companion: Companion,
        bot: Player,
        maxHitpoints: Int,
    ) {
        bot.inv = owner.invMap.getValue(BaseInvs.companion_storage)
        // Keep drop ownership in sync if the owner's observer id changed since spawn
        // (e.g. group-ironman join re-keys observerUUID to the group id).
        bot.observerUUID = companionObserverId(owner, companion.id)

        val combatLevels = companion.combatLevels()
        setCombatLevel(bot, stats.attack, combatLevels.attack)
        setCombatLevel(bot, stats.strength, combatLevels.strength)
        setCombatLevel(bot, stats.defence, combatLevels.defence)
        setCombatLevel(bot, stats.ranged, combatLevels.ranged)
        setCombatLevel(bot, stats.magic, combatLevels.magic)
        // Prayer is not an offensive style stat. Keep the companion's current level as the
        // shared baseline so prayer-dependent equipment/formula code remains deterministic.
        setCombatLevel(bot, stats.prayer, combatLevels.prayer)
        bot.statMap.setBaseLevel(stats.hitpoints, maxHitpoints.coerceIn(1, 126).toByte())
        val currentHp = companion.hitpoints.coerceIn(0, maxHitpoints).coerceIn(0, 126).toByte()
        bot.statMap.setCurrentLevel(stats.hitpoints, currentHp)

        val spellbook =
            when (companion.spellbook) {
                CompanionSpellbook.STANDARD -> Spellbook.Standard
                CompanionSpellbook.ANCIENTS -> Spellbook.Ancients
            }
        VarPlayerIntMapSetter.set(bot, varbits.spellbook, spellbook.varValue)
    }

    private fun setCombatLevel(bot: Player, stat: StatType, level: Int) {
        val value = level.coerceIn(1, 126).toByte()
        bot.statMap.setBaseLevel(stat, value)
        bot.statMap.setCurrentLevel(stat, value)
    }

    public fun despawn(companionId: Long) {
        companionPlayerRegistry.unregister(companionId)
        val bot = companionPlayers.remove(companionId)
        val infos = allocatedInfos.remove(companionId)
        if (bot != null && bot.isSlotAssigned) {
            playerRegistry.del(bot)
        }
        if (infos != null) {
            networkService.infoProtocols.dealloc(infos)
        }
    }

    public fun despawnAll(ownerCharacterId: Long) {
        val toRemove =
            companionPlayers.keys.filter { cid ->
                companionPlayerRegistry.getOwnerCharacterId(cid) == ownerCharacterId
            }

        for (cid in toRemove) {
            despawn(cid)
        }
    }

    public fun syncEquipment(companion: Companion, companionPlayer: Player) {
        for (wearpos in Wearpos.visibleWearpos) {
            companionPlayer.worn[wearpos.slot] = null
        }

        for (instanceId in companion.gearInstanceIds) {
            val inst = equipmentInstances[instanceId] ?: continue
            val type = objTypes[inst.templateObj] ?: continue
            val wearpos = Wearpos[type.wearpos1] ?: continue
            companionPlayer.worn[wearpos.slot] = InvObj(type, 1, instanceId = instanceId)
        }

        if (companion.npcId > 0) {
            companionPlayer.appearance.transmog = npcTypes[companion.npcId]
        } else {
            companionPlayer.appearance.transmog = null
        }

        companionPlayer.appearance.combatLevel = companion.level
        companionPlayer.displayName = companion.name
        companionPlayer.rebuildAppearance()
    }

    public fun getPlayer(companionId: Long): Player? = companionPlayers[companionId]

    public fun isCompanion(player: Player): Boolean =
        companionPlayerRegistry.isCompanionPlayer(player)

    public fun getCompanionId(player: Player): Long? =
        companionPlayerRegistry.getCompanionId(player)

    public fun getOwnerCharacterId(companionId: Long): Long? =
        companionPlayerRegistry.getOwnerCharacterId(companionId)
}

/**
 * The observer identity stamped on every drop attributed to a companion bot (`Obj.fromOwner`
 * `receiverId`/`ownerId`). Sharing the owner's [Player.observerUUID] makes companion kills drop
 * loot that the owner can see and take under `IronmanPolicy` - the same mechanism group ironman
 * uses to share drops inside a group. Falls back to the companion's own negative id when the owner
 * somehow has no observer id yet, preserving the old isolated-drop behaviour.
 */
internal fun companionObserverId(owner: Player, companionId: Long): Long =
    owner.observerUUID ?: -companionId

public class CompanionClient(public val infos: Infos) : Client<NetworkService<Player>, Any> {
    override fun close() {}

    override fun write(message: Any) {}

    override fun read(player: Player) {}

    override fun flush() {}

    override fun flushHighPriority() {}

    override fun unregister(service: NetworkService<Player>, player: Player) {
        service.infoProtocols.dealloc(infos)
    }
}

public class CompanionPlayerCycle(private val infos: Infos, private val objTypes: ObjTypeList) :
    ClientCycle {
    private var knownCoords: CoordGrid = CoordGrid.ZERO
    private var knownCachedSpeed: MoveSpeed = MoveSpeed.Stationary
    private var knownFaceEntity: Int? = -1

    private val playerExtendedInfo: PlayerAvatarExtendedInfo
        get() = infos.playerInfo.avatar.extendedInfo

    override fun update(player: Player) {
        if (knownCachedSpeed != player.cachedMoveSpeed) {
            playerExtendedInfo.setMoveSpeed(player.cachedMoveSpeed.steps)
            knownCachedSpeed = player.cachedMoveSpeed
        }
        val moveSpeed = player.resolvePendingMoveSpeed()
        if (moveSpeed != player.cachedMoveSpeed && player.coords != knownCoords) {
            playerExtendedInfo.setTempMoveSpeed(moveSpeed.steps)
        }

        infos.updateRootCoord(player.level, player.x, player.z)
        knownCoords = player.coords

        val faceSlot = player.faceEntity.entitySlot
        if (knownFaceEntity != faceSlot) {
            @Suppress("DEPRECATION") playerExtendedInfo.setFacePathingEntity(faceSlot)
            knownFaceEntity = faceSlot
        }

        if (player.pendingFaceAngle != EntityFaceAngle.NULL) {
            playerExtendedInfo.setFaceAngle(player.pendingFaceAngle.intValue)
        }

        when (player.pendingSequence) {
            EntitySeq.NULL -> {}
            EntitySeq.ZERO -> playerExtendedInfo.setSequence(-1, 0)
            else ->
                playerExtendedInfo.setSequence(
                    player.pendingSequence.id,
                    player.pendingSequence.delay,
                )
        }

        if (!player.pendingSpotanims.isEmpty) {
            for (packed in player.pendingSpotanims.longIterator()) {
                val (id, delay, height, slot) = EntitySpotanim(packed)
                playerExtendedInfo.setSpotAnim(slot, id, delay, height)
            }
        }

        val sayText = player.pendingSay
        if (sayText != null) {
            playerExtendedInfo.setSay(sayText)
        }

        for (packedHeadbar in player.activeHeadbars.longIterator()) {
            val headbar = Headbar(packedHeadbar)
            playerExtendedInfo.addHeadBar(
                sourceIndex = if (headbar.isNoSource) -1 else headbar.sourceSlot,
                selfType = headbar.self,
                otherType = if (headbar.isPrivate) -1 else headbar.public,
                startFill = headbar.startFill,
                endFill = headbar.endFill,
                startTime = headbar.startTime,
                endTime = headbar.endTime,
            )
        }

        for (packedHitmark in player.activeHitmarks.longIterator()) {
            val hitmark = Hitmark(packedHitmark)
            playerExtendedInfo.addHitMark(
                sourceIndex = if (hitmark.isNoSource) -1 else hitmark.sourceSlot,
                selfType = hitmark.self,
                sourceType = hitmark.source,
                otherType = if (hitmark.isPrivate) -1 else hitmark.public,
                value = hitmark.damage,
                delay = hitmark.delay,
            )
        }

        syncAppearance(player)
    }

    private fun Player.resolvePendingMoveSpeed(): MoveSpeed =
        when {
            pendingTelejump -> MoveSpeed.Stationary
            pendingTeleport -> MoveSpeed.Walk
            pendingStepCount == 1 -> MoveSpeed.Walk
            pendingStepCount == 2 -> MoveSpeed.Run
            else -> moveSpeed
        }

    private fun syncAppearance(player: Player) {
        if (!player.appearance.rebuild) {
            return
        }
        val info = playerExtendedInfo

        val colours = player.appearance.coloursSnapshot()
        for (i in colours.indices) {
            info.setColour(i, colours[i].toInt())
        }

        val identKit = player.appearance.identKitSnapshot()
        for (i in identKit.indices) {
            info.setIdentKit(i, identKit[i].toInt())
        }

        info.setName(player.displayName)
        info.setOverheadIcon(player.overheadIcon ?: -1)
        info.setSkullIcon(player.skullIcon ?: -1)
        info.setCombatLevel(player.combatLevel)
        info.setBodyType(player.appearance.bodyType)
        info.setPronoun(player.appearance.pronoun)
        info.setHidden(player.appearance.softHidden)

        info.setNameExtras(
            beforeName = player.appearance.namePrefix ?: "",
            afterName = player.appearance.nameSuffix ?: "",
            afterCombatLevel = player.appearance.combatLvlSuffix ?: "",
        )

        val bas = player.appearance.bas
        val weapon = player.righthand
        val transmog = player.appearance.transmog

        val readyAnim: Int
        val turnOnSpotAnim: Int
        val walkForwardAnim: Int
        val walkBackAnim: Int
        val walkLeftAnim: Int
        val walkRightAnim: Int
        val runningAnim: Int

        if (bas != null) {
            readyAnim = bas.readyAnim.id
            turnOnSpotAnim = bas.turnOnSpot.id
            walkForwardAnim = bas.walkForward.id
            walkBackAnim = bas.walkBack.id
            walkLeftAnim = bas.walkLeft.id
            walkRightAnim = bas.walkRight.id
            runningAnim = bas.running.id
        } else if (transmog != null) {
            readyAnim = transmog.readyAnim
            turnOnSpotAnim = transmog.turnBackAnim
            walkForwardAnim = transmog.walkAnim
            walkBackAnim = transmog.walkAnim
            walkLeftAnim = transmog.turnLeftAnim
            walkRightAnim = transmog.turnRightAnim
            runningAnim = transmog.runAnim
        } else if (weapon != null) {
            val type = objTypes[weapon]
            readyAnim = type.param(params.bas_readyanim).id
            turnOnSpotAnim = type.param(params.bas_turnonspot).id
            walkForwardAnim = type.param(params.bas_walk_f).id
            walkBackAnim = type.param(params.bas_walk_b).id
            walkLeftAnim = type.param(params.bas_walk_l).id
            walkRightAnim = type.param(params.bas_walk_r).id
            runningAnim = type.param(params.bas_running).id
        } else {
            val default = baseanimsets.human_default
            readyAnim = default.readyAnim.id
            turnOnSpotAnim = default.turnOnSpot.id
            walkForwardAnim = default.walkForward.id
            walkBackAnim = default.walkBack.id
            walkLeftAnim = default.walkLeft.id
            walkRightAnim = default.walkRight.id
            runningAnim = default.running.id
        }

        info.setTransmogrification(transmog?.id ?: -1)
        info.setBaseAnimationSet(
            readyAnim = readyAnim,
            turnAnim = turnOnSpotAnim,
            walkAnim = walkForwardAnim,
            walkAnimBack = walkBackAnim,
            walkAnimLeft = walkLeftAnim,
            walkAnimRight = walkRightAnim,
            runAnim = runningAnim,
        )

        for (wearpos in Wearpos.visibleWearpos) {
            val obj = player.worn[wearpos.slot]
            if (obj == null) {
                info.setWornObj(wearpos.slot, -1, -1, -1)
                continue
            }
            val objType = objTypes[obj]
            info.setWornObj(wearpos.slot, obj.id, objType.wearpos2, objType.wearpos3)
        }
    }

    override fun flush(player: Player) {
        val infoPackets = infos.getPackets()
        val root = infoPackets.rootWorldInfoPackets
        root.worldEntityInfo.onSuccess { it.consume() }
        root.playerInfo.onSuccess { it.consume() }
        if (!root.npcInfo.isEmpty()) {
            root.npcInfo.onSuccess { it.consume() }
        } else {
            root.npcInfo.safeReleaseOrThrow()
        }
    }

    override fun release() {}
}
