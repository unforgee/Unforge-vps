package org.rsmod.content.other.fragments

import jakarta.inject.Inject
import java.util.concurrent.ThreadLocalRandom
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Consumes the first universally safe fragment proc: one bounded secondary AOE hit. The engine
 * already prevents recursive secondary hits, caps it to the target's remaining hitpoints, and
 * preserves the normal hit pipeline. Bleed/freeze/forced movement remain data-defined until their
 * target-specific combat semantics are finalized.
 */
public class FragmentCombatScript
@Inject
constructor(private val players: PlayerList, private val fragments: FragmentService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.GlobalImpact> {
            if (!hit.isFromPlayer || hit.damage <= 0) return@onEvent
            val player =
                runCatching { hit.resolvePlayerSource(players) }.getOrNull() ?: return@onEvent
            val proc =
                fragments
                    .snapshot(player)
                    .procs
                    .filter { it.kind == FragmentProc.AOE_DAMAGE }
                    .maxByOrNull { it.powerBps } ?: return@onEvent
            if (ThreadLocalRandom.current().nextInt(10_000) >= proc.chanceBps.coerceAtMost(10_000))
                return@onEvent
            secondaryDamage =
                maxOf(secondaryDamage, (hit.damage * proc.powerBps / 10_000).coerceAtLeast(1))
        }
    }
}
