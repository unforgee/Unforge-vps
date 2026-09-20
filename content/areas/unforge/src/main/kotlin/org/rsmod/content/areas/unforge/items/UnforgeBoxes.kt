package org.rsmod.content.areas.unforge.items

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.config.refs.invs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.varp.VarpType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kronos mystery box port (`item/actions/impl/boxes/mystery`).
 *
 * The Kronos "spinning wheel" interfaces (702/713) do not exist in this cache, so vote/super/
 * summer boxes roll immediately - same loot tables, same broadcast tiers, minus the wheel.
 */
object UnforgeBoxObjs : ObjReferences() {
    val mysteryBox = find("macro_quiz_mystery_box") // kronos 6199
    val petBox = find("win05_boxbauble_unpainted") // kronos 6828
    val voteBox = find("win05_boxbauble_yellow") // kronos 6829
    val thirdAgeBox = find("win05_boxbauble_blue") // kronos 6831
    val pvpArmourBox = find("deadman_starter_pack") // kronos 22330
    val superBox = find("research_package") // kronos 290
    val summerBox = find("hw24_pumpkin_white_woo") // kronos 30185
    val easterEgg = find("easter17_egg_crunchy") // kronos 21227
    val slayerCasket = find("casket") // 405 - reused for the slayer task casket
}

object UnforgeBoxVarps : VarpReferences() {
    val opened = find("cw_mbox_opened")
    val pity = find("cw_mbox_pity")
}

internal object UnforgeBoxVarpBuilder : VarpBuilder() {
    init {
        build("cw_mbox_opened")
        build("cw_mbox_pity")
    }
}

/**
 * Adds the Kronos inventory ops to the box items. These ids are obscure cache entries (baubles,
 * macro box, deadman pack) with no meaningful iops in this cache, so slots 1-2 are free for
 * "Open"/"Gift".
 */
internal object UnforgeBoxObjEdits : ObjEditor() {
    init {
        val giftable =
            listOf(
                UnforgeBoxObjs.mysteryBox,
                UnforgeBoxObjs.petBox,
                UnforgeBoxObjs.voteBox,
                UnforgeBoxObjs.superBox,
                UnforgeBoxObjs.thirdAgeBox,
                UnforgeBoxObjs.pvpArmourBox,
            )
        for (box in giftable + UnforgeBoxObjs.summerBox + UnforgeBoxObjs.easterEgg) {
            edit(box) {
                iop1 = "Open"
                if (box in giftable) {
                    iop2 = "Gift"
                }
            }
        }
        edit(UnforgeBoxObjs.slayerCasket) { iop1 = "Open" }
    }
}

private data class CwLoot(val id: Int, val min: Int, val max: Int, val broadcast: Boolean)

private fun loot(id: Int, amount: Int, weight: Int, broadcast: Boolean = false) =
    WeightedLoot(CwLoot(id, amount, amount, broadcast), weight)

private fun loot(id: Int, min: Int, max: Int, weight: Int, broadcast: Boolean = false) =
    WeightedLoot(CwLoot(id, min, max, broadcast), weight)

private data class WeightedLoot(val loot: CwLoot, val weight: Int)

private enum class ClueTier(val label: String, val bonusChancePerThousand: Int) {
    BEGINNER("Beginner", 3),
    EASY("Easy", 5),
    MEDIUM("Medium", 8),
    HARD("Hard", 12),
    ELITE("Elite", 18),
    MASTER("Master", 25),
}

/** Standard OSRS clue item ids. Clues are completed instantly in this standalone realm. */
private val CLUE_SCROLLS =
    mapOf(
        23182 to ClueTier.BEGINNER,
        2677 to ClueTier.EASY,
        2801 to ClueTier.MEDIUM,
        2722 to ClueTier.HARD,
        12073 to ClueTier.ELITE,
        19835 to ClueTier.MASTER,
    )

private val BEGINNER_CLUE_REWARDS =
    arrayOf(
        loot(995, 500, 2),
        loot(379, 1, 3),
        loot(882, 20, 4),
        loot(558, 20, 4),
        loot(555, 20, 4),
        loot(1607, 1, 1),
        loot(12183, 1, 1),
    )

private val EASY_CLUE_REWARDS =
    arrayOf(
        loot(995, 1000, 3),
        loot(379, 2, 4),
        loot(892, 30, 4),
        loot(556, 30, 4),
        loot(560, 15, 3),
        loot(1615, 1, 1),
        loot(2363, 1, 1),
    )

private val MEDIUM_CLUE_REWARDS =
    arrayOf(
        loot(995, 2500, 3),
        loot(385, 2, 4),
        loot(892, 60, 4),
        loot(560, 30, 4),
        loot(565, 20, 3),
        loot(11212, 20, 2),
        loot(1631, 1, 1),
        loot(2363, 2, 1),
    )

private val HARD_CLUE_REWARDS =
    arrayOf(
        loot(995, 5000, 3),
        loot(385, 4, 4),
        loot(560, 60, 4),
        loot(565, 40, 3),
        loot(566, 25, 2),
        loot(11212, 40, 2),
        loot(1617, 1, 1),
        loot(2363, 3, 1),
    )

private val ELITE_CLUE_REWARDS =
    arrayOf(
        loot(995, 10000, 3),
        loot(385, 6, 4),
        loot(560, 100, 4),
        loot(565, 75, 3),
        loot(566, 50, 2),
        loot(11212, 60, 2),
        loot(1631, 2, 1),
        loot(2363, 5, 1),
    )

private val MASTER_CLUE_REWARDS =
    arrayOf(
        loot(995, 20000, 3),
        loot(385, 10, 4),
        loot(560, 150, 4),
        loot(565, 100, 3),
        loot(566, 75, 2),
        loot(11212, 100, 2),
        loot(1617, 3, 1),
        loot(2363, 8, 1),
    )

