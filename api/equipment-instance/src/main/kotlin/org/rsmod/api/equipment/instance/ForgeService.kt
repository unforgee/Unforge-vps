package org.rsmod.api.equipment.instance

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.SplittableRandom
import kotlin.math.abs

/** The three operations exposed by the single Forge window. */
public enum class ForgeOperation {
    UPGRADE,
    REFORGE_ALL,
    REFORGE_SELECTED,
    MYSTERY_ENCHANT,
}

/** Costs + the post-mutation instance for one Forge operation, before it is committed. */
public data class ForgePreview(
    public val operation: ForgeOperation,
    public val before: EquipmentInstance,
    public val after: EquipmentInstance,
    public val cost: ForgeCost,
    /** Affix slots whose magnitude changed (empty for [ForgeOperation.UPGRADE]). */
    public val changedSlots: List<Int>,
    /**
     * `true` when the item holds at least one high-roll affix (>=
     * [ForgeConfig.HIGH_ROLL_QUALITY_BPS]).
     */
    public val warnsHighRoll: Boolean,
)

/** Structured failure for a Forge request - surfaced verbatim to the client. */
public data class ForgeError(public val code: String, public val message: String)

public sealed interface ForgeResult {
    public data class Ok(public val preview: ForgePreview) : ForgeResult

    public data class Err(public val error: ForgeError) : ForgeResult
}

/**
 * Forge 2.0 domain service: upgrade, reforge-all and reforge-selected previews over the existing
 * [EquipmentInstance] affix model.
 *
 * The service only produces the post-state; persistence, idempotency, optimistic locking and the
 * audit log are the caller's job through [EquipmentInstanceMutationService]. By construction a
 * reforge can never change an item's rarity: the transform only rewrites affix *magnitudes* (the
 * affix identity - slot, definition, family, stat, unit, polarity - is carried over untouched)
 * inside the rarity-scaled ranges from [AffixRollService].
 *
 * All costs come from [ForgeConfig].
 */
