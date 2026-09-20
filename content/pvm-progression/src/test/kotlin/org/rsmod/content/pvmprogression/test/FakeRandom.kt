package org.rsmod.content.pvmprogression.test

import org.rsmod.api.random.GameRandom

/**
 * Deterministic [GameRandom] for unit tests. Returns a fixed value for [of], cycling through a
 * pre-set queue so a test can script a sequence of rolls without the game harness.
 */
class FakeRandom(vararg values: Int) : GameRandom {
    private val queue = ArrayDeque(values.toList())
    private var single = 0

    fun set(value: Int) {
        single = value
        queue.clear()
    }

    override fun of(maxExclusive: Int): Int {
        if (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            return v.coerceIn(0, maxExclusive - 1)
        }
        return single.coerceIn(0, maxExclusive - 1)
    }

    override fun of(minInclusive: Int, maxInclusive: Int): Int {
        if (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            return v.coerceIn(minInclusive, maxInclusive)
        }
        return single.coerceIn(minInclusive, maxInclusive)
    }

    override fun randomDouble(): Double = 0.0
}
