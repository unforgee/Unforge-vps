package org.rsmod.content.areas.city.lumbridge.drops

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcDropTables.DropItem
import org.rsmod.api.death.NpcDropTables.DropTable
import org.rsmod.content.areas.city.lumbridge.configs.LumbridgeR239BossRefs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Registers the live reward table for the Lumbridge Goblin Boss. */
class LumbridgeGoblinBossDrops @Inject constructor(private val dropTables: NpcDropTables) :
    PluginScript() {
    override fun ScriptContext.startup() {
        dropTables.register(
            npcTypeId = LumbridgeR239BossRefs.r239_test_boss.id,
            guaranteed = listOf(DropItem(COINS, 5_000, 15_000, 1), DropItem(BONES, 1, 1, 1)),
            tables =
                listOf(
                    DropTable(
                        name = "Lumbridge Goblin Boss unique equipment",
                        weight = 20,
                        items =
                            listOf(
                                DropItem(DRAGON_BOOTS, 1, 1, 1),
                                DropItem(AMULET_OF_FURY, 1, 1, 1),
                                DropItem(DRAGON_DEFENDER, 1, 1, 1),
                                DropItem(DRAGON_FULL_HELM, 1, 1, 1),
                                DropItem(DRAGON_PLATEBODY, 1, 1, 1),
                                DropItem(DRAGON_KITESHIELD, 1, 1, 1),
                                DropItem(DRAGON_PLATELEGS, 1, 1, 1),
                                DropItem(BARROWS_GLOVES, 1, 1, 1),
                                // 65008 is the server-side red Fire cape clone named Dragon cape.
                                DropItem(DRAGON_CAPE, 1, 1, 1),
                            ),
                    ),
                    DropTable(
                        name = "Lumbridge Goblin Boss supplies",
                        weight = 80,
                        items =
                            listOf(
                                DropItem(COINS, 2_000, 10_000, 5),
                                DropItem(POTATO, 5, 15, 3),
                                DropItem(AIR_RUNE, 100, 300, 2),
                            ),
                    ),
                ),
        )
    }

    private companion object {
        const val COINS = 995
        const val BONES = 526
        const val POTATO = 1942
        const val AIR_RUNE = 556

        const val DRAGON_BOOTS = 11840
        const val AMULET_OF_FURY = 6585
        const val DRAGON_DEFENDER = 12954
        const val DRAGON_FULL_HELM = 11335
        const val DRAGON_PLATEBODY = 21892
        const val DRAGON_KITESHIELD = 21895
        const val DRAGON_PLATELEGS = 4087
        const val BARROWS_GLOVES = 7462
        const val DRAGON_CAPE = 65008
    }
}
