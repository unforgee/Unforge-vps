package org.rsmod.content.areas.unforge.drops

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcDropTables.DropItem
import org.rsmod.api.death.NpcDropTables.DropTable
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Adds boss-specific drops that exist in the repository's Kronos boss tables but are not present in
 * the generated Unforge table yet.
 *
 * The source JSON is kept as the authority for item ids and boss variants. These registrations
 * intentionally merge with [UnforgeDrops] instead of replacing its generated tables.
 */
class UnforgeBossDropAdditions @Inject constructor(private val dropTables: NpcDropTables) :
    PluginScript() {

    override fun ScriptContext.startup() {
        // Vorkath: source table ids 8059 and 8061.
        register(
            ids = intArrayOf(8059, 8061),
            guaranteed =
                listOf(
                    DropItem(22124, 2, 2, 1), // Superior dragon bones
                    DropItem(1751, 2, 2, 1), // Blue dragonhide
                ),
            tables =
                listOf(
                    DropTable(
                        "Vorkath unique and utility drops",
                        4,
                        listOf(
                            // Kronos reference JSON says 2425; active revision-239 symbol is 21907.
                            DropItem(21907, 1, 1, 20), // Vorkath's head
                            DropItem(22111, 1, 1, 8), // Dragonbone necklace
                            DropItem(22118, 1, 1, 8), // Wrath talisman
                            DropItem(22106, 1, 1, 1), // Jar of decay
                            DropItem(11286, 1, 1, 1), // Draconic visage
                            DropItem(22006, 1, 1, 1), // Skeletal visage
                        ),
                    ),
                    DropTable(
                        "Vorkath supplies",
                        96,
                        listOf(
                            DropItem(995, 37_000, 81_000, 40), // Coins
                            DropItem(392, 25, 55, 40), // Manta ray (noted)
                            DropItem(21880, 30, 100, 12), // Wrath rune
                            DropItem(21930, 55, 100, 8), // Dragon bolts (unf)
                            DropItem(1602, 10, 30, 6), // Diamond (noted)
                            DropItem(1514, 50, 50, 6), // Magic logs (noted)
                            DropItem(12073, 1, 1, 8), // Clue scroll (elite)
                        ),
                    ),
                ),
        )

        // Alchemical Hydra: source table ids 8615-8622.
        register(
            ids = intArrayOf(8615, 8616, 8617, 8618, 8619, 8620, 8621, 8622),
            guaranteed = listOf(DropItem(22786, 2, 2, 1)), // Hydra bones
            tables =
                listOf(
                    DropTable(
                        "Hydra uniques",
                        1,
                        listOf(
                            DropItem(22973, 1, 1, 3), // Hydra's eye
                            DropItem(22971, 1, 1, 3), // Hydra's fang
                            DropItem(22969, 1, 1, 3), // Hydra's heart
                            DropItem(22988, 1, 1, 3), // Hydra tail
                            DropItem(22983, 1, 1, 3), // Hydra leather
                            DropItem(22966, 1, 1, 2), // Hydra's claw
                            DropItem(22804, 500, 1_000, 3), // Dragon knife
                            DropItem(20849, 500, 1_000, 3), // Dragon thrownaxe
                        ),
                    ),
                    DropTable(
                        "Hydra supplies",
                        99,
                        listOf(
                            DropItem(995, 50_000, 90_000, 2), // Coins
                            DropItem(562, 150, 300, 2), // Chaos rune
                            DropItem(560, 150, 300, 2), // Death rune
                            DropItem(565, 150, 300, 2), // Blood rune
                            DropItem(9075, 150, 300, 2), // Astral rune
                            DropItem(9244, 100, 120, 2), // Dragonstone bolts (e)
                            DropItem(9245, 35, 50, 1), // Onyx bolts (e)
                            DropItem(3024, 3, 4, 1), // Super restore (3)
                            DropItem(537, 30, 50, 2), // Dragon bones (noted)
                            DropItem(989, 1, 2, 1), // Crystal key
                            DropItem(12073, 1, 1, 1), // Clue scroll (elite)
                        ),
                    ),
                ),
        )

        // Abyssal Sire: source table ids 5886-5891 and 5908.
        register(
            ids = intArrayOf(5886, 5887, 5888, 5889, 5890, 5891, 5908),
            guaranteed = listOf(DropItem(592, 1, 1, 1)), // Ashes
            tables =
                listOf(
                    DropTable(
                        "Abyssal Sire uniques",
                        1,
                        listOf(
                            DropItem(13273, 1, 1, 1), // Unsired
                            DropItem(12073, 1, 1, 1), // Clue scroll (elite)
                        ),
                    ),
                    DropTable(
                        "Abyssal Sire supplies",
                        99,
                        listOf(
                            DropItem(995, 6_000, 52_000, 20), // Coins
                            DropItem(2364, 5, 5, 3), // Runite bar (noted)
                            DropItem(452, 6, 6, 3), // Runite ore (noted)
                            DropItem(3024, 4, 4, 3), // Super restore (4)
                            DropItem(6687, 6, 6, 3), // Saradomin brew (3)
                            DropItem(560, 300, 370, 3), // Death rune
                            DropItem(565, 160, 210, 2), // Blood rune
                            DropItem(566, 225, 275, 2), // Soul rune
                            DropItem(9194, 10, 10, 4), // Onyx bolt tips
                        ),
                    ),
                ),
        )

        // Cerberus: source table ids 5862, 5863 and 5866.
        register(
            ids = intArrayOf(5862, 5863, 5866),
            guaranteed = listOf(DropItem(592, 1, 1, 1)), // Ashes
            tables =
                listOf(
                    DropTable(
                        "Cerberus crystals",
                        1,
                        listOf(
                            DropItem(13231, 1, 1, 1), // Primordial crystal
                            DropItem(13229, 1, 1, 1), // Pegasian crystal
                            DropItem(13227, 1, 1, 1), // Eternal crystal
                            DropItem(13233, 1, 1, 1), // Smouldering stone
                            DropItem(13249, 3, 3, 2), // Key master teleport
                            DropItem(13245, 1, 1, 1), // Jar of souls
                        ),
                    ),
                    DropTable(
                        "Cerberus supplies",
                        99,
                        listOf(
                            DropItem(995, 10_000, 20_000, 12), // Coins
                            DropItem(3024, 2, 2, 3), // Super restore (4)
                            DropItem(5304, 3, 3, 2), // Torstol seed
                            DropItem(220, 6, 6, 1), // Grimy torstol (noted)
                            DropItem(452, 5, 5, 1), // Runite ore (noted)
                            DropItem(454, 120, 120, 3), // Coal (noted)
                            DropItem(12073, 1, 1, 1), // Clue scroll (elite)
                        ),
                    ),
                ),
        )

        // Barrows brothers: each brother owns a separate table. These registrations merge with
        // any generated entries, so existing supplies and legacy drops remain active.
        registerBarrows(1672, listOf(4708, 4712, 4714, 4710)) // Ahrim
        registerBarrows(1673, listOf(4716, 4720, 4722, 4718)) // Dharok
        registerBarrows(1674, listOf(4724, 4728, 4730, 4726)) // Guthan
        registerBarrows(1675, listOf(4732, 4736, 4738, 4734)) // Karil
        registerBarrows(1676, listOf(4745, 4749, 4751, 4747)) // Torag
        registerBarrows(1677, listOf(4753, 4757, 4759, 4755)) // Verac

        // Giant Mole: source table ids 5779 and 6499.
        register(
            ids = intArrayOf(5779, 6499),
            guaranteed =
                listOf(
                    DropItem(532, 1, 1, 1), // Big bones
                    DropItem(7416, 1, 1, 1), // Mole claw
                    DropItem(7418, 1, 3, 1), // Mole skin
                ),
            tables =
                listOf(
                    DropTable(
                        "Giant Mole supplies",
                        1,
                        listOf(
                            DropItem(385, 4, 4, 10), // Shark
                            DropItem(1516, 100, 100, 10), // Yew logs (noted)
                            DropItem(565, 15, 15, 3), // Blood rune
                            DropItem(560, 7, 7, 3), // Death rune
                            DropItem(441, 100, 100, 1), // Iron ore (noted)
                            DropItem(2359, 1, 1, 1), // Mithril bar
                            DropItem(12073, 1, 1, 1), // Clue scroll (elite)
                        ),
                    )
                ),
        )

        // Scorpia: source table id 6615. The source has no guaranteed item.
        register(
            ids = intArrayOf(6615),
            guaranteed = emptyList(),
            tables =
                listOf(
                    DropTable(
                        "Scorpia rare drops",
                        1,
                        listOf(
                            DropItem(11930, 1, 1, 1), // Odium shard 3
                            DropItem(11933, 1, 1, 1), // Malediction shard 3
                            DropItem(2722, 1, 1, 1), // Clue scroll (hard)
                            DropItem(12073, 1, 1, 1), // Clue scroll (elite)
                        ),
                    ),
                    DropTable(
                        "Scorpia supplies",
                        99,
                        listOf(
                            DropItem(995, 500, 3_987, 18), // Coins
                            DropItem(385, 1, 1, 6), // Shark
                            DropItem(2434, 1, 1, 4), // Prayer potion (4)
                            DropItem(1784, 25, 25, 4), // Bucket of sand (noted)
                            DropItem(6017, 10, 10, 4), // Cactus spine (noted)
                            DropItem(1622, 6, 6, 2), // Uncut emerald (noted)
                            DropItem(4696, 30, 30, 2), // Dust rune
                        ),
                    ),
                ),
        )

        // Custom Unforge bosses: the complete Dawnsteel starter set is discoverable in-game.
        register(
            ids = intArrayOf(16296, 16299),
            guaranteed = emptyList(),
            tables =
                listOf(
                    DropTable(
                        "Unforge Dawnsteel starter set",
                        1,
                        listOf(
                            DropItem(65020, 1, 1, 1), // Helm
                            DropItem(65021, 1, 1, 1), // Body
                            DropItem(65022, 1, 1, 1), // Legs
                            DropItem(65023, 1, 1, 1), // Gauntlets
                            DropItem(65024, 1, 1, 1), // Boots
                        ),
                    ),
                ),
        )
    }

    private fun register(ids: IntArray, guaranteed: List<DropItem>, tables: List<DropTable>) {
        ids.forEach { id -> dropTables.register(id, guaranteed, tables) }
    }

    private fun registerBarrows(npcId: Int, set: List<Int>) {
        dropTables.register(
            npcId,
            guaranteed = emptyList(),
            tables =
                listOf(
                    DropTable("Barrows brother unique set", 1, set.map { DropItem(it, 1, 1, 1) }),
                    DropTable(
                        "Barrows custom weapons",
                        1,
                        listOf(
                            DropItem(65005, 1, 1, 1),
                            DropItem(65006, 1, 1, 1),
                            DropItem(65007, 1, 1, 1),
                        ),
                    ),
                    DropTable(
                        "Barrows brother supplies",
                        9,
                        listOf(
                            DropItem(995, 2_000, 8_000, 5),
                            DropItem(385, 1, 3, 3),
                            DropItem(989, 1, 1, 1),
                            DropItem(6199, 1, 1, 1),
                        ),
                    ),
                ),
        )
    }
}
