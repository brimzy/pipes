package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class LibrarySongSearchTest {
    private val song = MusicTrack("spotify:new:0", "New Song", "The Artist", 123, "", "", "spotify-search:new")
    @Test fun newlySyncedSongsAppearInSearchWithoutAnOnlineResponse() {
        assertTrue(LibrarySongSearch.merge("song artist", emptyList(), emptyList()).isEmpty())
        val saved = listOf(MusicPlaylist("Spotify", listOf(song)))
        assertEquals(listOf(song), LibrarySongSearch.merge(" SONG artist ", saved, emptyList()))
        assertTrue(LibrarySongSearch.merge("other", saved, emptyList()).isEmpty())
    }
    @Test fun savedMatchesLeadOnlineResultsWithoutRepeatingSongsOrPollutingLinks() {
        val saved = listOf(MusicPlaylist("One", listOf(song)), MusicPlaylist("Two", listOf(song)))
        val online = song.copy(id = "online", pageUrl = "https://youtube.com/watch?v=abc")
        assertEquals(listOf(song, online), LibrarySongSearch.merge("new", saved, listOf(song, online)))
        assertEquals(listOf(online), LibrarySongSearch.merge("https://open.spotify.com/track/2zfRapW1M3LQ6zngjaANgp", saved, listOf(online)))
        assertTrue(LibrarySongSearch.merge("", saved, emptyList()).isEmpty())
    }
}
