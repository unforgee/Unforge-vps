package org.rsmod.content.other.fragments

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

@Singleton
public class FragmentService @Inject constructor() {
    private val states = ConcurrentHashMap<Int, FragmentState>()

    public fun restore(characterId: Int, state: FragmentState) {
        states[characterId] = state
    }

    public fun state(characterId: Int): FragmentState? = states[characterId]

    public fun ensure(player: Player): FragmentState =
        states.computeIfAbsent(player.characterId) { FragmentState() }

    public fun snapshot(player: Player): FragmentEffectSnapshot = snapshot(ensure(player))

    public fun snapshot(characterId: Long): FragmentEffectSnapshot =
        snapshot(states[characterId.toInt()] ?: FragmentState())

    public fun obtain(player: Player, fragmentId: String, source: String = "drop"): Boolean {
        val definition = FragmentCatalog.fragmentsById[fragmentId] ?: return false
        val current = ensure(player)
        val previous = current.progress[fragmentId]
        val nextProgress =
            FragmentXp.add(previous ?: FragmentProgress(), FragmentConfig.DUPLICATE_XP)
        val updated =
            current.copy(
                progress = current.progress + (fragmentId to nextProgress),
                revision = current.revision + 1,
            )
        states[player.characterId] = updated
        if (previous == null) {
            player.mes(
                "<col=ffb84d>FRAGMENT FOUND</col>: ${definition.name} [Lv${nextProgress.level}]"
            )
        } else {
            val levelUp = nextProgress.level > previous.level
            player.mes(
                "<col=ffb84d>FRAGMENT DUPLICATE</col>: ${definition.name} +${FragmentConfig.DUPLICATE_XP} XP" +
                    if (levelUp) " — LEVEL ${nextProgress.level}!" else ""
            )
        }
        player.mes("Source: $source")
        return true
    }

    public fun addExperience(player: Player, fragmentId: String, amount: Int): Boolean {
        val definition = FragmentCatalog.fragmentsById[fragmentId] ?: return false
        val current = ensure(player)
        val previous = current.progress[fragmentId] ?: FragmentProgress()
        val next = FragmentXp.add(previous, amount)
        states[player.characterId] =
            current.copy(
                progress = current.progress + (fragmentId to next),
                revision = current.revision + 1,
            )
        player.mes(
            "${definition.name}: ${next.xp} XP, level ${next.level}/${FragmentConfig.MAX_LEVEL}."
        )
        return true
    }

    public fun reset(player: Player) {
        states[player.characterId] = FragmentState(revision = ensure(player).revision + 1)
        player.mes("Fragment progression reset.")
    }

    public fun onNpcKilled(player: Player, npcLevel: Int, npcName: String) {
        val level = npcLevel.coerceAtLeast(1)
        val normalized = npcName.lowercase()
        val chance =
            when {
                normalized.contains("boss") || normalized.contains("guardian") ->
                    FragmentConfig.BOSS_DROP_CHANCE_BPS
                level >= 100 -> FragmentConfig.ELITE_DROP_CHANCE_BPS
                else -> FragmentConfig.NPC_DROP_CHANCE_BPS
            }
        if (ThreadLocalRandom.current().nextInt(10_000) >= chance) return
        val index = ThreadLocalRandom.current().nextInt(FragmentCatalog.fragments.size)
        obtain(player, FragmentCatalog.fragments[index].id, "$npcName kill")
    }

    private fun snapshot(state: FragmentState): FragmentEffectSnapshot {
        val stats = linkedMapOf<FragmentStat, Int>()
        val procs = mutableListOf<FragmentEffect.Proc>()
        val ownedIds = state.progress.keys.filter { it in FragmentCatalog.fragmentsById }.toSet()
        for ((id, progress) in state.progress) {
            val definition = FragmentCatalog.fragmentsById[id] ?: continue
            val level = progress.level.coerceIn(1, FragmentConfig.MAX_LEVEL)
            for (effect in definition.effects) add(effect, level, stats, procs)
        }
        val completeSets = mutableSetOf<String>()
        for (set in FragmentCatalog.sets) {
            if (set.fragments.all { it.id in ownedIds }) {
                completeSets += set.id
                set.bonus.effects.forEach { add(it, 1, stats, procs) }
            }
        }
        return FragmentEffectSnapshot(stats, procs.toList(), completeSets, ownedIds.size)
    }

    private fun add(
        effect: FragmentEffect,
        level: Int,
        stats: MutableMap<FragmentStat, Int>,
        procs: MutableList<FragmentEffect.Proc>,
    ) {
        when (effect) {
            is FragmentEffect.Stat -> stats.merge(effect.stat, effect.amount * level, Int::plus)
            is FragmentEffect.Proc ->
                procs +=
                    effect.copy(
                        chanceBps = effect.chanceBps * level.coerceAtMost(FragmentConfig.MAX_LEVEL)
                    )
        }
    }
}
