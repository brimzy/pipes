package com.nazcarbon.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class AudioReactiveTest {
    @Test fun `silence stays silent`() {
        val envelope = PcmEnvelope()
        repeat(4096) { envelope.accept(0f, 64) }
        assertTrue(envelope.snapshot().all { it == 0f })
    }
    @Test fun `audio energy follows input amplitude without depending on polarity`() {
        fun level(amplitude: Float): Float {
            val envelope = PcmEnvelope()
            repeat(64) { envelope.accept(amplitude * sin(it * Math.PI / 8).toFloat(), 64) }
            return envelope.snapshot().last()
        }
        assertTrue(level(.2f) > level(.05f) * 3f)
        assertEquals(level(.2f), level(-.2f), .0001f)
    }
    @Test fun `loud input clamps and silence replaces old energy`() {
        val envelope = PcmEnvelope()
        repeat(64) { envelope.accept(1f, 64) }
        assertEquals(1f, envelope.snapshot().last(), 0f)
        repeat(48 * 64) { envelope.accept(0f, 64) }
        assertTrue(envelope.snapshot().all { it == 0f })
    }
    @Test fun `partial input windows are carried across buffers`() {
        val envelope = PcmEnvelope()
        repeat(63) { assertFalse(envelope.accept(.1f, 64)) }
        assertTrue(envelope.accept(.1f, 64))
        assertEquals(.3f, envelope.snapshot().last(), .0001f)
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `analysis preserves decoded audio bytes and flush clears levels`() {
        val processor = ReactiveAudioProcessor()
        processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(
            48000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT))
        processor.flush()
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val input = java.nio.ByteBuffer.allocateDirect(bytes.size).put(bytes)
        input.flip()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output
        val actual = ByteArray(output.remaining())
        output.get(actual)
        assertArrayEquals(bytes, actual)
        assertTrue(AudioLevels.frame.any { it > 0f })
        processor.flush()
        assertTrue(AudioLevels.frame.all { it == 0f })
        processor.reset()
    }
    @Test fun `mini player left skips next and right skips previous only after threshold`() {
        assertEquals(1, trackSwipeDirection(-56f, 56f, true))
        assertEquals(-1, trackSwipeDirection(56f, 56f, true))
        assertEquals(0, trackSwipeDirection(-55f, 56f, true))
        assertEquals(0, trackSwipeDirection(55f, 56f, true))
        assertEquals(0, trackSwipeDirection(-90f, 56f, false))
        assertEquals(-1, trackSwipeDirection(90f, 56f, false))
    }
}
