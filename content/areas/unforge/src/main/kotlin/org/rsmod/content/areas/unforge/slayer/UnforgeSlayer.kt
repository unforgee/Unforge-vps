package org.rsmod.content.areas.unforge.slayer

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.ceil
import kotlin.random.Random
import org.rsmod.api.area.checker.Wilderness
import org.rsmod.api.combat.commons.SlayerTaskProviders
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.unforge.shops.UnforgeShopInvRefs
import org.rsmod.content.areas.unforge.slayer.hub.slayer_interfaces
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kronos Slayer port (`skills/slayer/Slayer.java`, `SlayerTask.java`, `SlayerUnlock.java` +
 * `SlayerMaster.java`).
 *
 * Task definitions and npc->task links are generated from `content/kronos-data` by
 * `work/port-tools/port_slayer.py` into `unforge_slayer_tasks.txt` and
 * `unforge_slayer_npcs.txt`.
 *
 * Player state is persisted in `cw_slayer_*` varps. The Kronos unlock/extension flags live in two
 * bitmask varps indexed by [CwSlayerUnlock.ordinal].
 */
object UnforgeSlayerVarps : VarpReferences() {
    val task = find("cw_slayer_task")
    val remaining = find("cw_slayer_remaining")
    val assigned = find("cw_slayer_assigned")
    val dangerous = find("cw_slayer_dangerous")
    val points = find("cw_slayer_points")
    val wildPoints = find("cw_slayer_wild_points")
    val completed = find("cw_slayer_completed")
    val wildCompleted = find("cw_slayer_wild_completed")
    val unlocksA = find("cw_slayer_unlocks_a")
    val unlocksB = find("cw_slayer_unlocks_b")
    val blocks =
        listOf(
            find("cw_slayer_block0"),
            find("cw_slayer_block1"),
            find("cw_slayer_block2"),
            find("cw_slayer_block3"),
            find("cw_slayer_block4"),
            find("cw_slayer_block5"),
        )
}

internal object UnforgeSlayerVarpBuilder : VarpBuilder() {
    init {
        build("cw_slayer_task")
        build("cw_slayer_remaining")
        build("cw_slayer_assigned")
        build("cw_slayer_dangerous")
        build("cw_slayer_points")
        build("cw_slayer_wild_points")
        build("cw_slayer_completed")
        build("cw_slayer_wild_completed")
        build("cw_slayer_unlocks_a")
        build("cw_slayer_unlocks_b")
        for (i in 0..5) {
            build("cw_slayer_block$i")
        }
    }
}

/** Mirrors `SlayerTask.Type` - base points/modifier/min combat per task category. */
enum class CwSlayerTaskType(val basePoints: Int, val modifier: Double, val minCombat: Int) {
    EASY(5, 1.0 / 3.0, 3),
    MEDIUM(15, 2.0 / 3.0, 60),
    HARD(25, 1.0, 80),
    BOSS(100, 1.0, 100),
}

/**
 * Mirrors `SlayerUnlock` - purchasable unlocks/extensions. Ordinal = bit index inside
 * `cw_slayer_unlocks_a`/`_b`.
 */
