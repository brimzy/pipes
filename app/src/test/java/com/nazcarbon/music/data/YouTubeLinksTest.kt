package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class YouTubeLinksTest {
    @Test fun `watch radio link preserves both seed and mix`() {
        val target = YouTubeLinks.parse("https://www.youtube.com/watch?v=0yJ2kIH-Zpk&list=RD0yJ2kIH-Zpk&start_radio=1")!!
        assertEquals("https://www.youtube.com/watch?v=0yJ2kIH-Zpk", target.videoUrl)
        assertEquals("RD0yJ2kIH-Zpk", target.playlistId)
        assertTrue(target.isRadio)
    }

    @Test fun `recognizes short music mobile shorts live and embed links`() {
        listOf("youtu.be/0yJ2kIH-Zpk?si=share", "music.youtube.com/watch?v=0yJ2kIH-Zpk",
            "m.youtube.com/watch?feature=share&v=0yJ2kIH-Zpk", "www.youtube.com/shorts/0yJ2kIH-Zpk",
            "youtube.com/live/0yJ2kIH-Zpk", "youtube.com/embed/0yJ2kIH-Zpk").forEach {
            assertEquals(it, "0yJ2kIH-Zpk", YouTubeLinks.parse(it)?.videoId)
        }
    }

    @Test fun `playlist links work with and without a video`() {
        assertEquals("PL_music123", YouTubeLinks.parse("https://youtube.com/playlist?list=PL_music123")?.playlistId)
        assertEquals("PL_music123", YouTubeLinks.parse("https://youtube.com/watch?v=0yJ2kIH-Zpk&list=PL_music123")?.playlistId)
    }

    @Test fun `ordinary searches malformed links and lookalike hosts stay out of link routing`() {
        listOf("a song on youtube", "https://youtube.com.evil.test/watch?v=0yJ2kIH-Zpk",
            "https://evil.test/youtube.com/watch?v=0yJ2kIH-Zpk", "https://youtube.com/watch?v=oops",
            "https://youtube.com@evil.test/watch?v=0yJ2kIH-Zpk", "https://youtube.com/watch?v=%ZZ").forEach {
            assertNull(it, YouTubeLinks.parse(it))
        }
    }
}
