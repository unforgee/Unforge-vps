package org.rsmod.content.skills.fletching.configs

import org.rsmod.game.type.obj.ObjType

/**
 * Carving a log: what one log becomes, and what it costs to learn.
 *
 * @param produced how many items one log yields - arrow shafts give fifteen, bows give one.
 */
data class LogCut(
    val product: ObjType,
    val label: String,
    val level: Int,
    val xp: Double,
    val produced: Int = 1,
)

/**
 * The knife table, grouped by the log it is used on.
 *
 * Ordered by level within a log, which is the order the menu lists them in. Levels are the game's
 * own: shafts at 1, a shortbow five levels before the matching longbow.
 */
object LogCuts {
    val byLog: Map<Int, List<LogCut>>
        get() =
            mapOf(
                fletching_objs.logs.id to
                    listOf(
                        LogCut(fletching_objs.arrow_shaft, "Arrow shaft", 1, 5.0, produced = 15),
                        LogCut(fletching_objs.unstrung_shortbow, "Shortbow (u)", 5, 5.0),
                        LogCut(fletching_objs.unstrung_longbow, "Longbow (u)", 10, 10.0),
                    ),
                fletching_objs.oak_logs.id to
                    listOf(
                        LogCut(fletching_objs.unstrung_oak_shortbow, "Oak shortbow (u)", 20, 16.5),
                        LogCut(fletching_objs.unstrung_oak_longbow, "Oak longbow (u)", 25, 25.0),
                    ),
                fletching_objs.willow_logs.id to
                    listOf(
                        LogCut(
                            fletching_objs.unstrung_willow_shortbow,
                            "Willow shortbow (u)",
                            35,
                            33.3,
                        ),
                        LogCut(
                            fletching_objs.unstrung_willow_longbow,
                            "Willow longbow (u)",
                            40,
                            41.5,
                        ),
                    ),
                fletching_objs.maple_logs.id to
                    listOf(
                        LogCut(
                            fletching_objs.unstrung_maple_shortbow,
                            "Maple shortbow (u)",
                            50,
                            50.0,
                        ),
                        LogCut(fletching_objs.unstrung_maple_longbow, "Maple longbow (u)", 55, 58.3),
                    ),
                fletching_objs.yew_logs.id to
                    listOf(
                        LogCut(fletching_objs.unstrung_yew_shortbow, "Yew shortbow (u)", 65, 67.5),
                        LogCut(fletching_objs.unstrung_yew_longbow, "Yew longbow (u)", 70, 75.0),
                    ),
                fletching_objs.magic_logs.id to
                    listOf(
                        LogCut(
                            fletching_objs.unstrung_magic_shortbow,
                            "Magic shortbow (u)",
                            80,
                            83.3,
                        ),
                        LogCut(fletching_objs.unstrung_magic_longbow, "Magic longbow (u)", 85, 91.5),
                    ),
            )

    /** Every log the knife has a table for, which is what the knife is bound against. */
    val logs: Map<Int, ObjType>
        get() =
            mapOf(
                fletching_objs.logs.id to fletching_objs.logs,
                fletching_objs.oak_logs.id to fletching_objs.oak_logs,
                fletching_objs.willow_logs.id to fletching_objs.willow_logs,
                fletching_objs.maple_logs.id to fletching_objs.maple_logs,
                fletching_objs.yew_logs.id to fletching_objs.yew_logs,
                fletching_objs.magic_logs.id to fletching_objs.magic_logs,
            )

    fun cutsFor(logId: Int): List<LogCut> = byLog[logId].orEmpty()
}

/**
 * Stringing a bow: unstrung bow plus one bow string.
 *
 * @param unstrung what the string is used on. Never produced by this table itself - stringing is a
 *   separate step from carving, and the level gap between the two is the point of the skill.
 */
data class StringRecipe(
    val unstrung: ObjType,
    val strung: ObjType,
    val label: String,
    val level: Int,
    val xp: Double,
)

/**
 * Bow stringing, keyed by unstrung bow.
 *
 * Every pair matches a row in [LogCuts]: the stringing requirement and the carve requirement are
 * deliberately different numbers, and stringing pays the same xp as carving the bow did.
 */
