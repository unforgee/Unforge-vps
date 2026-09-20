package org.rsmod.api.cache.types.obj

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.cache.util.EncoderContext
import org.rsmod.game.type.obj.ObjTypeBuilder

class ObjTypeMembersDisabledTest {
    @Test
    fun `legacy members opcode is ignored when decoding`() {
        val builder = ObjTypeDecoder.decode(Unpooled.wrappedBuffer(byteArrayOf(16, 0)))

        assertFalse(builder.build(1).members)
    }

    @Test
    fun `merged item definitions remain non-members`() {
        val edit = ObjTypeBuilder("test_item").build(1)
        val legacyBase = edit.copy(members = true)

        assertFalse(ObjTypeBuilder.merge(edit, legacyBase).members)
    }

    @Test
    fun `members opcode is not emitted for client cache`() {
        val base = ObjTypeBuilder("test_item").apply { name = "Test item" }.build(1)
        val legacyMembersType = base.copy(members = true)
        val nonMembersType = base.copy(members = false)
        val legacyData = Unpooled.buffer()
        val nonMembersData = Unpooled.buffer()

        try {
            ObjTypeEncoder.encodeJs5(
                legacyMembersType,
                legacyData,
                EncoderContext.client(emptySet(), emptySet(), emptySet()),
            )
            ObjTypeEncoder.encodeJs5(
                nonMembersType,
                nonMembersData,
                EncoderContext.client(emptySet(), emptySet(), emptySet()),
            )

            assertEquals(nonMembersData.readableBytes(), legacyData.readableBytes())
            assertEquals(nonMembersData, legacyData)
        } finally {
            legacyData.release()
            nonMembersData.release()
        }
    }
}
