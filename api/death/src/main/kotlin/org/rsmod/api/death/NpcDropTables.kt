package org.rsmod.api.death

import jakarta.inject.Singleton
import kotlin.random.Random

/**
 * Registry of npc drop tables, rolled by [NpcDeath.spawnDeathDrops] after a kill.
 *
 * Semantics follow the Kronos `LootTable.rollItems` model that Unforge used: every guaranteed item
 * always drops; then exactly one table is selected with a weight-proportional roll; inside the
 * selected table a single weighted item is rolled (items with weight 0 are always included when
 * their table is picked).
 */
@Singleton
public class NpcDropTables {
    public data class DropItem(val objId: Int, val min: Int, val max: Int, val weight: Int)

    public data class DropTable(val name: String, val weight: Int, val items: List<DropItem>)

    public class NpcDrops(guaranteed: List<DropItem>, tables: List<DropTable>) {
        public val guaranteed: List<DropItem> = guaranteed
        public val tables: List<DropTable> = tables
        public val totalWeight: Int = tables.sumOf { it.weight }
    }

    private val byNpc = HashMap<Int, NpcDrops>()

    public fun register(npcTypeId: Int, guaranteed: List<DropItem>, tables: List<DropTable>) {
        val merged =
            byNpc[npcTypeId]?.let { prev ->
                NpcDrops(prev.guaranteed + guaranteed, prev.tables + tables)
            } ?: NpcDrops(guaranteed, tables)
        byNpc[npcTypeId] = merged
    }

    public fun has(npcTypeId: Int): Boolean = byNpc.containsKey(npcTypeId)

    /**
     * Rolls the drop table for [npcTypeId]. Returns null when the npc has no registered table
     * (caller should fall back to default behaviour).
     */
    public fun roll(npcTypeId: Int, random: Random = Random.Default): List<Pair<Int, Int>>? {
        val drops = byNpc[npcTypeId] ?: return null
        val out = ArrayList<Pair<Int, Int>>(drops.guaranteed.size + 1)
        for (g in drops.guaranteed) {
            out += g.objId to randCount(g.min, g.max, random)
        }
        if (drops.tables.isNotEmpty() && drops.totalWeight > 0) {
            var tableRand = random.nextDouble() * drops.totalWeight
            for (table in drops.tables) {
                tableRand -= table.weight
                if (tableRand <= 0) {
                    val tableTotal = table.items.sumOf { it.weight }
                    var itemsRand = random.nextDouble() * tableTotal
                    for (item in table.items) {
                        if (item.weight == 0) {
                            out += item.objId to randCount(item.min, item.max, random)
                            continue
                        }
                        itemsRand -= item.weight
                        if (itemsRand <= 0) {
                            out += item.objId to randCount(item.min, item.max, random)
                            break
                        }
                    }
                    break
                }
            }
        }
        return out
    }

    private fun randCount(min: Int, max: Int, random: Random): Int =
        if (max <= min) min else min + random.nextInt(max - min + 1)
}
