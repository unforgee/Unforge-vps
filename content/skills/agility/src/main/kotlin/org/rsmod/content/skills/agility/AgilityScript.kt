package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AgilityScript
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class AgilityObstacle(
        val nameMatch: String,
        val levelReq: Int,
        val xp: Double,
        val energyDrain: Int = 150, // Out of 10,000 max (1.5%)
        val failChance: Int = 0,
        val failDamage: Int = 0,
        val animId: Int = 762, // Default climb/jump anim
    )

    private val obstacles =
        listOf(
            // Gnome Stronghold
            AgilityObstacle("log balance", 1, 7.5, energyDrain = 150, failChance = 0, animId = 762),
            AgilityObstacle(
                "obstacle net",
                1,
                7.5,
                energyDrain = 200,
                failChance = 0,
                animId = 828,
            ),
            AgilityObstacle("tree branch", 1, 5.0, energyDrain = 150, failChance = 0, animId = 828),
            AgilityObstacle(
                "balancing rope",
                1,
                7.5,
                energyDrain = 200,
                failChance = 0,
                animId = 762,
            ),
            AgilityObstacle(
                "obstacle pipe",
                1,
                7.5,
                energyDrain = 250,
                failChance = 0,
                animId = 749,
            ),

            // Rooftop Obstacles
            AgilityObstacle(
                "rough wall",
                10,
                10.0,
                energyDrain = 200,
                failChance = 15,
                failDamage = 2,
                animId = 828,
            ),
            AgilityObstacle(
                "tightrope",
                10,
                15.0,
                energyDrain = 250,
                failChance = 18,
                failDamage = 2,
                animId = 762,
            ),
            AgilityObstacle(
                "narrow wall",
                10,
                8.0,
                energyDrain = 200,
                failChance = 12,
                failDamage = 2,
                animId = 762,
            ),
            AgilityObstacle(
                "wall run",
                10,
                8.0,
                energyDrain = 200,
                failChance = 15,
                failDamage = 2,
                animId = 762,
            ),
            AgilityObstacle(
                "clothesline",
                30,
                21.0,
                energyDrain = 300,
                failChance = 20,
                failDamage = 4,
                animId = 762,
            ),
            AgilityObstacle(
                "gap",
                20,
                18.0,
                energyDrain = 300,
                failChance = 15,
                failDamage = 3,
                animId = 762,
            ),
            AgilityObstacle(
                "cable swing",
                20,
                40.0,
                energyDrain = 350,
                failChance = 20,
                failDamage = 3,
                animId = 762,
            ),
            AgilityObstacle(
                "zip line",
                20,
                40.0,
                energyDrain = 350,
                failChance = 15,
                failDamage = 3,
                animId = 762,
            ),
            AgilityObstacle(
                "pole-vault",
                40,
                10.0,
                energyDrain = 350,
                failChance = 22,
                failDamage = 4,
                animId = 762,
            ),
            AgilityObstacle(
                "handholds",
                50,
                40.0,
                energyDrain = 350,
                failChance = 22,
                failDamage = 5,
                animId = 762,
            ),
            AgilityObstacle(
                "steep roof",
                90,
                57.0,
                energyDrain = 450,
                failChance = 20,
                failDamage = 8,
                animId = 762,
            ),

            // Shortcuts
            AgilityObstacle(
                "crumbling wall",
                5,
                2.0,
                energyDrain = 100,
                failChance = 0,
                animId = 828,
            ),
            AgilityObstacle("fence", 13, 4.0, energyDrain = 150, failChance = 0, animId = 828),
            AgilityObstacle(
                "stepping stone",
                31,
                8.0,
                energyDrain = 200,
                failChance = 10,
                failDamage = 2,
                animId = 762,
            ),
            AgilityObstacle(
                "pipe squeeze",
                70,
                20.0,
                energyDrain = 250,
                failChance = 0,
                animId = 749,
            ),
        )

    private val playerProgress = ConcurrentHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        val markOfGraceObj = objTypes[11849] // Mark of grace OSRS item id

        for (loc in locTypes.values) {
            val name = loc.name.lowercase()
            val matchedObs = obstacles.firstOrNull { name.contains(it.nameMatch) } ?: continue

            val obstacleAction: suspend ProtectedAccess.(BoundLocInfo) -> Unit =
                obstacleAction@{ locInfo ->
                    val agilityLevel = player.stat(stats.agility)
                    if (agilityLevel < matchedObs.levelReq) {
                        mes(
                            "You need an Agility level of ${matchedObs.levelReq} to tackle this ${loc.name}."
                        )
                        return@obstacleAction
                    }

                    // Graceful outfit check (up to 30% energy drain reduction)
                    var gracefulPieces = 0
                    val gracefulSlots =
                        listOf(
                            Wearpos.Hat,
                            Wearpos.Torso,
                            Wearpos.Legs,
                            Wearpos.Hands,
                            Wearpos.Feet,
                            Wearpos.Back,
                        )
                    for (slot in gracefulSlots) {
                        val worn = player.worn[slot.slot] ?: continue
                        val obj = objTypes[worn.id] ?: continue
                        if (obj.name.lowercase().contains("graceful")) {
                            gracefulPieces++
                        }
                    }
                    val drainReduction = gracefulPieces * 5
                    val drain =
                        (matchedObs.energyDrain * (100 - drainReduction) / 100).coerceAtLeast(0)

                    // Drain energy
                    player.runEnergy = max(0, player.runEnergy - drain)

                    // Failure roll
                    val failChance =
                        (matchedObs.failChance - (agilityLevel - matchedObs.levelReq) / 2).coerceIn(
                            1,
                            30,
                        )
                    val failed = matchedObs.failChance > 0 && Random.nextInt(100) < failChance

                    val animSeq = seqTypes[matchedObs.animId]
                    if (animSeq != null) anim(animSeq)
                    delay(2)

                    if (failed) {
                        statSub(stats.hitpoints, constant = matchedObs.failDamage, percent = 0)
                        mes(
                            "You lose your balance on the ${loc.name} and suffer ${matchedObs.failDamage} damage!"
                        )
                        playerProgress.remove(player)
                        delay(3)
                        return@obstacleAction
                    }

                    // Step across the obstacle
                    val dx = (locInfo.coords.x - player.coords.x).coerceIn(-1, 1)
                    val dz = (locInfo.coords.z - player.coords.z).coerceIn(-1, 1)
                    val destCoords =
                        if (dx != 0 || dz != 0) {
                            locInfo.coords.translate(dx, dz)
                        } else {
                            locInfo.coords.translate(0, 1)
                        }
                    telejump(destCoords)

                    // Advance Agility XP
                    val multiplier = skillingRewards.xpMultiplier(player, "AGILITY")
                    player.statAdvance(stats.agility, matchedObs.xp * multiplier)
                    skillingRewards.bonusDrop(this, "AGILITY")

                    val currentStep = (playerProgress[player] ?: 0) + 1
                    playerProgress[player] = currentStep

                    // Lap completion bonus (every 5 obstacles in sequence)
                    if (currentStep >= 5) {
                        playerProgress.remove(player)
                        val bonusXp = 50.0 * multiplier
                        player.statAdvance(stats.agility, bonusXp)
                        mes("You completed an agility lap! (+${bonusXp.toInt()} bonus XP)")

                        // Mark of Grace drop roll (25% chance)
                        if (markOfGraceObj != null && Random.nextInt(100) < 25) {
                            skillingRewards.grant(this, "AGILITY", markOfGraceObj, 1)
                            mes("You spot a Mark of Grace and pick it up!")
                        }
                    } else {
                        mes("You skillfully pass the ${loc.name}.")
                    }
                }

            val op1 = loc.op[0]?.lowercase() ?: ""
            val op2 = loc.op[1]?.lowercase() ?: ""

            if (op1.isNotEmpty()) {
                onOpLoc1(loc) { obstacleAction(it.loc) }
            } else if (op2.isNotEmpty()) {
                onOpLoc2(loc) { obstacleAction(it.loc) }
            }
        }
    }
}
