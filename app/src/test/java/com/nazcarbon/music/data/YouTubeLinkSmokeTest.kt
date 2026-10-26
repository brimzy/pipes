package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeLinkSmokeTest {
    @Test fun `shared YouTube radio link resolves song metadata and a substantial mix`() {
        assumeTrue(System.getenv("PIPES_LIVE_SOURCE_TEST") == "1")
        val source = NewPipeMusicSource().also { it.initialize() }
        val target = YouTubeLinks.parse("https://www.youtube.com/watch?v=0yJ2kIH-Zpk&list=RD0yJ2kIH-Zpk&start_radio=1")!!
        val tracks = source.searchYouTubeLink(target)
        assertEquals(target.videoUrl, tracks.first().pageUrl)
        assertTrue(tracks.first().title.isNotBlank())
        assertTrue("Expected at least ten songs, got ${tracks.size}", tracks.size >= 10)
        assertEquals(tracks.size, tracks.distinctBy { it.pageUrl }.size)
    }
}
