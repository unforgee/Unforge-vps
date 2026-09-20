package org.rsmod.content.skills.farming

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.varbit.VarBitTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FarmingScript
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val seqTypes: SeqTypeList,
    private val varbits: VarBitTypeList,
    private val rewards: SkillingRewards,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        // Interactions are dispatched by visual type; different patches share these variants.
        val visuals =
            FarmingData.patches.keys
                .flatMap { id ->
                    val root = locTypes.getValue(id)
                    listOf(id) + root.multiLoc.map { it.toInt() and 0xffff }
                }
                .distinct()
                .filter { it != 65535 }
        for (id in visuals) {
            val loc = locTypes.getValue(id)
            onOpLoc1(loc) { if (it.loc.id in FarmingData.patches) tend(it.loc.id) }
            onOpLoc2(loc) { if (it.loc.id in FarmingData.patches) inspect(it.loc.id) }
            onOpLoc3(loc) {
                if (it.loc.id in FarmingData.patches)
                    mes(
                        "Rake the patch, use seeds on it, then return when the crop has grown. Compost improves your yield."
                    )
            }
            onOpLocU(loc) { if (it.loc.id in FarmingData.patches) use(it.loc.id, it.objType.id) }
        }
        onPlayerLogin {
            refresh(player)
            player.softTimer(FarmingTimers.growth, 100)
        }
        onPlayerSoftTimer(FarmingTimers.growth) { refresh(player) }
    }

    private fun refresh(player: Player) {
        for (id in FarmingData.patches.keys) refresh(player, id)
    }

    private fun refresh(player: Player, id: Int) {
        val seed = player.vars[FarmingVars.seed.getValue(id)]
        val planted = player.vars[FarmingVars.planted.getValue(id)]
        val crop = FarmingData.bySeed[seed]
        val now = FarmingData.nowMinutes()
        // Unplanted patches regrow weeds after five minutes, including while logged out.
        if (seed == -1 && now.toLong() - planted >= 5) setSeed(player, id, 0)
        val visual =
            when {
                crop != null -> crop.visual(planted, now)
                player.vars[FarmingVars.seed.getValue(id)] == -1 -> 3
                else -> 0
            }
        val bit = varbits[locTypes.getValue(id).multiVarBit] ?: return
        if (player.vars[bit] != visual) VarPlayerIntMapSetter.set(player, bit, visual)
    }

    private suspend fun ProtectedAccess.tend(id: Int) {
        refresh(player, id)
        val seed = player.vars[FarmingVars.seed.getValue(id)]
        if (seed == 0) {
            if (!tool(5341)) return
            anim(seqTypes.getValue(2273))
            delay(3)
            if (!tool(5341)) return
            setSeed(player, id, -1)
            VarPlayerIntMapSetter.set(
                player,
                FarmingVars.planted.getValue(id),
                FarmingData.nowMinutes(),
            )
            statAdvance(stats.farming, 4.0 * xpMods.get(player, stats.farming))
            resetAnim()
            refresh(player, id)
            mes("You clear the weeds. Use seeds on the patch to plant them.")
            return
        }
        val crop = FarmingData.bySeed[seed]
        if (
            crop == null ||
                crop.stage(
                    player.vars[FarmingVars.planted.getValue(id)],
                    FarmingData.nowMinutes(),
                ) < crop.stages
        ) {
            inspect(id)
            return
        }
        val requiredTool = if (crop.kind == PatchKind.HERB) 5329 else 952
        try {
            while (player.vars[FarmingVars.remaining.getValue(id)] > 0) {
                if (!tool(requiredTool)) return
                if (inv.isFull()) {
                    mes("Your inventory is too full to harvest this crop.")
                    return
                }
                anim(seqTypes.getValue(2282))
                delay(3)
                if (!tool(requiredTool) || inv.isFull()) return
                val remaining = player.vars[FarmingVars.remaining.getValue(id)]
                // Commit the harvest before delivering the item; interrupting cannot repeat it.
                VarPlayerIntMapSetter.set(player, FarmingVars.remaining.getValue(id), remaining - 1)
                val delivered = rewards.grant(this, "FARMING", objTypes.getValue(crop.product))
                statAdvance(stats.farming, crop.harvestXp * xpMods.get(player, stats.farming))
                mes("You harvest some ${objTypes.getValue(crop.product).name.lowercase()}.")
                if (remaining == 1) {
                    clear(player, id)
                    refresh(player, id)
                    return
                }
                if (!delivered) return
            }
        } finally {
            resetAnim()
        }
    }

    private fun ProtectedAccess.inspect(id: Int) {
        refresh(player, id)
        val crop = FarmingData.bySeed[player.vars[FarmingVars.seed.getValue(id)]]
        if (crop == null) {
            mes(
                if (player.vars[FarmingVars.seed.getValue(id)] == -1)
                    "This patch is ready for seeds."
                else "This patch needs raking."
            )
            return
        }
        val left =
            crop.minutes -
                (FarmingData.nowMinutes() - player.vars[FarmingVars.planted.getValue(id)])
        mes(
            if (left > 0)
                "Your ${objTypes.getValue(crop.product).name.lowercase()} will be ready in $left minutes."
            else "Your crop is ready to harvest."
        )
    }

    private suspend fun ProtectedAccess.use(id: Int, item: Int) {
        refresh(player, id)
        val seed = player.vars[FarmingVars.seed.getValue(id)]
        when (item) {
            5341 -> {
                if (seed == 0) tend(id) else inspect(id)
                return
            }
            952 -> {
                if (seed > 0) {
                    if (!tool(952)) return
                    val crop = FarmingData.bySeed.getValue(seed)
                    if (
                        crop.stage(
                            player.vars[FarmingVars.planted.getValue(id)],
                            FarmingData.nowMinutes(),
                        ) == crop.stages
                    ) {
                        tend(id)
                        return
                    }
                    mes("This crop is still growing. Return when it is ready.")
                } else inspect(id)
                return
            }
            6032,
            6034 -> {
                if (seed != -1) {
                    mes("Rake an empty patch before adding compost.")
                    return
                }
                if (player.vars[FarmingVars.compost.getValue(id)] != 0) {
                    mes("This patch already has compost.")
                    return
                }
                if (!invDel(inv, objTypes.getValue(item), 1).success) return
                invAdd(inv, objTypes.getValue(1925))
                VarPlayerIntMapSetter.set(
                    player,
                    FarmingVars.compost.getValue(id),
                    if (item == 6034) 2 else 1,
                )
                anim(seqTypes.getValue(2283))
                mes("You treat the patch with compost.")
                return
            }
        }
        val crop =
            FarmingData.bySeed[item]
                ?: run {
                    mes("You cannot use that on this patch.")
                    return
                }
        if (crop.kind != FarmingData.patches.getValue(id)) {
            mes("Those seeds do not grow in this type of patch.")
            return
        }
        if (seed != -1) {
            mes("You need an empty, raked patch to plant seeds.")
            return
        }
        if (player.stat(stats.farming) < crop.level) {
            mes("You need a Farming level of ${crop.level} to plant those seeds.")
            return
        }
        if (!tool(5343)) return
        val seeds = objTypes.getValue(item)
        if (invTotal(inv, seeds) < crop.seedCount) {
            mes("You need ${crop.seedCount} seeds to plant this crop.")
            return
        }
        anim(seqTypes.getValue(2291))
        delay(3)
        if (!tool(5343) || player.stat(stats.farming) < crop.level) return
        if (!invDel(inv, seeds, crop.seedCount).success) return
        val compost = player.vars[FarmingVars.compost.getValue(id)]
        // Standalone crops always mature. Level and compost improve the rolled harvest quantity.
        val yield =
            3 +
                random.of(4) +
                compost * 2 +
                (player.stat(stats.farming) - crop.level).coerceAtLeast(0) / 20
        setSeed(player, id, item)
        VarPlayerIntMapSetter.set(
            player,
            FarmingVars.planted.getValue(id),
            FarmingData.nowMinutes(),
        )
        VarPlayerIntMapSetter.set(player, FarmingVars.remaining.getValue(id), yield)
        statAdvance(stats.farming, crop.plantXp * xpMods.get(player, stats.farming))
        resetAnim()
        refresh(player, id)
        mes("You plant the seeds. The crop will grow in ${crop.minutes} minutes.")
    }

    private fun ProtectedAccess.tool(id: Int): Boolean {
        val tool = objTypes.getValue(id)
        if (invTotal(inv, tool) > 0) return true
        mes("You need a ${tool.name.lowercase()} to do that.")
        return false
    }

    private fun setSeed(player: Player, id: Int, value: Int) =
        VarPlayerIntMapSetter.set(player, FarmingVars.seed.getValue(id), value)

    private fun clear(player: Player, id: Int) {
        setSeed(player, id, -1)
        VarPlayerIntMapSetter.set(
            player,
            FarmingVars.planted.getValue(id),
            FarmingData.nowMinutes(),
        )
        VarPlayerIntMapSetter.set(player, FarmingVars.remaining.getValue(id), 0)
        VarPlayerIntMapSetter.set(player, FarmingVars.compost.getValue(id), 0)
    }
}
