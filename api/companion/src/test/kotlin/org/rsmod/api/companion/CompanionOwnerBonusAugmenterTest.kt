package org.rsmod.api.companion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.bonus.WornBonuses

/**
 * Verifies [CompanionOwnerBonusAugmenter.merge]: the owner's equipment bonuses are merged into the
 * companion bot's resolved bonuses strictly by attack type, so melee/ranged/magic bonuses can never
 * leak into a companion of the wrong style.
 */
public class CompanionOwnerBonusAugmenterTest {
    private fun bonuses(
        offStab: Int = 0,
        offSlash: Int = 0,
        offCrush: Int = 0,
        offMagic: Int = 0,
        offRange: Int = 0,
        defStab: Int = 0,
        defSlash: Int = 0,
        defCrush: Int = 0,
        defRange: Int = 0,
        defMagic: Int = 0,
        meleeStr: Int = 0,
        rangedStr: Int = 0,
        magicDmg: Int = 0,
        prayer: Int = 0,
    ): WornBonuses.Bonuses =
        WornBonuses.Bonuses(
            offStab = offStab,
            offSlash = offSlash,
            offCrush = offCrush,
            offMagic = offMagic,
            offRange = offRange,
            defStab = defStab,
            defSlash = defSlash,
            defCrush = defCrush,
            defRange = defRange,
            defMagic = defMagic,
            meleeStr = meleeStr,
            rangedStr = rangedStr,
            magicDmg = magicDmg,
            prayer = prayer,
            undead = 0,
            slayer = 0,
            magicDmgAdditive = 0,
            magicDmgMultiplier = 10,
            undeadMeleeOnly = false,
            slayerMeleeOnly = false,
        )

    private val owner =
        bonuses(
            offStab = 10,
            offSlash = 11,
            offCrush = 12,
            offMagic = 20,
            offRange = 30,
            defStab = 40,
            defSlash = 41,
            defCrush = 42,
            defRange = 43,
            defMagic = 44,
            meleeStr = 15,
            rangedStr = 25,
            magicDmg = 8,
            prayer = 6,
        )

    @Test
    public fun `melee companion gains owner melee attack and strength bonuses`() {
        val own = bonuses(meleeStr = 5, offStab = 2)
        val merged = CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, own, owner)
        assertEquals(12, merged.offStab)
        assertEquals(11, merged.offSlash)
        assertEquals(12, merged.offCrush)
        assertEquals(20, merged.meleeStr)
    }

    @Test
    public fun `ranged companion gains owner ranged attack and strength bonuses`() {
        val merged =
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.RANGED, bonuses(), owner)
        assertEquals(30, merged.offRange)
        assertEquals(25, merged.rangedStr)
    }

    @Test
    public fun `magic companion gains owner magic attack and damage bonuses`() {
        val merged =
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MAGIC, bonuses(), owner)
        assertEquals(20, merged.offMagic)
        assertEquals(8, merged.magicDmg)
    }

    @Test
    public fun `wrong-type bonuses never leak into the companion`() {
        val melee = CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, bonuses(), owner)
        assertEquals(0, melee.offRange)
        assertEquals(0, melee.rangedStr)
        assertEquals(0, melee.offMagic)
        assertEquals(0, melee.magicDmg)

        val ranged =
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.RANGED, bonuses(), owner)
        assertEquals(0, ranged.meleeStr)
        assertEquals(0, ranged.offStab)
        assertEquals(0, ranged.offMagic)

        val magic = CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MAGIC, bonuses(), owner)
        assertEquals(0, magic.meleeStr)
        assertEquals(0, magic.offStab)
        assertEquals(0, magic.rangedStr)
    }

    @Test
    public fun `defence prayer and utility bonuses are never inherited`() {
        val merged =
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, bonuses(), owner)
        assertEquals(0, merged.defStab)
        assertEquals(0, merged.defSlash)
        assertEquals(0, merged.defCrush)
        assertEquals(0, merged.defRange)
        assertEquals(0, merged.defMagic)
        assertEquals(0, merged.prayer)
    }

    @Test
    public fun `companion gear bonuses stack with owner armor without double counting`() {
        val own = bonuses(meleeStr = 5, offStab = 3, defStab = 9)
        val merged = CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, own, owner)
        // Own values are preserved and owner values are added exactly once.
        assertEquals(13, merged.offStab)
        assertEquals(20, merged.meleeStr)
        assertEquals(9, merged.defStab)
    }

    @Test
    public fun `merge is recomputed from live inputs so equipment swaps take effect`() {
        val own = bonuses(meleeStr = 5)
        val firstArmor = bonuses(meleeStr = 15)
        val upgradedArmor = bonuses(meleeStr = 40)
        assertEquals(
            20,
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, own, firstArmor).meleeStr,
        )
        assertEquals(
            45,
            CompanionOwnerBonusAugmenter.merge(CompanionAttackStyle.MELEE, own, upgradedArmor)
                .meleeStr,
        )
    }
}
