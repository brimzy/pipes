package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class SpotifyLinksTest {
    @Test fun `shared playlist link routes to playlist import and preserves its ID`() {
        val link = "https://open.spotify.com/playlist/2pGRCAw0YXXAJtHC8JmhwB?si=EDDiqY5fT8qpajtQ9tXUJQ"
        assertTrue(SpotifyLinks.isSpotifyLink(link))
        assertTrue(SpotifyPlaylists.isPlaylistLink(link))
        assertEquals("2pGRCAw0YXXAJtHC8JmhwB", SpotifyPlaylists.playlistId(link))
    }

    private val id = "0VjIjW4GlUZAMYd2vXMi3b"

    @Test fun `Spotify track links normalize before lookup`() {
        val expected = "https://open.spotify.com/track/$id"
        assertEquals(expected, SpotifyLinks.canonicalTrackUrl("https://open.spotify.com/track/$id?si=shared"))
        assertEquals(expected, SpotifyLinks.canonicalTrackUrl("open.spotify.com/intl-en/track/$id"))
        assertEquals(expected, SpotifyLinks.canonicalTrackUrl("spotify:track:$id"))
    }

    @Test fun `non tracks and lookalike hosts are rejected`() {
        assertFalse(SpotifyLinks.isSpotifyLink("https://open.spotify.com.evil.test/track/$id"))
        assertTrue(SpotifyLinks.isSpotifyLink("https://open.spotify.com/album/$id"))
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyLinks.canonicalTrackUrl("https://open.spotify.com/album/$id")
        }
    }

    @Test fun `artist is read from Spotify page title`() {
        assertEquals("The Weeknd", SpotifyLinks.artistFromPage("<title>Blinding Lights - song and lyrics by The Weeknd | Spotify</title>"))
        assertEquals("Artist & Friend", SpotifyLinks.artistFromPage("<title>Song - song by Artist &amp; Friend | Spotify</title>"))
    }

    @Test fun `public playlist rows become importable searches`() {
        val html = """
            <meta property="og:title" content="girlypop"/>
            <meta property="og:description" content="Playlist · nj · 128 items"/>
            <div data-testid="track-row">
              <a href="/track/1DwscornXpj8fmOmYVlqZt">
                <p data-encore-id="listRowTitle"><span>Stateside + Zara Larsson</span></p>
              </a>
              <span data-testid="internal-artist-link"><a href="/artist/test">PinkPantheress</a></span>
            </div>
        """.trimIndent()

        val playlist = SpotifyPlaylists.parsePage(html)
        assertEquals("girlypop", playlist.name)
        assertEquals(128, playlist.totalTracks)
        assertEquals(listOf(SpotifyPlaylistTrack("Stateside + Zara Larsson", "PinkPantheress")), playlist.tracks)
    }
}
