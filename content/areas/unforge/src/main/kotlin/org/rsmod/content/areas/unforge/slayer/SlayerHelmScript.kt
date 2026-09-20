package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Slayer helmet assembly: using any two helmet components on each other crafts a [objs.slayer_helm]
 * once the player carries the full set and has bought the [CwSlayerUnlock.MALEVOLENT_MASQUERADE]
 * reward-shop unlock (the "Malevolent masquerade" 400-point unlock, matching OSRS).
 *
 * The combat bonus itself is not re-implemented here: `SlayerTaskProviders` plus the
 * `slayer_helm`/`blackmask` cache params already feed the melee/ranged/magic formulas, so this
 * script only adds the missing item-crafting half.
 */
class SlayerHelmScript @Inject constructor(private val objTypes: ObjTypeList) : PluginScript() {

    override fun ScriptContext.startup() {
        for (part in PARTS) {
            onOpHeldU(part) { event ->
                // Only react when the target is also a helmet component; using a part on any
                // unrelated item falls through untouched.
                if (event.second.id in PART_IDS) {
                    combine()
                } else if (part.id == objs.enchanted_gem.id) {
                    tryImbue(event.second.id, event.secondSlot)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.combine() {
        if (!player.hasSlayerUnlock(CwSlayerUnlock.MALEVOLENT_MASQUERADE)) {
            mes("You must learn how to craft Slayer helmets from a Slayer master first.")
            return
        }
        val missing = PARTS.firstOrNull { player.inv.count(objTypes[it]) == 0 }
        if (missing != null) {
            mes(MISSING_MESSAGE)
            return
        }
        for (part in PARTS) {
            player.invDel(player.inv, part, count = 1)
        }
        player.invAdd(player.inv, HELM, count = 1)
        mes("You piece the items together and form a Slayer helmet.")
    }

    /**
     * Imbue: use the enchanted gem on any non-imbued slayer helm to charge [IMBUE_COST] slayer
     * reward points and swap it for its `_i` variant. Mirrors the NMZ imbue (per-helm point cost)
     * since this server has no Nightmare Zone currency. The gem itself is a reusable slayer tool
     * and is not consumed.
     */
    private suspend fun ProtectedAccess.tryImbue(helmTypeId: Int, helmSlot: Int) {
        val (input, result) = CwSlayerHelmUpgrades.imbueResultFor(helmTypeId) ?: return
        val points = player.vars[UnforgeSlayerVarps.points]
        if (points < IMBUE_COST) {
            mes("You need $IMBUE_COST Slayer reward points to imbue your Slayer helmet.")
            return
        }
        cwSetVarp(player, UnforgeSlayerVarps.points, points - IMBUE_COST)
        player.invDel(player.inv, input, count = 1, slot = helmSlot)
        player.invAdd(player.inv, result, count = 1)
        mes("You imbue your Slayer helmet.")
    }

    private companion object {
        private const val IMBUE_COST = 250

        private val HELM: ObjType = objs.slayer_helm
        private val PARTS: List<ObjType> =
            listOf(
                objs.black_mask,
                objs.earmuffs,
                objs.facemask,
                objs.nose_peg,
                objs.spiny_helmet,
                objs.enchanted_gem,
            )
        private val PART_IDS: Set<Int> = PARTS.mapTo(HashSet()) { it.id }

        private const val MISSING_MESSAGE =
            "You need a black mask, earmuffs, a facemask, a nose peg, a spiny helmet and an " +
                "enchanted gem to craft a Slayer helmet."
    }
}
