package org.rsmod.content.areas.unforge.raids

import kotlin.random.Random
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.unforge.items.UnforgeBoxObjs
import org.rsmod.content.areas.unforge.items.UnforgeKeyObjs
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.varp.VarpType
import org.rsmod.map.CoordGrid

/** Npc types used by the raid engine. All names exist in the packed cache (`npc.sym`). */
object RaidNpcs : NpcReferences() {
    // Theatre of Blood.
    val tobMaiden = find("tob_maiden_70")
    val tobNylocasMelee = find("tob_nylocas_fighting_melee")
    val tobNylocasRanged = find("tob_nylocas_fighting_ranged")
    val tobNylocasMagic = find("tob_nylocas_fighting_magic")
    val tobNylocasBoss = find("nylocas_boss_melee")
    val tobSotetseg = find("tob_sotetseg_combat")
    val tobXarpus = find("tob_xarpus_combat")
    val tobBloat = find("tob_bloat")
    val verzikP1 = find("verzik_phase1")
    val verzikP2 = find("verzik_phase2")
    val verzikP3 = find("verzik_phase3")
    val verzikNylocasMelee = find("verzik_nylocas_melee")
    val verzikNylocasRanged = find("verzik_nylocas_ranged")

    // Chambers of Xeric.
    val tekton = find("raids_tekton_fighting_standard")
    val tektonEnraged = find("raids_tekton_fighting_enraged")
    val vespula = find("raids_vespula_flying")
    val vespulaPortal = find("raids_vespula_portal")
    val vespulaVespine = find("raids_vespula_vespine_walking")
    val vanguardMelee = find("raids_vanguard_melee")
    val vanguardRanged = find("raids_vanguard_ranged")
    val vanguardMagic = find("raids_vanguard_magic")
    val muttadileJunior = find("raids_dogodile_junior")
    val muttadile = find("raids_dogodile")
    val mysticA = find("raids_skeletonmystic_a")
    val mysticB = find("raids_skeletonmystic_b")
    val mysticC = find("raids_skeletonmystic_c")
    val iceDemonDormant = find("raids_icedemon_noncombat")
    val iceDemon = find("raids_icedemon_combat")
    val iceFiend = find("raids_icefiend")
    val tightropeMage = find("raids_tightrope_mage")
    val tightropeRanger = find("raids_tightrope_ranger")
    val scavenger = find("raids_scavenger_beast_a")
    val olmHead = find("olm_head")
    val olmHandLeft = find("olm_hand_left")
    val olmHandRight = find("olm_hand_right")

    /** Reused exit portal (same type the Nightmare Zone arena uses). */
    // Separate npc type from the solo-boss exit portal so op handlers do not clash.
    val exitPortal = find("blankrunestone_exit_portal_2")
}

/** Reward chest locs. */
object RaidLocs : LocReferences() {
    val tobChestClosed = find("tob_treasureroom_chest_mine_standard")
    val tobChestOpen = find("tob_treasureroom_chest_open")
    val coxChestClosed = find("shuttwistedchest")
    val coxChestOpen = find("chestopen")
}

/** Item types paid out by the raid reward chest. */
object RaidRewardObjs : ObjReferences() {
    // Common supplies / currency.
    val coins = find("coins")
    val deathRune = find("deathrune")
    val bloodRune = find("bloodrune")
    val soulRune = find("soulrune")
    val shark = find("shark")
    val prayerRestore = find("4doseprayerrestore")
    val rangingPotion = find("4doserangerspotion")
    val superCombat = find("set_supercombat_potion")
    val sanfewSalve = find("sanfew_salve_4_dose")
    val dragonBones = find("dragon_bones")
    val runePlatebody = find("rune_platebody")
    val raidsElderVial = find("raids_vial_elder_4")
    val raidsTwistedVial = find("raids_vial_twisted_4")
    val raidsKodaiVial = find("raids_vial_kodai_4")

    // Theatre of Blood uniques.
    val scythe = find("scythe")
    val sanguinestiStaff = find("sanguinesti_staff")
    val ghraziRapier = find("ghrazi_rapier")
    val justiciarFaceguard = find("justiciar_faceguard")
    val justiciarChestguard = find("justiciar_chestguard")
    val justiciarLegGuards = find("justiciar_leg_guards")
    val avernicTreads = find("avernic_treads")

