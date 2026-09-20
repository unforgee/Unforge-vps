package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType

/**
 * Every rock in the first cut, and the numbers behind it.
 *
 * **Where the numbers come from.** Level, xp and respawn are the OSRS Wiki's mining-info box for
 * each rock. The success rates are the `low`/`high` parameters of that page's `Skilling success
 * chart` template - the same pair `SkillingSuccessRate` consumes, so `(1 + low)/256` is the chance
 * at level 1 and `(1 + high)/256` the chance at level 99. Checked against a rendered chart before
 * being trusted: iron reads 96/350, and `(1 + floor(96*84/98 + 350*14/98 + 0.5))/256` is 133/256 =
 * 0.51953125, which is exactly the value the wiki plots at level 15.
 *
 * **Two rocks scale to level 110, not 99.** Runite and amethyst carry `highLevel=110`, so their
 * published pair interpolates over 1..110 while this engine interpolates over 1..99. Both are
 * re-solved onto the 99 scale here (see the per-rock notes). Do not "restore" the wiki's raw pair.
 *
 * **Two rocks have no published chart at all** - lovakite and soft clay. Their rates are estimates
 * and are marked as such below. Everything else about them is sourced.
 *
 * **`deplete_chance = 0` means always deplete.** The script rolls `random.of(1, 255) > chance`, so
 * zero is unconditionally true - which is what an ore rock does: one ore, then it is spent. This is
 * the same value regular trees use.
 */
internal object MiningRocks : LocEditor() {
    init {
        clay(rocks.clay_1, empty_rocks.rocks_1)
        clay(rocks.clay_2, empty_rocks.rocks_2)
        softClay(rocks.soft_clay_1, empty_rocks.rocks_1)
        softClay(rocks.soft_clay_2, empty_rocks.rocks_2)
        softClay(rocks.prif_soft_clay, empty_rocks.prif_rocks)
        copper(rocks.copper_1, empty_rocks.rocks_1)
        copper(rocks.copper_2, empty_rocks.rocks_2)
        copper(rocks.newbie_copper, empty_rocks.newbie_rocks)
        tin(rocks.tin_1, empty_rocks.rocks_1)
        tin(rocks.tin_2, empty_rocks.rocks_2)
        tin(rocks.newbie_tin, empty_rocks.newbie_rocks)
        blurite(rocks.blurite_1, empty_rocks.rocks_1)
        blurite(rocks.blurite_2, empty_rocks.rocks_2)
        iron(rocks.iron_1, empty_rocks.rocks_1)
        iron(rocks.iron_2, empty_rocks.rocks_2)
        iron(rocks.gim_iron, empty_rocks.rocks_1)
        iron(rocks.prif_iron, empty_rocks.prif_rocks)
        silver(rocks.silver_1, empty_rocks.rocks_1)
        silver(rocks.silver_2, empty_rocks.rocks_2)
        silver(rocks.prif_silver, empty_rocks.prif_rocks)
        coal(rocks.coal_1, empty_rocks.rocks_1)
        coal(rocks.coal_2, empty_rocks.rocks_2)
        coal(rocks.prif_coal, empty_rocks.prif_rocks)
        gold(rocks.gold_1, empty_rocks.rocks_1)
        gold(rocks.gold_2, empty_rocks.rocks_2)
        gold(rocks.prif_gold, empty_rocks.prif_rocks)
        lovakite(rocks.lovakite_1, empty_rocks.rocks_1)
        lovakite(rocks.lovakite_2, empty_rocks.rocks_2)
        mithril(rocks.mithril_1, empty_rocks.rocks_1)
        mithril(rocks.mithril_2, empty_rocks.rocks_2)
        mithril(rocks.prif_mithril, empty_rocks.prif_rocks)
        adamantite(rocks.adamantite_1, empty_rocks.rocks_1)
        adamantite(rocks.adamantite_2, empty_rocks.rocks_2)
        adamantite(rocks.prif_adamantite, empty_rocks.prif_rocks)
        runite(rocks.runite_1, empty_rocks.rocks_1)
        runite(rocks.runite_2, empty_rocks.rocks_2)
        runite(rocks.prif_runite, empty_rocks.prif_rocks)
        amethyst(rocks.amethyst_1, empty_rocks.amethyst_empty)
        amethyst(rocks.amethyst_2, empty_rocks.amethyst_empty)
    }

