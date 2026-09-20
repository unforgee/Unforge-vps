package org.rsmod.api.equipment.instance

/**
 * Centralized Forge 2.0 balance configuration.
 *
 * Every cost and tuning knob the Forge UI displays and the server enforces lives here - nothing is
 * hardcoded in the operation handlers, so balancing the forge is a one-file change.
 *
 * Currency: the existing Forge ticket item (obj [TICKET_OBJ_ID], the same currency NPC drops and
 * the legacy `::itemupgrade` command consume) is the primary resource; coins are the secondary gold
 * fee. Both are item-based and are charged from the player's inventory.
 */
public object ForgeConfig {
    /** Forge ticket currency item (`village_trade_sticks`, dropped by npcs via `NpcDeath`). */
    public const val TICKET_OBJ_ID: Int = 6306

    /** Gold currency item (coins). */
    public const val GOLD_OBJ_ID: Int = 995

    /** Highest reachable upgrade level (`+0` .. `+10`). */
    public const val MAX_UPGRADE_LEVEL: Int = 10

    /**
     * Per-upgrade affix magnitude scaling in basis points (1000 = +10% per level). Affixes are
     * never rerolled by upgrading - only scaled by this explicitly configured multiplier.
     */
    public const val UPGRADE_AFFIX_SCALE_BPS: Int = 1_000

    /** Forge ticket cost of upgrading from [level] to `level + 1`. */
    public fun upgradeTickets(level: Int): Int = 1 + level

    /** Gold fee of upgrading from [level] to `level + 1`. */
    public fun upgradeGold(level: Int): Int = level * 50_000

    /** Forge ticket cost of a Reforge All. */
    public const val REFORGE_ALL_TICKETS: Int = 1

    /** Gold fee of a Reforge All. */
    public const val REFORGE_ALL_GOLD: Int = 250_000

    /**
     * Reforge Selected ticket formula: `base + count * perAffix`. With the defaults that is 2
     * tickets for 1 affix, 3 for 2, 4 for 3 - the premium over Reforge All.
     */
    public const val REFORGE_SELECTED_BASE_TICKETS: Int = 1

    public const val REFORGE_SELECTED_TICKETS_PER_AFFIX: Int = 1

    /** Gold fee per selected affix for a targeted reforge. */
    public const val REFORGE_SELECTED_GOLD_PER_AFFIX: Int = 250_000

    /** Mystery Enchant uses the same ticket currency as every other Forge operation. */
    public const val MYSTERY_ENCHANT_TICKETS: Int = MysteryEnchantConfig.TICKET_COST

    /**
     * Roll-quality threshold (basis points of the possible range) at or above which an affix is
     * considered a high roll; Reforge All surfaces a one-time confirmation when any are present.
     */
    public const val HIGH_ROLL_QUALITY_BPS: Int = 9_000

    public fun upgradeCost(currentLevel: Int): ForgeCost =
        ForgeCost(upgradeTickets(currentLevel), upgradeGold(currentLevel))

    public fun reforgeAllCost(): ForgeCost = ForgeCost(REFORGE_ALL_TICKETS, REFORGE_ALL_GOLD)

    public fun reforgeSelectedCost(selectedAffixCount: Int): ForgeCost =
        ForgeCost(
            REFORGE_SELECTED_BASE_TICKETS + selectedAffixCount * REFORGE_SELECTED_TICKETS_PER_AFFIX,
            selectedAffixCount * REFORGE_SELECTED_GOLD_PER_AFFIX,
        )

    public fun mysteryEnchantCost(): ForgeCost = ForgeCost(MYSTERY_ENCHANT_TICKETS, 0)
}

/** A Forge operation cost in the two supported currencies. */
public data class ForgeCost(public val tickets: Int, public val gold: Int) {
    init {
        require(tickets >= 0 && gold >= 0)
    }
}
