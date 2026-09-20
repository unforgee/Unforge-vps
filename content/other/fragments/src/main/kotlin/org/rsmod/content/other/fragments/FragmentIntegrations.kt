package org.rsmod.content.other.fragments

import jakarta.inject.Inject
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionStat
import org.rsmod.api.companion.CompanionStatAugmenter
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentRollContext
import org.rsmod.api.equipment.instance.EquipmentRollModifier

/** Feeds fragment effects into the canonical companion stat calculator and its cache. */
public class FragmentCompanionAugmenter
@Inject
constructor(private val fragments: FragmentService) : CompanionStatAugmenter {
    override fun cacheStamp(companion: Companion): String =
        "${companion.ownerCharacterId}:${fragments.state(companion.ownerCharacterId.toInt())?.revision ?: 0L}"

    override fun augment(
        companion: Companion,
        add: (stat: CompanionStat, value: Int, label: String) -> Unit,
    ) {
        val snapshot = fragments.snapshot(companion.ownerCharacterId)
        val mapping =
            mapOf(
                FragmentStat.COMPANION_DAMAGE_BPS to CompanionStat.DAMAGE_BPS,
                FragmentStat.COMPANION_DEFENCE_BPS to CompanionStat.DAMAGE_REDUCTION_BPS,
                FragmentStat.COMPANION_ATTACK_SPEED_BPS to CompanionStat.ATTACK_SPEED_BPS,
                FragmentStat.COMPANION_COOLDOWN_REDUCTION_BPS to
                    CompanionStat.COOLDOWN_REDUCTION_BPS,
                FragmentStat.COMPANION_ABILITY_POWER_BPS to CompanionStat.ABILITY_POWER_BPS,
                FragmentStat.COMPANION_ABILITY_PROC_CHANCE_BPS to CompanionStat.CRITICAL_CHANCE_BPS,
            )
        for ((fragmentStat, companionStat) in mapping) {
            val value = snapshot.value(fragmentStat)
            if (value != 0) add(companionStat, value, "Fragments: ${fragmentStat.name}")
        }
    }
}

/** Applies fragment rarity and quality effects to every canonical item-instance roll. */
public class FragmentEquipmentRollModifier
@Inject
constructor(private val fragments: FragmentService) : EquipmentRollModifier {
    override fun rarityWeightBonus(context: EquipmentRollContext, rarity: EquipmentRarity): Int {
        val snapshot = context.ownerCharacterId?.let(fragments::snapshot) ?: return 0
        val legendary = snapshot.value(FragmentStat.LEGENDARY_CHANCE_BPS) / 10
        val mythic = snapshot.value(FragmentStat.MYTHIC_CHANCE_BPS) / 10
        return when (rarity) {
            EquipmentRarity.Legendary -> legendary
            EquipmentRarity.Mythic -> mythic
            EquipmentRarity.Rare -> -(legendary / 2 + mythic / 2)
            EquipmentRarity.Uncommon -> -(legendary / 4 + mythic / 4)
            else -> 0
        }
    }

    override fun qualityBonus(context: EquipmentRollContext, rarity: EquipmentRarity): Int {
        val snapshot = context.ownerCharacterId?.let(fragments::snapshot) ?: return 0
        return snapshot.value(FragmentStat.ITEM_QUALITY_BPS) / 100
    }

    override fun additionalUniqueEffectChanceBps(
        context: EquipmentRollContext,
        rarity: EquipmentRarity,
    ): Int {
        val snapshot = context.ownerCharacterId?.let(fragments::snapshot) ?: return 0
        return snapshot.value(FragmentStat.ITEM_PROC_CHANCE_BPS)
    }
}
