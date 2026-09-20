package org.rsmod.content.skills.herblore.configs

/** A player stat a potion can move. */
enum class PotionStat {
    ATTACK,
    STRENGTH,
    DEFENCE,
    RANGED,
    MAGIC,
    PRAYER,
    HITPOINTS,
    AGILITY,
    FISHING,
    HUNTER,
}

/** One stat operation a dose applies, in the order the dose applies them. */
sealed interface PotionEffect {
    /** Raises the stat by `percent%` of base plus [constant], never past `base + that`. */
    data class Boost(val stat: PotionStat, val constant: Int, val percent: Int) : PotionEffect

    /** Restores a lowered stat back towards base by `percent%` of base plus [constant]. */
    data class Restore(val stat: PotionStat, val constant: Int, val percent: Int) : PotionEffect

    /** Lowers the stat by `percent%` of base plus [constant], never below `base - that`. */
    data class Drain(val stat: PotionStat, val constant: Int, val percent: Int) : PotionEffect

    /** Restores every stat a restore potion covers by `percent%` of base plus [constant]. */
    data class RestoreAll(val constant: Int, val percent: Int) : PotionEffect

    /** Adds [percent] percentage points of run energy. */
    data class RunEnergy(val percent: Int) : PotionEffect

    data object CurePoison : PotionEffect
}

/**
 * @param effects what one dose does.
 * @param next the obj this dose leaves behind - the next dose down, or the empty container when
 *   this was the last one.
 */
data class Potion(val effects: List<PotionEffect>, val next: Int)

private fun boost(stat: PotionStat, constant: Int, percent: Int): PotionEffect =
    PotionEffect.Boost(stat, constant, percent)

private fun restore(stat: PotionStat, constant: Int, percent: Int): PotionEffect =
    PotionEffect.Restore(stat, constant, percent)

private fun drain(stat: PotionStat, constant: Int, percent: Int): PotionEffect =
    PotionEffect.Drain(stat, constant, percent)

/**
 * The drinking table: obj id -> what that dose does and what it leaves behind.
 *
 * Values are the live OSRS ones (ported from the client's item-stat data, which is the same table
 * the wiki documents). Dose variants are authored as a chain - the four-dose obj hands back the
 * three-dose one, and the one-dose obj hands back [VIAL] - so every dose of every potion is listed
 * and no dose can silently do nothing.
 *
 * Potions whose effect has no backing system on this server (antifire, overload, absorption, ...)
 * are deliberately absent rather than faked: they keep the engine's default "not implemented"
 * behaviour, and [DrinkPotionScript][org.rsmod.content.skills.herblore.DrinkPotionScript] logs them
 * at startup.
 */
object PotionTable {
    /** The empty vial every standard potion leaves behind on its last dose. */
    const val VIAL: Int = 229

    /** Guthix rest comes in a cup rather than a vial. */
    private const val CUP: Int = 1980

    private val attackPotion = listOf(boost(PotionStat.ATTACK, 3, 10))
    private val strengthPotion = listOf(boost(PotionStat.STRENGTH, 3, 10))
    private val defencePotion = listOf(boost(PotionStat.DEFENCE, 3, 10))
    private val superAttackPotion = listOf(boost(PotionStat.ATTACK, 5, 15))
    private val superStrengthPotion = listOf(boost(PotionStat.STRENGTH, 5, 15))
    private val superDefencePotion = listOf(boost(PotionStat.DEFENCE, 5, 15))
    private val rangePotion = listOf(boost(PotionStat.RANGED, 4, 10))

