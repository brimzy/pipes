package com.nazcarbon.music.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackRetryTest {
    @Test fun transientFailuresKeepTheSong() = runTest {
        var calls = 0
        val song = retryPlaybackRequest {
            calls++
            if (calls < 3) throw java.io.IOException("offline")
            "song"
        }
        assertEquals("song", song)
        assertEquals(3, calls)
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test fun permanentFailuresAreBounded() = runTest {
        var calls = 0
        try {
            retryPlaybackRequest<Unit> { calls++; throw java.io.IOException("unavailable") }
            fail("Expected error")
        } catch (_: java.io.IOException) {
            assertEquals(3, calls)
        }
    }

    @Test fun cancellationNeverRetries() = runTest {
        var calls = 0
        try {
            retryPlaybackRequest<Unit> { calls++; throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
            assertEquals(0L, testScheduler.currentTime)
        }
    }
}
