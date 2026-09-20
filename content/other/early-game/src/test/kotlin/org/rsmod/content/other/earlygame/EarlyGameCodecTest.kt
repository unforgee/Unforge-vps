package org.rsmod.content.other.earlygame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class EarlyGameCodecTest {
    @Test
    public fun `set and counter codecs are deterministic and lossless`() {
        val discoveries = setOf("varrock", "starter-area", "first-boss")
        val counts = mapOf("backpack" to 2, "treasuremap" to 1)

        assertEquals(discoveries, EarlyGameCodec.decode(EarlyGameCodec.encode(discoveries)))
        assertEquals(counts, EarlyGameCodec.decodeCounts(EarlyGameCodec.encodeCounts(counts)))
        assertTrue(EarlyGameCodec.encode(discoveries).startsWith("first-boss;"))
    }

    @Test
    public fun `starter path has stable persisted ids`() {
        assertEquals("CHOOSE_RELIC", AdventureMilestone.CHOOSE_RELIC.name)
        assertEquals("TRIAL", AdventureMilestone.TRIAL.name)
        assertEquals("first-companion", EarlyGameDiscoveries.byId["first-companion"]?.id)
    }
}