// Rare side rolls are intentionally useful but stop below BIS gear.
private val CLUE_BONUS_REWARDS =
    arrayOf(
        loot(11840, 1, 4), // dragon boots
        loot(4151, 1, 3), // abyssal whip
        loot(11838, 1, 3), // dragon defender
        loot(2577, 1, 2), // ranger boots
        loot(6920, 1, 2), // infinity boots
        loot(6737, 1, 1), // berserker ring
    )

private fun clueRewards(tier: ClueTier): Array<WeightedLoot> =
    when (tier) {
        ClueTier.BEGINNER -> BEGINNER_CLUE_REWARDS
        ClueTier.EASY -> EASY_CLUE_REWARDS
        ClueTier.MEDIUM -> MEDIUM_CLUE_REWARDS
        ClueTier.HARD -> HARD_CLUE_REWARDS
        ClueTier.ELITE -> ELITE_CLUE_REWARDS
        ClueTier.MASTER -> MASTER_CLUE_REWARDS
    }

private fun roll(table: Array<WeightedLoot>): CwLoot {
    val total = table.sumOf { it.weight }
    var roll = Random.nextInt(total)
    for (entry in table) {
        roll -= entry.weight
        if (roll < 0) {
            return entry.loot
        }
    }
    return table.last().loot
}

// MysteryBox.MYSTERY_BOX_TABLE
private val MYSTERY_BOX_TABLE =
    arrayOf(
        loot(4708, 1, 50),
        loot(4712, 1, 50),
        loot(4714, 1, 50),
        loot(4716, 1, 40),
        loot(4720, 1, 40),
        loot(4722, 1, 40),
        loot(4718, 1, 40),
        loot(4736, 1, 50),
        loot(13442, 100, 250, 65),
        loot(11235, 1, 45),
        loot(8927, 1, 25),
        loot(2643, 1, 35),
        loot(12301, 1, 25),
        loot(2577, 1, 15),
        loot(12639, 1, 25),
        loot(12430, 1, 25),
        loot(12245, 1, 25),
        loot(12638, 1, 25),
        loot(12637, 1, 25),
        loot(11899, 1, 25),
        loot(11900, 1, 25),
        loot(11898, 1, 25),
        loot(11896, 1, 25),
        loot(11897, 1, 25),
        loot(12375, 1, 25),
        loot(12377, 1, 25),
        loot(12365, 1, 25),
        loot(12367, 1, 25),
        loot(12369, 1, 25),
        loot(12518, 1, 25),
        loot(12522, 1, 25),
        loot(12524, 1, 25),
        loot(12763, 1, 25),
        loot(12761, 1, 25),
        loot(12759, 1, 25),
        loot(12757, 1, 25),
        loot(12769, 1, 25),
        loot(12771, 1, 25),
        loot(12829, 1, 25),
        loot(6922, 1, 25),
        loot(6918, 1, 25),
        loot(6916, 1, 25),
        loot(6924, 1, 25),
        loot(6528, 1, 25),
        loot(6525, 1, 25),
        loot(4151, 1, 25),
        loot(4153, 1, 25),
        loot(6920, 1, 25),
        loot(11128, 1, 25),
        loot(12696, 200, 250, 25),
        loot(13442, 200, 250, 25),
        loot(12696, 400, 500, 25),
        loot(13442, 450, 650, 25),
        loot(1037, 1, 25),
        loot(6666, 1, 25),
        loot(4566, 1, 25),
        loot(13182, 1, 25),
        loot(12006, 1, 25),
        loot(6665, 1, 25),
        loot(11919, 1, 15),
        loot(12956, 1, 15),
        loot(12957, 1, 15),
        loot(12958, 1, 15),
        loot(12959, 1, 15),
        loot(12002, 1, 15),
        loot(6585, 1, 15),
        loot(11283, 1, 15),
        loot(12902, 1, 15),
        loot(11791, 1, 15),
        loot(4224, 1, 15),
        loot(12831, 1, 15),
        loot(11926, 1, 15),
        loot(11924, 1, 15),
        loot(12379, 1, 15),
        loot(12373, 1, 15),
        loot(12363, 1, 15),
        loot(12371, 1, 15),
        loot(12931, 1, 5),
        loot(13235, 1, 5),
        loot(13237, 1, 5),
        loot(13239, 1, 5),
        loot(11828, 1, 5),
        loot(11830, 1, 5),
        loot(11826, 1, 5),
        loot(11834, 1, 5),
        loot(11832, 1, 5),
        loot(11808, 1, 5),
        loot(11806, 1, 5),
        loot(11804, 1, 5),
        loot(6737, 1, 5),
        loot(6735, 1, 5),
        loot(6733, 1, 5),
        loot(6731, 1, 5),
        loot(11773, 1, 5),
        loot(11772, 1, 5),
        loot(11771, 1, 5),
        loot(11770, 1, 5),
        loot(20517, 1, 5),
        loot(20520, 1, 5),
        loot(20595, 1, 5),
        loot(12696, 800, 1000, 1),
        loot(13442, 800, 1000, 1),
        loot(12696, 1300, 1500, 1),
        loot(13442, 1200, 1500, 1),
        loot(11806, 1, 1),
        loot(12825, 1, 1),
        loot(12821, 1, 1, broadcast = true),
        loot(13271, 1, 1),
        loot(12791, 1, 1),
        loot(11785, 1, 1),
        loot(13576, 1, 1),
        loot(2581, 1, 1),
        loot(22981, 1, 1),
        loot(22975, 1, 1),
        loot(22978, 1, 1),
        loot(12596, 1, 1),
        loot(1053, 1, 1, broadcast = true),
        loot(1055, 1, 1, broadcast = true),
        loot(1057, 1, 1, broadcast = true),
        loot(11847, 1, 1, broadcast = true),
        loot(1050, 1, 1, broadcast = true),
        loot(13343, 1, 1, broadcast = true),
        loot(13344, 1, 1, broadcast = true),
        loot(12696, 4000, 5000, 1),
        loot(13442, 4000, 5000, 1),
        loot(1038, 1, 1, broadcast = true),
        loot(1040, 1, 1, broadcast = true),
        loot(1042, 1, 1, broadcast = true),
        loot(1044, 1, 1, broadcast = true),
        loot(1046, 1, 1, broadcast = true),
        loot(1048, 1, 1, broadcast = true),
        loot(11862, 1, 1, broadcast = true),
        loot(11863, 1, 1, broadcast = true),
        loot(12399, 1, 1, broadcast = true),
        loot(962, 1, 1, broadcast = true),
        loot(1419, 1, 1),
        loot(12422, 1, 1, broadcast = true),
        loot(12424, 1, 1, broadcast = true),
        loot(12426, 1, 1, broadcast = true),
        loot(12437, 1, 1, broadcast = true),
        loot(10330, 1, 1, broadcast = true),
        loot(10332, 1, 1, broadcast = true),
        loot(10334, 1, 1, broadcast = true),
        loot(10336, 1, 1, broadcast = true),
        loot(10338, 1, 1, broadcast = true),
        loot(10340, 1, 1, broadcast = true),
        loot(10342, 1, 1, broadcast = true),
        loot(10344, 1, 1, broadcast = true),
        loot(10346, 1, 1, broadcast = true),
        loot(10348, 1, 1, broadcast = true),
        loot(10350, 1, 1, broadcast = true),
        loot(10352, 1, 1, broadcast = true),
        loot(19988, 1, 1),
        loot(19991, 1, 1),
        loot(20059, 1, 1),
        loot(13652, 1, 1),
        loot(20050, 1, 1),
        loot(20008, 1, 1),
        loot(20110, 1, 1),
        loot(20020, 1, 1),
        loot(20023, 1, 1),
        loot(20026, 1, 1),
        loot(20029, 1, 1),
        loot(20032, 1, 1),
    )

