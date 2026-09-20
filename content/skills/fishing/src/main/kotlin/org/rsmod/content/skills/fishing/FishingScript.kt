package org.rsmod.content.skills.fishing

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingScript
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val npcRepo: NpcRepository,
    private val perks: PerkService,
    private val xpMods: XpModifiers,
    private val rewards: SkillingRewards,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in npcTypes.values) {
            val methods = FishingMethod.forSpot(npc.internalName ?: continue) ?: continue
            onOpNpc1(npc) { fish(it.npc, methods.first) }
            methods.second?.let { method -> onOpNpc2(npc) { fish(it.npc, method) } }
        }
    }

    private suspend fun ProtectedAccess.fish(spot: Npc, method: FishingMethod) {
        val origin = spot.coords
        val tool = objTypes.getValue(method.tool)
        val bait = method.bait?.let(objTypes::getValue)
        val animation = seqTypes.getValue(method.animation)
        try {
            while (spot.isSlotAssigned && spot.isVisible && spot.coords == origin) {
                if (invTotal(inv, tool) == 0) {
                    mes("You need a ${tool.name.lowercase()} to fish here.")
                    return
                }
                if (bait != null && invTotal(inv, bait) == 0) {
                    mes("You have run out of ${bait.name.lowercase()}.")
                    return
                }
                val available = method.catches.filter { player.stat(stats.fishing) >= it.level }
                if (available.isEmpty()) {
                    mes("You need a Fishing level of ${method.catches.first().level} to fish here.")
                    return
                }
                if (inv.isFull()) {
                    mes("Your inventory is too full to hold any more fish.")
                    return
                }
                anim(animation)
                delay(4)
                // Another player can exhaust the same spot during the delay.
                if (!spot.isSlotAssigned || spot.isInvisible || spot.coords != origin) return
                if (invTotal(inv, tool) == 0 || inv.isFull()) return
                val eligible = available.filter { player.stat(stats.fishing) >= it.level }
                if (eligible.isEmpty()) return
                val catch = eligible[random.of(eligible.size)]
                if (random.of(256) >= catch.chance(player.stat(stats.fishing))) continue
                if (bait != null && !invDel(inv, bait, 1).success) return
                val count = if (random.of(10_000) < perks.doubleFishBps(player)) 2 else 1
                val delivered = rewards.grant(this, "FISHING", objTypes.getValue(catch.item), count)
                statAdvance(stats.fishing, catch.xp * xpMods.get(player, stats.fishing))
                mes("You catch some ${objTypes.getValue(catch.item).name.lowercase()}.")
                if (random.of(20) == 0) {
                    npcRepo.del(spot, 25)
                    mes("The fish move away. The spot will replenish shortly.")
                    return
                }
                if (!delivered) return
            }
        } finally {
            resetAnim()
        }
    }
}