    private fun clay(type: LocType, empty: LocType) =
        rock(type, empty, mining_objs.clay, level = 1, xp = 5.0, respawn = 2, low = 128, high = 400)

    /**
     * Prifddinas' soft clay. Level, xp and respawn are published; the success rate is **not** - the
     * wiki page carries a "needs skilling success chart" marker. Clay's own pair is reused, which
     * makes a level-70 rock effectively never fail. That matches what the rock is for, but it is an
     * estimate, not a source.
     */
    private fun softClay(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.soft_clay,
            level = 70,
            xp = 5.0,
            respawn = 2,
            low = 128,
            high = 400,
        )

    private fun copper(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.copper_ore,
            level = 1,
            xp = 17.5,
            respawn = 4,
            low = 100,
            high = 350,
        )

    private fun tin(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.tin_ore,
            level = 1,
            xp = 17.5,
            respawn = 4,
            low = 100,
            high = 350,
        )

    private fun blurite(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.blurite_ore,
            level = 10,
            xp = 17.5,
            respawn = 42,
            low = 90,
            high = 350,
        )

    private fun iron(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.iron_ore,
            level = 15,
            xp = 35.0,
            respawn = 9,
            low = 96,
            high = 350,
        )

    private fun silver(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.silver_ore,
            level = 20,
            xp = 40.0,
            respawn = 100,
            low = 25,
            high = 200,
        )

    private fun coal(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.coal,
            level = 30,
            xp = 50.0,
            respawn = 50,
            low = 16,
            high = 100,
        )

    private fun gold(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.gold_ore,
            level = 40,
            xp = 65.0,
            respawn = 100,
            low = 7,
            high = 75,
        )

    /**
     * Level, xp (60, not the 10 it was before Kourend Favour was removed) and the 35-second respawn
     * are published; the success rate is **not** - lovakite has no skilling success chart. The pair
     * below is interpolated between mithril (4/50 at level 55) and adamantite (2/25 at level 70) at
     * lovakite's level 65. An estimate, not a source.
     */
    private fun lovakite(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.lovakite_ore,
            level = 65,
            xp = 60.0,
            respawn = 58,
            low = 3,
            high = 33,
        )

    private fun mithril(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.mithril_ore,
            level = 55,
            xp = 80.0,
            respawn = 200,
            low = 4,
            high = 50,
        )

    private fun adamantite(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.adamantite_ore,
            level = 70,
            xp = 95.0,
            respawn = 400,
            low = 2,
            high = 25,
        )

    /**
     * The wiki publishes 1/18 over a **110** scale. Re-solved onto this engine's 99 scale as 3/16,
     * which reproduces the published chance exactly at every level from 85 to 99 except 95..98,
     * where it is one 256th low.
     */
    private fun runite(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.runite_ore,
            level = 85,
            xp = 125.0,
            respawn = 1200,
            low = 3,
            high = 16,
        )

    /**
     * The wiki publishes -18/10 over a **110** scale. Re-solved onto this engine's 99 scale as
     * -17/7, which reproduces the published chance exactly at every level from 92 to 99. The
     * negative low is intentional: it is below the level requirement and never reached.
     */
    private fun amethyst(type: LocType, empty: LocType) =
        rock(
            type,
            empty,
            mining_objs.amethyst,
            level = 92,
            xp = 240.0,
            respawn = 125,
            low = -17,
            high = 7,
        )

    private fun rock(
        type: LocType,
        empty: LocType,
        ore: ObjType,
        level: Int,
        xp: Double,
        respawn: Int,
        low: Int,
        high: Int,
    ) {
        edit(type) {
            contentGroup = mining_content.rock
            param[params.levelrequire] = level
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
            param[params.skill_productitem] = ore
            param[params.next_loc_stage] = empty
            param[params.respawn_time] = respawn
            param[params.deplete_chance] = 0
            param[mining_params.rate_low] = low
            param[mining_params.rate_high] = high
        }
    }
}