// MysteryBox.INCENTIVE_TABLE (first box / every 5th box)
private val INCENTIVE_TABLE =
    arrayOf(
        loot(6585, 1, 100),
        loot(11283, 1, 100),
        loot(12902, 1, 100),
        loot(11791, 1, 75),
        loot(12931, 1, 75),
        loot(13235, 1, 75),
        loot(13237, 1, 75),
        loot(13239, 1, 50),
        loot(11828, 1, 50),
        loot(11830, 1, 50),
        loot(11826, 1, 50),
        loot(11834, 1, 50),
        loot(11832, 1, 50),
        loot(11808, 1, 50),
        loot(11806, 1, 50),
        loot(11804, 1, 50),
        loot(6737, 1, 50),
        loot(6731, 1, 50),
        loot(11806, 1, 1),
        loot(12821, 1, 1, broadcast = true),
        loot(13271, 1, 1),
        loot(12791, 1, 1),
        loot(11785, 1, 1),
        loot(13576, 1, 1),
        loot(2581, 1, 1),
        loot(12596, 1, 1),
        loot(1053, 1, 1, broadcast = true),
        loot(1055, 1, 1, broadcast = true),
        loot(1057, 1, 1, broadcast = true),
        loot(11847, 1, 1, broadcast = true),
        loot(1050, 1, 1, broadcast = true),
        loot(13343, 1, 1, broadcast = true),
        loot(13344, 1, 1, broadcast = true),
        loot(12696, 4000, 5000, 1),
        loot(13442, 4000, 5000, 1),
        loot(13652, 1, 1),
    )

// PVPArmourMysteryBox.PVP_ARMOUR_BOX_TABLE
private val PVP_ARMOUR_TABLE =
    arrayOf(
        loot(22622, 1, 1),
        loot(22625, 1, 1),
        loot(22628, 1, 1),
        loot(22631, 1, 1),
        loot(22610, 1, 1),
        loot(22613, 1, 1),
        loot(22616, 1, 1),
        loot(22619, 1, 1),
        loot(22647, 1, 1),
        loot(22650, 1, 1),
        loot(22653, 1, 1),
        loot(22656, 1, 1),
        loot(22638, 1, 1),
        loot(22641, 1, 1),
        loot(22644, 1, 1),
        loot(22634, 50, 1),
        loot(22636, 50, 1),
    )

// ThirdAgeMysteryBox.THIRD_AGE_BOX_TABLE
private val THIRD_AGE_TABLE =
    arrayOf(
        loot(12422, 1, 1),
        loot(12424, 1, 1),
        loot(12426, 1, 1),
        loot(12437, 1, 1),
        loot(10330, 1, 1),
        loot(10332, 1, 1),
        loot(10334, 1, 1),
        loot(10336, 1, 1),
        loot(10338, 1, 1),
        loot(10340, 1, 1),
        loot(10342, 1, 1),
        loot(10344, 1, 1),
        loot(10346, 1, 1),
        loot(10348, 1, 1),
        loot(10350, 1, 1),
        loot(10352, 1, 1),
    )