enum class CwSlayerUnlock(val price: Int, val extension: Boolean, val label: String) {
    GARGOYLE_SMASHER(120, false, "Gargoyle Smasher"),
    SLUG_SALTER(80, false, "Slug Salter"),
    REPTILE_FREEZER(90, false, "Reptile Freezer"),
    SHROOM_SPRAYER(110, false, "Shroom Sprayer"),
    NEED_MORE_DARKNESS(100, true, "Need More Darkness"),
    MALEVOLENT_MASQUERADE(400, false, "Malevolent Masquerade"),
    RING_BLING(300, false, "Ring Bling"),
    BROADER_FLETCHING(300, false, "Broader Fletching"),
    ANKOU_VERY_MUCH(100, true, "Ankou Very Much"),
    SUQ_ANOTHER_ONE(100, true, "Suq Another One"),
    FIRE_AND_DARKNESS(50, true, "Fire and Darkness"),
    PEDAL_TO_THE_METALS(100, true, "Pedal to the Metals"),
    SPIRITUAL_FERVOUR(100, true, "Spiritual Fervour"),
    AUGMENT_MY_ABBIES(100, true, "Augment My Abbies"),
    ITS_DARK_IN_HERE(100, true, "It's Dark in Here"),
    GREATER_CHALLENGE(100, true, "Greater Challenge"),
    I_HOPE_YOU_MITH_ME(80, false, "I Hope You Mith Me"),
    WATCH_THE_BIRDIE(80, false, "Watch the Birdie"),
    HOT_STUFF(100, false, "Hot Stuff"),
    LIKE_A_BOSS(200, false, "Like a Boss"),
    BLEED_ME_DRY(75, true, "Bleed Me Dry"),
    SMELL_YA_LATER(100, true, "Smell Ya Later"),
    BIRDS_OF_A_FEATHER(100, true, "Birds of a Feather"),
    I_REALLY_MITH_YOU(120, true, "I Really Mith You"),
    HORRORIFIC(100, true, "Horrific"),
    TO_DUST_YOU_SHALL_RETURN(100, true, "To Dust You Shall Return"),
    WYVER_NOTHER_ONE(100, true, "Wyver-Nother One"),
    GET_SMASHED(100, true, "Get Smashed"),
    NECHS_PLEASE(100, true, "Nechs Please"),
    KRACK_ON(100, true, "Krack On"),
    REPTILE_GOT_RIPPED(75, false, "Reptile Got Ripped"),
    KING_BLACK_BONNET(1000, false, "King Black Bonnet"),
    KALPHITE_KHAT(1000, false, "Kalphite Khat"),
    UNHOLY_HELMET(1000, false, "Unholy Helmet"),
    SEEING_RED(50, false, "Seeing Red"),
    GET_SCABARIGHT_ON_IT(50, true, "Get Scabaright on It"),
    UNLOCK_DULY_NOTED(200, false, "Duly Noted"),
    DARK_MANTLE(1000, false, "Dark Mantle"),
    WYVER_NOTHER_TWO(100, true, "Wyver-Nother Two"),
    ADA_MIND_SOME_MORE(100, true, "Ada-Mind Some More"),
    RUUUUUNE(100, true, "Ruuuuune!"),
    UNDEAD_HEAD(1000, false, "Undead Head"),
    STOP_THE_WYVERN(500, false, "Stop the Wyvern"),
    DOUBLE_TROUBLE(500, false, "Double Trouble"),
    USE_MORE_HEAD(1000, false, "Use More Head"),
    BASILONGER(100, true, "Basilonger"),
    BASILOCKED(80, false, "Basilocked"),
    BIGGER_AND_BADDER(150, false, "Bigger and Badder"),
    TWISTED_VISION(1000, false, "Twisted Vision"),
}

fun Player.hasSlayerUnlock(unlock: CwSlayerUnlock): Boolean {
    val bit = unlock.ordinal % 32
    val value =
        vars[
            if (unlock.ordinal < 32) {
                UnforgeSlayerVarps.unlocksA
            } else {
                UnforgeSlayerVarps.unlocksB
            },
        ]
    return (value ushr bit) and 1 == 1
}

fun Player.setSlayerUnlock(unlock: CwSlayerUnlock, on: Boolean) {
    val bit = unlock.ordinal % 32
    val varp =
        if (unlock.ordinal < 32) UnforgeSlayerVarps.unlocksA else UnforgeSlayerVarps.unlocksB
    val cur = vars[varp]
    VarPlayerIntMapSetter.set(this, varp, if (on) cur or (1 shl bit) else cur and (1 shl bit).inv())
}

class CwSlayerTeleport(val x: Int, val z: Int, val level: Int, val label: String) {
    val wilderness: Boolean
        get() =
            label.contains("WILDERNESS", ignoreCase = true) ||
                Wilderness.getWildernessLevel(CoordGrid(x, z, level)) > 0

    val display: String
        get() = label.replace(Regex("<[^>]*>"), "")
}