object StringRecipes {
    val all: List<StringRecipe> =
        listOf(
            StringRecipe(
                fletching_objs.unstrung_shortbow,
                fletching_objs.shortbow,
                "Shortbow",
                5,
                5.0,
            ),
            StringRecipe(
                fletching_objs.unstrung_longbow,
                fletching_objs.longbow,
                "Longbow",
                10,
                10.0,
            ),
            StringRecipe(
                fletching_objs.unstrung_oak_shortbow,
                fletching_objs.oak_shortbow,
                "Oak shortbow",
                20,
                16.5,
            ),
            StringRecipe(
                fletching_objs.unstrung_oak_longbow,
                fletching_objs.oak_longbow,
                "Oak longbow",
                25,
                25.0,
            ),
            StringRecipe(
                fletching_objs.unstrung_willow_shortbow,
                fletching_objs.willow_shortbow,
                "Willow shortbow",
                35,
                33.3,
            ),
            StringRecipe(
                fletching_objs.unstrung_willow_longbow,
                fletching_objs.willow_longbow,
                "Willow longbow",
                40,
                41.5,
            ),
            StringRecipe(
                fletching_objs.unstrung_maple_shortbow,
                fletching_objs.maple_shortbow,
                "Maple shortbow",
                50,
                50.0,
            ),
            StringRecipe(
                fletching_objs.unstrung_maple_longbow,
                fletching_objs.maple_longbow,
                "Maple longbow",
                55,
                58.3,
            ),
            StringRecipe(
                fletching_objs.unstrung_yew_shortbow,
                fletching_objs.yew_shortbow,
                "Yew shortbow",
                65,
                67.5,
            ),
            StringRecipe(
                fletching_objs.unstrung_yew_longbow,
                fletching_objs.yew_longbow,
                "Yew longbow",
                70,
                75.0,
            ),
            StringRecipe(
                fletching_objs.unstrung_magic_shortbow,
                fletching_objs.magic_shortbow,
                "Magic shortbow",
                80,
                83.3,
            ),
            StringRecipe(
                fletching_objs.unstrung_magic_longbow,
                fletching_objs.magic_longbow,
                "Magic longbow",
                85,
                91.5,
            ),
        )

    /** Keyed by unstrung obj id, which is how a bow string finds the bow it was used on. */
    val byUnstrung: Map<Int, StringRecipe>
        get() = all.associateBy { it.unstrung.id }
}

/**
 * Finishing an arrow: headless arrow plus one arrowhead.
 *
 * @param xp awarded **per arrow**, which is why it is not a whole number.
 */
data class ArrowRecipe(
    val tip: ObjType,
    val arrow: ObjType,
    val label: String,
    val level: Int,
    val xp: Double,
)

/**
 * Arrowheads, keyed by the arrowhead obj.
 *
 * Level and xp are the game's own and climb with the metal rather than with the bow that shoots it.
 */
object ArrowRecipes {
    val all: List<ArrowRecipe> =
        listOf(
            ArrowRecipe(
                fletching_objs.bronze_arrowheads,
                fletching_objs.bronze_arrow,
                "Bronze arrow",
                1,
                1.3,
            ),
            ArrowRecipe(
                fletching_objs.iron_arrowheads,
                fletching_objs.iron_arrow,
                "Iron arrow",
                15,
                2.5,
            ),
            ArrowRecipe(
                fletching_objs.steel_arrowheads,
                fletching_objs.steel_arrow,
                "Steel arrow",
                30,
                5.0,
            ),
            ArrowRecipe(
                fletching_objs.mithril_arrowheads,
                fletching_objs.mithril_arrow,
                "Mithril arrow",
                45,
                7.5,
            ),
            ArrowRecipe(
                fletching_objs.adamant_arrowheads,
                fletching_objs.adamant_arrow,
                "Adamant arrow",
                60,
                10.0,
            ),
            ArrowRecipe(
                fletching_objs.rune_arrowheads,
                fletching_objs.rune_arrow,
                "Rune arrow",
                75,
                12.5,
            ),
            ArrowRecipe(
                fletching_objs.dragon_arrowheads,
                fletching_objs.dragon_arrow,
                "Dragon arrow",
                90,
                15.0,
            ),
        )

    /** Keyed by arrowhead obj id, which is how an arrowhead finds its arrow. */
    val byTip: Map<Int, ArrowRecipe>
        get() = all.associateBy { it.tip.id }
}

/**
 * Feathering arrow shafts.
 *
 * No level requirement and no menu: the game feathers whatever shafts the player is carrying, one
 * arrow per shaft, for 1 xp each. It is modelled as an ordinary recipe so it runs on the same loop.
 */
object FeatherRecipes {
    const val LEVEL: Int = 1

    const val XP_PER_ARROW: Double = 1.0

    val product: ObjType
        get() = fletching_objs.headless_arrow

    val ingredient: ObjType
        get() = fletching_objs.arrow_shaft
}

/** Ticks per carved log, matching the length of `human_fletching`. */
const val FLETCH_CUT_CYCLES: Int = 3

/** Ticks per strung bow, matching `stringing_shortbow`. */
const val FLETCH_STRING_CYCLES: Int = 2

/** Ticks per feathered shaft: the game feathers a whole inventory in one action. */
const val FLETCH_FEATHER_CYCLES: Int = 1

/** Ticks per arrowhead attached. */
const val FLETCH_ARROW_CYCLES: Int = 2
