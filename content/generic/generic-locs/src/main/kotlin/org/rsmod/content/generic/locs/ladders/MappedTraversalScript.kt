package org.rsmod.content.generic.locs.ladders

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Explicit cache-backed traversal routes whose two endpoints have different
 * loc IDs or coordinates and therefore cannot use the generic level delta.
 *
 * Every route is keyed by the source loc symbol and an exact vanilla map-loc
 * coordinate. An instance/template coordinate that is not in this table is
 * intentionally ignored until instance remapping is verified.
 */
class MappedTraversalScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(mapped_locs.slayer_stairs_up) { travel(it.loc, slayerStairs) }
        onOpLoc1(mapped_locs.slayer_stairs_down) { travel(it.loc, slayerStairs.reverse()) }
        onOpLoc1(mapped_locs.slayer_stairs_up_2) { travel(it.loc, slayerStairs2) }
        onOpLoc1(mapped_locs.slayer_stairs_down_2) { travel(it.loc, slayerStairs2.reverse()) }

        onOpLoc1(mapped_locs.troll_entrance) { travel(it.loc, trollStronghold) }
        onOpLoc1(mapped_locs.troll_exit) { travel(it.loc, trollStronghold.reverse()) }
        onOpLoc1(mapped_locs.troll_stairs_up) { travel(it.loc, trollStairs) }
        onOpLoc1(mapped_locs.troll_stairs_down) { travel(it.loc, trollStairs.reverse()) }

        onOpLoc1(mapped_locs.hunter_guild_down) { travel(it.loc, hunterGuild) }
        onOpLoc1(mapped_locs.hunter_guild_up) { travel(it.loc, hunterGuild.reverse()) }
        onOpLoc1(mapped_locs.farming_guild_down) { travel(it.loc, farmingGuild) }
        onOpLoc1(mapped_locs.farming_guild_up) { travel(it.loc, farmingGuild.reverse()) }
        onOpLoc1(mapped_locs.woodcutting_guild_up) { travel(it.loc, woodcuttingGuild) }
        onOpLoc1(mapped_locs.woodcutting_guild_down) {
            travel(it.loc, woodcuttingGuild.reverse())
        }
        onOpLoc1(mapped_locs.warrior_guild_down) { travel(it.loc, warriorGuild) }
        onOpLoc1(mapped_locs.warrior_guild_up) { travel(it.loc, warriorGuild.reverse()) }
        // These cache rope objects are pre-installed. No rope inventory item is required.
        onOpLoc1(mapped_locs.godwars_rope_bottom) { travel(it.loc, godwarsRope) }
        onOpLoc1(mapped_locs.godwars_rope_top) { travel(it.loc, godwarsRope.reverse()) }

        onOpLoc1(mapped_locs.motherlode_down) { travel(it.loc, darkmMine) }
        onOpLoc1(mapped_locs.motherlode_up) { travel(it.loc, darkmMine.reverse()) }
        onOpLoc1(mapped_locs.tob_crypt_down) { travel(it.loc, tobCrypt) }
        onOpLoc1(mapped_locs.tob_crypt_up) { travel(it.loc, tobCrypt.reverse()) }
        onOpLoc1(mapped_locs.dark_mine_down) { travel(it.loc, hauntedMine) }
        onOpLoc1(mapped_locs.dark_mine_up) { travel(it.loc, hauntedMine.reverse()) }
        onOpLoc1(mapped_locs.tapoyauik_down) { travel(it.loc, tapoyauik) }
        onOpLoc1(mapped_locs.tapoyauik_up) { travel(it.loc, tapoyauik.reverse()) }
        onOpLoc1(mapped_locs.colosseum_outside) { travel(it.loc, colosseum) }
        onOpLoc1(mapped_locs.colosseum_inside) { travel(it.loc, colosseum.reverse()) }
        onOpLoc1(mapped_locs.mm_dungeon_down) { travel(it.loc, mmDungeon) }
        onOpLoc1(mapped_locs.mm_dungeon_up) { travel(it.loc, mmDungeon.reverse()) }
        onOpLoc1(mapped_locs.tzhaar_fightcave_entrance) { travel(it.loc, tzhaarFightCave) }
        onOpLoc1(mapped_locs.tzhaar_fightcave_exit) { travel(it.loc, tzhaarFightCave.reverse()) }
        onOpLoc1(mapped_locs.tzhaar_dungeon_entrance) { travel(it.loc, tzhaarDungeon) }
        onOpLoc1(mapped_locs.tzhaar_dungeon_exit) { travel(it.loc, tzhaarDungeon.reverse()) }
        onOpLoc1(mapped_locs.mudskipper_entrance_left) { travel(it.loc, mudskipperLeft) }
        onOpLoc1(mapped_locs.mudskipper_exit_right) { travel(it.loc, mudskipperLeft.reverse()) }
        onOpLoc1(mapped_locs.mudskipper_entrance_right) { travel(it.loc, mudskipperRight) }
        onOpLoc1(mapped_locs.mudskipper_exit_left) { travel(it.loc, mudskipperRight.reverse()) }
        onOpLoc1(mapped_locs.barbassault_recruitment_entrance) {
            travel(it.loc, barbarianAssaultRecruitment)
        }
        onOpLoc1(mapped_locs.barbassault_recruitment_exit) {
            travel(it.loc, barbarianAssaultRecruitment.reverse())
        }
        onOpLoc1(mapped_locs.lunar_mine_up) { travel(it.loc, lunarMine) }
        onOpLoc1(mapped_locs.lunar_mine_down) { travel(it.loc, lunarMine.reverse()) }
    }

    private suspend fun ProtectedAccess.travel(
        loc: BoundLocInfo,
        routes: Map<CoordGrid, CoordGrid>,
    ) {
        val destination = routes[loc.coords] ?: return
        arriveDelay()
        telejump(destination)
    }

    private fun Map<CoordGrid, CoordGrid>.reverse(): Map<CoordGrid, CoordGrid> =
        entries.associate { (from, to) -> to to from }

    private companion object {
        val slayerStairs =
            mapOf(CoordGrid(3434, 3537, 0) to CoordGrid(3434, 3537, 1))
        val slayerStairs2 =
            mapOf(CoordGrid(3413, 3540, 1) to CoordGrid(3415, 3540, 2))
        val trollStronghold =
            mapOf(CoordGrid(2827, 3647, 0) to CoordGrid(2823, 10048, 0))
        val trollStairs =
            mapOf(
                CoordGrid(2842, 10051, 1) to CoordGrid(2843, 10051, 2),
                CoordGrid(2842, 10108, 1) to CoordGrid(2843, 10108, 2),
                CoordGrid(2852, 10061, 0) to CoordGrid(2852, 10061, 1),
                CoordGrid(2852, 10106, 0) to CoordGrid(2852, 10107, 1),
            )
        val hunterGuild =
            mapOf(CoordGrid(1556, 3048, 0) to CoordGrid(1557, 9448, 1))
        val farmingGuild =
            mapOf(
                CoordGrid(1224, 3755, 0) to CoordGrid(1224, 3755, 1),
                CoordGrid(1233, 3755, 0) to CoordGrid(1233, 3755, 1),
            )
        val woodcuttingGuild =
            mapOf(
                CoordGrid(1566, 3483, 0) to CoordGrid(1566, 3483, 1),
                CoordGrid(1566, 3493, 0) to CoordGrid(1566, 3493, 1),
                CoordGrid(1575, 3483, 0) to CoordGrid(1575, 3483, 1),
                CoordGrid(1575, 3493, 0) to CoordGrid(1575, 3493, 1),
            )
        val warriorGuild =
            mapOf(CoordGrid(2833, 3542, 0) to CoordGrid(2906, 9968, 0))
        val godwarsRope =
            mapOf(CoordGrid(2881, 5311, 2) to CoordGrid(2881, 5311, 3))
        val darkmMine =
            mapOf(CoordGrid(3631, 3339, 0) to CoordGrid(3696, 9765, 2))
        val tobCrypt =
            mapOf(CoordGrid(3682, 3231, 0) to CoordGrid(3680, 9651, 0))
        val hauntedMine =
            mapOf(CoordGrid(3452, 3243, 0) to CoordGrid(2790, 4589, 0))
        val tapoyauik =
            mapOf(CoordGrid(1693, 3230, 0) to CoordGrid(1693, 9630, 2))
        val colosseum =
            mapOf(CoordGrid(1796, 3106, 0) to CoordGrid(1796, 9506, 0))
        val mmDungeon =
            mapOf(CoordGrid(2763, 2703, 0) to CoordGrid(2763, 9103, 0))
        val tzhaarFightCave =
            mapOf(CoordGrid(2437, 5166, 0) to CoordGrid(2412, 5118, 0))
        val tzhaarDungeon =
            mapOf(CoordGrid(2863, 9571, 0) to CoordGrid(2479, 5176, 0))
        val mudskipperLeft =
            mapOf(CoordGrid(2949, 9516, 1) to CoordGrid(2949, 9519, 1))
        val mudskipperRight =
            mapOf(CoordGrid(2950, 9516, 1) to CoordGrid(2950, 9519, 1))
        val barbarianAssaultRecruitment =
            mapOf(CoordGrid(2534, 3572, 0) to CoordGrid(2593, 5260, 0))
        val lunarMine =
            mapOf(CoordGrid(2330, 10353, 2) to CoordGrid(2142, 3944, 0))
    }
}

