package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.type.obj.ObjType

/**
 * The prospector kit, and its golden Motherlode variant.
 *
 * The percentages match `WoodcuttingOutfit`, which is the same outfit shape one skill over. Note
 * `xpmod_percent` is a whole percent - `WornXpModifiers` divides by 100 - so OSRS' real
 * 0.4/0.8/0.6/0.2 cannot be expressed by it. These are that set of numbers times ten, exactly as
 * lumberjack already is. Do not "fix" one without the other.
 */
internal object MiningOutfit : ObjEditor() {
    init {
        outfitXpMod(mining_objs.prospector_hat, percent = 4)
        outfitXpMod(mining_objs.prospector_hat_gold, percent = 4)

        outfitXpMod(mining_objs.prospector_top, percent = 8)
        outfitXpMod(mining_objs.prospector_top_gold, percent = 8)

        outfitXpMod(mining_objs.prospector_legs, percent = 6)
        outfitXpMod(mining_objs.prospector_legs_gold, percent = 6)

        outfitXpMod(mining_objs.prospector_boots, percent = 2)
        outfitXpMod(mining_objs.prospector_boots_gold, percent = 2)
    }

    private fun outfitXpMod(type: ObjType, percent: Int) {
        edit(type) {
            param[params.xpmod_stat] = stats.mining
            param[params.xpmod_percent] = percent
        }
    }
}
