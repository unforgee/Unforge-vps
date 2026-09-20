package org.rsmod.content.areas.unforge.wilderness

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.combat.commons.npc.canRetaliate
import org.rsmod.api.combat.commons.npc.combatAttackStyle
import org.rsmod.api.combat.commons.types.NpcAttackStyle
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.content.areas.unforge.items.UnforgeBoxObjs
import org.rsmod.content.areas.unforge.items.UnforgeKeyObjs
import org.rsmod.content.areas.unforge.configs.UnforgeCustomNpcRefs
import org.rsmod.content.areas.unforge.map.UnforgeCombatNpcs
import org.rsmod.content.areas.unforge.map.UnforgeWanderNpcs
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Solo "Nightmare Zone" boss instance.
 *
 * Talk to the Nightmare Zone gatekeeper (`death_shopkeeper`, placed beside Yanille) to be
 * teleported into a private dream region. A random boss spawns inside; each kill advances the
 * round, scaling the next boss's stats up (+15% per round, capped at 3x) and adding bonus drop
 * rolls (+1 per round, capped at 4). Every boss kill also pays Perk Points through [NpcDeath].
 *
 * Leaving the region (teleport, death) or logging out ends the run and removes the boss.
 */
class SoloBossArenaScript
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
    private val dropTables: NpcDropTables,
    private val equipmentInstances: EquipmentInstanceService,
    private val perks: PerkService,
    private val pvmPoints: PvmPoints,
    private val soloBossPerks: SoloBossPerkService,
    private val launcher: ProtectedAccessLauncher,
    private val eventBus: EventBus,
    private val worldQueues: WorldQueueList,
    private val aiPlayers: AiPlayerInteractions,
) : PluginScript() {
    /** Active runs keyed by player. Removed on leave/logout; regions recycle once empty. */
    private val runs = mutableMapOf<Player, SoloBossRun>()

    /** Boss pool ordered by combat level - later rounds unlock deeper slices of the list. */
    private val bossPool by lazy { ArenaBosses.all.map { npcTypes[it] }.sortedBy { it.vislevel } }
    private val easyPool by lazy { ArenaBosses.easy.map { npcTypes[it] }.sortedBy { it.vislevel } }
    private val hardPool by lazy { ArenaBosses.hard.map { npcTypes[it] }.sortedBy { it.vislevel } }

    override fun ScriptContext.startup() {
        onCommand("soloboss") {
            desc = "Open the Solo Boss Arena"
            cheat(::openFromCommand)
        }
        onCommand("nm") {
            desc = "Open the Nightmare Zone mode menu"
            cheat(::openFromCommand)
        }
        onIfOpen(solo_boss_interfaces.menu) {
            player.ifSetEvents(solo_boss_components.close, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.easy, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.hard, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.boss, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.damage, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.attackSpeed, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.maxHealth, -1..-1, IfEvent.Op1)
            player.ifSetEvents(solo_boss_components.bank, -1..-1, IfEvent.Op1)
            player.ifSetText(
                solo_boss_components.perkStatus,
                "PvM points: ${pvmPoints.balance(player)} | Damage ${soloBossPerks.level(player, SoloBossPerk.DAMAGE)}/100 | Speed ${soloBossPerks.level(player, SoloBossPerk.ATTACK_SPEED)}/100 | Health ${soloBossPerks.level(player, SoloBossPerk.MAX_HEALTH)}/100",
            )
        }
        onIfClose(solo_boss_interfaces.menu) {}
        onIfModalButton(solo_boss_components.close) { ifClose() }
        onIfModalButton(solo_boss_components.easy) {
            ifClose()
            enterArena(ArenaMode.EASY)
        }
        onIfModalButton(solo_boss_components.hard) {
            ifClose()
            enterArena(ArenaMode.HARD)
        }
        onIfModalButton(solo_boss_components.boss) {
            ifClose()
            enterArena(ArenaMode.BOSS)
        }
        onIfModalButton(solo_boss_components.damage) { upgradePerk(SoloBossPerk.DAMAGE) }
        onIfModalButton(solo_boss_components.attackSpeed) { upgradePerk(SoloBossPerk.ATTACK_SPEED) }
        onIfModalButton(solo_boss_components.maxHealth) { upgradePerk(SoloBossPerk.MAX_HEALTH) }
        onIfModalButton(solo_boss_components.bank) { player.openBank(eventBus) }
        // `death_shopkeeper` is also spawned elsewhere (e.g. Wistan) - only the npc placed at
        // The Yanille-side NMZ post acts as the gatekeeper.
        onOpNpc1(UnforgeWanderNpcs.death_shopkeeper) {
            if (it.npc.spawnCoords == ArenaBosses.gatekeeperSpawn) {
                gatekeeperDialogue(it.npc)
            }
        }
        onOpNpc2(UnforgeWanderNpcs.death_shopkeeper) {
            if (it.npc.spawnCoords == ArenaBosses.gatekeeperSpawn) {
                gatekeeperDialogue(it.npc)
            }
        }
        // The portal npc's op labels vary by cache - accept any of the first three ops so the
        // exit always responds to a click.
        onOpNpc1(ArenaBosses.exitPortal) { exitPortal(it.npc) }
        onOpNpc2(ArenaBosses.exitPortal) { exitPortal(it.npc) }
        onOpNpc3(ArenaBosses.exitPortal) { exitPortal(it.npc) }
        onEvent<SessionStateEvent.Logout> { endRun(player) }
        onEvent<NpcKilledEvent> { onBossKilled(this) }
    }

    private fun openFromCommand(cheat: Cheat) {
        launcher.launch(cheat.player) {
            val enter =
                choice2(
                    "Enter the Nightmare Zone.",
                    true,
                    "Not today.",
                    false,
                    title = "Enter the Nightmare Zone?",
                )
            if (enter) {
                openModeMenu()
            }
        }
    }

    private suspend fun ProtectedAccess.openModeMenu() {
        ifOpenMainModal(solo_boss_interfaces.menu)
    }

    private suspend fun ProtectedAccess.upgradePerk(perk: SoloBossPerk) {
        val owner = this.player
        val before = soloBossPerks.level(owner, perk)
        val cost = soloBossPerks.cost(owner, perk)
        if (soloBossPerks.upgrade(owner, perk)) {
            owner.mes("<col=33b532>${perk.displayName} upgraded to level ${before + 1}/100.</col>")
        } else if (before >= SoloBossPerkService.MAX_LEVEL) {
            owner.mes("<col=ff9900>${perk.displayName} is already maxed.</col>")
        } else {
            owner.mes(
                "<col=ff3333>You need ${cost} PvM points for the next ${perk.displayName} level.</col>"
            )
        }
        owner.ifSetText(
            solo_boss_components.perkStatus,
            "PvM points: ${pvmPoints.balance(owner)} | Damage ${soloBossPerks.level(owner, SoloBossPerk.DAMAGE)}/100 | Speed ${soloBossPerks.level(owner, SoloBossPerk.ATTACK_SPEED)}/100 | Health ${soloBossPerks.level(owner, SoloBossPerk.MAX_HEALTH)}/100",
        )
    }

    private suspend fun ProtectedAccess.gatekeeperDialogue(npc: Npc) {
        var enter = false
        startDialogue(npc) {
            chatNpc(
                neutral,
                "Beyond me lies the Nightmare Zone - a private arena where a " +
                    "random boss awaits. Each round grows harder, but the loot improves.",
            )
            chatNpc(neutral, "Are you brave enough to enter?")
            enter =
                choice2(
                    "Enter the Nightmare Zone.",
                    true,
                    "Not today.",
                    false,
                    title = "Enter the Nightmare Zone?",
                )
        }
        if (enter) {
            openModeMenu()
        }
    }

    private suspend fun ProtectedAccess.enterArena(mode: ArenaMode) {
        if (runs.containsKey(player)) {
            mes("<col=ff0000>You are already inside the Nightmare Zone.</col>")
            return
        }
        val region = regionRepo.add(arenaTemplate)
        if (region == null) {
            mes("<col=ff0000>The Nightmare Zone is full. Try again shortly.</col>")
            return
        }
        val run = SoloBossRun(player, region, mode)
        runs[player] = run
        telejump(run.resolve(ArenaBosses.playerSpawn))
        spawnExitPortal(run)
        spawnRound(run)
        mes(
            "<col=ffb84d>${mode.displayName} mode: round 1 - ${run.npcs.size} enemies attack!</col>"
        )
        mes(
            "<col=ffb84d>Kill them all to advance rounds. Each round: +15% stats, +1 bonus drop.</col>"
        )
        scheduleWatch(run)
    }

    /**
     * Polls via the world queue until the player leaves the region bounds, then cleans the run up.
     * Must not be a player-coroutine `delay` loop: a suspended coroutine keeps the player
     * access-protected, blocking all movement and input for the whole run.
     */
    private fun scheduleWatch(run: SoloBossRun) {
        worldQueues.add(5) { pollRun(run) }
    }

    private fun pollRun(run: SoloBossRun) {
        val player = run.player
        if (runs[player] !== run) {
            return
        }
        if (player.coords !in run) {
            launcher.launch(player) {
                endRun(run)
                mes("<col=ffb84d>You have left the Nightmare Zone.</col>")
            }
            return
        }
        // Fallback exit: stepping onto the portal tile always leaves, even if the portal npc
        // exposes no clickable op in this cache.
        if (player.coords.chebyshevDistance(run.resolve(ArenaBosses.portalSpawn)) <= 1) {
            launcher.launch(player) { leaveArena(run) }
            return
        }
        driveAggression(run)
        scheduleWatch(run)
    }

    /**
     * Arena npcs are always aggressive toward the player: any round npc that is not actively
     * fighting is re-engaged. This deliberately bypasses the normal retaliation path's wander-range
     * escape - dream bosses hunt the player across the whole lair.
     */
    private fun driveAggression(run: SoloBossRun) {
        val player = run.player
        if (!player.isValidTarget()) {
            return
        }
        for (npc in run.npcs) {
            if (npc.isValidTarget() && npc.canRetaliate()) {
                engage(npc, player)
            }
        }
    }

    /** Orders [npc] to attack [player] - melee walks adjacent, other styles approach at range. */
    private fun engage(npc: Npc, player: Player) {
        if (npc.combatAttackStyle() != NpcAttackStyle.Melee) {
            npc.apPlayer2(player, aiPlayers)
        } else {
            npc.opPlayer2(player, aiPlayers)
        }
    }

    private suspend fun ProtectedAccess.exitPortal(portal: Npc) {
        val run = runs[player] ?: return
        if (portal.coords !in run) {
            return
        }
        leaveArena(run)
    }

    private suspend fun ProtectedAccess.leaveArena(run: SoloBossRun) {
        endRun(run)
        telejump(ArenaBosses.gatekeeperCoords)
        mes("<col=ffb84d>You escaped the Nightmare Zone on round ${run.round}.</col>")
    }

    private fun onBossKilled(event: NpcKilledEvent) {
        // Match the run by npc membership, not by killer: a companion bot scoring the kill
        // reports itself as `event.killer`, which is not a key in `runs` - that used to end
        // the round chain after the first companion-assisted clear.
        val run =
            runs.values.firstOrNull { event.npc === it.portal || event.npc in it.npcs } ?: return
        if (event.npc === run.portal) {
            // The exit portal is attackable - replace it so the player is never stranded.
            run.portal = null
            spawnExitPortal(run)
            return
        }
        if (!run.npcs.remove(event.npc)) {
            return
        }
        // Drops and points always credit the run's owner: the killer in a private dream is
        // either the owner or one of their companions.
        val hero = run.player

        // Bonus drop rolls scale with the completed round: +1 per round, capped at 4.
        val bonusRolls = (run.round - 1).coerceIn(0, MAX_BONUS_ROLLS)
        if (bonusRolls > 0) {
            val duration = constants.lootdrop_duration
            repeat(bonusRolls) {
                dropTables.roll(event.npc.type.id)?.forEach { (objId, count) ->
                    val objType = objTypes[objId] ?: return@forEach
                    // Match NpcDeath: equipment-eligible drops roll a persisted instance.
                    if (count == 1 && equipmentInstances.isInstanceEligible(objType)) {
                        equipmentInstances.rollAndPersist(
                            objType,
                            source = "solo-arena:${event.npc.type.id}:r${run.round}",
                            tier = EquipmentTierResolver.resolve(objType),
                            itemLevel = event.npc.type.vislevel.coerceAtLeast(1),
                            onFailure = {
                                objRepo.add(objType, event.npc.coords, duration, hero, count)
                            },
                        ) { instance ->
                            val invObj =
                                InvObj(objType, count = 1, instanceId = instance.instanceId)
                            objRepo.add(invObj, event.npc.coords, duration, hero)
                        }
                    } else {
                        objRepo.add(objType, event.npc.coords, duration, hero, count)
                    }
                }
            }
        }
        // Round bonus perk points: +1 per round beyond the first.
        if (run.round > 1) {
            perks.addPoints(hero, run.round - 1)
        }
        val modePoints =
            when (run.mode) {
                ArenaMode.EASY -> 1
                ArenaMode.HARD -> 3
                ArenaMode.BOSS -> 5 + run.round
            }
        val awardedPvm =
            pvmPoints.award(hero, event.npc.type.vislevel, bonusBps = (modePoints - 1) * 10_000)
        if (awardedPvm > 0) {
            mesAward(hero, awardedPvm)
        }
        rollArenaBonusDrop(run, event.npc)

        // The round only clears once every spawned npc is dead.
        if (run.npcs.isNotEmpty()) {
            return
        }
        run.round++
        launcher.launch(hero) {
            mes(
                "<col=00ff00>Round ${run.round - 1} cleared!</col> " +
                    "<col=ffb84d>Round ${run.round} begins - stats +${statBonusPercent(run.round)}%, " +
                    "bonus drops +${(run.round - 1).coerceIn(0, MAX_BONUS_ROLLS)}.</col>"
            )
        }
        // The next wave arrives through the world queue, not a suspended player coroutine:
        // a `delay()` there would keep the player access-protected and block their input.
        worldQueues.add(3) {
            if (runs[run.player] === run) {
                spawnRound(run)
            }
        }
    }

    private fun spawnRound(run: SoloBossRun) {
        val pool =
            when (run.mode) {
                ArenaMode.EASY -> easyPool
                ArenaMode.HARD -> hardPool
                ArenaMode.BOSS -> bossPool
            }
        if (pool.isEmpty()) {
            return
        }
        // Every round spawns a pack of 4-10 npcs, growing one per round until the cap.
        val count = (run.round + 3).coerceIn(MIN_ROUND_NPCS, MAX_ROUND_NPCS)
        // Rounds progressively unlock the harder end of the pool: round 1 picks from the two
        // weakest entries, and every round unlocks two more.
        val unlocked = (run.round * 2).coerceIn(2, pool.size)
        for (i in 0 until count) {
            val type = pool[Random.nextInt(unlocked)]
            val (dx, dz) = SPAWN_OFFSETS[i % SPAWN_OFFSETS.size]
            spawnArenaNpc(run, type, ArenaBosses.bossSpawn.translate(dx, dz))
        }
    }

    private fun spawnArenaNpc(run: SoloBossRun, type: UnpackedNpcType, normalSpawn: CoordGrid) {
        val npc = Npc(type, run.resolve(normalSpawn))
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false

        val mult = statMultiplier(run.round)
        npc.baseAttackLvl = (type.attack * mult).toInt()
        npc.baseStrengthLvl = (type.strength * mult).toInt()
        npc.baseDefenceLvl = (type.defence * mult).toInt()
        npc.baseHitpointsLvl = (type.hitpoints * mult).toInt()
        npc.baseRangedLvl = (type.ranged * mult).toInt()
        npc.baseMagicLvl = (type.magic * mult).toInt()
        npc.attackLvl = npc.baseAttackLvl
        npc.strengthLvl = npc.baseStrengthLvl
        npc.defenceLvl = npc.baseDefenceLvl
        npc.hitpoints = npc.baseHitpointsLvl
        npc.rangedLvl = npc.baseRangedLvl
        npc.magicLvl = npc.baseMagicLvl

        run.npcs += npc
        // Dream npcs are hostile on sight - the moment they exist they hunt the player.
        engage(npc, run.player)
    }

    private fun spawnExitPortal(run: SoloBossRun) {
        val type = npcTypes[ArenaBosses.exitPortal]
        val npc = Npc(type, run.resolve(ArenaBosses.portalSpawn))
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        run.portal = npc
    }

    private fun endRun(run: SoloBossRun) {
        if (runs.remove(run.player) !== run) {
            return
        }
        for (npc in run.npcs) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
        run.npcs.clear()
        run.portal?.let { npcRepo.del(it, Int.MAX_VALUE) }
        run.portal = null
    }

    private fun endRun(player: Player) {
        runs[player]?.let(::endRun)
    }

    private fun mesAward(player: Player, amount: Int) {
        launcher.launch(player) { mes("<col=66ccff>+${amount} PvM points</col>") }
    }

    private fun rollArenaBonusDrop(run: SoloBossRun, npc: Npc) {
        val chance =
            when (run.mode) {
                ArenaMode.EASY -> 100
                ArenaMode.HARD -> 60
                ArenaMode.BOSS -> 35
            }
        if (Random.nextInt(chance) != 0) return
        val type =
            if (Random.nextBoolean()) {
                objTypes[UnforgeKeyObjs.crystalKey]
            } else {
                objTypes[UnforgeBoxObjs.mysteryBox]
            }
        objRepo.add(type, npc.coords, constants.lootdrop_duration, run.player, 1)
        launcher.launch(run.player) { mes("<col=ffb84d>Bonus drop: ${type.name}!</col>") }
    }

    private fun statMultiplier(round: Int): Double =
        (1.0 + (round - 1) * STAT_BONUS_PER_ROUND).coerceAtMost(MAX_STAT_MULTIPLIER)

    private fun statBonusPercent(round: Int): Int = ((statMultiplier(round) - 1.0) * 100).toInt()

    private enum class ArenaMode(val displayName: String) {
        EASY("Easy"),
        HARD("Hard"),
        BOSS("Boss"),
    }

    private class SoloBossRun(val player: Player, val region: Region, val mode: ArenaMode) {
        var round = 1
        val npcs = mutableListOf<Npc>()
        var portal: Npc? = null

        /** Maps a normal-world coord inside the copied zone block to its region coord. */
        fun resolve(normalCoords: CoordGrid): CoordGrid = region.normal[normalCoords]

        operator fun contains(coords: CoordGrid): Boolean =
            coords.x in region.southWest.x..region.northEast.x &&
                coords.z in region.southWest.z..region.northEast.z
    }

    public object ArenaBosses : NpcReferences() {
        val king_dragon = find("king_dragon")
        val slayer_kraken_boss = find("slayer_kraken_boss")
        val smoke_devil_boss = find("smoke_devil_boss")
        val dagcave_melee_boss = find("dagcave_melee_boss")
        val dagcave_magic_boss = find("dagcave_magic_boss")
        val dagcave_ranged_boss = find("dagcave_ranged_boss")
        val chaoselemental = find("chaoselemental")
        val kalphite_queen = find("kalphite_queen")
        val godwars_armadyl_avatar = find("godwars_armadyl_avatar")
        val godwars_saradomin_avatar = find("godwars_saradomin_avatar")
        val godwars_bandos_avatar = find("godwars_bandos_avatar")
        val godwars_zamorak_avatar = find("godwars_zamorak_avatar")
        val tzhaar_fightcave_swarm_boss = find("tzhaar_fightcave_swarm_boss")
        val corp_beast = find("corp_beast")
        val corrupted_nechryarch = UnforgeCustomNpcRefs.corrupted_nechryarch
        val brutal_lava_dragon = UnforgeCustomNpcRefs.brutal_lava_dragon
        val exitPortal = find("blankrunestone_exit_portal_1")

        val all =
            listOf(
                king_dragon,
                slayer_kraken_boss,
                smoke_devil_boss,
                dagcave_melee_boss,
                dagcave_magic_boss,
                dagcave_ranged_boss,
                chaoselemental,
                kalphite_queen,
                godwars_armadyl_avatar,
                godwars_saradomin_avatar,
                godwars_bandos_avatar,
                godwars_zamorak_avatar,
                tzhaar_fightcave_swarm_boss,
                corp_beast,
                corrupted_nechryarch,
                brutal_lava_dragon,
            )

        val easy =
            listOf(
                UnforgeCombatNpcs.kourend_rockcrab,
                UnforgeCombatNpcs.zeah_sandcrab,
                UnforgeCombatNpcs.kourend_hillgiant,
                UnforgeCombatNpcs.hobgoblin_unarmed,
                UnforgeCombatNpcs.goblin,
                UnforgeCombatNpcs.skeleton_unarmed,
                UnforgeCombatNpcs.scorpion,
                UnforgeCombatNpcs.mossgiant,
                UnforgeCombatNpcs.giant,
            )
        val hard =
            listOf(
                UnforgeCombatNpcs.kourend_hellhound,
                UnforgeCombatNpcs.wild_cave_ork,
                UnforgeCombatNpcs.smoke_devil,
                UnforgeCombatNpcs.black_demon,
                godwars_bandos_avatar,
                UnforgeCombatNpcs.adamant_dragon,
                UnforgeCombatNpcs.abyssal_walker,
                corrupted_nechryarch,
                brutal_lava_dragon,
            )

        /** OSRS NMZ entrance: Dominic Onion's position north-west of Yanille bank. */
        val gatekeeperSpawn = CoordGrid(2608, 3116)

        /** OSRS NMZ entrance path tile used after leaving the dream. */
        val gatekeeperCoords = CoordGrid(2608, 3117)

        // Dream source coords inside the King Black Dragon lair, which is the arena the OSRS
        // Nightmare Zone dreams instance. These are deliberately separate from the real-world
        // Yanille entrance above and are mapped into each private dream region through
        // Region.normal.
        val playerSpawn = CoordGrid(2273, 4687)
        val bossSpawn = CoordGrid(2273, 4695)
        val portalSpawn = CoordGrid(2269, 4687)
    }

    private companion object {
        private const val STAT_BONUS_PER_ROUND = 0.15
        private const val MAX_STAT_MULTIPLIER = 3.0
        private const val MAX_BONUS_ROLLS = 4
        private const val MIN_ROUND_NPCS = 4
        private const val MAX_ROUND_NPCS = 10

        /**
         * Spread around [ArenaBosses.bossSpawn] for multi-npc rounds; every tile stays well inside
         * the copied lair block and on the open cave floor.
         */
        private val SPAWN_OFFSETS =
            listOf(
                0 to 0,
                2 to 0,
                -2 to 0,
                0 to 2,
                0 to -2,
                2 to 2,
                -2 to 2,
                2 to -2,
                -2 to -2,
                4 to 0,
            )

        /**
         * The OSRS Nightmare Zone dreams take place in an instance of the King Black Dragon lair
         * (normal region 35,73). Copies the lair's 5x6 zone block (normal coords 2256-2295 x
         * 4672-4719) into the private dream region.
         */
        private val arenaTemplate =
            RegionTemplate.create {
                copy(282, 584, 0) {
                    zoneWidth = 5
                    zoneLength = 6
                }
            }
    }
}
