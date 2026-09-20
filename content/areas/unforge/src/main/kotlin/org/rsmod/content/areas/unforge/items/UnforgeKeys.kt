package org.rsmod.content.areas.unforge.items

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.config.refs.objs
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kronos key ports:
 * - `item/actions/impl/CrystalKey.java` - tooth + loop halves combine into a crystal key.
 * - `map/object/actions/impl/edgeville/CrystalKeyChest.java` - crystal chest in Edgeville.
 * - `activities/wilderness/cluekeys/ClueKeys.java` + `KeyData.java` - three wilderness clue keys,
 *   spade dig spawns a demon whose kill pays out loot + 1M coins.
 * - `item/actions/impl/Spade.java` - generic "Dig" handling for the registered dig spots.
 * - `content/Unforge/CrownItemKey.java` - canonical item identity helper ([CrownItemKey]).
 */
object UnforgeKeyObjs : ObjReferences() {
    val keyTooth = find("keyhalf1") // kronos 985
    val keyLoop = find("keyhalf2") // kronos 987
    val crystalKey = find("crystal_key") // kronos 989
    val uncutDragonstone = find("uncut_dragonstone") // kronos 1631
    val spade = find("spade") // kronos 952
    val keyBloodred = find("shadekey_steel_bloodred") // kronos 3455
    val keyCrimson = find("shadekey_steel_crimson") // kronos 3457
    val keyBlack = find("shadekey_steel_black") // kronos 3458
}

object UnforgeKeyLocs : LocReferences() {
    val chestClosed = find("crystal_chestclosed") // kronos 172
    val chestOpen = find("crystal_chestopen") // kronos 173
}

object UnforgeKeyNpcs : NpcReferences() {
    val clueDemon = find("black_knight_titan") // kronos 4067
}

internal object UnforgeKeySeqs : SeqReferences() {
    val dig = find("human_dig")
    val openChest = find("human_openchest")
}

/**
 * The shade keys and the spade have no usable iops in this cache - give them the Kronos inventory
 * options.
 */
internal object UnforgeKeyObjEdits : ObjEditor() {
    init {
        edit(UnforgeKeyObjs.spade) { iop1 = "Dig" }
        edit(UnforgeKeyObjs.keyBloodred) { iop1 = "Check-Hint" }
        edit(UnforgeKeyObjs.keyCrimson) { iop1 = "Check-Hint" }
        edit(UnforgeKeyObjs.keyBlack) { iop1 = "Check-Hint" }
    }
}

/** Port of `content/Unforge/CrownItemKey.java` - canonical "namespace:name" item identity. */
class CrownItemKey(val namespace: String, val canonicalName: String) {
    init {
        require(namespace.isNotEmpty()) { "namespace required" }
        require(canonicalName.isNotEmpty()) { "canonicalName required" }
        require(':' !in namespace && ':' !in canonicalName) {
            "no colons allowed in namespace or name"
        }
    }

    val fullKey: String
        get() = "$namespace:$canonicalName"

    override fun equals(other: Any?): Boolean = other is CrownItemKey && other.fullKey == fullKey

    override fun hashCode(): Int = fullKey.hashCode()

    override fun toString(): String = fullKey

    companion object {
        const val NAMESPACE_OSRS = "osrs"
        const val NAMESPACE_Unforge = "cw"

        fun parse(fullKey: String?): CrownItemKey? {
            if (fullKey == null) return null
            val colon = fullKey.indexOf(':')
            if (colon < 1 || colon >= fullKey.length - 1) return null
            return CrownItemKey(fullKey.substring(0, colon), fullKey.substring(colon + 1))
        }
    }
}

/** `KeyData` - clue key item -> dig spot + hint text. */
private enum class CwClueKey(
    val item: org.rsmod.game.type.obj.ObjType,
    val digSpot: CoordGrid,
    val locationName: String,
    val clue: String,
) {
    CHINCHOMPA(
        UnforgeKeyObjs.keyCrimson,
        CoordGrid(3138, 3784, 0),
        "near chins in 34 wild!",
        "The top of Black Chinchompa Hill",
    ),
    VOLCANO(
        UnforgeKeyObjs.keyBloodred,
        CoordGrid(3132, 3911, 0),
        "near the wilderness lever in 49 wild!",
        "Near the volcano vents in 49 wild, by the wilderness lever",
    ),
    SEAWEED(
        UnforgeKeyObjs.keyBlack,
        CoordGrid(3030, 3948, 0),
        "near the seaweed rocks in 54 wild!",
        "By the seaweed rocks in 54 wild",
    ),
}

// ClueKeys.REWARDS
private val CLUE_DEMON_REWARDS =
    intArrayOf(
        6914,
        4151,
        20128,
        20131,
        20137,
        4153,
        6528,
        10887,
        1249,
        11128,
        4716,
        4718,
        4720,
        4722,
        4708,
        4712,
        4714,
        6585,
        12831,
        6733,
        6735,
        6920,
        12397,
    )

