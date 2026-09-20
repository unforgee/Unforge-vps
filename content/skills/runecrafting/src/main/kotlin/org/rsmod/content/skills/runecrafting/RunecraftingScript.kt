package org.rsmod.content.skills.runecrafting

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RunecraftingScript
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val synthTypes: SynthTypeList,
    private val perks: PerkService,
    private val skillingRewards: SkillingRewards,
) : PluginScript() {

    private data class Altar(
        val altarLocId: Int,
        val runeId: Int,
        val levelReq: Int,
        val xp: Double,
        val multStep: Int? = null,
    )

    private val altars =
        listOf(
            Altar(14897, 556, 1, 5.0, 11), // Air
            Altar(14898, 558, 2, 5.5, 14), // Mind
            Altar(14899, 555, 5, 6.0, 19), // Water
            Altar(14900, 557, 9, 6.5, 26), // Earth
            Altar(14901, 554, 14, 7.0, 35), // Fire
            Altar(14902, 559, 20, 7.5, 46), // Body
            Altar(14903, 564, 27, 8.0, 59), // Cosmic
            Altar(14906, 562, 35, 8.5, 74), // Chaos
            Altar(14911, 9075, 40, 8.7, 82), // Astral
            Altar(14905, 561, 44, 9.0, 91), // Nature
            Altar(14904, 563, 54, 9.5), // Law
            Altar(14907, 560, 65, 10.0, 99), // Death
            Altar(27978, 565, 77, 10.5), // Blood
            Altar(34772, 21880, 95, 8.0), // Wrath
        )

    override fun ScriptContext.startup() {
        val pureEss = objTypes[7936]
        val runeEss = objTypes[1436]
        val craftAnim = seqTypes[791]
        val craftSynth = synthTypes[2710]

        for (altar in altars) {
            val loc = locTypes[altar.altarLocId] ?: continue
            val runeObj = objTypes[altar.runeId] ?: continue

            val craftAction: suspend ProtectedAccess.() -> Unit = craftAction@{
                val rcLevel = player.stat(stats.runecrafting)
                if (rcLevel < altar.levelReq) {
                    mes("You need a Runecraft level of ${altar.levelReq} to bind these runes.")
                    return@craftAction
                }

                val pureCount = if (pureEss != null) invTotal(inv, pureEss) else 0
                val runeCount =
                    if (runeEss != null && altar.levelReq <= 20) invTotal(inv, runeEss) else 0
                val totalEss = pureCount + runeCount

                if (totalEss <= 0) {
                    mes("You do not have any rune essence to bind.")
                    return@craftAction
                }

                if (runeCount > 0 && runeEss != null) {
                    invDel(inv, runeEss, runeCount)
                }
                if (pureCount > 0 && pureEss != null) {
                    invDel(inv, pureEss, pureCount)
                }

                val mult = if (altar.multStep != null) (rcLevel / altar.multStep) + 1 else 1
                var totalRunes = totalEss * mult
                // Runic Mastery perk: chance of double runes.
                if (Random.nextInt(10_000) < perks.doubleRunesBps(player)) {
                    totalRunes *= 2
                }

                if (craftAnim != null) anim(craftAnim)
                if (craftSynth != null) soundSynth(craftSynth)
                delay(2)

                skillingRewards.grant(this, "RUNECRAFTING", runeObj, totalRunes)
                player.statAdvance(stats.runecrafting, altar.xp * totalEss)
                mes("You bind the temple's power into ${runeObj.name.lowercase()}s.")
            }

            onOpLoc1(loc) { craftAction() }
            if (pureEss != null) onOpLocU(loc, pureEss) { craftAction() }
            if (runeEss != null) onOpLocU(loc, runeEss) { craftAction() }
        }
    }
}