    // Chambers of Xeric uniques.
    val twistedBow = find("twisted_bow")
    val twistedBuckler = find("twisted_buckler")
    val elderMaul = find("elder_maul")
    val kodaiWand = find("kodai_wand")
    val kodaiInsignia = find("kodai_insignia")
    val dinhsBulwark = find("dinhs_bulwark")
    val ancestralHat = find("ancestral_hat")
    val ancestralRobeTop = find("ancestral_robe_top")
    val ancestralRobeBottom = find("ancestral_robe_bottom")
    val arcaneSigil = find("arcane_sigil")
    val xericTalisman = find("xeric_talisman")
}

/** Permanent, server-only raid varps (registered in `varp.sym`). */
object RaidVarps : VarpReferences() {
    val points: VarpType = find("raid_points")
    val tobCompletions: VarpType = find("raid_tob_completions")
    val coxCompletions: VarpType = find("raid_cox_completions")
}

internal object RaidVarpBuilder : VarpBuilder() {
    init {
        build("raid_points") {
            permanent = true
            transmitNever = true
        }
        build("raid_tob_completions") {
            permanent = true
            transmitNever = true
        }
        build("raid_cox_completions") {
            permanent = true
            transmitNever = true
        }
    }
}

/** Theatre of Blood-style raid: six linear rooms ending at Verzik. */
object TobRaid {
    val rooms: List<RaidRoomSpec> =
        listOf(
            RaidRoomSpec(
                key = "maiden",
                displayName = "The Maiden of Sugadinti",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.tobMaiden, localX = 8, localZ = 10)),
                mechanic = RaidMechanicId.MAIDEN,
            ),
            RaidRoomSpec(
                key = "bloat",
                displayName = "The Pestilent Bloat",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.tobBloat, 8, 10)),
                mechanic = RaidMechanicId.BLOAT,
            ),
            RaidRoomSpec(
                key = "nylocas",
                displayName = "The Nylocas Warren",
                kind = RaidRoomKind.WAVE,
                spawns =
                    listOf(
                        RaidSpawnSpec(RaidNpcs.tobNylocasMelee, 4, 6, statScale = 0.45),
                        RaidSpawnSpec(RaidNpcs.tobNylocasRanged, 11, 6, statScale = 0.45),
                        RaidSpawnSpec(RaidNpcs.tobNylocasMagic, 8, 11, statScale = 0.45),
                    ),
                mechanic = RaidMechanicId.SWARM,
            ),
            RaidRoomSpec(
                key = "sotetseg",
                displayName = "Sotetseg the Corrupted",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.tobSotetseg, 8, 10)),
                mechanic = RaidMechanicId.SOTETSEG,
            ),
            RaidRoomSpec(
                key = "xarpus",
                displayName = "Xarpus, the Exhumed",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.tobXarpus, 8, 10)),
                mechanic = RaidMechanicId.XARPUS,
            ),
            RaidRoomSpec(
                key = "verzik",
                displayName = "Verzik Vitur's Throne",
                kind = RaidRoomKind.BOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.verzikP1, 8, 10)),
                mechanic = RaidMechanicId.VERZIK,
            ),
        )
}

/**
 * Chambers of Xeric-style raid: a shuffled selection of rooms followed by the Great Olm. Always
 * guarantees at least one combat room, one puzzle room and one miniboss.
 */
