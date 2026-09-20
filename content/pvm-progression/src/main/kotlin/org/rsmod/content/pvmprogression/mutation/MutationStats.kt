package org.rsmod.content.pvmprogression.mutation

import org.rsmod.content.pvmprogression.events.MutationKind

/**
 * Lifetime mutation kill stats for one character. Only mutated-npc kills touch this; a normal kill
 * leaves it unchanged, so the counters are a clean signal of mutation engagement.
 */
data class MutationStats(
    val killsByType: Map<MutationKind, Int> = emptyMap(),
    val totalKills: Int = 0,
    val rarestKilled: MutationKind? = null,
) {
    /**
     * Returns a copy with one more kill of [kind], updating the totals and the rarest-kind marker.
     */
    fun add(kind: MutationKind): MutationStats {
        val newByType = killsByType.toMutableMap().apply { merge(kind, 1) { a, b -> a + b } }
        val newTotal = totalKills + 1
        val newRarest = pickRarest(newByType, rarestKilled)
        return copy(killsByType = newByType, totalKills = newTotal, rarestKilled = newRarest)
    }

    private fun pickRarest(byType: Map<MutationKind, Int>, previous: MutationKind?): MutationKind? {
        val order =
            listOf(
                MutationKind.GIANT,
                MutationKind.ANCIENT,
                MutationKind.CORRUPTED,
                MutationKind.TREASURE,
                MutationKind.UNSTABLE,
                MutationKind.VAMPIRIC,
                MutationKind.BERSERK,
            )
        for (kind in order) {
            if (kind in byType) {
                return kind
            }
        }
        return previous
    }

    companion object {
        val EMPTY = MutationStats()
    }
}
