package org.rsmod.content.skills.prayer

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrayerBurying
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val synthTypes: SynthTypeList,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class Bone(val id: Int, val xp: Double)

    private val bones =
        listOf(
            Bone(526, 4.5), // Bones
            Bone(528, 4.5), // Burnt bones
            Bone(2859, 4.5), // Wolf bones
            Bone(3183, 5.0), // Monkey bones
            Bone(530, 5.3), // Bat bones
            Bone(532, 15.0), // Big bones
            Bone(3125, 15.0), // Jogre bones
            Bone(4812, 22.5), // Zogre bones
            Bone(3123, 25.0), // Shaikahan bones
            Bone(534, 30.0), // Baby dragon bones
            Bone(22786, 50.0), // Wyrm bones
            Bone(536, 72.0), // Dragon bones
            Bone(6812, 72.0), // Wyvern bones
            Bone(22783, 80.0), // Drake bones
            Bone(4830, 84.0), // Fayrg bones
            Bone(11943, 85.0), // Lava dragon bones
            Bone(4832, 96.0), // Raurg bones
            Bone(22780, 110.0), // Hydra bones
            Bone(6729, 125.0), // Dagannoth bones
            Bone(4834, 140.0), // Ourg bones
            Bone(22124, 150.0), // Superior dragon bones
        )

    override fun ScriptContext.startup() {
        val buryAnim = seqTypes[827]
        val burySynth = synthTypes[2738]

        for (bone in bones) {
            val type = objTypes[bone.id] ?: continue
            onOpHeld1(type) {
                val deleted = invDel(inv, type, 1)
                if (deleted.success) {
                    if (buryAnim != null) anim(buryAnim)
                    if (burySynth != null) soundSynth(burySynth)
                    mes("You bury the bones.")
                    player.statAdvance(
                        stats.prayer,
                        bone.xp * skillingRewards.xpMultiplier(player, "PRAYER"),
                    )
                    skillingRewards.bonusDrop(this, "PRAYER")
                    delay(2)
                }
            }
        }
    }
}
