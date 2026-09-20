package org.rsmod.api.cache.types.comp

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

/**
 * Proves [ComponentTypeEncoder] is a faithful inverse of [ComponentTypeDecoder] by re-encoding
 * every vanilla component and asserting the decoded result is identical to the original.
 *
 * This is the guard for authored interfaces (see `InterfaceBuilder`): the cache packer verifies
 * builder output by decoding what was written, so any encoder/decoder asymmetry turns into an
 * unresolvable `CacheUpdateRequired` loop at pack time. A mismatch here means the encoder wrote
 * bytes the decoder reads differently.
 */
class ComponentTypeRoundTripTest {
    @Test
    fun GameTestState.`re-encoding every vanilla component preserves identity`() =
        runBasicGameTest {
            val types = cacheTypes.components
            var v1Count = 0
            var v3Count = 0
            for ((combinedId, type) in types) {
                val buf = Unpooled.buffer()
                try {
                    ComponentTypeEncoder.encode(type, buf)
                    val decoded = ComponentTypeDecoder.decode(combinedId, buf).build(combinedId)
                    assertEquals(type, decoded) {
                        "Round-trip mismatch for component ${combinedId ushr 16}:${combinedId and 0xFFFF}"
                    }
                } finally {
                    buf.release()
                }
                if (type.v3) v3Count++ else v1Count++
            }
            // Both encoder paths must have been exercised for this test to prove anything.
            assertTrue(v1Count > 0) { "No v1 components decoded - v1 path not exercised." }
            assertTrue(v3Count > 0) { "No v3 components decoded - v3 path not exercised." }
        }
}
