package com.nazcarbon.music.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal suspend fun <T> retryPlaybackRequest(request: suspend () -> T): T {
    repeat(2) { attempt ->
        try {
            return request()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            delay(1_000L * (attempt + 1))
        }
    }
    return request()
}
