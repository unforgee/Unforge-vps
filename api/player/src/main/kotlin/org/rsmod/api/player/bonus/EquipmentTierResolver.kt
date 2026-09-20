package org.rsmod.api.player.bonus

import org.rsmod.api.equipment.instance.EquipmentTier
import org.rsmod.api.player.output.GearSpeedBonus
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Derives the [EquipmentTier] label for an equipment template.
 *
 * The primary signal is the item's level requirement (`levelrequire`, `statreq1_level`,
 * `statreq2_level`). Items without a requirement - common for jewellery and similar wearables -
 * fall back to a gear score computed from their combat bonuses so they still land on a sane tier.
 *
 * The score itself lives in [GearSpeedBonus] (`api:player-output`), which also consumes it for the
 * per-item attack-speed bonus; that module sits below `api:player` so both callers share one
 * implementation.
 */
public object EquipmentTierResolver {
    public fun resolve(type: UnpackedObjType): EquipmentTier = fromScore(requirementScore(type))

    /**
     * The raw tier signal for [type]: the highest of its level requirements, or a gear score
     * derived from its combat bonuses when it declares none.
     */
    public fun requirementScore(type: UnpackedObjType): Int = GearSpeedBonus.requirementScore(type)

    public fun fromScore(score: Int): EquipmentTier =
        when {
            score < 5 -> EquipmentTier.Bronze
            score < 10 -> EquipmentTier.Iron
            score < 20 -> EquipmentTier.Steel
            score < 30 -> EquipmentTier.Black
            score < 40 -> EquipmentTier.Mithril
            score < 50 -> EquipmentTier.Adamant
            score < 60 -> EquipmentTier.Rune
            score < 75 -> EquipmentTier.Dragon
            score < 90 -> EquipmentTier.Bandos
            else -> EquipmentTier.Torva
        }
}
