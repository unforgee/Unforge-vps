package org.rsmod.content.areas.unforge.slayer

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shared slayer-helmet variant data: every recolourable helm and its imbued counterpart.
 *
 * The `_i` variants all carry the `slayer_helm_imbued` param in the cache, so the combat formulas
 * pick them up automatically - this object only describes the item-level mapping.
 */
internal object CwSlayerHelmUpgrades {
    /** Non-imbued helm type -> its imbued counterpart. */
    val IMBUE: Map<ObjType, ObjType> =
        mapOf(
            objs.slayer_helm to objs.slayer_helm_i,
            objs.slayer_helm_black to objs.slayer_helm_i_black,
            objs.slayer_helm_green to objs.slayer_helm_i_green,
            objs.slayer_helm_red to objs.slayer_helm_i_red,
            objs.slayer_helm_purple to objs.slayer_helm_i_purple,
            objs.slayer_helm_turquoise to objs.slayer_helm_i_turquoise,
            objs.slayer_helm_hydra to objs.slayer_helm_i_hydra,
            objs.slayer_helm_twisted to objs.slayer_helm_i_twisted,
            objs.slayer_helm_jad to objs.slayer_helm_i_jad,
            objs.slayer_helm_verzik to objs.slayer_helm_i_verzik,
            objs.slayer_helm_zuk to objs.slayer_helm_i_zuk,
            objs.slayer_helm_araxyte to objs.slayer_helm_i_araxyte,
            objs.slayer_helm_hooded to objs.slayer_helm_i_hooded,
        )

    private val imbueById: Map<Int, Pair<ObjType, ObjType>> by lazy {
        IMBUE.entries.associate { (base, imbued) -> base.id to (base to imbued) }
    }

    private val imbuedIds: Set<Int> by lazy { IMBUE.values.mapTo(HashSet()) { it.id } }

    /** Every slayer helm type id, base and imbued variants alike. */
    val helmIds: Set<Int> by lazy { imbueById.keys + imbuedIds }

    /** (input helm type id, imbued result) when [typeId] is a non-imbued slayer helm. */
    fun imbueResultFor(typeId: Int): Pair<ObjType, ObjType>? = imbueById[typeId]

    /** Whether [typeId] is any imbued slayer helm variant. */
    fun isImbued(typeId: Int): Boolean = typeId in imbuedIds

    /** A recolour unlocked by a reward-shop unlock, or `null` for a free recolour. */
    data class Recolor(
        val unlock: CwSlayerUnlock?,
        val base: ObjType,
        val imbued: ObjType,
        val label: String,
    )

    /**
     * Recolour material -> result, mirroring OSRS reward-shop gates:
     * - Vorkath's head -> turquoise via "Undead head" (1000 pts)
     * - Twisted horns -> twisted via "Twisted Vision" (1000 pts)
     * - Infernal cape -> TzKal (no shop unlock; OSRS requires the TzTok helm, which has no
     *   obtainable path here since the fire cape is not in this cache revision)
     *
     * Materials for the remaining OSRS recolours (kbd/kq/abyssal heads, dark claw, hydra heads) are
     * not in this cache revision, so those unlocks have no combinable items yet.
     */
    val RECOLORS: Map<ObjType, Recolor> =
        mapOf(
            objs.vorkath_head to
                Recolor(
                    CwSlayerUnlock.UNDEAD_HEAD,
                    objs.slayer_helm_turquoise,
                    objs.slayer_helm_i_turquoise,
                    "turquoise",
                ),
            objs.twisted_horns to
                Recolor(
                    CwSlayerUnlock.TWISTED_VISION,
                    objs.slayer_helm_twisted,
                    objs.slayer_helm_i_twisted,
                    "twisted",
                ),
            objs.infernal_cape to
                Recolor(null, objs.slayer_helm_zuk, objs.slayer_helm_i_zuk, "TzKal"),
        )
}

/**
 * Slayer helmet recolours: use a boss-drop material ([CwSlayerHelmUpgrades.RECOLORS]) on any slayer
 * helm variant. Registered pairwise so the player can use either item on the other;
 * [HeldUEvents.Type.first] is always the material and [HeldUEvents.Type.second] the helm.
 *
 * Recolouring an already-recoloured helm swaps the colour; imbued helms produce the imbued recolour
 * so the imbue state is preserved, matching OSRS.
 */
class SlayerHelmRecolorScript @Inject constructor(private val objTypes: ObjTypeList) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for ((material, recolor) in CwSlayerHelmUpgrades.RECOLORS) {
            for (helmId in CwSlayerHelmUpgrades.helmIds) {
                onOpHeldU(material, objTypes.getValue(helmId)) { event ->
                    recolor(material, recolor, event)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.recolor(
        material: ObjType,
        recolor: CwSlayerHelmUpgrades.Recolor,
        event: HeldUEvents.Type,
    ) {
        val helm = event.second
        val result = if (CwSlayerHelmUpgrades.isImbued(helm.id)) recolor.imbued else recolor.base
        if (result.id == helm.id) {
            mes("Your Slayer helmet already has that colour.")
            return
        }
        if (recolor.unlock != null && !player.hasSlayerUnlock(recolor.unlock)) {
            mes("You must learn how to recolour a Slayer helmet from a Slayer master first.")
            return
        }
        player.invDel(player.inv, material, count = 1, slot = event.firstSlot)
        player.invDel(player.inv, helm, count = 1, slot = event.secondSlot)
        player.invAdd(player.inv, result, count = 1)
        mes("You recolour your Slayer helmet ${recolor.label}.")
    }
}