object CoxRaid {
    private val minibosses =
        listOf(
            RaidRoomSpec(
                key = "tekton",
                displayName = "Tekton's Forge",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.tekton, 8, 10)),
                mechanic = RaidMechanicId.TEKTON,
            ),
            RaidRoomSpec(
                key = "muttadile",
                displayName = "The Muttadile's Den",
                kind = RaidRoomKind.MINIBOSS,
                spawns = listOf(RaidSpawnSpec(RaidNpcs.muttadileJunior, 8, 10)),
                mechanic = RaidMechanicId.MUTTADILE,
            ),
        )

    private val combatRooms =
        listOf(
            RaidRoomSpec(
                key = "vespula",
                displayName = "Vespula's Hive",
                kind = RaidRoomKind.COMBAT,
                spawns =
                    listOf(
                        RaidSpawnSpec(RaidNpcs.vespula, 8, 10),
                        RaidSpawnSpec(RaidNpcs.vespulaPortal, 12, 12, statScale = 0.6),
                    ),
                mechanic = RaidMechanicId.VESPULA,
            ),
            RaidRoomSpec(
                key = "vanguards",
                displayName = "The Vanguard's Stand",
                kind = RaidRoomKind.COMBAT,
                spawns =
                    listOf(
                        RaidSpawnSpec(RaidNpcs.vanguardMelee, 4, 10),
                        RaidSpawnSpec(RaidNpcs.vanguardRanged, 11, 10),
                        RaidSpawnSpec(RaidNpcs.vanguardMagic, 8, 13),
                    ),
                mechanic = RaidMechanicId.VANGUARDS,
            ),
            RaidRoomSpec(
                key = "mystics",
                displayName = "The Mystic's Sanctum",
                kind = RaidRoomKind.COMBAT,
                spawns =
                    listOf(
                        RaidSpawnSpec(RaidNpcs.mysticA, 4, 10),
                        RaidSpawnSpec(RaidNpcs.mysticB, 11, 10),
                        RaidSpawnSpec(RaidNpcs.mysticC, 8, 13),
                    ),
                mechanic = RaidMechanicId.MYSTICS,
            ),
            RaidRoomSpec(
                key = "icedemon",
                displayName = "The Ice Demon's Lair",
                kind = RaidRoomKind.COMBAT,
                spawns =
                    listOf(
                        RaidSpawnSpec(RaidNpcs.iceDemonDormant, 8, 10),
                        RaidSpawnSpec(RaidNpcs.iceFiend, 4, 6, statScale = 0.5),
                        RaidSpawnSpec(RaidNpcs.iceFiend, 11, 6, statScale = 0.5),
                    ),
                mechanic = RaidMechanicId.ICEDEMON,
            ),
        )

    private val puzzleRoom =
        RaidRoomSpec(
            key = "tightrope",
            displayName = "The Tightrope Crossing",
            kind = RaidRoomKind.PUZZLE,
            spawns =
                listOf(
                    RaidSpawnSpec(RaidNpcs.tightropeMage, 5, 10, statScale = 0.7),
                    RaidSpawnSpec(RaidNpcs.tightropeRanger, 10, 10, statScale = 0.7),
                    RaidSpawnSpec(RaidNpcs.scavenger, 8, 6, statScale = 0.4),
                ),
            mechanic = RaidMechanicId.TIGHTROPE,
        )

    private val olmRoom =
        RaidRoomSpec(
            key = "olm",
            displayName = "The Great Olm",
            kind = RaidRoomKind.BOSS,
            spawns =
                listOf(
                    RaidSpawnSpec(RaidNpcs.olmHead, 8, 10),
                    RaidSpawnSpec(RaidNpcs.olmHandLeft, 5, 10, statScale = 0.8),
                    RaidSpawnSpec(RaidNpcs.olmHandRight, 11, 10, statScale = 0.8),
                ),
            mechanic = RaidMechanicId.OLM,
        )

    /**
     * Builds a randomized room list: 4-6 shuffled mid rooms (always including the tightrope puzzle
     * and at least one miniboss) followed by the Olm finale - 5-7 rooms in total.
     */
    fun rooms(random: Random): List<RaidRoomSpec> {
        val midCount = 4 + random.nextInt(3) // 4..6 mid rooms
        val picked = mutableListOf<RaidRoomSpec>()
        picked += puzzleRoom
        picked += minibosses.shuffled(random).first()
        val remaining =
            (minibosses + combatRooms).filter { it !in picked }.shuffled(random).toMutableList()
        while (picked.size < midCount && remaining.isNotEmpty()) {
            picked += remaining.removeAt(0)
        }
        return picked.shuffled(random) + olmRoom
    }
}

/** One entry on the common (non-unique) reward table. */
class RaidCommonReward(val obj: ObjType, val min: Int, val max: Int, val weight: Int)

/** One entry on a raid's unique drop table. */
class RaidUniqueReward(val obj: ObjType, val weight: Int)