// VoteMysteryBox.ECO_VOTING_BOX_TABLE (eco server variant)
private val VOTE_BOX_TABLE =
    arrayOf(
        loot(995, 50_000, 300_000, 500),
        loot(6914, 1, 80),
        loot(4151, 1, 80),
        loot(20128, 1, 80),
        loot(20131, 1, 80),
        loot(20137, 1, 80),
        loot(4153, 1, 80),
        loot(6528, 1, 80),
        loot(10887, 1, 80),
        loot(1249, 1, 80),
        loot(11128, 1, 80),
        loot(4716, 1, 80),
        loot(4718, 1, 80),
        loot(4720, 1, 80),
        loot(4722, 1, 80),
        loot(4708, 1, 80),
        loot(4712, 1, 80),
        loot(4714, 1, 80),
        loot(6585, 1, 80),
        loot(12831, 1, 80),
        loot(6733, 1, 80),
        loot(6735, 1, 80),
        loot(6920, 1, 80),
        loot(12397, 1, 1, broadcast = true),
        loot(21295, 1, 1, broadcast = true),
        loot(21902, 1, 4, broadcast = true),
        loot(12929, 1, 5, broadcast = true),
        loot(11791, 1, 6, broadcast = true),
        loot(6889, 1, 6, broadcast = true),
        loot(22109, 1, 7, broadcast = true),
        loot(12791, 1, 7, broadcast = true),
        loot(12954, 1, 20, broadcast = true),
        loot(21301, 1, 20, broadcast = true),
        loot(21304, 1, 20, broadcast = true),
        loot(21298, 1, 20, broadcast = true),
    )

// SuperMysteryBox.ECO_MYSTERY_BOX_TABLE
private val SUPER_BOX_TABLE =
    arrayOf(
        loot(995, 4_000_000, 6_000_000, 70),
        loot(995, 4_000_000, 8_000_000, 50),
        loot(4151, 1, 40),
        loot(11128, 1, 30),
        loot(11840, 1, 50),
        loot(12002, 1, 15),
        loot(6585, 1, 15),
        loot(12902, 1, 15),
        loot(11791, 1, 15),
        loot(11785, 1, 15),
        loot(4224, 1, 15),
        loot(12831, 1, 15),
        loot(11926, 1, 15),
        loot(11924, 1, 15),
        loot(12379, 1, 25),
        loot(12373, 1, 15),
        loot(12363, 1, 15),
        loot(6889, 1, 15),
        loot(12900, 1, 15),
        loot(20724, 1, 5),
        loot(11908, 1, 15),
        loot(12371, 1, 15),
        loot(21634, 1, 15),
        loot(22003, 1, 15),
        loot(11284, 1, 15),
        loot(22545, 1, 15),
        loot(22550, 1, 15),
        loot(22555, 1, 15),
        loot(12931, 1, 5, broadcast = true),
        loot(13235, 1, 5, broadcast = true),
        loot(13237, 1, 5, broadcast = true),
        loot(13239, 1, 5, broadcast = true),
        loot(11828, 1, 5, broadcast = true),
        loot(11830, 1, 5, broadcast = true),
        loot(11826, 1, 5, broadcast = true),
        loot(11834, 1, 5, broadcast = true),
        loot(11832, 1, 5, broadcast = true),
        loot(11808, 1, 5, broadcast = true),
        loot(11806, 1, 5, broadcast = true),
        loot(11804, 1, 5, broadcast = true),
        loot(11773, 1, 5, broadcast = true),
        loot(11772, 1, 5, broadcast = true),
        loot(11771, 1, 5, broadcast = true),
        loot(11770, 1, 5, broadcast = true),
        loot(20517, 1, 5, broadcast = true),
        loot(20520, 1, 5, broadcast = true),
        loot(20595, 1, 5, broadcast = true),
        loot(13652, 1, 3, broadcast = true),
        loot(19553, 1, 3, broadcast = true),
        loot(19547, 1, 3, broadcast = true),
        loot(19544, 1, 3, broadcast = true),
        loot(19550, 1, 3, broadcast = true),
        loot(20017, 1, 2, broadcast = true),
        loot(6583, 1, 2, broadcast = true),
        loot(20005, 1, 2, broadcast = true),
        loot(995, 5_000_000, 10_000_000, 2, broadcast = true),
        loot(995, 8_000_000, 12_000_000, 2, broadcast = true),
        loot(11806, 1, 2, broadcast = true),
        loot(12821, 1, 2, broadcast = true),
        loot(13271, 1, 2, broadcast = true),
        loot(11785, 1, 2, broadcast = true),
        loot(13576, 1, 2, broadcast = true),
        loot(2581, 1, 2, broadcast = true),
        loot(12596, 1, 2),
        loot(22981, 1, 1),
        loot(22975, 1, 1),
        loot(22978, 1, 1, broadcast = true),
        loot(20997, 1, 2, broadcast = true),
        loot(1053, 1, 1, broadcast = true),
        loot(1055, 1, 1, broadcast = true),
        loot(1057, 1, 1, broadcast = true),
        loot(11847, 1, 1, broadcast = true),
        loot(1050, 1, 1, broadcast = true),
        loot(13343, 1, 1, broadcast = true),
        loot(13344, 1, 1, broadcast = true),
        loot(1038, 1, 1, broadcast = true),
        loot(1040, 1, 1, broadcast = true),
        loot(1042, 1, 1, broadcast = true),
        loot(1044, 1, 1, broadcast = true),
        loot(1046, 1, 1, broadcast = true),
        loot(1048, 1, 1, broadcast = true),
        loot(11862, 1, 1, broadcast = true),
        loot(11863, 1, 1, broadcast = true),
        loot(12399, 1, 1, broadcast = true),
        loot(962, 1, 1, broadcast = true),
        loot(21003, 1, 4, broadcast = true),
        loot(1419, 1, 1, broadcast = true),
        loot(1037, 1, 1, broadcast = true),
        loot(21006, 1, 3, broadcast = true),
        loot(12422, 1, 1, broadcast = true),
        loot(12424, 1, 1, broadcast = true),
        loot(12426, 1, 1, broadcast = true),
        loot(12437, 1, 1, broadcast = true),
        loot(10330, 1, 1, broadcast = true),
        loot(10332, 1, 1, broadcast = true),
        loot(10334, 1, 1, broadcast = true),
        loot(10336, 1, 1, broadcast = true),
        loot(10338, 1, 1, broadcast = true),
        loot(10340, 1, 1, broadcast = true),
        loot(10342, 1, 1, broadcast = true),
        loot(10344, 1, 1, broadcast = true),
        loot(10346, 1, 1, broadcast = true),
        loot(10348, 1, 1, broadcast = true),
        loot(10350, 1, 1, broadcast = true),
        loot(10352, 1, 1, broadcast = true),
    )

