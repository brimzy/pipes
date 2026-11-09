package com.nazcarbon.music.data

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PlaybackStreamsTest {
    private val song = MusicTrack("spotify:1", "夜 — A & B", "Artist", 225, "https://art/1", "https://expired/audio", "spotify-search:A & B")

    @Test fun `queue encoding retains identity and source but discards expiring stream URLs`() {
        val encoded = PlaybackStreams.encode(song)
        assertFalse(encoded.contains("expired"))
        assertEquals(song.copy(streamUrl = ""), PlaybackStreams.decode(encoded))
        assertNull(PlaybackStreams.decode("content://audio/1"))
    }

    @Test fun `a thousand queued tracks resolve only as they are loaded`() {
        var calls = 0
        val cache = PlaybackStreamCache { calls++; it.copy(streamUrl = "https://cdn/fresh-$calls") }
        val queue = (1..1000).map { PlaybackStreams.encode(song.copy(id = "$it")) }
        assertEquals(0, calls)
        assertEquals("https://cdn/fresh-1", cache.resolve(queue.first()))
        assertEquals("https://cdn/fresh-1", cache.resolve(queue.first()))
        assertEquals(1, calls)
        assertEquals("https://cdn/fresh-2", cache.resolve(queue.last()))
    }

    @Test fun `expired and rejected stream addresses are refreshed without changing queue identity`() {
        var clock = 0L
        var calls = 0
        val cache = PlaybackStreamCache({ clock }) { calls++; it.copy(streamUrl = "https://cdn/$calls") }
        val uri = PlaybackStreams.encode(song)
        assertEquals("https://cdn/1", cache.resolve(uri))
        clock = 5 * 60_000L
        assertEquals("https://cdn/2", cache.resolve(uri))
        cache.invalidate(uri)
        assertEquals("https://cdn/3", cache.resolve(uri))
        assertEquals(song.copy(streamUrl = ""), PlaybackStreams.decode(uri))
    }

    @Test fun `downloads and local sources remain playable without online resolution`() {
        var calls = 0
        val cache = PlaybackStreamCache { calls++; it.copy(streamUrl = "content://download/1") }
        assertEquals("content://download/1", cache.resolve(PlaybackStreams.encode(song)))
        assertEquals("file:///local.wav", cache.resolve("file:///local.wav"))
        assertEquals(1, calls)
    }

    @Test fun `resolver failures become retryable IO errors and are not cached`() {
        var calls = 0
        val cache = PlaybackStreamCache {
            calls++
            if (calls == 1) error("offline")
            it.copy(streamUrl = "https://reconnected")
        }
        val uri = PlaybackStreams.encode(song)
        assertThrows(IOException::class.java) { cache.resolve(uri) }
        assertEquals("https://reconnected", cache.resolve(uri))
    }
}