@Singleton
public class ForgeService(
    private val catalog: List<EquipmentAffixDefinition> = EquipmentAffixCatalog.definitions
) {
    @Inject public constructor() : this(EquipmentAffixCatalog.definitions)

    private val byId = catalog.associateBy(EquipmentAffixDefinition::id)

    /** `true` when the instance can enter the Forge at all (active and not trade-locked). */
    public fun isForgeable(instance: EquipmentInstance): Boolean =
        instance.state == EquipmentInstanceState.ACTIVE &&
            instance.binding != ItemBinding.TRADE_LOCKED &&
            instance.binding != ItemBinding.DESTROYED

    /** Affix slots the Forge may reroll on this instance right now. */
    public fun rerollableSlots(instance: EquipmentInstance): Set<Int> =
        instance.affixes
            .filter { AffixRollService.isRerollable(it, byId[it.definitionId], instance) }
            .mapTo(linkedSetOf(), EquipmentAffixRoll::slot)

    /** The rarity-scaled permitted range for [affix], or `null` when it is not rerollable. */
    public fun affixRange(instance: EquipmentInstance, affix: EquipmentAffixRoll): IntRange? =
        byId[affix.definitionId]
            ?.takeIf { it.rerollable }
            ?.let { AffixRollService.magnitudeRange(it, instance.rarity, instance.tier) }

    /** Normalized roll quality in basis points, or `-1` for fixed-range affixes. */
    public fun qualityBps(instance: EquipmentInstance, affix: EquipmentAffixRoll): Int =
        AffixRollService.qualityBps(affix, byId[affix.definitionId], instance.rarity, instance.tier)

    /** `true` when any affix rolled at or above [ForgeConfig.HIGH_ROLL_QUALITY_BPS]. */
    public fun hasHighRoll(instance: EquipmentInstance): Boolean =
        instance.affixes.any { qualityBps(instance, it) >= ForgeConfig.HIGH_ROLL_QUALITY_BPS }

    // --- UPGRADE ---

    /**
     * Preview of `+N` -> `+N+1`: bumps [EquipmentInstance.upgradeLevel] and scales every affix and
     * skill-affix magnitude by [ForgeConfig.UPGRADE_AFFIX_SCALE_BPS]. Affixes are scaled, never
     * rerolled; rarity, sockets, unique effects and identity fields are untouched.
     */
    public fun previewUpgrade(instance: EquipmentInstance): ForgeResult {
        if (!isForgeable(instance)) {
            return err("not-forgeable", "This item cannot be forged in its current state.")
        }
        if (instance.upgradeLevel >= ForgeConfig.MAX_UPGRADE_LEVEL) {
            return err(
                "max-upgrade",
                "This item is already at the maximum upgrade level " +
                    "(+${ForgeConfig.MAX_UPGRADE_LEVEL}).",
            )
        }
        val after =
            instance.copy(
                upgradeLevel = instance.upgradeLevel + 1,
                affixes =
                    instance.affixes.map {
                        it.copy(
                            magnitude =
                                scaleMagnitude(it.magnitude, ForgeConfig.UPGRADE_AFFIX_SCALE_BPS)
                        )
                    },
                skillAffixes =
                    instance.skillAffixes.map {
                        it.copy(
                            magnitude =
                                scaleMagnitude(it.magnitude, ForgeConfig.UPGRADE_AFFIX_SCALE_BPS)
                        )
                    },
                reforgeHistory = (instance.reforgeHistory + "forge-upgrade").takeLast(50),
            )
        return ForgeResult.Ok(
            ForgePreview(
                ForgeOperation.UPGRADE,
                instance,
                after,
                ForgeConfig.upgradeCost(instance.upgradeLevel),
                changedSlots = emptyList(),
                warnsHighRoll = false,
            )
        )
    }

    // --- REFORGE ALL ---

    /**
     * Preview of rerolling the *value* of every rerollable affix. Unchecked semantics of Reforge
     * Selected apply here too: non-rerollable and locked affixes keep their exact magnitude.
     */
    public fun previewReforgeAll(instance: EquipmentInstance, seed: Long): ForgeResult {
        if (!isForgeable(instance)) {
            return err("not-forgeable", "This item cannot be forged in its current state.")
        }
        val slots = rerollableSlots(instance)
        if (slots.isEmpty()) {
            return err("nothing-to-reforge", "This item has no rerollable affixes.")
        }
        return reforge(instance, slots, ForgeOperation.REFORGE_ALL, seed)
    }

    // --- REFORGE SELECTED ---

    /**
     * Preview of rerolling only [selectedSlots]; every other affix is byte-for-byte unchanged. Each
     * selected slot must exist, be rerollable and unlocked - a single invalid slot rejects the
     * whole request (the client can never partially apply a targeted reforge).
     */
    public fun previewReforgeSelected(
        instance: EquipmentInstance,
        selectedSlots: Set<Int>,
        seed: Long,
    ): ForgeResult {
        if (!isForgeable(instance)) {
            return err("not-forgeable", "This item cannot be forged in its current state.")
        }
        if (selectedSlots.isEmpty()) {
            return err("empty-selection", "Select at least one affix to reforge.")
        }
        val rerollable = rerollableSlots(instance)
        for (slot in selectedSlots) {
            if (slot !in instance.affixes.indices) {
                return err("slot-not-on-item", "Affix slot $slot does not exist on this item.")
            }
            if (slot !in rerollable) {
                return err(
                    "slot-not-rerollable",
                    "Affix slot $slot is locked or cannot be rerolled.",
                )
            }
        }
        return reforge(instance, selectedSlots, ForgeOperation.REFORGE_SELECTED, seed)
    }

    /** Rolls/replaces only the independent temporary Mystery Enchant state. */
    public fun previewMysteryEnchant(instance: EquipmentInstance, seed: Long): ForgeResult {
        if (!isForgeable(instance)) {
            return err("not-forgeable", "This item cannot be forged in its current state.")
        }
        val roll = MysteryEnchantService.roll(seed)
        val after =
            MysteryEnchantService.apply(instance, roll)
                .copy(
                    reforgeHistory =
                        (instance.reforgeHistory + "forge-mystery-enchant").takeLast(50)
                )
        return ForgeResult.Ok(
            ForgePreview(
                ForgeOperation.MYSTERY_ENCHANT,
                instance,
                after,
                ForgeConfig.mysteryEnchantCost(),
                changedSlots = emptyList(),
                warnsHighRoll = instance.mysteryEnchantId != null,
            )
        )
    }

    private fun reforge(
        instance: EquipmentInstance,
        slots: Set<Int>,
        operation: ForgeOperation,
        seed: Long,
    ): ForgeResult {
        val random = SplittableRandom(seed)
        val affixes =
            instance.affixes.map { affix ->
                if (affix.slot in slots) {
                    val definition = byId.getValue(affix.definitionId)
                    affix.copy(
                        magnitude =
                            AffixRollService.rollMagnitude(
                                random,
                                definition,
                                instance.rarity,
                                instance.tier,
                            )
                    )
                } else {
                    affix
                }
            }
        val after =
            instance.copy(
                affixes = affixes,
                reforgeCount = instance.reforgeCount + 1,
                reforgeHistory =
                    (instance.reforgeHistory +
                            when (operation) {
                                ForgeOperation.REFORGE_ALL -> "forge-reforge-all"
                                ForgeOperation.REFORGE_SELECTED -> "forge-reforge-selected"
                                ForgeOperation.MYSTERY_ENCHANT -> "forge-mystery-enchant"
                                else -> operation.name
                            })
                        .takeLast(50),
            )
        val cost =
            when (operation) {
                ForgeOperation.REFORGE_ALL -> ForgeConfig.reforgeAllCost()
                ForgeOperation.REFORGE_SELECTED -> ForgeConfig.reforgeSelectedCost(slots.size)
                else -> ForgeCost(0, 0)
            }
        return ForgeResult.Ok(
            ForgePreview(
                operation,
                instance,
                after,
                cost,
                changedSlots = slots.sorted(),
                warnsHighRoll = hasHighRoll(instance),
            )
        )
    }

    private fun err(code: String, message: String): ForgeResult =
        ForgeResult.Err(ForgeError(code, message))

    private companion object {
        /**
         * Scales [value] by [bps] basis points, rounding away from zero with a minimum step of 1 so
         * every upgrade level visibly increases a non-zero magnitude.
         */
        internal fun scaleMagnitude(value: Int, bps: Int): Int {
            if (value == 0 || bps == 0) return value
            val sign = if (value < 0) -1 else 1
            val magnitude = abs(value).toLong()
            val bonus = ((magnitude * bps + 9_999L) / 10_000L).coerceAtLeast(1L)
            return (sign * (magnitude + bonus)).toInt()
        }
    }
}
