package org.rsmod.api.companion

/**
 * The single canonical incoming-damage pipeline for companions. Ordering is fixed and matches the
 * UI breakdown:
 * 1. style resistance (`AbsorbMelee`/`AbsorbRanged`/`AbsorbMagic` affixes and talents),
 * 2. flat [CompanionStat.DAMAGE_REDUCTION_BPS],
 * 3. block roll - a successful block halves the post-resistance damage.
 *
 * The function is pure: callers supply [blockRoll] (typically `random.of(10_000)`) so the roll is
 * testable and deterministic in game tests.
 */
public object CompanionMitigation {
    /** Damage divisor applied when the block roll succeeds. */
    public const val BLOCKED_DAMAGE_DIVISOR: Int = 2

    /**
     * Resolves the final damage a companion takes from a hit of [rawDamage].
     *
     * @param attackStyle raw `npc_attack_style` param of the attacker (`0`=melee, `1`=ranged,
     *   `2`=magic); negative/unknown values apply no style resistance.
     * @param blockRoll roll in `0 until 10_000`; a value below the sheet's block chance blocks.
     */
    public fun resolve(
        rawDamage: Int,
        sheet: CompanionStatSheet,
        attackStyle: Int,
        blockRoll: Int,
    ): Int {
        require(blockRoll in 0 until 10_000) { "blockRoll must be within 0 until 10_000" }
        var damage = rawDamage.coerceAtLeast(0)
        damage = CompanionStatCaps.applyDamageReduction(damage, sheet.resistanceBpsFor(attackStyle))
        damage = sheet.reducedDamage(damage)
        if (blockRoll < sheet.blockChanceBps) {
            damage /= BLOCKED_DAMAGE_DIVISOR
        }
        return damage
    }
}