class CwSlayerTask(
    val key: Int,
    val name: String,
    val types: Set<CwSlayerTaskType>,
    val level: Int,
    val min: Int,
    val max: Int,
    val weight: Int,
    val disabled: Boolean,
    val unlock: CwSlayerUnlock?,
    val extension: CwSlayerUnlock?,
    val mainSpawns: Int,
    val wildSpawns: Int,
    val teleports: List<CwSlayerTeleport>,
) {
    /** Highest-ordinal type, matching `SlayerTask.getHighestType` (used for the point modifier). */
    val highestType: CwSlayerTaskType
        get() = types.maxBy { it.ordinal }
}

object UnforgeSlayerNpcs : NpcReferences() {
    val nieve = find("slayer_master_nieve")

    /** Kronos TURAEL id (401) - offers the "reset to easy task" option. */
    val turael = find("tog_light_creature")
    val masters =
        listOf(
            nieve,
            find("wyvern_cave_steve"),
            find("slayer_master_7"),
            find("slayer_master_8"),
            turael,
            find("wgs_thaerisk_cemphier_multinpc1"), // kronos id 402
            find("slayer_master_3"),
            find("slayer_master_4"),
            find("wgs_thaerisk_cemphier_multinpc1_instance"), // kronos id 405
            find("tzhaar_merchant_cityequipment"),
        )
}