// SuperMysteryBox.EASTER_EGG_TABLE (box 21227)
private val EASTER_EGG_TABLE =
    arrayOf(
        loot(995, 4_000_000, 6_000_000, 30),
        loot(995, 4_000_000, 8_000_000, 30),
        loot(4151, 1, 30),
        loot(22351, 1, 10),
        loot(22353, 1, 10),
        loot(21214, 1, 10),
        loot(1037, 1, 10),
        loot(13182, 1, 10),
        loot(13663, 1, 10),
        loot(13664, 1, 10),
        loot(13665, 1, 10),
        loot(6585, 1, 15),
        loot(12902, 1, 15),
        loot(11791, 1, 15),
        loot(11785, 1, 15),
        loot(4224, 1, 15),
        loot(12831, 1, 15),
        loot(11926, 1, 15),
        loot(11924, 1, 15),
        loot(12379, 1, 15),
        loot(12373, 1, 15),
        loot(12363, 1, 15),
        loot(6889, 1, 15),
        loot(12900, 1, 15),
        loot(20724, 1, 15),
        loot(11908, 1, 15),
        loot(12371, 1, 15),
        loot(21634, 1, 15),
        loot(22003, 1, 15),
        loot(11284, 1, 15),
        loot(22545, 1, 15),
        loot(22550, 1, 15),
        loot(22555, 1, 15),
        loot(12931, 1, 5, broadcast = true),
        loot(13235, 1, 5, broadcast = true),
        loot(13237, 1, 5, broadcast = true),
        loot(13239, 1, 5, broadcast = true),
        loot(11828, 1, 5, broadcast = true),
        loot(11830, 1, 5, broadcast = true),
        loot(11826, 1, 5, broadcast = true),
        loot(11834, 1, 5, broadcast = true),
        loot(11832, 1, 5, broadcast = true),
        loot(11808, 1, 5, broadcast = true),
        loot(11806, 1, 5, broadcast = true),
        loot(11804, 1, 5, broadcast = true),
        loot(11773, 1, 5, broadcast = true),
        loot(11772, 1, 5, broadcast = true),
        loot(11771, 1, 5, broadcast = true),
        loot(11770, 1, 5, broadcast = true),
        loot(20517, 1, 5, broadcast = true),
        loot(20520, 1, 5, broadcast = true),
        loot(20595, 1, 5, broadcast = true),
        loot(13652, 1, 3, broadcast = true),
        loot(19553, 1, 3, broadcast = true),
        loot(19547, 1, 3, broadcast = true),
        loot(19544, 1, 3, broadcast = true),
        loot(19550, 1, 3, broadcast = true),
        loot(20017, 1, 2, broadcast = true),
        loot(6583, 1, 2, broadcast = true),
        loot(20005, 1, 2, broadcast = true),
        loot(995, 5_000_000, 10_000_000, 2),
        loot(995, 8_000_000, 12_000_000, 2),
        loot(11806, 1, 2, broadcast = true),
        loot(12821, 1, 2, broadcast = true),
        loot(13271, 1, 2, broadcast = true),
        loot(11785, 1, 2, broadcast = true),
        loot(13576, 1, 2, broadcast = true),
        loot(2581, 1, 2, broadcast = true),
        loot(12596, 1, 2),
        loot(20997, 1, 2, broadcast = true),
        loot(22981, 1, 1),
        loot(22975, 1, 1),
        loot(22978, 1, 1, broadcast = true),
        loot(1053, 1, 1, broadcast = true),
        loot(1055, 1, 1, broadcast = true),
        loot(1057, 1, 1, broadcast = true),
        loot(11847, 1, 1, broadcast = true),
        loot(1050, 1, 1, broadcast = true),
        loot(13343, 1, 1, broadcast = true),
        loot(13344, 1, 1, broadcast = true),
        loot(1038, 1, 1, broadcast = true),
        loot(1040, 1, 1, broadcast = true),
        loot(1042, 1, 1, broadcast = true),
        loot(1044, 1, 1, broadcast = true),
        loot(1046, 1, 1, broadcast = true),
        loot(1048, 1, 1, broadcast = true),
        loot(11862, 1, 1, broadcast = true),
        loot(11863, 1, 1, broadcast = true),
        loot(12399, 1, 1, broadcast = true),
        loot(962, 1, 1, broadcast = true),
        loot(1419, 1, 1, broadcast = true),
        loot(12422, 1, 1, broadcast = true),
        loot(12424, 1, 1, broadcast = true),
        loot(12426, 1, 1, broadcast = true),
        loot(12437, 1, 1, broadcast = true),
        loot(10330, 1, 1, broadcast = true),
        loot(10332, 1, 1, broadcast = true),
        loot(10334, 1, 1, broadcast = true),
        loot(10336, 1, 1, broadcast = true),
        loot(10338, 1, 1, broadcast = true),
        loot(10340, 1, 1, broadcast = true),
        loot(10342, 1, 1, broadcast = true),
        loot(10344, 1, 1, broadcast = true),
        loot(10346, 1, 1, broadcast = true),
        loot(10348, 1, 1, broadcast = true),
        loot(10350, 1, 1, broadcast = true),
        loot(10352, 1, 1, broadcast = true),
    )