// CrystalKeyChest.LOOTS - flattened into int quads (objId, count).
private val CRYSTAL_CHEST_LOOTS =
    arrayOf(
        intArrayOf(13442, 60, 995, 300000),
        intArrayOf(995, 1000000, 13440, 60),
        intArrayOf(560, 500, 566, 450, 565, 450),
        intArrayOf(454, 600),
        intArrayOf(1632, 10),
        intArrayOf(995, 750000, 985, 1),
        intArrayOf(2364, 20),
        intArrayOf(995, 750000, 987, 1),
        intArrayOf(450, 150),
        intArrayOf(1202, 3),
        intArrayOf(1080, 3, 1094, 3),
    )

// CrystalKeyChest.RARE_LOOT
private val CRYSTAL_CHEST_RARE = intArrayOf(4212, 4224, 13091)

class UnforgeKeys
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val players: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    /** Clue-key demons currently alive; maps the npc to its owner for the kill reward. */
    private val clueDemons = mutableMapOf<Npc, Player>()

    override fun ScriptContext.startup() {
        // CrystalKey.java - tooth half + loop half -> crystal key.
        onOpHeldU(UnforgeKeyObjs.keyLoop, UnforgeKeyObjs.keyTooth) {
            invDel(inv, UnforgeKeyObjs.keyLoop, 1, slot = it.firstSlot)
            invDel(inv, UnforgeKeyObjs.keyTooth, 1, slot = it.secondSlot)
            invAdd(inv, UnforgeKeyObjs.crystalKey)
            mes("You join the two halves of the key together.")
        }

        onOpLoc1(UnforgeKeyLocs.chestClosed) { openCrystalChest(it.loc) }

        // Spade.java dig - "Nothing interesting happens." unless on a registered dig spot.
        onOpHeld1(UnforgeKeyObjs.spade) { dig() }

        for (key in CwClueKey.entries) {
            onOpHeld1(key.item) { checkHint(key) }
        }

        onEvent<NpcKilledEvent> { onDemonKilled(this) }
    }

    // ---------- crystal chest ----------

    private suspend fun ProtectedAccess.openCrystalChest(chest: org.rsmod.game.loc.BoundLocInfo) {
        if (UnforgeKeyObjs.crystalKey !in inv) {
            mes("You need a crystal key to open this chest.")
            return
        }
        mes("You unlock the chest with your key.")
        invDel(inv, UnforgeKeyObjs.crystalKey)
        anim(UnforgeKeySeqs.openChest)
        // Kronos swaps the closed chest to the open variant for 2 cycles.
        locRepo.change(chest, locTypes[UnforgeKeyLocs.chestOpen], duration = 2)
        // The consumed key becomes an uncut dragonstone (key.setId(1631)).
        invAddOrDrop(objRepo, UnforgeKeyObjs.uncutDragonstone)
        if (Random.nextInt(250) == 0) {
            val loot = objTypes[CRYSTAL_CHEST_RARE.random()]
            if (loot != null) {
                invAddOrDrop(objRepo, loot)
                for (p in players) {
                    p.mes(
                        "<col=a52a2a>${player.displayName} just received ${loot.name} " +
                            "from the crystal chest!</col>"
                    )
                }
            }
        } else {
            val loot = CRYSTAL_CHEST_LOOTS.random()
            var i = 0
            while (i + 1 < loot.size) {
                val type =
                    objTypes[loot[i]]
                        ?: run {
                            i += 2
                            continue
                        }
                invAddOrDrop(objRepo, type, loot[i + 1])
                i += 2
            }
        }
    }

    // ---------- clue keys ----------

    private fun ProtectedAccess.checkHint(key: CwClueKey) {
        // Kronos shows a hint arrow when within 10 tiles, otherwise the clue text.
        if (key.digSpot.chebyshevDistance(player.coords) <= 10) {
            HintArrows.setTile(player, key.digSpot)
        } else {
            mes(key.clue)
        }
    }

    private suspend fun ProtectedAccess.dig() {
        anim(UnforgeKeySeqs.dig)
        delay(2)
        val key = CwClueKey.entries.firstOrNull { it.digSpot == player.coords }
        if (key == null || key.item !in inv) {
            mes("Nothing interesting happens.")
            return
        }
        invDel(inv, key.item)
        spawnClueDemon(player)
    }

    private fun spawnClueDemon(player: Player) {
        val type = npcTypes[UnforgeKeyNpcs.clueDemon]
        // Kronos uses RouteFinder.findWalkable(player.position); a neighbour tile is close
        // enough here since dig spots are open wilderness terrain.
        val demon = Npc(type, player.coords.translate(1, 0))
        npcRepo.add(demon, Int.MAX_VALUE)
        demon.respawns = false
        demon.say("You dare disturb me!!")
        demon.mode = NpcMode.OpPlayer2
        aiPlayerInteractions.interactOp(demon, player, InteractionOp.Op2)
        clueDemons[demon] = player
    }

    private fun onDemonKilled(event: NpcKilledEvent) {
        val owner = clueDemons.remove(event.npc) ?: return
        val killer = event.killer
        launcher.launch(killer) {
            val reward = objTypes[CLUE_DEMON_REWARDS.random()]
            if (reward != null) {
                invAddOrDrop(objRepo, reward)
            }
            invAddOrDrop(objRepo, objs.coins, 1_000_000)
            if (killer !== owner) {
                mes("<col=ff0000>That wasn't your clue key demon!</col>")
            }
        }
    }
}