class UnforgeSlayer
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
    private val shops: Shops,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val logger = InlineLogger()

    /** Task index -> task; index+1 is stored in `cw_slayer_task` (0 = no task). */
    private val tasks: List<CwSlayerTask>
        get() = CwSlayerData.tasks

    /** npc type id -> slayer task names the npc counts towards. */
    private val npcTasks: Map<Int, Set<String>>
        get() = CwSlayerData.npcTasks

    /**
     * Task names an npc type counts towards: the generated task-npc links plus the superior
     * variants registered in [CwSuperiorData] (a superior kill credits its base task).
     */
    private fun taskNamesFor(npcTypeId: Int): Set<String>? =
        npcTasks[npcTypeId] ?: CwSuperiorData.taskNameFor(npcTypes, npcTypeId)?.let(::setOf)

    override fun ScriptContext.startup() {
        logger.info { "Unforge slayer: ${tasks.size} tasks, ${npcTasks.size} task npcs loaded." }

        // Lets combat formulas ask whether an npc counts towards the player's current task
        // (activates the already-wired slayer helm / black mask accuracy+maxhit bonuses).
        SlayerTaskProviders.isTaskNpc = { type, player ->
            val task = player.cwCurrentTask()
            val names = taskNamesFor(type.id)
            task != null && names != null && names.any { it.equals(task.name, ignoreCase = true) }
        }

        bindMasters()
        onEvent<NpcKilledEvent> { onTaskKill(this) }
    }

    // ---------- master ops ----------

    /**
     * Binds master ops by label (like `ShopkeeperScript`) so we never overwrite an unrelated op
     * such as "Attack" on a master whose op layout differs per cache type.
     */
    private fun ScriptContext.bindMasters() {
        for (ref in UnforgeSlayerNpcs.masters) {
            val type = npcTypes[ref]
            for (slot in type.op.indices) {
                when (type.op[slot]?.lowercase()) {
                    "talk-to" -> bindOp(type, slot + 1) { masterTalk(it.npc) }
                    "assignment" -> bindOp(type, slot + 1) { assignmentDialogue(it.npc) }
                    // "rewards" is bound by the slayer hub (SlayerHubScript).
                    "teleport" -> bindOp(type, slot + 1) { teleportToTaskDialogue(it.npc) }
                }
            }
        }
    }

    private fun ScriptContext.bindOp(
        type: UnpackedNpcType,
        slot: Int,
        action: suspend ProtectedAccess.(NpcEvents.Op) -> Unit,
    ) {
        when (slot) {
            1 -> onOpNpc1(type) { action(it) }
            2 -> onOpNpc2(type) { action(it) }
            3 -> onOpNpc3(type) { action(it) }
            4 -> onOpNpc4(type) { action(it) }
            5 -> onOpNpc5(type) { action(it) }
        }
    }

    private suspend fun ProtectedAccess.masterTalk(npc: Npc) {
        var choice = -1
        startDialogue(npc) {
            chatNpc(neutral, "Yeah? What do you want?")
            choice =
                choice5(
                    if (player.cwCurrentTask() == null) {
                        "I want a Slayer assignment."
                    } else {
                        "Tell me about my Slayer assignment."
                    },
                    0,
                    "Teleport to my Slayer task.",
                    1,
                    "Do you have anything to trade?",
                    2,
                    "Have you any rewards for me?",
                    3,
                    "Er... Nothing...",
                    4,
                )
            when (choice) {
                0 -> chatPlayer(neutral, "I want a Slayer assignment.")
                1 -> chatPlayer(neutral, "Can you teleport me to my Slayer task?")
                2 -> {
                    chatPlayer(neutral, "Do you have anything to trade?")
                    chatNpc(
                        neutral,
                        "I have a wide variety of Slayer equipment for sale! Have a look..",
                    )
                }
                3 -> {
                    chatPlayer(neutral, "Have you any rewards for me?")
                    chatNpc(neutral, "I have quite a few rewards you can earn!<br>Take a look..")
                }
                else -> chatPlayer(neutral, "Er... Nothing...")
            }
        }
        when (choice) {
            0 -> assignmentDialogue(npc)
            1 -> teleportToTaskDialogue(npc)
            2 ->
                shops.open(
                    player,
                    npc,
                    "Slayer Equipment",
                    UnforgeShopInvRefs.cw_slayer_equipment,
                )
            3 -> ifOpenMainModal(slayer_interfaces.slayer_hub)
        }
    }

    private suspend fun ProtectedAccess.assignmentDialogue(npc: Npc) {
        startDialogue(npc) { assignment(npc) }
    }

    private suspend fun Dialogue.assignment(npc: Npc) {
        // Turael (kronos 401) offers to reset the current task to an easy one.
        if (npc.type.id == UnforgeSlayerNpcs.turael.id && player.cwCurrentTask() != null) {
            chatNpc(
                neutral,
                "Would you like me to reset your current task? You will be assigned an " +
                    "<col=dc143c>easy</col> task.",
            )
            if (choice2("Yes please.", true, "No, thanks.", false, title = "Reset task?")) {
                chatPlayer(neutral, "Yes please.")
                player.cwResetTask()
                val task = cwAssignTask(player, CwSlayerTaskType.EASY, false)
                if (task == null) {
                    chatNpc(neutral, "I couldn't find a suitable task for you right now.")
                } else {
                    chatNpc(
                        neutral,
                        "Your new task is to kill ${player.cwRemaining()} " +
                            "${task.name}.<br>Good luck!",
                    )
                }
            }
            return
        }
        if (player.cwRemaining() == -1) {
            requestAmount()
            return
        }
        val current = player.cwCurrentTask()
        if (current == null) {
            if (
                player.vars[UnforgeSlayerVarps.completed] == 0 &&
                    player.vars[UnforgeSlayerVarps.wildCompleted] == 0
            ) {
                val task = cwAssignTask(player, CwSlayerTaskType.EASY, false)
                if (task == null) {
                    chatNpc(neutral, "I couldn't find a suitable task for you right now.")
                } else {
                    chatNpc(
                        neutral,
                        "Your new task is to kill ${player.cwRemaining()} " +
                            "${task.name}.<br>Good luck!",
                    )
                }
                return
            }
            chatNpc(neutral, "What kind of task would you like?")
            val type =
                choice4(
                    "I want an easy task.",
                    CwSlayerTaskType.EASY,
                    "I want a medium task.",
                    CwSlayerTaskType.MEDIUM,
                    "I want a hard task.",
                    CwSlayerTaskType.HARD,
                    "I want a boss task.",
                    CwSlayerTaskType.BOSS,
                )
            chatPlayer(neutral, "I want a <col=dc143c>${type.name.lowercase()}</col> task.")
            if (
                type == CwSlayerTaskType.BOSS && !player.hasSlayerUnlock(CwSlayerUnlock.LIKE_A_BOSS)
            ) {
                chatNpc(neutral, "You haven't unlocked the ability to receive boss tasks yet.")
                return
            }
            if (player.combatLevel < type.minCombat) {
                chatNpc(
                    neutral,
                    "You must have combat level of ${type.minCombat} or higher for this " +
                        "type of task.",
                )
                return
            }
            chatNpc(neutral, "Do you prefer monsters that can be found in the wilderness?")
            val wild =
                choice3(
                    "Yes, the more dangerous the better!",
                    true,
                    "No, I'd rather stay out of the wilderness.",
                    false,
                    "It doesn't matter, surprise me!",
                    null,
                )
            val task = cwAssignTask(player, type, wild ?: Random.nextBoolean())
            if (task == null) {
                chatNpc(neutral, "I couldn't find a suitable task for you right now.")
                return
            }
            if (player.cwRemaining() == -1) {
                requestAmount()
            } else {
                chatNpc(
                    neutral,
                    "Your new task is to kill ${player.cwRemaining()} ${task.name}.<br>Good luck!",
                )
            }
            return
        }
        chatNpc(
            neutral,
            "You're currently assigned to kill ${current.name}; only " +
                "${player.cwRemaining()} more to go.",
        )
    }

    private suspend fun Dialogue.requestAmount() {
        val task = player.cwCurrentTask() ?: return
        chatNpc(neutral, "Your new task is to kill ${task.name}. How many would you like to slay?")
        var amount = access.countDialog("Enter amount: (${task.min}-${task.max})")
        while (amount < task.min || amount > task.max) {
            amount = access.countDialog("Invalid amount, try again: (${task.min}-${task.max})")
        }
        cwSetVarp(player, UnforgeSlayerVarps.remaining, amount)
        cwSetVarp(player, UnforgeSlayerVarps.assigned, amount)
        chatNpc(neutral, "Your new task is to kill $amount ${task.name}.<br>Good luck!")
    }

    // ---------- teleport ----------

    private suspend fun ProtectedAccess.teleportToTaskDialogue(npc: Npc) {
        var target: CoordGrid? = null
        startDialogue(npc) {
            val task = player.cwCurrentTask()
            if (task == null || player.cwRemaining() <= 0) {
                chatNpc(
                    neutral,
                    "You don't currently have an active Slayer task assignment! " +
                        "Get an assignment from me first.",
                )
                return@startDialogue
            }
            val dangerous = player.cwIsDangerous()
            val tp =
                task.teleports.firstOrNull { it.wilderness == dangerous }
                    ?: task.teleports.firstOrNull()
            if (tp == null) {
                chatNpc(neutral, "Your task is ${task.name}. Head to their usual habitat!")
                return@startDialogue
            }
            chatNpc(
                neutral,
                "I can teleport you directly to your task location: " +
                    "<col=dc143c>${task.name}</col>" +
                    (if (dangerous) " (Wilderness)" else "") +
                    ". Would you like to go now?",
            )
            if (choice2("Yes, teleport me now!", true, "No, thanks.", false)) {
                target = CoordGrid(tp.x, tp.z, tp.level)
            }
        }
        target?.let(::telejump)
    }

    // ---------- kill tracking ----------

    private fun onTaskKill(event: NpcKilledEvent) {
        val killer = event.killer
        val task = killer.cwCurrentTask() ?: return
        val names = taskNamesFor(event.npc.type.id) ?: return
        if (names.none { it.equals(task.name, ignoreCase = true) }) {
            return
        }
        val remaining = killer.cwRemaining()
        if (remaining <= 0) {
            return
        }

        val xp = (event.npc.visType.paramOrNull(params.slayer_experience) ?: 0) / 10.0
        cwSetVarp(killer, UnforgeSlayerVarps.remaining, remaining - 1)
        if (xp > 0) {
            launcher.launch(killer) { statAdvance(stats.slayer, xp) }
        }

        // Kronos wilderness bonus drops on task kills (Slayer.onNPCKill).
        if (Wilderness.getWildernessLevel(event.npc.coords) > 0) {
            rollWildernessBonus(event)
        }
        rollTaskRewards(event)

        if (remaining - 1 <= 0) {
            finishTask(killer, task)
        }
    }

    private fun drop(event: NpcKilledEvent, id: Int) {
        val type = objTypes[id] ?: return
        objRepo.add(type, event.npc.coords, constants.lootdrop_duration, event.killer, 1)
    }

    private fun rollWildernessBonus(event: NpcKilledEvent) {
        val rate = combatLevelDropRate(event.npc.visType.vislevel)
        if (Random.nextInt(1, 41) == 1) {
            drop(event, 23490) // slayer wilderness key
        }
        if (Random.nextInt(ceil(rate * 500).toInt()) == 0) {
            drop(event, 12746) // bh emblem
        }
        if (Random.nextInt(ceil(rate * 40).toInt()) == 0) {
            drop(event, 30104)
        }
    }

    /**
     * Bonus rolls on any valid task kill (not wilderness-gated). Rates scale with the npc's combat
     * level via [combatLevelDropRate], matching the wilderness bonus model: slayer casket ~1/48
     * (contains barrows/BH/deadman/supplies, no bis), crystal key ~1/150, random mystery box
     * ~1/200.
     */
    private fun rollTaskRewards(event: NpcKilledEvent) {
        val rate = combatLevelDropRate(event.npc.visType.vislevel)
        if (Random.nextInt(ceil(rate * 48).toInt()) == 0) {
            drop(event, 405) // slayer casket
        }
        if (Random.nextInt(ceil(rate * 150).toInt()) == 0) {
            drop(event, 989) // crystal key
        }
        if (Random.nextInt(ceil(rate * 200).toInt()) == 0) {
            drop(event, randomMysteryBox())
        }
    }

    /** Random box drop: mostly standard mystery boxes, rarer premium variants. */
    private fun randomMysteryBox(): Int =
        when (Random.nextInt(20)) {
            0 -> 22330 // pvp armour box (deadman starter pack)
            1,
            2 -> 6831 // third age box
            3,
            4 -> 290 // super box
            5,
            6,
            7 -> 6828 // pet box
            8,
            9,
            10 -> 6829 // vote box
            11 -> 30185 // summer box
            else -> 6199 // mystery box
        }

    private fun combatLevelDropRate(level: Int): Double =
        when {
            level >= 500 -> 0.80
            level >= 300 -> 0.85
            level >= 200 -> 0.90
            level >= 100 -> 0.95
            else -> 1.0
        }

    private fun finishTask(player: Player, task: CwSlayerTask) {
        val dangerous = player.cwIsDangerous()
        val completedVarp =
            if (dangerous) {
                UnforgeSlayerVarps.wildCompleted
            } else {
                UnforgeSlayerVarps.completed
            }
        val pointsVarp =
            if (dangerous) UnforgeSlayerVarps.wildPoints else UnforgeSlayerVarps.points
        val completed = player.vars[completedVarp] + 1
        val reward = (pointsReward(completed) * task.highestType.modifier).toInt()
        cwSetVarp(player, completedVarp, completed)
        cwSetVarp(player, pointsVarp, player.vars[pointsVarp] + reward)
        player.cwResetTask()
        launcher.launch(player) {
            mes("Your slayer task is now complete.")
            if (dangerous) {
                mes(
                    "You've completed a total of $completed wilderness tasks, earning " +
                        "$reward wilderness points. You now have a total of " +
                        "${player.vars[UnforgeSlayerVarps.wildPoints]} points."
                )
            } else {
                mes(
                    "You've completed a total of $completed tasks, earning $reward points. " +
                        "You now have a total of " +
                        "${player.vars[UnforgeSlayerVarps.points]} points."
                )
            }
        }
    }

    /** Mirrors `Slayer.getPointsReward` - base 30 with milestone multipliers. */
    private fun pointsReward(tasks: Int): Int {
        val base = 30
        return when {
            tasks % 1000 == 0 -> base * 50
            tasks % 250 == 0 -> base * 35
            tasks % 100 == 0 -> base * 25
            tasks % 50 == 0 -> base * 15
            tasks % 10 == 0 -> base * 5
            else -> base
        }
    }
}