// SummerMysteryBox.summerMboxLoot
private val SUMMER_BOX_TABLE =
    arrayOf(
        loot(4151, 1, 30),
        loot(11128, 1, 20),
        loot(11840, 1, 30),
        loot(12002, 1, 10),
        loot(6585, 1, 10),
        loot(12902, 1, 10),
        loot(11791, 1, 10),
        loot(11785, 1, 10),
        loot(4224, 1, 10),
        loot(12831, 1, 10),
        loot(11926, 1, 10),
        loot(11924, 1, 10),
        loot(12379, 1, 20),
        loot(12373, 1, 15),
        loot(6889, 1, 10),
        loot(12900, 1, 10),
        loot(20724, 1, 5),
        loot(11908, 1, 10),
        loot(12371, 1, 10),
        loot(21634, 1, 10),
        loot(22003, 1, 10),
        loot(11284, 1, 10),
        loot(22545, 1, 10),
        loot(22550, 1, 10),
        loot(13235, 1, 5, broadcast = true),
        loot(13237, 1, 5, broadcast = true),
        loot(13239, 1, 5, broadcast = true),
        loot(11806, 1, 5, broadcast = true),
        loot(11804, 1, 5, broadcast = true),
        loot(11773, 1, 5, broadcast = true),
        loot(11772, 1, 5, broadcast = true),
        loot(11771, 1, 5, broadcast = true),
        loot(11770, 1, 5, broadcast = true),
        loot(13652, 1, 3, broadcast = true),
        loot(19553, 1, 3, broadcast = true),
        loot(19547, 1, 3, broadcast = true),
        loot(19544, 1, 3, broadcast = true),
        loot(19550, 1, 3, broadcast = true),
        loot(20017, 1, 2, broadcast = true),
        loot(6583, 1, 2, broadcast = true),
        loot(20005, 1, 2, broadcast = true),
        loot(995, 5_000_000, 10_000_000, 2, broadcast = true),
        loot(995, 8_000_000, 12_000_000, 2, broadcast = true),
        loot(11806, 1, 2, broadcast = true),
        loot(12821, 1, 2, broadcast = true),
        loot(11785, 1, 2, broadcast = true),
        loot(13576, 1, 2, broadcast = true),
        loot(1038, 1, 1, broadcast = true),
        loot(1040, 1, 1, broadcast = true),
        loot(1042, 1, 1, broadcast = true),
        loot(1044, 1, 1, broadcast = true),
        loot(1046, 1, 1, broadcast = true),
        loot(1048, 1, 1, broadcast = true),
        loot(11862, 1, 1, broadcast = true),
        loot(11863, 1, 1, broadcast = true),
        loot(12399, 1, 1, broadcast = true),
        loot(962, 1, 1, broadcast = true),
        loot(1050, 1, 1, broadcast = true),
        loot(21003, 1, 4, broadcast = true),
        loot(1419, 1, 1, broadcast = true),
        loot(1037, 1, 1, broadcast = true),
        loot(21006, 1, 3, broadcast = true),
        loot(12437, 1, 1, broadcast = true),
        loot(10344, 1, 1, broadcast = true),
        loot(10346, 1, 1, broadcast = true),
        loot(10348, 1, 1, broadcast = true),
        loot(10350, 1, 1, broadcast = true),
        loot(10352, 1, 1, broadcast = true),
        // Summer exclusives
        loot(30194, 1, 3, broadcast = true),
        loot(30172, 1, 3, broadcast = true),
        loot(30169, 1, 3, broadcast = true),
        loot(30166, 1, 3, broadcast = true),
        loot(30163, 1, 3, broadcast = true),
        loot(30160, 1, 3, broadcast = true),
        loot(20997, 1, 1, broadcast = true),
        loot(30185, 1, 3, broadcast = true),
        loot(12817, 3, 1, broadcast = true),
    )

// Pet.java entries flagged mysteryBox=true - item ids only.
private val PET_BOX_ITEMS =
    intArrayOf(
        13262,
        12646,
        13178,
        13247,
        12647,
        11995,
        12644,
        12645,
        12643,
        12816,
        12650,
        21291,
        12652,
        12655,
        12649,
        21750,
        21748,
        12648,
        12921,
        12651,
        12653,
        13181,
        13225,
        13177,
        13179,
        21273,
        22319,
        22318,
        22746,
        30047,
        30045,
        30044,
        13324,
        13322,
        13320,
        13321,
        20659,
        20661,
        20663,
        20665,
        20667,
        20669,
        20671,
        20673,
        20675,
        20677,
        20679,
        20681,
        20683,
        20685,
        20687,
        20689,
        20691,
        21509,
        12703,
        19730,
        13071,
        20693,
        20851,
        22376,
        22378,
        22380,
        22382,
        22384,
        30016,
        30194,
        30217,
    )

/**
 * Slayer casket (obj 405, `casket`) dropped from slayer-task kills. Deliberately mid-tier: barrows
 * sets, bounty-hunter utility and deadman cosmetics - no best-in-slot uniques. One roll on this
 * table plus one guaranteed roll on [SLAYER_CASKET_SUPPLY_TABLE].
 */
