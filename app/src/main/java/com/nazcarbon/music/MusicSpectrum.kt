package com.nazcarbon.music

import kotlin.math.*

/** Hann-windowed stereo power spectrum. Audio never leaves the playback process. */
internal class MusicSpectrum(private val sampleRate: Int) {
    private val count = 2048
    private val left = FloatArray(count)
    private val right = FloatArray(count)
    private val real = DoubleArray(count)
    private val imaginary = DoubleArray(count)
    private val power = DoubleArray(count / 2)
    private val window = DoubleArray(count) { .5 - .5 * cos(2 * PI * it / (count - 1)) }
    private val bands = FloatArray(48)
    private var cursor = 0
    private var filled = 0
    private var hop = 0

    /** Returns true every ~21ms at 48kHz, after the first complete analysis window. */
    fun accept(l: Float, r: Float = l): Boolean {
        left[cursor] = l
        right[cursor] = r
        cursor = (cursor + 1) % count
        filled = (filled + 1).coerceAtMost(count)
        if (++hop < count / 2 || filled < count) return false
        hop = 0
        power.fill(0.0)
        transform(left)
        transform(right)
        val low = max(40.0, sampleRate.toDouble() / count)
        val high = min(16000.0, sampleRate / 2.0)
        for (band in bands.indices) {
            val from = low * (high / low).pow(band / bands.size.toDouble())
            val to = low * (high / low).pow((band + 1) / bands.size.toDouble())
            val first = floor(from * count / sampleRate).toInt().coerceIn(1, power.lastIndex)
            val last = ceil(to * count / sampleRate).toInt().coerceIn(first, power.lastIndex)
            var peak = 0.0
            for (bin in first..last) peak = max(peak, power[bin])
            // Power combines both channels, including music with opposite stereo polarity.
            val magnitude = sqrt(peak / 2) * 4 / count
            val db = 20 * log10(max(magnitude, 1e-7))
            bands[band] = ((db + 66) / 60).toFloat().coerceIn(0f, 1f)
        }
        return true
    }

    fun snapshot() = bands.copyOf()

    private fun transform(samples: FloatArray) {
        for (i in 0 until count) {
            real[i] = samples[(cursor + i) % count] * window[i]
            imaginary[i] = 0.0
        }
        var j = 0
        for (i in 1 until count) {
            var bit = count shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val swap = real[i]; real[i] = real[j]; real[j] = swap
            }
        }
        var length = 2
        while (length <= count) {
            val angle = -2 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < count) {
                var wr = 1.0
                var wi = 0.0
                for (offset in 0 until length / 2) {
                    val a = start + offset
                    val b = a + length / 2
                    val tr = wr * real[b] - wi * imaginary[b]
                    val ti = wr * imaginary[b] + wi * real[b]
                    real[b] = real[a] - tr; imaginary[b] = imaginary[a] - ti
                    real[a] += tr; imaginary[a] += ti
                    val next = wr * stepReal - wi * stepImaginary
                    wi = wr * stepImaginary + wi * stepReal; wr = next
                }
                start += length
            }
            length *= 2
        }
        for (i in power.indices) power[i] += real[i] * real[i] + imaginary[i] * imaginary[i]
    }
}

/** The original seek silhouette is always present; music adds motion instead of replacing it. */
internal fun waveformHalfHeight(idle: Float, available: Float, level: Float, activity: Float): Float =
    idle + min((available - idle).coerceAtLeast(0f), idle * .85f + available * .15f) *
        level.coerceIn(0f, 1f).pow(1.4f) * activity.coerceIn(0f, 1f)