/** Reward chest roll logic. Pure functions so the whole pipeline is unit-testable. */
object RaidRewards {
    /**
     * Unique roll denominator: a unique drops on a `1 in N` roll where N shrinks as score grows. At
     * score 0 -> 1/50; around 10k score -> ~1/25; floored at 1/15 for very high scores.
     */
    fun uniqueDenominator(score: Int, difficulty: RaidDifficulty): Int {
        val base = (50 - score / 400).coerceIn(15, 50)
        return if (difficulty == RaidDifficulty.EXPERT) (base * 2 / 3).coerceAtLeast(10) else base
    }

    /** How many common-table rolls a chest grants (difficulty adds bonus rolls). */
    fun commonRolls(score: Int, difficulty: RaidDifficulty): Int =
        2 + score / 6_000 + difficulty.bonusChestRolls

    val commons: List<RaidCommonReward> =
        listOf(
            RaidCommonReward(RaidRewardObjs.coins, 8_000, 40_000, 20),
            RaidCommonReward(RaidRewardObjs.deathRune, 80, 250, 15),
            RaidCommonReward(RaidRewardObjs.bloodRune, 60, 200, 15),
            RaidCommonReward(RaidRewardObjs.soulRune, 50, 150, 10),
            RaidCommonReward(RaidRewardObjs.shark, 5, 15, 15),
            RaidCommonReward(RaidRewardObjs.prayerRestore, 1, 3, 10),
            RaidCommonReward(RaidRewardObjs.rangingPotion, 1, 2, 8),
            RaidCommonReward(RaidRewardObjs.superCombat, 1, 2, 8),
            RaidCommonReward(RaidRewardObjs.sanfewSalve, 1, 2, 6),
            RaidCommonReward(RaidRewardObjs.dragonBones, 5, 15, 10),
            RaidCommonReward(RaidRewardObjs.runePlatebody, 1, 2, 5),
            RaidCommonReward(RaidRewardObjs.raidsElderVial, 1, 2, 5),
            RaidCommonReward(RaidRewardObjs.raidsTwistedVial, 1, 2, 5),
            RaidCommonReward(RaidRewardObjs.raidsKodaiVial, 1, 2, 5),
            RaidCommonReward(UnforgeBoxObjs.mysteryBox, 1, 1, 8),
            RaidCommonReward(UnforgeBoxObjs.petBox, 1, 1, 2),
            RaidCommonReward(UnforgeBoxObjs.superBox, 1, 1, 3),
            RaidCommonReward(UnforgeBoxObjs.slayerCasket, 1, 2, 6),
            RaidCommonReward(UnforgeKeyObjs.crystalKey, 1, 2, 8),
            RaidCommonReward(UnforgeKeyObjs.uncutDragonstone, 1, 3, 8),
        )

    val tobUniques: List<RaidUniqueReward> =
        listOf(
            RaidUniqueReward(RaidRewardObjs.scythe, 4),
            RaidUniqueReward(RaidRewardObjs.sanguinestiStaff, 10),
            RaidUniqueReward(RaidRewardObjs.ghraziRapier, 10),
            RaidUniqueReward(RaidRewardObjs.justiciarFaceguard, 12),
            RaidUniqueReward(RaidRewardObjs.justiciarChestguard, 12),
            RaidUniqueReward(RaidRewardObjs.justiciarLegGuards, 12),
            RaidUniqueReward(RaidRewardObjs.avernicTreads, 12),
        )

    val coxUniques: List<RaidUniqueReward> =
        listOf(
            RaidUniqueReward(RaidRewardObjs.twistedBow, 4),
            RaidUniqueReward(RaidRewardObjs.twistedBuckler, 10),
            RaidUniqueReward(RaidRewardObjs.elderMaul, 10),
            RaidUniqueReward(RaidRewardObjs.kodaiWand, 10),
            RaidUniqueReward(RaidRewardObjs.kodaiInsignia, 12),
            RaidUniqueReward(RaidRewardObjs.dinhsBulwark, 10),
            RaidUniqueReward(RaidRewardObjs.ancestralHat, 12),
            RaidUniqueReward(RaidRewardObjs.ancestralRobeTop, 12),
            RaidUniqueReward(RaidRewardObjs.ancestralRobeBottom, 12),
            RaidUniqueReward(RaidRewardObjs.arcaneSigil, 12),
            RaidUniqueReward(RaidRewardObjs.xericTalisman, 20),
        )

    fun uniquesFor(kind: RaidKind): List<RaidUniqueReward> =
        when (kind) {
            RaidKind.THEATRE -> tobUniques
            RaidKind.CHAMBERS -> coxUniques
        }

