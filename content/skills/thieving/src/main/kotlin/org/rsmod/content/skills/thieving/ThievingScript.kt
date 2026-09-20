package org.rsmod.content.skills.thieving

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ThievingScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val perks: PerkService,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class PickpocketTarget(
        val nameMatch: String,
        val levelReq: Int,
        val xp: Double,
        val minCoins: Int,
        val maxCoins: Int,
        val failDamage: Int,
        val extraItems: List<Int> = emptyList(),
    )

    private val targets =
        listOf(
            PickpocketTarget("man", 1, 8.0, 3, 15, 1),
            PickpocketTarget("woman", 1, 8.0, 3, 15, 1),
            PickpocketTarget("farmer", 10, 14.5, 9, 25, 1, listOf(5318, 5319, 5324)), // Seeds
            PickpocketTarget("warrior woman", 25, 26.0, 18, 40, 2),
            PickpocketTarget("al-kharid warrior", 25, 26.0, 18, 40, 2),
            PickpocketTarget("rogue", 32, 35.5, 25, 40, 2, listOf(1523)), // Lockpick
            PickpocketTarget("guard", 40, 46.8, 30, 60, 2),
            PickpocketTarget(
                "master farmer",
                38,
                43.0,
                1,
                1,
                3,
                listOf(5291, 5292, 5293, 5294, 5295, 5296, 5297, 5298, 5299, 5300),
            ),
            PickpocketTarget("knight of ardougne", 55, 84.3, 50, 100, 3),
            PickpocketTarget("paladin", 70, 151.8, 80, 150, 3, listOf(562)), // Chaos runes
            PickpocketTarget("hero", 80, 273.3, 200, 300, 4, listOf(560, 565)), // Death & Blood
            PickpocketTarget(
                "elf",
                85,
                353.0,
                280,
                350,
                5,
                listOf(560, 565, 1617),
            ), // Runes & Diamond
        )

    private data class Stall(
        val nameMatch: String,
        val levelReq: Int,
        val xp: Double,
        val lootIds: List<Int>,
    )

    private val stalls =
        listOf(
            Stall("baker's stall", 5, 16.0, listOf(1891, 2309)), // Cake, Bread
            Stall("tea stall", 5, 16.0, listOf(1978)), // Cup of tea
            Stall("silk stall", 20, 24.0, listOf(950)), // Silk
            Stall("fur stall", 35, 36.0, listOf(958)), // Grey wolf fur
            Stall("silver stall", 50, 54.0, listOf(442)), // Silver ore
            Stall("spice stall", 65, 81.3, listOf(2007)), // Spice
            Stall(
                "gem stall",
                75,
                160.0,
                listOf(1623, 1621, 1619, 1617),
            ), // Sapphire, Emerald, Ruby, Diamond
        )

    private data class Chest(
        val nameMatch: String,
        val levelReq: Int,
        val xp: Double,
        val requiresLockpick: Boolean,
        val minCoins: Int,
        val maxCoins: Int,
        val lootIds: List<Int> = emptyList(),
    )

    private val chests =
        listOf(
            Chest("10gp", 13, 7.5, false, 10, 10),
            Chest("nature", 28, 25.0, true, 3, 10, listOf(561)), // Nature rune
            Chest("dorgesh", 52, 200.0, true, 50, 200, listOf(1623, 1621)),
            Chest("rogue", 84, 100.0, true, 500, 1500, listOf(565, 1617)), // Blood rune, diamond
        )

    override fun ScriptContext.startup() {
        val pickAnim = seqTypes[881]
        val coinsObj = objTypes[995]

        // 1. Pickpocket NPCs
        for (npc in npcTypes.values) {
            val name = npc.name.lowercase()
            val matchedTarget = targets.firstOrNull { name.contains(it.nameMatch) } ?: continue

            val op2 = npc.op[1]?.lowercase() ?: ""
            val op3 = npc.op[2]?.lowercase() ?: ""

            val pickAction: suspend ProtectedAccess.() -> Unit = {
                val thievingLevel = player.stat(stats.thieving)
                if (thievingLevel < matchedTarget.levelReq) {
                    mes(
                        "You need a Thieving level of ${matchedTarget.levelReq} to pickpocket this person."
                    )
                } else {
                    val successChance =
                        (thievingLevel - matchedTarget.levelReq +
                                40 +
                                perks.pickpocketBonus(player))
                            .coerceIn(40, 98)
                    val success = Random.nextInt(100) < successChance
                    if (pickAnim != null) anim(pickAnim)
                    delay(2)

                    if (success) {
                        // Check Rogue Outfit (each piece grants 20% chance to double loot)
                        var roguePieces = 0
                        val rogueSlots =
                            listOf(
                                Wearpos.Hat,
                                Wearpos.Torso,
                                Wearpos.Legs,
                                Wearpos.Hands,
                                Wearpos.Feet,
                            )
                        for (slot in rogueSlots) {
                            val wornItem = player.worn[slot.slot] ?: continue
                            val objType = objTypes[wornItem.id] ?: continue
                            if (objType.name.lowercase().contains("rogue")) {
                                roguePieces++
                            }
                        }
                        val isDoubled = Random.nextInt(100) < (roguePieces * 20)

                        var coins =
                            Random.nextInt(matchedTarget.minCoins, matchedTarget.maxCoins + 1)
                        if (isDoubled) coins *= 2

                        if (coinsObj != null && coins > 0) {
                            skillingRewards.grant(this, "THIEVING", coinsObj, coins)
                        }
                        if (matchedTarget.extraItems.isNotEmpty() && Random.nextInt(100) < 30) {
                            val extra = matchedTarget.extraItems.random()
                            val extraObj = objTypes[extra]
                            if (extraObj != null) {
                                val count = if (isDoubled) 2 else 1
                                skillingRewards.grant(this, "THIEVING", extraObj, count)
                            }
                        }
                        player.statAdvance(stats.thieving, matchedTarget.xp)
                        if (isDoubled) {
                            mes(
                                "You pick the ${npc.name}'s pocket. Your Rogue equipment doubles your loot!"
                            )
                        } else {
                            mes("You pick the ${npc.name}'s pocket.")
                        }
                    } else {
                        // Check Dodgy Necklace protection
                        val neckObj = player.worn[Wearpos.Front.slot]
                        val hasDodgyNecklace =
                            neckObj != null &&
                                objTypes[neckObj.id]
                                    ?.name
                                    ?.lowercase()
                                    ?.contains("dodgy necklace") == true
                        val protected = hasDodgyNecklace && Random.nextInt(100) < 25

                        if (protected) {
                            mes("Your dodgy necklace protects you from being stunned or damaged.")
                        } else {
                            statSub(
                                stats.hitpoints,
                                constant = matchedTarget.failDamage,
                                percent = 0,
                            )
                            mes("You fail to pick the ${npc.name}'s pocket and receive a blow.")
                            delay(5) // Stun duration: cannot perform actions for 5 ticks
                        }
                    }
                }
            }

            if (op2.contains("pickpocket")) {
                onOpNpc2(npc) { pickAction() }
            } else if (op3.contains("pickpocket")) {
                onOpNpc3(npc) { pickAction() }
            }
        }

        // 2. Steal from Stalls
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            val matchedStall = stalls.firstOrNull { name.contains(it.nameMatch) } ?: continue

            onOpLoc2(loc) {
                val thievingLevel = player.stat(stats.thieving)
                if (thievingLevel < matchedStall.levelReq) {
                    mes(
                        "You need a Thieving level of ${matchedStall.levelReq} to steal from this stall."
                    )
                    return@onOpLoc2
                }
                if (inv.freeSpace() <= 0) {
                    mes("Your inventory is too full to hold anything from the stall.")
                    return@onOpLoc2
                }

                if (pickAnim != null) anim(pickAnim)
                delay(2)

                val lootId = matchedStall.lootIds.random()
                val lootObj = objTypes[lootId]
                if (lootObj != null) {
                    skillingRewards.grant(this, "THIEVING", lootObj)
                }
                player.statAdvance(stats.thieving, matchedStall.xp)
                mes("You steal from the ${loc.name}.")
            }
        }

        // 3. Lockpicking & Trapped Chests
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (!name.contains("chest")) continue
            val matchedChest = chests.firstOrNull { name.contains(it.nameMatch) } ?: continue

            val chestAction: suspend ProtectedAccess.() -> Unit = chestAction@{
                val thievingLevel = player.stat(stats.thieving)
                if (thievingLevel < matchedChest.levelReq) {
                    mes(
                        "You need a Thieving level of ${matchedChest.levelReq} to pick the lock on this chest."
                    )
                    return@chestAction
                }

                if (matchedChest.requiresLockpick) {
                    val hasLockpick =
                        inv.any {
                            it != null &&
                                objTypes[it.id]?.name?.lowercase()?.contains("lockpick") == true
                        }
                    if (!hasLockpick) {
                        mes("You need a lockpick to crack this lock.")
                        return@chestAction
                    }
                }

                if (pickAnim != null) anim(pickAnim)
                delay(2)

                // Trap trigger chance (15%)
                if (Random.nextInt(100) < 15) {
                    statSub(stats.hitpoints, constant = 2, percent = 0)
                    mes("You trigger a trap on the chest and take 2 damage!")
                    delay(3)
                    return@chestAction
                }

                val coins = Random.nextInt(matchedChest.minCoins, matchedChest.maxCoins + 1)
                if (coinsObj != null && coins > 0) {
                    skillingRewards.grant(this, "THIEVING", coinsObj, coins)
                }

                for (lootId in matchedChest.lootIds) {
                    val lootObj = objTypes[lootId]
                    if (lootObj != null) {
                        skillingRewards.grant(this, "THIEVING", lootObj)
                    }
                }

                player.statAdvance(stats.thieving, matchedChest.xp)
                mes("You skillfully crack the lock and plunder the ${loc.name}!")
            }

            val op1 = loc.op[0]?.lowercase() ?: ""
            val op2 = loc.op[1]?.lowercase() ?: ""

            if (op1.contains("pick") || op1.contains("open")) {
                onOpLoc1(loc) { chestAction() }
            } else if (op2.contains("pick") || op2.contains("open")) {
                onOpLoc2(loc) { chestAction() }
            }
        }
    }
}
