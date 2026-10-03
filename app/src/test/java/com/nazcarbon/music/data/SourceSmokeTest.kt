package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Opt-in network check: PIPES_LIVE_SOURCE_TEST=1 gradle :app:testDebugUnitTest. */
class SourceSmokeTest {
    @Test fun `search and audio extraction work against the live source`() {
        assumeTrue(System.getenv("PIPES_LIVE_SOURCE_TEST") == "1")
        val source = NewPipeMusicSource().also { it.initialize() }
        val tracks = source.searchSongs("Daft Punk Get Lucky")
        assertTrue("No search results returned", tracks.isNotEmpty())
        assertEquals(tracks.size, tracks.distinctBy { it.id }.size)
        val resolved = source.resolveAudio(tracks.first())
        assertTrue(resolved.streamUrl.startsWith("https://"))
        assertTrue(resolved.audioQuality.isNotBlank())
        val connection = URL(resolved.streamUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Range", "bytes=0-1023")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            assertTrue("Audio URL returned ${connection.responseCode}", connection.responseCode in 200..299)
            connection.inputStream.use { assertTrue("Empty audio response", it.read() >= 0) }
        } finally { connection.disconnect() }
        val related = source.relatedSongs(tracks.first())
        assertTrue("No radio recommendations returned", related.isNotEmpty())
        assertTrue(related.none { it.id == tracks.first().id })
    }
}
