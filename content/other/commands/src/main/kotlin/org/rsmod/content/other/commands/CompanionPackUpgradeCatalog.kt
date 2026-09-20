package org.rsmod.content.other.commands

import org.rsmod.api.invtx.invDel
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

public data class PackUpgradeMaterial(
    public val itemId: Int,
    public val certId: Int?,
    public val count: Int,
    public val name: String,
)

public data class CompanionPackTier(
    public val tier: Int,
    public val capacity: Int,
    public val gold: Int,
    public val materials: List<PackUpgradeMaterial>,
)

public object CompanionPackUpgradeCatalog {
    public const val BASE_CAPACITY: Int = 10
    public const val MAX_CAPACITY: Int = 100
    public const val SCROLL_OBJ_ID: Int = 24187 // Ancient pack scroll (trouver_parchment)

    public val tiers: List<CompanionPackTier> =
        listOf(
            CompanionPackTier(
                tier = 1,
                capacity = 20,
                gold = 50_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 1741,
                            certId = 1742,
                            count = 10,
                            name = "Soft leather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1734,
                            certId = null,
                            count = 1,
                            name = "Thread",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 2,
                capacity = 30,
                gold = 150_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 1743,
                            certId = 1744,
                            count = 10,
                            name = "Hard leather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1734,
                            certId = null,
                            count = 5,
                            name = "Thread",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 3,
                capacity = 40,
                gold = 500_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 1745,
                            certId = 1746,
                            count = 10,
                            name = "Green dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1734,
                            certId = null,
                            count = 10,
                            name = "Thread",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 4,
                capacity = 50,
                gold = 1_500_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2509,
                            certId = 2510,
                            count = 10,
                            name = "Black dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2357,
                            certId = 2358,
                            count = 1,
                            name = "Gold bar",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 5,
                capacity = 60,
                gold = 5_000_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2507,
                            certId = 2508,
                            count = 25,
                            name = "Red dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1515,
                            certId = 1516,
                            count = 50,
                            name = "Yew logs",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2357,
                            certId = 2358,
                            count = 5,
                            name = "Gold bar",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 6,
                capacity = 70,
                gold = 15_000_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2509,
                            certId = 2510,
                            count = 50,
                            name = "Black dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1513,
                            certId = 1514,
                            count = 100,
                            name = "Magic logs",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2359,
                            certId = 2360,
                            count = 20,
                            name = "Mithril bar",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 7,
                capacity = 80,
                gold = 35_000_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2509,
                            certId = 2510,
                            count = 75,
                            name = "Black dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1513,
                            certId = 1514,
                            count = 150,
                            name = "Magic logs",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2361,
                            certId = 2362,
                            count = 30,
                            name = "Adamantite bar",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 8,
                capacity = 90,
                gold = 65_000_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2509,
                            certId = 2510,
                            count = 100,
                            name = "Black dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1513,
                            certId = 1514,
                            count = 200,
                            name = "Magic logs",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2363,
                            certId = 2364,
                            count = 50,
                            name = "Runite bar",
                        ),
                    ),
            ),
            CompanionPackTier(
                tier = 9,
                capacity = 100,
                gold = 100_000_000,
                materials =
                    listOf(
                        PackUpgradeMaterial(
                            itemId = 2509,
                            certId = 2510,
                            count = 100,
                            name = "Black dragonleather",
                        ),
                        PackUpgradeMaterial(
                            itemId = 1513,
                            certId = 1514,
                            count = 250,
                            name = "Magic logs",
                        ),
                        PackUpgradeMaterial(
                            itemId = 2363,
                            certId = 2364,
                            count = 50,
                            name = "Runite bar",
                        ),
                    ),
            ),
        )

    public fun nextTier(currentCapacity: Int): CompanionPackTier? =
        tiers.firstOrNull { it.capacity > currentCapacity }

    public fun hasRequirements(player: Player, tier: CompanionPackTier): Boolean =
        hasRequirements(player.inv, tier)

    public fun hasRequirements(inv: Inventory, tier: CompanionPackTier): Boolean {
        val totalCoins = inv.filterNotNull().filter { it.id == 995 }.sumOf { it.count.toLong() }
        if (totalCoins < tier.gold) return false
        for (mat in tier.materials) {
            val count =
                inv.filterNotNull()
                    .filter { it.id == mat.itemId || (mat.certId != null && it.id == mat.certId) }
                    .sumOf { it.count }
            if (count < mat.count) return false
        }
        return true
    }

    public fun consumeRequirements(player: Player, tier: CompanionPackTier) {
        if (tier.gold > 0) {
            player.invDel(player.inv, obj = 995, count = tier.gold, strict = false)
        }
        for (mat in tier.materials) {
            var remaining = mat.count
            val unnotedCount =
                player.inv.filterNotNull().filter { it.id == mat.itemId }.sumOf { it.count }
            val takeUnnoted = minOf(remaining, unnotedCount)
            if (takeUnnoted > 0) {
                player.invDel(player.inv, obj = mat.itemId, count = takeUnnoted, strict = false)
                remaining -= takeUnnoted
            }
            if (remaining > 0 && mat.certId != null) {
                player.invDel(player.inv, obj = mat.certId, count = remaining, strict = false)
            }
        }
    }

    public fun formatCost(tier: CompanionPackTier): String {
        val parts = mutableListOf<String>()
        parts.add("${tier.gold.formatAmount} GP")
        for (mat in tier.materials) {
            parts.add("${mat.count}x ${mat.name}")
        }
        return parts.joinToString(" + ")
    }
}
