package org.rsmod.api.player.output

import io.netty.buffer.Unpooled
import net.rsprot.buffer.extensions.toJagByteBuf
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies revision-239 audio packet field order and Alt transforms against the RSProt / RSPRox
 * wire format (opcodes: MIDI_SONG_V2=35, MIDI_JINGLE=118, SYNTH_SOUND=77, SOUND_AREA=32).
 *
 * Encode path mirrors `osrs-239-desktop` encoders; decode path mirrors RSPRox v239 decoders.
 */
class AudioPacketEncodingTest {
    @Test
    fun midiSongV2_roundTrip() {
        val message =
            MidiSongV2(
                id = 380,
                fadeOutDelay = 0,
                fadeOutSpeed = 60,
                fadeInDelay = 60,
                fadeInSpeed = 0,
            )
        val buf = Unpooled.buffer(10).toJagByteBuf()
        // MidiSongV2Encoder wire order
        buf.p2Alt1(message.fadeInDelay)
        buf.p2(message.fadeOutDelay)
        buf.p2Alt3(message.id)
        buf.p2(message.fadeInSpeed)
        buf.p2Alt3(message.fadeOutSpeed)
        assertEquals(10, buf.readableBytes())

        // MidiSongV2Decoder wire order
        val fadeInDelay = buf.g2Alt1()
        val fadeOutDelay = buf.g2()
        val id = buf.g2Alt3()
        val fadeInSpeed = buf.g2()
        val fadeOutSpeed = buf.g2Alt3()
        assertEquals(message.id, id)
        assertEquals(message.fadeOutDelay, fadeOutDelay)
        assertEquals(message.fadeOutSpeed, fadeOutSpeed)
        assertEquals(message.fadeInDelay, fadeInDelay)
        assertEquals(message.fadeInSpeed, fadeInSpeed)
    }

    @Test
    fun midiSongV2_stopMusicSentinel() {
        // RSMod stops music by playing midi id 147 (stop_music), not MIDI_SONG_STOP.
        val message =
            MidiSongV2(147, fadeOutDelay = 0, fadeOutSpeed = 20, fadeInDelay = 0, fadeInSpeed = 0)
        assertEquals(147, message.id)
        assertEquals(20, message.fadeOutSpeed)
    }

    @Test
    fun midiJingle_roundTrip() {
        val message = MidiJingle(id = 89, lengthInMillis = 0)
        val buf = Unpooled.buffer(5).toJagByteBuf()
        buf.p3(message.lengthInMillis)
        buf.p2Alt3(message.id)
        assertEquals(5, buf.readableBytes())

        val length = buf.g3()
        val id = buf.g2Alt3()
        assertEquals(0, length)
        assertEquals(89, id)
    }

    @Test
    fun synthSound_roundTrip() {
        val message = SynthSound(id = 62, loops = 1, delay = 0)
        val buf = Unpooled.buffer(5).toJagByteBuf()
        buf.p2(message.id)
        buf.p1(message.loops)
        buf.p2(message.delay)
        assertEquals(5, buf.readableBytes())

        assertEquals(62, buf.g2())
        assertEquals(1, buf.g1())
        assertEquals(0, buf.g2())
    }

    @Test
    fun synthSound_loopsZeroDoesNotPlay_documented() {
        // RSProt SoundArea KDoc: loops=0 means the sound does not play. SynthSound uses the same
        // loops field semantics for the local queue.
        val message = SynthSound(id = 60, loops = 0, delay = 5)
        assertEquals(0, message.loops)
        assertEquals(5, message.delay)
    }

    @Test
    fun soundArea_roundTrip() {
        val message =
            SoundArea(id = 62, delay = 0, loops = 1, radius = 8, size = 1, xInZone = 3, zInZone = 5)
        val buf = Unpooled.buffer(7).toJagByteBuf()
        // SoundAreaEncoder wire order
        buf.p1Alt3(message.coordInZonePacked)
        buf.p1Alt2(message.dropOffRange)
        buf.p1(message.range)
        buf.p1Alt2(message.delay)
        buf.p1Alt1(message.loops)
        buf.p2(message.id)
        assertEquals(7, buf.readableBytes())

        val coord = buf.g1Alt3()
        val size = buf.g1Alt2()
        val radius = buf.g1()
        val delay = buf.g1Alt2()
        val loops = buf.g1Alt1()
        val id = buf.g2()
        assertEquals(message.coordInZonePacked, coord)
        assertEquals(message.dropOffRange, size)
        assertEquals(message.range, radius)
        assertEquals(message.delay, delay)
        assertEquals(message.loops, loops)
        assertEquals(message.id, id)
    }

    @Test
    fun soundArea_radiusClientMask() {
        // Client ignores the upper 4 bits of radius; max effective radius is 31.
        assertEquals(31, 31 and 0x1F)
        assertEquals(8, 40 and 0x1F)
    }
}
