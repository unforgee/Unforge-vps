package org.rsmod.content.skills.cooking.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.skills.cooking.configs.FoodTable
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.synth.SynthType
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Eating: the `Eat` op restores hitpoints, consumes one of the obj and hands back the next stage
 * for multi-bite food.
 *
 * Every obj in the cache's `food` content group is handled, so all food is eatable - the heal it
 * restores comes from [FoodTable] and falls back to [FoodTable.DEFAULT_HEAL] for objs the table
 * does not describe.
 */
class EatFoodScript
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val synthTypes: SynthTypeList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val eatAnim = seqTypes[EAT_ANIM]
        val eatSynth = synthTypes[EAT_SYNTH]

        onOpHeld1(content.food) { eat(it.type, it.slot, eatAnim, eatSynth) }

        // The content group is what makes this cover every food; registering the objs the table
        // describes as well keeps the server's own food working even if one is missing the tag.
        for (id in FoodTable.ids) {
            val type = objTypes[id] ?: continue
            onOpHeld1(type) { eat(type, it.slot, eatAnim, eatSynth) }
        }

        warnOnUncoveredFood()
    }

    private suspend fun ProtectedAccess.eat(
        type: UnpackedObjType,
        slot: Int,
        eatAnim: SeqType?,
        eatSynth: SynthType?,
    ) {
        val food = FoodTable[type.id]
        val heal = food?.heal ?: FoodTable.DEFAULT_HEAL

        val deleted = invDel(inv, type, count = 1, slot = slot)
        if (!deleted.success) {
            return
        }

        val next = food?.next
        if (next != null) {
            val nextType = objTypes[next]
            if (nextType != null) {
                invAdd(inv, nextType, count = 1, slot = slot)
            }
        }

        if (eatAnim != null) {
            anim(eatAnim)
        }
        if (eatSynth != null) {
            soundSynth(eatSynth)
        }
        mes("You eat the ${type.lowercaseName}.")
        player.statHeal(stats.hitpoints, heal, 0)
        delay(EAT_DELAY)
    }

    /** Flags food that would eat for [FoodTable.DEFAULT_HEAL] instead of its real value. */
    private fun warnOnUncoveredFood() {
        val food = objTypes.values.filter { it.isContentType(content.food) }
        val missing = food.filter { it.id !in FoodTable.ids }
        if (missing.isEmpty()) {
            return
        }
        logger.warn {
            "Food objs with no heal value in FoodTable ${missing.size}/${food.size}: " +
                missing.take(MAX_LOGGED_MISSING).joinToString { it.name }
        }
    }

    private companion object {
        private const val EAT_ANIM: Int = 829 // human_eat
        private const val EAT_SYNTH: Int = 2393 // eat
        private const val EAT_DELAY: Int = 3
        private const val MAX_LOGGED_MISSING: Int = 25
        private val logger = InlineLogger()
    }
}
