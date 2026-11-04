package com.nazcarbon.music.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotifyPlaylistSyncLiveTest {
    @Test fun freshPublicPlaylistRefreshKeepsAllRowsAndExistingSongIdentities() = runBlocking {
        assumeTrue(System.getenv("PIPES_LIVE_SPOTIFY_TEST") == "1")
        val id = "2pGRCAw0YXXAJtHC8JmhwB"
        val link = "spotify:playlist:$id"
        val initial = SpotifyPlaylists.load(link, forceRefresh = true)
        assertTrue(initial.tracks.isNotEmpty())
        var saved = listOf(SpotifyPlaylistUpdates.import(emptyList(), id, initial, System.currentTimeMillis()))
        var fresh: SpotifyPlaylistPreview? = null
        val sync = SpotifyPlaylistSync({ saved }, { saved = it }, { source ->
            SpotifyPlaylists.load("spotify:playlist:$source", forceRefresh = true).also { fresh = it }
        })
        assertTrue(sync.refresh(id, force = true).isEmpty())
        val received = requireNotNull(fresh)
        val updated = saved.single()
        assertEquals(received.totalTracks, updated.spotifyTrackIds.size + updated.sourceSkipped)
        assertEquals(received.tracks.map { it.title }, updated.tracks.map { it.title })
        val firstRows = updated.tracks.distinctBy { it.pageUrl }
        val originalFirstRows = SpotifyPlaylistUpdates.import(emptyList(), id, initial, 0).tracks.distinctBy { it.pageUrl }
            .associate { it.pageUrl to it.id }
        firstRows.forEach { track -> originalFirstRows[track.pageUrl]?.let { assertEquals(it, track.id) } }
        println("Fresh Spotify sync: ${updated.tracks.size} songs + ${updated.sourceSkipped} skipped / ${updated.sourceTotal} items")
    }
}
