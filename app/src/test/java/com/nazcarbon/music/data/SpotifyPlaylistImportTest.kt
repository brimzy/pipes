package com.nazcarbon.music.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SpotifyPlaylistImportTest {
    @Test fun `large embed preserves all rows order and duplicate recordings`() {
        val rows = JSONArray()
        repeat(1464) { index -> rows.put(JSONObject().put("entityType", "track")
            .put("uri", "spotify:track:2zfRapW1M3LQ6zngjaANgp").put("title", "Song $index")
            .put("subtitle", "Artist").put("duration", 210000)) }
        val entity = JSONObject().put("name", "Large playlist").put("trackList", rows)
        val payload = JSONObject().put("props", JSONObject().put("pageProps", JSONObject()
            .put("state", JSONObject().put("data", JSONObject().put("entity", entity)))))
        val parsed = SpotifyPlaylists.parseEmbed("<script id=\"__NEXT_DATA__\" type=\"application/json\">$payload</script>", 1464)
        assertEquals(1464, parsed.tracks.size)
        assertEquals("Song 1463", parsed.tracks.last().title)
        assertEquals(210L, parsed.tracks.first().durationSeconds)
    }

    @Test fun `public page total survives a shorter embed response`() {
        val html = """<meta name="music:song_count" content="1464"/><meta property="og:description" content="Playlist · person · 1,464 items · 1 saves"/>"""
        assertEquals(1464, SpotifyPlaylists.declaredTotal(html))
        assertEquals(1464, SpotifyPlaylists.declaredTotal("""<meta property="og:description" content="Playlist · person · 1,464 items · 1 saves"/>"""))
    }

    @Test fun `live public playlist imports without Spotify authorization`() = runBlocking {
        assumeTrue(System.getenv("PIPES_LIVE_SPOTIFY_TEST") == "1")
        val playlist = SpotifyPlaylists.load("https://open.spotify.com/playlist/2pGRCAw0YXXAJtHC8JmhwB?si=EDDiqY5fT8qpajtQ9tXUJQ")
        assertEquals("Favorites", playlist.name)
        assertTrue("Full playlist must exceed the old 100-song preview", playlist.tracks.size > 1000)
        assertEquals(playlist.totalTracks, playlist.tracks.size + playlist.skippedItems)
        assertTrue(playlist.totalTracks >= playlist.tracks.size)
        assertTrue(playlist.tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() })
        println("Anonymous Spotify import: ${playlist.tracks.size} songs + ${playlist.skippedItems} skipped / ${playlist.totalTracks} items")
        val row = playlist.tracks.first()
        val track = MusicTrack("spotify:${row.id}:0", row.title, row.artist, row.durationSeconds, "", "", "spotify-search:${row.id}")
        val source = NewPipeMusicSource().also { it.initialize() }
        val resolved = source.resolveForPlayback(track)
        assertEquals(track.id, resolved.id)
        assertEquals(track.title, resolved.title)
        assertTrue(resolved.pageUrl.startsWith("https://www.youtube.com/"))
        assertTrue(resolved.streamUrl.startsWith("https://"))
        val radio = source.relatedSongs(resolved)
        assertTrue(radio.isNotEmpty())
        assertTrue(radio.none { it.pageUrl == resolved.pageUrl })
        val connection = java.net.URL(resolved.streamUrl).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.setRequestProperty("Range", "bytes=0-1023")
            assertTrue("Matched song audio HTTP ${connection.responseCode}", connection.responseCode in 200..299)
            connection.inputStream.use { assertTrue(it.read() >= 0) }
        } finally { connection.disconnect() }
        println("Matched Spotify song plays and returns ${radio.size} radio candidates")
    }
}
