package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ConstructionScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val statTypes: StatTypeList,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class FurnitureRecipe(
        val name: String,
        val levelReq: Int,
        val xp: Double,
        val plankId: Int, // 960: Regular, 8778: Oak, 8780: Teak, 8782: Mahogany
        val plankCount: Int,
        val nailsRequired: Int = 0,
    )

    private val recipes =
        listOf(
            FurnitureRecipe("Crude Wooden Chair", 1, 58.0, 960, 2, 2),
            FurnitureRecipe("Wooden Chair", 8, 87.0, 960, 3, 3),
            FurnitureRecipe("Oak Chair", 19, 120.0, 8778, 2),
            FurnitureRecipe("Oak Larder", 33, 480.0, 8778, 8),
            FurnitureRecipe("Teak Armchair", 35, 180.0, 8780, 2),
            FurnitureRecipe("Mahogany Armchair", 50, 280.0, 8782, 2),
            FurnitureRecipe("Gilded Altar", 75, 1050.0, 8782, 4),
        )

    private val boneXp =
        mapOf(
            526 to 4.5, // Regular bones
            532 to 15.0, // Big bones
            534 to 30.0, // Baby dragon bones
            536 to 72.0, // Dragon bones
            11943 to 85.0, // Lava dragon bones
            22124 to 150.0, // Superior dragon bones
        )

    override fun ScriptContext.startup() {
        val sawIds = setOf(8794, 9472) // Regular saw, crystal saw
        val hammerId = 2347
        val nailIds = setOf(4819, 4820, 1539) // Iron, steel nails
        val coinsObj = objTypes[995]
        val buildAnim = seqTypes[898] ?: seqTypes[827]

        // 1. Estate Agent NPC (House purchase & deeds)
        for (npc in npcTypes.values) {
            val name = npc.name.lowercase()
            if (!name.contains("estate agent")) continue

            val agentAction: suspend ProtectedAccess.() -> Unit = agentAction@{
                val coinsCount = inv.count { it != null && it.id == 995 }
                if (coinsCount < 1000) {
                    mes("The Estate Agent says: 'A house deed in Rimmington costs 1,000 coins.'")
                    return@agentAction
                }

                if (coinsObj != null) {
                    invDel(inv, coinsObj, 1000)
                }
                player.statAdvance(stats.construction, 100.0)
                mes(
                    "The Estate Agent sells you a deed to your new Player-Owned House in Rimmington! (+100 XP)"
                )
            }

            onOpNpc1(npc) { agentAction() }
            onOpNpc2(npc) { agentAction() }
        }

        // 2. Butler / Demon Butler NPC (Fetch planks)
        for (npc in npcTypes.values) {
            val name = npc.name.lowercase()
            if (!name.contains("butler")) continue

            val butlerAction: suspend ProtectedAccess.() -> Unit = butlerAction@{
                val conLvl = player.stat(stats.construction)
                if (conLvl < 40) {
                    mes("You need at least Level 40 Construction to hire a Butler.")
                    return@butlerAction
                }

                val freeSlots = inv.freeSpace()
                if (freeSlots <= 0) {
                    mes("Your inventory is full.")
                    return@butlerAction
                }

                val coinsCount = inv.count { it != null && it.id == 995 }
                if (coinsCount < 500) {
                    mes("The butler requires a 500 coin fee to run to the bank.")
                    return@butlerAction
                }

                if (coinsObj != null) {
                    invDel(inv, coinsObj, 500)
                }

                // Grant oak planks (8778)
                val oakPlankObj = objTypes[8778]
                val toFetch = freeSlots.coerceAtMost(20)
                if (oakPlankObj != null) {
                    skillingRewards.grant(this, "CONSTRUCTION", oakPlankObj, toFetch)
                }
                player.statAdvance(stats.construction, 40.0)
                mes("The butler dashes to the bank and returns with $toFetch oak planks! (-500 gp)")
            }

            onOpNpc1(npc) { butlerAction() }
            onOpNpc2(npc) { butlerAction() }
        }

        // 3. Workbench (Carpentry building)
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (!name.contains("workbench") && !name.contains("work bench")) continue

            onOpLoc1(loc) {
                val hasSaw = inv.any { it != null && it.id in sawIds }
                val hasHammer = inv.any { it != null && it.id == hammerId }
                if (!hasSaw || !hasHammer) {
                    mes("You need a saw and a hammer in your inventory to build furniture.")
                    return@onOpLoc1
                }

                val conLvl = player.stat(stats.construction)
                // Find highest craftable recipe
                val availableRecipe =
                    recipes
                        .filter { conLvl >= it.levelReq }
                        .sortedByDescending { it.levelReq }
                        .firstOrNull { r ->
                            val plankType = objTypes[r.plankId] ?: return@firstOrNull false
                            val plankCount = inv.count { it != null && it.id == r.plankId }
                            val hasNails =
                                r.nailsRequired == 0 || inv.any { it != null && it.id in nailIds }
                            plankCount >= r.plankCount && hasNails
                        }

                if (availableRecipe == null) {
                    mes("You do not have the required planks or nails to construct furniture.")
                    return@onOpLoc1
                }

                if (buildAnim != null) anim(buildAnim)
                delay(2)

                val plankObj = objTypes[availableRecipe.plankId]
                if (plankObj != null) {
                    invDel(inv, plankObj, availableRecipe.plankCount)
                }
                if (availableRecipe.nailsRequired > 0) {
                    val nailItem = inv.firstOrNull { it != null && it.id in nailIds }
                    if (nailItem != null) {
                        val nailObj = objTypes[nailItem.id]
                        if (nailObj != null) {
                            invDel(inv, nailObj, availableRecipe.nailsRequired)
                        }
                    }
                }

                val multiplier = skillingRewards.xpMultiplier(player, "CONSTRUCTION")
                player.statAdvance(stats.construction, availableRecipe.xp * multiplier)
                skillingRewards.bonusDrop(this, "CONSTRUCTION")
                mes(
                    "You build a ${availableRecipe.name}! (+${(availableRecipe.xp * multiplier).toInt()} Construction XP)"
                )
            }
        }

        // 4. Altars (Prayer bone sacrifice with 350% Gilded Altar bonus)
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (!name.contains("altar")) continue
            val isGilded = name.contains("gilded")

            onOpLocU(loc) { event ->
                val boneItem = event.objType
                val baseXp = boneXp[boneItem.id]
                if (baseXp == null) {
                    mes("Nothing interesting happens.")
                    return@onOpLocU
                }

                invDel(inv, boneItem, 1)
                val prayAnim = seqTypes[896] ?: seqTypes[827]
                if (prayAnim != null) anim(prayAnim)
                delay(2)

                val multiplier = if (isGilded) 3.5 else 1.0
                val totalXp = baseXp * multiplier
                player.statAdvance(stats.prayer, totalXp)

                if (isGilded) {
                    mes(
                        "You offer the ${boneItem.name} at the Gilded Altar. The gods reward you with 350% Prayer XP! (+${totalXp.toInt()} XP)"
                    )
                } else {
                    mes("You offer the ${boneItem.name} at the altar. (+${totalXp.toInt()} XP)")
                }
            }
        }

        // 5. Rejuvenation / Ornate Pools
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (!name.contains("pool") && !name.contains("rejuvenation")) continue

            onOpLoc1(loc) {
                statRestoreAll(statTypes.values)
                player.runEnergy = 10_000
                mes(
                    "You drink from the pool. Your stats, Prayer, and Run Energy are fully restored!"
                )
            }
        }

        // 6. Mounted Amulet of Glory
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (!name.contains("glory")) continue

            onOpLoc1(loc) {
                val tpAnim = seqTypes[714] ?: seqTypes[827]
                if (tpAnim != null) anim(tpAnim)
                delay(2)
                telejump(CoordGrid(3087, 3496, 0)) // Edgeville
                mes("You rub the Mounted Amulet of Glory and teleport to Edgeville.")
            }
            onOpLoc2(loc) {
                val tpAnim = seqTypes[714] ?: seqTypes[827]
                if (tpAnim != null) anim(tpAnim)
                delay(2)
                telejump(CoordGrid(2918, 3176, 0)) // Karamja
                mes("You rub the Mounted Amulet of Glory and teleport to Karamja.")
            }
        }

        // 7. House Portals (Rimmington, Taverley, etc.)
        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            if (name != "portal") continue

            onOpLoc1(loc) {
                val conLvl = player.stat(stats.construction)
                if (conLvl < 1) {
                    mes("You must talk to an Estate Agent to purchase a house before entering.")
                    return@onOpLoc1
                }
                mes("You step through the portal into your Player-Owned House.")
            }
            onOpLoc2(loc) {
                val conLvl = player.stat(stats.construction)
                if (conLvl < 1) {
                    mes("You must talk to an Estate Agent to purchase a house before entering.")
                    return@onOpLoc2
                }
                mes("You enter your Player-Owned House in Building Mode.")
            }
        }
    }
}