private val SLAYER_CASKET_TABLE =
    arrayOf(
        // Barrows - all 24 pieces, equally rare.
        loot(4708, 1, 2), // ahrim hood
        loot(4710, 1, 2), // ahrim staff
        loot(4712, 1, 2), // ahrim top
        loot(4714, 1, 2), // ahrim skirt
        loot(4716, 1, 2), // dharok helm
        loot(4718, 1, 2), // dharok axe
        loot(4720, 1, 2), // dharok body
        loot(4722, 1, 2), // dharok legs
        loot(4724, 1, 2), // guthan helm
        loot(4726, 1, 2), // guthan spear
        loot(4728, 1, 2), // guthan body
        loot(4730, 1, 2), // guthan legs
        loot(4732, 1, 2), // karil coif
        loot(4734, 1, 2), // karil crossbow
        loot(4736, 1, 2), // karil top
        loot(4738, 1, 2), // karil skirt
        loot(4745, 1, 2), // torag helm
        loot(4747, 1, 2), // torag hammers
        loot(4749, 1, 2), // torag body
        loot(4751, 1, 2), // torag legs
        loot(4753, 1, 2), // verac helm
        loot(4755, 1, 2), // verac flail
        loot(4757, 1, 2), // verac top
        loot(4759, 1, 2), // verac skirt
        // Bounty hunter utility.
        loot(12746, 1, 6), // bh emblem
        loot(12789, 1, 6), // bh clue box
        loot(12791, 1, 5), // bh rune pouch
        // Deadman cosmetics and coin stacks.
        loot(13317, 1, 5), // deadman body
        loot(13318, 1, 5), // deadman legs
        loot(13319, 1, 5), // deadman cape
        loot(13313, 1, 6), // deadman coins 100
        loot(13314, 1, 4), // deadman coins 250
        loot(13315, 1, 3), // deadman coins 1000
        // Skilling tools.
        loot(12019, 1, 5), // coal bag
        loot(12020, 1, 5), // gem bag
    )

/** Guaranteed supply roll for the slayer casket - noted resources and consumables. */
private val SLAYER_CASKET_SUPPLY_TABLE =
    arrayOf(
        loot(386, 50, 100, 10), // cert shark
        loot(13442, 30, 60, 8), // cert anglerfish
        loot(9740, 10, 20, 8), // cert super combat potions
        loot(2435, 15, 30, 10), // cert prayer potions
        loot(3025, 15, 30, 10), // cert super restores
        loot(537, 30, 60, 10), // cert dragon bones
        loot(452, 20, 40, 8), // cert runite ore
        loot(448, 40, 80, 8), // cert mithril ore
        loot(454, 80, 160, 10), // cert coal
        loot(1514, 60, 120, 10), // cert magic logs
        loot(1516, 100, 200, 8), // cert yew logs
        loot(258, 8, 15, 8), // cert ranarr weed
        loot(270, 4, 8, 6), // cert torstol
        loot(1602, 5, 12, 6), // cert diamond
        loot(1616, 3, 8, 5), // cert dragonstone
        loot(1618, 10, 20, 8), // cert uncut diamond
        loot(2363, 15, 30, 8), // runite bar
        loot(892, 150, 300, 10), // rune arrows
        loot(11230, 80, 150, 8), // dragon darts
        loot(21905, 80, 150, 8), // dragon bolts
        loot(560, 150, 300, 8), // death runes
        loot(565, 150, 300, 8), // blood runes
        loot(566, 100, 250, 6), // soul runes
        loot(563, 150, 300, 6), // law runes
        loot(561, 150, 300, 6), // nature runes
        loot(1513, 100, 200, 8), // magic logs
        loot(269, 5, 10, 5), // torstol
        loot(257, 10, 20, 6), // ranarr weed
    )

