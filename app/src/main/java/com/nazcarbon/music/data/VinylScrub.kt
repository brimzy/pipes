package com.nazcarbon.music.data

internal fun vinylAngleDelta(previous: Float, current: Float): Float =
    ((current - previous + 540f) % 360f + 360f) % 360f - 180f

internal fun vinylSeekPosition(start: Long, degrees: Float, duration: Long): Long =
    (start + (degrees / 360f * 30_000).toLong()).coerceIn(0, duration.coerceAtLeast(0))
