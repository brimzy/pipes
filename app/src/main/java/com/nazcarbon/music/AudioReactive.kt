package com.nazcarbon.music

import androidx.compose.runtime.*
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

/** In-process amplitude data only: never records the microphone or retains audio. */
internal object AudioLevels {
    @Volatile var frame = FloatArray(48)
    @Volatile var updatedAt = 0L
    fun clear() { frame = FloatArray(48); updatedAt = 0L }
}

@UnstableApi
internal class ReactiveAudioProcessor : BaseAudioProcessor() {
    private var spectrum: MusicSpectrum? = null
    private var channel = 0
    private var left = 0f
    private var right = 0f
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET
    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 polls with its shared EMPTY_BUFFER before audio and while draining.
        // Avoid reusing that same zero-capacity buffer as both copy source and destination.
        if (!inputBuffer.hasRemaining()) return
        val copy = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val analyzer = spectrum ?: MusicSpectrum(inputAudioFormat.sampleRate).also { spectrum = it }
        var changed = false
        while (copy.remaining() >= 2) {
            val sample = copy.short / 32768f
            if (channel % 2 == 0) left += sample else right += sample
            channel++
            if (channel == channels) {
                val l = left / ((channels + 1) / 2)
                val r = if (channels == 1) l else right / (channels / 2)
                if (analyzer.accept(l, r)) changed = true
                channel = 0; left = 0f; right = 0f
            }
        }
        if (changed) {
            AudioLevels.frame = analyzer.snapshot()
            AudioLevels.updatedAt = System.nanoTime()
        }
        replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
    }
    override fun onFlush() {
        spectrum = null; channel = 0; left = 0f; right = 0f
        AudioLevels.clear()
    }
    override fun onReset() = onFlush()
}

@Composable
internal fun rememberAudioLevels(active: Boolean): State<FloatArray> {
    val levels = remember { mutableStateOf(FloatArray(48)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (!active) { levels.value = FloatArray(48); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
          while (true) {
            val live = System.nanoTime() - AudioLevels.updatedAt < 500_000_000L
            val target = if (live) AudioLevels.frame else FloatArray(48)
            levels.value = FloatArray(48) { i ->
                val old = levels.value[i]
                old + (target[i] - old) * if (target[i] > old) .8f else .10f
            }
            delay(16)
          }
        }
    }
    return levels
}
