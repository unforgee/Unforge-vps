package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.config.refs.BaseVarps
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.lumbridge.configs.lumbridge_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** First runtime bridge for the shared r239 NPC/Boss/Shop/Quest definitions. */
class Rsps2MasterRuntime @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(lumbridge_npcs.count_check) { startQuest(it.npc) }
        onOpNpc2(lumbridge_npcs.count_check) { checkpointQuest(it.npc) }
        onOpNpc3(lumbridge_npcs.count_check) {
            shops.open(player, it.npc, "R239 General Shop", BaseInvs.generalshop1)
        }
        onOpNpc4(lumbridge_npcs.count_check) { finishQuest(it.npc) }
        onOpNpc5(lumbridge_npcs.count_check) { bossInfo(it.npc) }
    }

    private suspend fun ProtectedAccess.startQuest(npc: Npc) {
        vars[BaseVarps.rjquest] = 1
        startDialogue(npc) { chatNpc(happy, "Quest started: First Steps.") }
    }

    private suspend fun ProtectedAccess.checkpointQuest(npc: Npc) {
        vars[BaseVarps.rjquest] = 2
        startDialogue(npc) { chatNpc(neutral, "Checkpoint reached: visited the R239 shop.") }
    }

    private suspend fun ProtectedAccess.finishQuest(npc: Npc) {
        vars[BaseVarps.rjquest] = 3
        startDialogue(npc) { chatNpc(happy, "Quest complete: First Steps.") }
    }

    private suspend fun ProtectedAccess.bossInfo(npc: Npc) {
        startDialogue(npc) {
            chatNpc(
                angry,
                "R239 boss encounter registered. Combat hook is ready for the configured boss definition.",
            )
        }
    }
}