    private val entries: Map<Int, Potion> = buildMap {
        doses(attackPotion, 2428, 121, 123, 125)
        doses(strengthPotion, 113, 115, 117, 119)
        doses(defencePotion, 2432, 133, 135, 137)
        doses(superAttackPotion, 2436, 145, 147, 149)
        doses(superStrengthPotion, 2440, 157, 159, 161)
        doses(superDefencePotion, 2442, 163, 165, 167)
        doses(
            listOf(boost(PotionStat.ATTACK, 3, 10), boost(PotionStat.STRENGTH, 3, 10)),
            9739,
            9741,
            9743,
            9745,
        )
        doses(
            listOf(
                boost(PotionStat.ATTACK, 5, 15),
                boost(PotionStat.STRENGTH, 5, 15),
                boost(PotionStat.DEFENCE, 5, 15),
            ),
            12695,
            12697,
            12699,
            12701,
        )
        doses(rangePotion, 2444, 169, 171, 173)
        doses(listOf(boost(PotionStat.MAGIC, 4, 0)), 3040, 3042, 3044, 3046)
        doses(listOf(boost(PotionStat.MAGIC, 3, 0)), 9021, 9022, 9023, 9024)
        doses(listOf(boost(PotionStat.AGILITY, 3, 0)), 3032, 3034, 3036, 3038)
        doses(listOf(boost(PotionStat.FISHING, 3, 0)), 2438, 151, 153, 155)
        doses(listOf(boost(PotionStat.HUNTER, 3, 0)), 9998, 10000, 10002, 10004)

        doses(listOf(restore(PotionStat.PRAYER, 7, 25)), 2434, 139, 141, 143)
        doses(
            listOf(
                restore(PotionStat.ATTACK, 10, 30),
                restore(PotionStat.STRENGTH, 10, 30),
                restore(PotionStat.DEFENCE, 10, 30),
                restore(PotionStat.RANGED, 10, 30),
                restore(PotionStat.MAGIC, 10, 30),
            ),
            2430,
            127,
            129,
            131,
        )
        doses(
            listOf(PotionEffect.RestoreAll(8, 25), restore(PotionStat.PRAYER, 8, 25)),
            3024,
            3026,
            3028,
            3030,
        )
        doses(
            listOf(
                PotionEffect.RestoreAll(4, 30),
                restore(PotionStat.PRAYER, 4, 30),
                PotionEffect.CurePoison,
            ),
            10925,
            10927,
            10929,
            10931,
        )

        doses(listOf(PotionEffect.RunEnergy(10)), 3008, 3010, 3012, 3014)
        doses(listOf(PotionEffect.RunEnergy(20)), 3016, 3018, 3020, 3022)
        doses(listOf(PotionEffect.RunEnergy(20)), 12625, 12627, 12629, 12631)

        doses(
            listOf(
                boost(PotionStat.ATTACK, 2, 20),
                boost(PotionStat.STRENGTH, 2, 12),
                restore(PotionStat.PRAYER, 0, 10),
                drain(PotionStat.DEFENCE, 2, 10),
                drain(PotionStat.HITPOINTS, 0, 12),
            ),
            2450,
            189,
            191,
            193,
        )
        doses(
            listOf(
                boost(PotionStat.HITPOINTS, 2, 15),
                boost(PotionStat.DEFENCE, 2, 20),
                drain(PotionStat.ATTACK, 2, 10),
                drain(PotionStat.STRENGTH, 2, 10),
                drain(PotionStat.RANGED, 2, 10),
                drain(PotionStat.MAGIC, 2, 10),
            ),
            6685,
            6687,
            6689,
            6691,
        )
        doses(listOf(PotionEffect.CurePoison), 2446, 175, 177, 179)
        doses(listOf(PotionEffect.CurePoison), 2448, 181, 183, 185)

        doses(
            listOf(
                boost(PotionStat.HITPOINTS, 5, 0),
                PotionEffect.RunEnergy(5),
                PotionEffect.CurePoison,
            ),
            4417,
            4419,
            4421,
            4423,
            container = CUP,
        )
    }

    val ids: Set<Int> = entries.keys

    val all: Map<Int, Potion> = entries

    operator fun get(obj: Int): Potion? = entries[obj]

    /**
     * Registers a dose chain. [ids] run highest dose first; each one hands back the next, and the
     * last hands back [container].
     */
    private fun MutableMap<Int, Potion>.doses(
        effects: List<PotionEffect>,
        vararg ids: Int,
        container: Int = VIAL,
    ) {
        for ((index, id) in ids.withIndex()) {
            val next = ids.getOrElse(index + 1) { container }
            put(id, Potion(effects, next))
        }
    }
}
