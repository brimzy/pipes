package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueRequestsTest {
    @Test fun `large playlist transfers once without serialization and stale sessions cannot consume it`() {
        val tracks = (0 until 10000).map { MusicTrack("$it", "Song $it", "Artist", 200, "art", "", "spotify-search:$it") }
        val id = PlaybackQueueRequests.register("session", tracks)
        assertTrue(id.length < 100)
        assertEquals(tracks, PlaybackQueueRequests.consume(id, "session"))
        assertNull(PlaybackQueueRequests.consume(id, "session"))
        val stale = PlaybackQueueRequests.register("old", tracks)
        assertNull(PlaybackQueueRequests.consume(stale, "new"))
    }

    @Test fun `unconsumed requests are bounded`() {
        val old = PlaybackQueueRequests.register("old", emptyList())
        repeat(4) { PlaybackQueueRequests.register("new", emptyList()) }
        assertNull(PlaybackQueueRequests.consume(old, "old"))
    }
}
