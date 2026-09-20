package org.rsmod.content.other.fragments

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class FragmentScript @Inject constructor(private val fragments: FragmentService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> {
            if (validCombatKill)
                fragments.onNpcKilled(killer, npc.visType.vislevel, npc.visType.name)
        }
        onCommand("fragments") {
            desc = "Show fragment collection progress"
            cheat(::show)
        }
        onCommand("fragment") {
            desc = "Show one fragment"
            cheat(::showOne)
        }
        onCommand("fragmentgive") {
            desc = "Give a fragment (admin)"
            modLevel = modlevels.admin
            cheat(::give)
        }
        onCommand("fragmentxp") {
            desc = "Add fragment XP (admin)"
            modLevel = modlevels.admin
            cheat(::xp)
        }
        onCommand("fragmentreset") {
            desc = "Reset fragments (admin)"
            modLevel = modlevels.admin
            cheat(::reset)
        }
    }

    private fun show(cheat: Cheat) =
        with(cheat) {
            val state = fragments.ensure(player)
            val view = fragments.snapshot(player)
            val levels = state.progress.values.sumOf { it.level }
            player.mes(
                "<col=ffb84d>FRAGMENTS</col>: ${view.ownedCount}/${FragmentCatalog.fragments.size} collected, total levels $levels"
            )
            player.mes(
                "Complete sets: ${view.completeSets.size}/${FragmentCatalog.sets.size}; max level ${FragmentConfig.MAX_LEVEL}."
            )
            FragmentCatalog.sets
                .filter { it.id in view.completeSets }
                .take(5)
                .forEach { player.mes("  ✓ ${it.name} — ${it.bonus.description}") }
        }

    private fun showOne(cheat: Cheat) =
        with(cheat) {
            val query = args.getOrNull(0).orEmpty().lowercase()
            val definition =
                FragmentCatalog.fragmentsById[query]
                    ?: FragmentCatalog.fragments.firstOrNull { it.name.lowercase().contains(query) }
            if (definition == null) {
                player.mes("Unknown fragment. Use an id such as ember-1.")
                return
            }
            val progress = fragments.ensure(player).progress[definition.id]
            player.mes(
                "${definition.name} (${definition.id}) — ${progress?.xp ?: 0} XP, level ${progress?.level ?: 0}/${FragmentConfig.MAX_LEVEL}"
            )
            definition.effects.forEach { player.mes("  ${it.description()}") }
        }

    private fun give(cheat: Cheat) =
        with(cheat) {
            val id = args.getOrNull(0)?.lowercase()
            if (id == null || !fragments.obtain(player, id, "admin"))
                player.mes("Use ::fragmentgive fragment-id")
        }

    private fun xp(cheat: Cheat) =
        with(cheat) {
            val id = args.getOrNull(0)?.lowercase()
            val amount = args.getOrNull(1)?.toIntOrNull()
            if (
                id == null ||
                    amount == null ||
                    amount < 1 ||
                    !fragments.addExperience(player, id, amount)
            )
                player.mes("Use ::fragmentxp fragment-id amount")
        }

    private fun reset(cheat: Cheat) = with(cheat) { fragments.reset(player) }

    private fun FragmentEffect.description(): String =
        when (this) {
            is FragmentEffect.Stat -> "+${amount} ${stat.name.lowercase().replace('_', ' ')}"
            is FragmentEffect.Proc ->
                "${chanceBps / 100.0}% ${kind.name.lowercase().replace('_', ' ')} proc"
        }
}
