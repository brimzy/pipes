package com.nazcarbon.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class AudioReactiveTest {
    private fun tone(frequency: Double, amplitude: Float = .2f, invertedRight: Boolean = false): FloatArray {
        val spectrum = MusicSpectrum(48000)
        repeat(4096) {
            val sample = amplitude * sin(2 * Math.PI * frequency * it / 48000).toFloat()
            spectrum.accept(sample, if (invertedRight) -sample else sample)
        }
        return spectrum.snapshot()
    }
    @Test fun `silence stays silent and clears old spectrum`() {
        val spectrum = MusicSpectrum(48000)
        repeat(4096) { spectrum.accept(sin(it * .2).toFloat()) }
        assertTrue(spectrum.snapshot().any { it > 0f })
        repeat(4096) { spectrum.accept(0f) }
        assertTrue(spectrum.snapshot().all { it == 0f })
    }
    @Test fun `bass mids and treble excite distinct frequency bands`() {
        fun peak(values: FloatArray) = values.indices.maxBy { values[it] }
        val bass = peak(tone(100.0))
        val mids = peak(tone(1000.0))
        val treble = peak(tone(8000.0))
        assertTrue("bass bin $bass", bass < 15)
        assertTrue("mid bin $mids", mids in 20..31)
        assertTrue("treble bin $treble", treble > 35)
        assertTrue(bass < mids && mids < treble)
    }
    @Test fun `spectrum responds to volume and does not cancel opposite stereo channels`() {
        val loud = tone(1000.0, .2f)
        val quiet = tone(1000.0, .02f)
        assertTrue(loud.max() > quiet.max() + .2f)
        assertArrayEquals(loud, tone(1000.0, .2f, true), .0001f)
    }
    @Test fun `analysis waits for complete window then updates with half window overlap`() {
        val spectrum = MusicSpectrum(48000)
        repeat(2047) { assertFalse(spectrum.accept(.1f)) }
        assertTrue(spectrum.accept(.1f))
        repeat(1023) { assertFalse(spectrum.accept(.1f)) }
        assertTrue(spectrum.accept(.1f))
    }
    @Test fun `original waveform remains intact and idle or scrubbing restores exact height`() {
        for (idle in listOf(2f, 7f, 15f)) {
            assertEquals(idle, waveformHalfHeight(idle, 20f, 1f, 0f), 0f)
            assertEquals(idle, waveformHalfHeight(idle, 20f, 0f, 1f), 0f)
            assertTrue(waveformHalfHeight(idle, 20f, .5f, 1f) > idle)
            assertTrue(waveformHalfHeight(idle, 20f, 1f, 1f) <= 20f)
        }
        assertTrue(waveformHalfHeight(2f, 20f, 1f, 1f) < waveformHalfHeight(15f, 20f, 1f, 1f))
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `analysis preserves decoded audio bytes and flush clears levels`() {
        val processor = ReactiveAudioProcessor()
        processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(
            48000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT))
        processor.flush()
        val bytes = ByteArray(8192) { (it % 251).toByte() }
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
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `empty pipeline input before first audio frame does not copy buffer onto itself`() {
        val processor = ReactiveAudioProcessor()
        processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(
            48000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
        assertFalse(processor.output.hasRemaining())
        processor.reset()
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `media pipeline handles startup audio draining and reconfiguration`() {
        val processor = ReactiveAudioProcessor()
        val pipeline = androidx.media3.common.audio.AudioProcessingPipeline(
            com.google.common.collect.ImmutableList.of(processor))
        for (rate in listOf(48000, 44100)) {
            pipeline.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(
                rate, 2, androidx.media3.common.C.ENCODING_PCM_16BIT))
            pipeline.flush()
            assertTrue(pipeline.isOperational)
            repeat(3) { assertFalse(pipeline.output.hasRemaining()) }
            val bytes = ByteArray(8192) { (it % 251).toByte() }
            val input = java.nio.ByteBuffer.allocateDirect(bytes.size)
                .order(java.nio.ByteOrder.nativeOrder()).put(bytes)
            input.flip()
            pipeline.queueInput(input)
            val actual = ByteArray(bytes.size)
            pipeline.output.get(actual)
            assertArrayEquals(bytes, actual)
            repeat(3) { assertFalse(pipeline.output.hasRemaining()) }
            pipeline.queueEndOfStream()
            assertFalse(pipeline.output.hasRemaining())
            assertTrue(pipeline.isEnded)
        }
        pipeline.reset()
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
