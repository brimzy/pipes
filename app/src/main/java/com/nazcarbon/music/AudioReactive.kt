package com.nazcarbon.music

import androidx.compose.runtime.*
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

/** In-process amplitude data only: never records the microphone or retains audio. */
internal object AudioLevels {
    @Volatile var frame = FloatArray(48)
    @Volatile var updatedAt = 0L
    fun clear() { frame = FloatArray(48); updatedAt = 0L }
}

internal class PcmEnvelope {
    private val history = FloatArray(48)
    private var cursor = 0
    private var sum = 0.0
    private var samples = 0
    fun accept(sample: Float, window: Int): Boolean {
        sum += sample * sample
        samples++
        if (samples < window) return false
        history[cursor] = (sqrt(sum / samples).toFloat() * 3f).coerceIn(0f, 1f)
        cursor = (cursor + 1) % history.size
        sum = 0.0; samples = 0
        return true
    }
    fun snapshot() = FloatArray(history.size) { history[(cursor + it) % history.size] }
}

@UnstableApi
internal class ReactiveAudioProcessor : BaseAudioProcessor() {
    private var envelope = PcmEnvelope()
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET
    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 polls with its shared EMPTY_BUFFER before audio and while draining.
        // Avoid reusing that same zero-capacity buffer as both copy source and destination.
        if (!inputBuffer.hasRemaining()) return
        val copy = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val window = (inputAudioFormat.sampleRate / 120).coerceAtLeast(1) * inputAudioFormat.channelCount
        var changed = false
        while (copy.remaining() >= 2) {
            if (envelope.accept(copy.short / 32768f, window)) changed = true
        }
        if (changed) {
            AudioLevels.frame = envelope.snapshot()
            AudioLevels.updatedAt = System.nanoTime()
        }
        replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
    }
    override fun onFlush() { envelope = PcmEnvelope(); AudioLevels.clear() }
    override fun onReset() { envelope = PcmEnvelope(); AudioLevels.clear() }
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
                old + (target[i] - old) * if (target[i] > old) .72f else .24f
            }
            delay(33)
          }
        }
    }
    return levels
}