private typealias mapped_locs = MappedTraversalLocs

public object MappedTraversalLocs : LocReferences() {
    val slayer_stairs_up = find("slayer_stairs_lv1")
    val slayer_stairs_down = find("slayer_stairs_lv1_top")
    val slayer_stairs_up_2 = find("slayer_stairs_lv2")
    val slayer_stairs_down_2 = find("slayer_stairs_lv2_top")
    val troll_entrance = find("troll_stronghold_entrance")
    val troll_exit = find("troll_stronghold_exit")
    val troll_stairs_up = find("troll_stronghold_stairs")
    val troll_stairs_down = find("troll_stronghold_stairstop")
    val hunter_guild_down = find("hunterguild_stairs_down01_combined")
    val hunter_guild_up = find("hunterguild_stairs_up01")
    val farming_guild_down = find("kebos_farming_guild_redwood_ladder_bottom")
    val farming_guild_up = find("kebos_farming_guild_redwood_ladder_top")
    val woodcutting_guild_up = find("wcguild_redwood_ropebottom")
    val woodcutting_guild_down = find("wcguild_redwood_ropetop")
    val warrior_guild_down = find("warguild_ladder_down")
    val warrior_guild_up = find("warguild_ladder_up")
    val godwars_rope_bottom = find("godwars_entrance_rope_bottom")
    val godwars_rope_top = find("godwars_entrance_rope")
    val motherlode_down = find("darkm_mine_entrance")
    val motherlode_up = find("darkm_mine_exit")
    val tob_crypt_down = find("tobquest_crypt_entrance")
    val tob_crypt_up = find("tobquest_crypt_exit")
    val dark_mine_down = find("hauntedmine_main_entrance")
    val dark_mine_up = find("hauntedmine_main_entrance_inside")
    val tapoyauik_down = find("tapoyauik_temple_entrance")
    val tapoyauik_up = find("tapoyauik_temple_exit")
    val colosseum_outside = find("colosseum_entrance_outside")
    val colosseum_inside = find("colosseum_exit_lobby")
    val mm_dungeon_down = find("mm_bamboo_ladder_dungeon_entrance")
    val mm_dungeon_up = find("mm_bamboo_ladder_dungeon_exit")
    val tzhaar_fightcave_entrance = find("tzhaar_fightcave_wall_entrance")
    val tzhaar_fightcave_exit = find("tzhaar_fightcave_wall_exit")
    val tzhaar_dungeon_entrance = find("tzhaar_karamjadungeon_wall_entrance")
    val tzhaar_dungeon_exit = find("tzhaar_karamjadungeon_wall_exit")
    val mudskipper_entrance_left =
        find("seabed2_cavewall_mudskipper_caveentrancel")
    val mudskipper_entrance_right =
        find("seabed2_cavewall_mudskipper_caveentrancer")
    val mudskipper_exit_left = find("seabed2_cavewall_mudskipper_caveexitl")
    val mudskipper_exit_right = find("seabed2_cavewall_mudskipper_caveexitr")
    val barbassault_recruitment_entrance = find("barbassault_recruitment_entrance")
    val barbassault_recruitment_exit = find("barbassault_recruitment_exit")
    val lunar_mine_up = find("lunar_mine_slanty_ladder_up")
    val lunar_mine_down = find("lunar_mine_slanty_ladder_down")
}