    /** Weighted pick from a reward table. */
    fun <T> pickWeighted(random: Random, table: List<T>, weight: (T) -> Int): T {
        val total = table.sumOf(weight)
        var roll = random.nextInt(total)
        for (entry in table) {
            roll -= weight(entry)
            if (roll < 0) return entry
        }
        return table.last()
    }

    /** Rolls the full chest contents for one member. */
    fun rollChest(
        random: Random,
        kind: RaidKind,
        score: Int,
        difficulty: RaidDifficulty,
    ): List<Pair<ObjType, Int>> {
        val rewards = mutableListOf<Pair<ObjType, Int>>()
        if (random.nextInt(uniqueDenominator(score, difficulty)) == 0) {
            val unique = pickWeighted(random, uniquesFor(kind)) { it.weight }
            rewards += unique.obj to 1
        }
        repeat(commonRolls(score, difficulty)) {
            val entry = pickWeighted(random, commons) { it.weight }
            rewards += entry.obj to (entry.min + random.nextInt(entry.max - entry.min + 1))
        }
        return rewards
    }
}

/** Region template and pad layout shared by both raids. */
object RaidInstance {
    /** Normal-world tile players are returned to when a raid ends. */
    val exit: CoordGrid = CoordGrid(3096, 3526)

    /** A large template arranges the lobby and up to seven room chunks in a 3x3 grid. */
    const val MAX_PADS = 8

    private fun padStrideZones(kind: RaidKind): Int = if (kind == RaidKind.CHAMBERS) 12 else 8

    /**
     * Builds a large template with the lobby and rooms placed in a 3x3 zone grid. Room source
     * dimensions come from [RaidMapLayouts], rather than a shared 16x16 source arena.
     */
    fun template(kind: RaidKind, rooms: List<RaidRoomSpec>): RegionTemplate =
        RegionTemplate.createLarge {
            val layouts = rooms.map { RaidMapLayouts.forRoom(kind, it.key) }
            require(rooms.size + 1 in 1..MAX_PADS) { "padCount out of range: ${rooms.size + 1}" }
            val lobbyLayout = RaidMapLayouts.forLobby(kind)
            val stride = padStrideZones(kind)
            if (lobbyLayout.copyAllLevels) {
                copyAllLevels(lobbyLayout.sourceZoneX, lobbyLayout.sourceZoneZ) {
                    regionZoneX = 0
                    regionZoneZ = 0
                    zoneWidth = lobbyLayout.zoneWidth
                    zoneLength = lobbyLayout.zoneLength
                    rotation = lobbyLayout.rotation
                }
            } else {
                copy(lobbyLayout.sourceZoneX, lobbyLayout.sourceZoneZ, lobbyLayout.sourceLevel) {
                    regionZoneX = 0
                    regionZoneZ = 0
                    zoneWidth = lobbyLayout.zoneWidth
                    zoneLength = lobbyLayout.zoneLength
                    rotation = lobbyLayout.rotation
                }
            }
            rooms.forEachIndexed { index, room ->
                val layout =
                    layouts[index]
                        ?: error("No verified source layout for ${kind.name}:${room.key}")
                val pad = index + 1
                if (layout.copyAllLevels) {
                    copyAllLevels(layout.sourceZoneX, layout.sourceZoneZ) {
                        regionZoneX = (pad % 3) * stride
                        regionZoneZ = (pad / 3) * stride
                        zoneWidth = layout.zoneWidth
                        zoneLength = layout.zoneLength
                        rotation = layout.rotation
                    }
                } else {
                    copy(layout.sourceZoneX, layout.sourceZoneZ, layout.sourceLevel) {
                        regionZoneX = (pad % 3) * stride
                        regionZoneZ = (pad / 3) * stride
                        zoneWidth = layout.zoneWidth
                        zoneLength = layout.zoneLength
                        rotation = layout.rotation
                    }
                }
            }
        }

    /** South-west tile of pad [index] inside a region. */
    fun padOrigin(southWest: CoordGrid, index: Int, kind: RaidKind = RaidKind.THEATRE): CoordGrid {
        val stride = padStrideZones(kind) * 8
        return southWest.translate((index % 3) * stride, (index / 3) * stride)
    }
}
