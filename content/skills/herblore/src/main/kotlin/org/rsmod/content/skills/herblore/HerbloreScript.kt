package org.rsmod.content.skills.herblore

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HerbloreScript
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class Herb(
        val grimyId: Int,
        val cleanId: Int,
        val levelReq: Int,
        val xp: Double,
        val unfPotId: Int? = null,
    )

    private val herbs =
        listOf(
            Herb(199, 249, 3, 2.5, 91), // Guam
            Herb(201, 251, 5, 3.8, 93), // Marrentill
            Herb(203, 253, 11, 5.0, 95), // Tarromin
            Herb(205, 255, 20, 6.3, 97), // Harralander
            Herb(207, 257, 25, 7.5, 99), // Ranarr weed
            Herb(3049, 2998, 30, 8.0, 3002), // Toadflax
            Herb(209, 259, 40, 8.8, 101), // Irit
            Herb(211, 261, 48, 10.0, 103), // Avantoe
            Herb(213, 263, 54, 11.3, 105), // Kwuarm
            Herb(3051, 3000, 59, 11.8, 3004), // Snapdragon
            Herb(215, 265, 65, 12.5, 107), // Cadantine
            Herb(2485, 2481, 67, 13.1, 2483), // Lantadyme
            Herb(217, 267, 70, 13.8, 109), // Dwarf weed
            Herb(219, 269, 75, 15.0, 111), // Torstol
        )

    private data class PotionRecipe(
        val unfPotId: Int,
        val secondaryId: Int,
        val finishedId: Int,
        val levelReq: Int,
        val xp: Double,
    )

    private val recipes =
        listOf(
            PotionRecipe(91, 221, 121, 3, 25.0), // Attack pot (3)
            PotionRecipe(95, 223, 115, 12, 50.0), // Strength pot (3)
            PotionRecipe(99, 231, 139, 38, 87.5), // Prayer pot (3)
            PotionRecipe(105, 225, 157, 55, 125.0), // Super str (3)
            PotionRecipe(107, 239, 163, 66, 150.0), // Super def (3)
        )

    override fun ScriptContext.startup() {
        val vialOfWater = objTypes[227]
        val mixAnim = seqTypes[363] ?: seqTypes[885]

        // Herb cleaning
        for (herb in herbs) {
            val grimyObj = objTypes[herb.grimyId] ?: continue
            val cleanObj = objTypes[herb.cleanId] ?: continue

            onOpHeld1(grimyObj) {
                val herbLevel = player.stat(stats.herblore)
                if (herbLevel < herb.levelReq) {
                    mes("You need a Herblore level of ${herb.levelReq} to clean this herb.")
                    return@onOpHeld1
                }

                val del = invDel(inv, grimyObj, 1)
                if (del.success) {
                    skillingRewards.grant(this, "HERBLORE", cleanObj)
                    player.statAdvance(stats.herblore, herb.xp)
                    mes("You clean the ${cleanObj.name.lowercase()}.")
                }
            }

            // Clean herb on vial of water -> unf pot
            if (vialOfWater != null && herb.unfPotId != null) {
                val unfPotObj = objTypes[herb.unfPotId] ?: continue
                onOpHeldU(vialOfWater, cleanObj) {
                    val herbLevel = player.stat(stats.herblore)
                    if (herbLevel < herb.levelReq) {
                        mes("You need a Herblore level of ${herb.levelReq} to brew this potion.")
                        return@onOpHeldU
                    }

                    while (invTotal(inv, vialOfWater) > 0 && invTotal(inv, cleanObj) > 0) {
                        if (mixAnim != null) anim(mixAnim)
                        delay(2)

                        val del1 = invDel(inv, vialOfWater, 1)
                        val del2 = invDel(inv, cleanObj, 1)
                        if (!del1.success || !del2.success) break

                        skillingRewards.grant(this, "HERBLORE", unfPotObj)
                        mes("You put the ${cleanObj.name.lowercase()} into the vial of water.")
                    }
                }
            }
        }

        // Secondary on unfinished pot
        for (rec in recipes) {
            val unfObj = objTypes[rec.unfPotId] ?: continue
            val secObj = objTypes[rec.secondaryId] ?: continue
            val finObj = objTypes[rec.finishedId] ?: continue

            onOpHeldU(secObj, unfObj) {
                val herbLevel = player.stat(stats.herblore)
                if (herbLevel < rec.levelReq) {
                    mes("You need a Herblore level of ${rec.levelReq} to complete this potion.")
                    return@onOpHeldU
                }

                while (invTotal(inv, secObj) > 0 && invTotal(inv, unfObj) > 0) {
                    if (mixAnim != null) anim(mixAnim)
                    delay(2)

                    val del2 = invDel(inv, unfObj, 1)
                    if (!del2.success) break
                    // Worn skilling gear can preserve the secondary ingredient entirely.
                    if (
                        !skillingRewards.rollChance(this, "HERBLORE", SkillingRewards.HERBLORE_SAVE)
                    ) {
                        val del1 = invDel(inv, secObj, 1)
                        if (!del1.success) break
                    }

                    skillingRewards.grant(this, "HERBLORE", finObj)
                    player.statAdvance(stats.herblore, rec.xp)
                    mes("You combine the ingredients to make a ${finObj.name.lowercase()}.")
                }
            }
        }
    }
}
