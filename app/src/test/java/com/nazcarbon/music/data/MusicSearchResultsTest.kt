package com.nazcarbon.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSearchResultsTest {
    private fun song(id: String, title: String, artist: String) = MusicTrack(id, title, artist, 200, "", "", id)

    @Test fun `Tokyo Weeknd video matches precede partial music catalog matches`() {
        val partial = song("catalog", "Other song", "The Weeknd")
        val match = song("tokyo", "TOKYO (Remix)", "The Weeknd")
        assertEquals(listOf(match, partial), MusicSearchResults.merge("Tokyo weeknd", listOf(listOf(partial), listOf(match))))
    }

    @Test fun `equal relevance interleaves sources and removes duplicate recordings`() {
        val a = song("a", "Tokyo", "Artist")
        val b = song("b", "Tokyo remix", "Artist")
        val c = song("c", "Tokyo live", "Artist")
        assertEquals(listOf(a, b, c), MusicSearchResults.merge("tokyo", listOf(listOf(a, b), listOf(a, c))))
    }
}