class UnforgeBoxes
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val players: PlayerList,
    private val objRepo: ObjRepository,
    private val invTypes: InvTypeList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(UnforgeBoxObjs.mysteryBox) { openMysteryBox(it.slot) }
        onOpHeld1(UnforgeBoxObjs.petBox) { openPetBox(it.slot) }
        onOpHeld1(UnforgeBoxObjs.voteBox) {
            openBox(it.slot, VOTE_BOX_TABLE, "Voting Mystery Box")
        }
        onOpHeld1(UnforgeBoxObjs.thirdAgeBox) {
            openBox(it.slot, THIRD_AGE_TABLE, "3rd Age Mystery Box")
        }
        onOpHeld1(UnforgeBoxObjs.pvpArmourBox) {
            openBox(it.slot, PVP_ARMOUR_TABLE, "PVP Armour Mystery Box")
        }
        onOpHeld1(UnforgeBoxObjs.superBox) {
            openBox(it.slot, SUPER_BOX_TABLE, "Super Mystery Box")
        }
        onOpHeld1(UnforgeBoxObjs.summerBox) {
            openBox(it.slot, SUMMER_BOX_TABLE, "Summer Mystery Box")
        }
        onOpHeld1(UnforgeBoxObjs.easterEgg) { openBox(it.slot, EASTER_EGG_TABLE, "Easter Egg") }
        onOpHeld1(UnforgeBoxObjs.slayerCasket) { openSlayerCasket(it.slot) }
        for ((id, tier) in CLUE_SCROLLS) {
            objTypes[id]?.let { clue -> onOpHeld1(clue) { openClueScroll(it.slot, tier) } }
        }

        // MysteryBox.gift registrations (kronos: 6199, 6828, 6829, 290, 6831, 22330)
        onOpHeld2(UnforgeBoxObjs.mysteryBox) { gift(it.slot) }
        onOpHeld2(UnforgeBoxObjs.petBox) { gift(it.slot) }
        onOpHeld2(UnforgeBoxObjs.voteBox) { gift(it.slot) }
        onOpHeld2(UnforgeBoxObjs.superBox) { gift(it.slot) }
        onOpHeld2(UnforgeBoxObjs.thirdAgeBox) { gift(it.slot) }
        onOpHeld2(UnforgeBoxObjs.pvpArmourBox) { gift(it.slot) }
    }

    private fun setVarp(player: Player, varp: VarpType, value: Int) {
        player.vars.backing[varp.id] = value
        player.resyncVar(varp)
    }

    /** `MysteryBox` open - incentive table on the first box and every 5th after. */
    private fun ProtectedAccess.openMysteryBox(slot: Int) {
        val loot =
            if (player.vars[UnforgeBoxVarps.opened] == 0) {
                setVarp(player, UnforgeBoxVarps.opened, 1)
                roll(INCENTIVE_TABLE)
            } else if (player.vars[UnforgeBoxVarps.pity] >= 5) {
                setVarp(player, UnforgeBoxVarps.pity, 1)
                roll(INCENTIVE_TABLE)
            } else {
                setVarp(player, UnforgeBoxVarps.pity, player.vars[UnforgeBoxVarps.pity] + 1)
                roll(MYSTERY_BOX_TABLE)
            }
        invDel(inv, UnforgeBoxObjs.mysteryBox, 1, slot = slot)
        giveLoot(loot, "Mystery Box")
    }

    /** `PetMysteryBox` - rolls a pet item the player doesn't already own. */
    private suspend fun ProtectedAccess.openPetBox(slot: Int) {
        val confirm =
            choice2(
                "Open your Pet Mystery Box and receive a random pet?",
                true,
                "No, thanks.",
                false,
            )
        if (!confirm) {
            return
        }
        val owned = PET_BOX_ITEMS.toMutableSet()
        for (list in listOf(player.inv, player.worn, bank)) {
            for (obj in list) {
                if (obj != null) {
                    owned.remove(obj.id)
                }
            }
        }
        if (owned.isEmpty()) {
            mes("You already have every obtainable pet unlocked!")
            return
        }
        val petId = owned.random()
        invDel(inv, UnforgeBoxObjs.petBox, 1, slot = slot)
        val pet = objTypes[petId]
        if (pet != null) {
            invAddOrDrop(objRepo, pet)
            mes("You have unlocked ${pet.name}.")
            broadcast("Pet Mystery Box", "${player.displayName} has just received ${pet.name}.")
        }
    }

    /** Slayer task casket: one main-table roll plus one guaranteed supply roll. */
    private fun ProtectedAccess.openSlayerCasket(slot: Int) {
        val box = inv[slot]?.let { objTypes[it.id] } ?: return
        val main = roll(SLAYER_CASKET_TABLE)
        val supply = roll(SLAYER_CASKET_SUPPLY_TABLE)
        invDel(inv, box, 1, slot = slot)
        giveLoot(main, "Slayer Casket")
        giveLoot(supply, "Slayer Casket")
    }

    /**
     * Clue scrolls are deliberately a one-click activity in Unforge: the clue is treated as the
     * casket, consumed immediately, and its normal tiered reward is paid out. A separate bonus roll
     * is rare and contains useful progression gear, never BIS equipment.
     */
    private fun ProtectedAccess.openClueScroll(slot: Int, tier: ClueTier) {
        val clue = inv[slot]?.let { objTypes[it.id] } ?: return
        invDel(inv, clue, 1, slot = slot)
        mes("<col=8B4513>Your ${tier.label} clue opens as a casket.</col>")

        repeat(if (tier >= ClueTier.HARD) 3 else 2) {
            giveLoot(roll(clueRewards(tier)), "${tier.label} Clue Casket")
        }
        if (Random.nextInt(1000) < tier.bonusChancePerThousand) {
            giveLoot(roll(CLUE_BONUS_REWARDS), "${tier.label} Clue Casket")
        }
    }

    private fun ProtectedAccess.openBox(slot: Int, table: Array<WeightedLoot>, label: String) {
        val box = inv[slot]?.let { objTypes[it.id] } ?: return
        val loot = roll(table)
        invDel(inv, box, 1, slot = slot)
        giveLoot(loot, label)
    }

    private fun ProtectedAccess.giveLoot(loot: CwLoot, boxLabel: String) {
        val type = objTypes[loot.id] ?: return
        val count = if (loot.min == loot.max) loot.min else Random.nextInt(loot.min, loot.max + 1)
        invAddOrDrop(objRepo, type, count)
        mes("<col=006400>You open the box to find ${countText(count)}${type.name}.</col>")
        if (loot.broadcast) {
            broadcast(boxLabel, "${player.displayName} just received ${type.name}!")
        }
    }

    private fun countText(count: Int): String = if (count > 1) "$count x " else ""

    private fun broadcast(source: String, message: String) {
        for (p in players) {
            p.mes("<img=91> <col=a52a2a>[$source]</col> $message")
        }
    }

    /** `MysteryBox.gift` - name input -> target -> confirm -> transfer. */
    private suspend fun ProtectedAccess.gift(slot: Int) {
        val box = inv[slot] ?: return
        val boxType = objTypes[box.id] ?: return
        val name =
            stringDialog("Enter player's display name:")
                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                .take(12)
        if (name.isBlank()) {
            mes("Invalid username.")
            return
        }
        if (name.equals(player.displayName, ignoreCase = true)) {
            mes("Cannot gift yourself.")
            return
        }
        val target = players.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
        if (target == null) {
            mes("Player cannot be found.")
            return
        }
        val confirm =
            choice2("Gift your ${boxType.name} to ${target.displayName}?", true, "Cancel.", false)
        if (!confirm) {
            return
        }
        if (inv[slot]?.id != box.id) {
            return
        }
        invDel(inv, boxType, 1, slot = slot)
        // Add to the target's inventory, falling back to the bank when full - same as Kronos.
        target.mes("<col=a52a2a>You have received a gift: ${boxType.name}!</col>")
        if (target.inv.freeSpace() > 0) {
            target.invAdd(target.inv, boxType)
        } else {
            target.invAdd(target.invMap.getOrPut(invTypes[invs.bank]), boxType)
        }
        mes("<col=a52a2a>You have gifted your ${boxType.name} to ${target.displayName}.</col>")
    }
}
