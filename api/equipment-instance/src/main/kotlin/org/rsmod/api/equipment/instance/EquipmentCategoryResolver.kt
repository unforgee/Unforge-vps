package org.rsmod.api.equipment.instance

import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos

public object EquipmentCategoryResolver {
    /**
     * `true` when [type] occupies a real equipment slot.
     *
     * Stackable ammo counts - it is worn in the quiver slot and its instance-independent bonuses
     * (e.g. the gear speed bonus) apply like any other worn piece.
     */
    public fun isWearable(type: UnpackedObjType): Boolean {
        val wearpos = Wearpos[type.wearpos1] ?: return false
        return resolve(wearpos) != EquipmentCategory.Custom
    }

    public fun resolve(wearpos: Wearpos?): EquipmentCategory =
        when (wearpos) {
            Wearpos.Hat -> EquipmentCategory.Head
            Wearpos.Back -> EquipmentCategory.Back
            Wearpos.Front -> EquipmentCategory.Neck
            Wearpos.RightHand -> EquipmentCategory.RightHand
            Wearpos.Torso -> EquipmentCategory.Torso
            Wearpos.LeftHand -> EquipmentCategory.LeftHand
            Wearpos.Legs -> EquipmentCategory.Legs
            Wearpos.Hands -> EquipmentCategory.Hands
            Wearpos.Feet -> EquipmentCategory.Feet
            Wearpos.Ring -> EquipmentCategory.Ring
            Wearpos.Quiver -> EquipmentCategory.Quiver
            Wearpos.Arms,
            Wearpos.Head,
            Wearpos.Jaw,
            null -> EquipmentCategory.Custom
        }
}
