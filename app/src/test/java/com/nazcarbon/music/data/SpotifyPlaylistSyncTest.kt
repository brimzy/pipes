package com.nazcarbon.music.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyPlaylistSyncTest {
    private val preview = SpotifyPlaylistPreview("Spotify", listOf(SpotifyPlaylistTrack("New song", "Artist", "new")), 1)
    private fun saved(id: String = "source") = MusicPlaylist("Saved $id", emptyList(), spotifyPlaylistId = id)

    @Test fun foregroundChecksAreThrottledAndManualRefreshIsImmediate() = runTest {
        var collections = listOf(saved())
        var time = 1_000L
        var calls = 0
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { calls++; preview }, { time })
        sync.refresh()
        assertEquals("New song", collections.single().tracks.single().title)
        sync.refresh()
        assertEquals(1, calls)
        sync.refresh("source", force = true)
        assertEquals(2, calls)
        time += SpotifyPlaylistSync.INTERVAL_MS
        sync.refresh()
        assertEquals(3, calls)
        val restarted = SpotifyPlaylistSync({ collections }, { collections = it }, { calls++; preview }, { time })
        restarted.refresh()
        assertEquals(3, calls)
    }

    @Test fun failedPlaylistKeepsSavedSongsAndDoesNotBlockOtherSourcesOrNextRetry() = runTest {
        var collections = listOf(saved(), saved("other"))
        val before = collections.first()
        var fail = true
        var calls = 0
        var time = 1_000L
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { id ->
            calls++
            if (id == "source" && fail) error("Offline")
            preview
        }, { time })
        assertEquals("Offline", sync.refresh()["source"])
        assertEquals(before, collections.first())
        assertEquals(1, collections.last().tracks.size)
        sync.refresh()
        assertEquals(2, calls)
        fail = false
        time += SpotifyPlaylistSync.INTERVAL_MS
        assertTrue(sync.refresh().isEmpty())
        assertEquals(1, collections.first().tracks.size)
    }

    @Test fun inFlightRefreshPreservesLatestRenameArtworkAndUnrelatedCollectionEdits() = runTest {
        var collections = listOf(saved())
        val gate = CompletableDeferred<SpotifyPlaylistPreview>()
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { gate.await() }, { 1_000L })
        val job = launch { sync.refresh() }
        runCurrent()
        collections = listOf(collections.single().copy(name = "Renamed", artworkUri = "new.gif"), MusicPlaylist("Local", emptyList()))
        gate.complete(preview)
        job.join()
        assertEquals("Renamed", collections.first().name)
        assertEquals("new.gif", collections.first().artworkUri)
        assertEquals("Local", collections.last().name)
        assertEquals(1, collections.first().tracks.size)
    }

    @Test fun deletingDuringFetchDoesNotResurrectThePlaylist() = runTest {
        var collections = listOf(saved())
        val gate = CompletableDeferred<SpotifyPlaylistPreview>()
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { gate.await() })
        val job = launch { sync.refresh() }
        runCurrent()
        collections = emptyList()
        gate.complete(preview)
        job.join()
        assertTrue(collections.isEmpty())
    }

    @Test fun staleRefreshCannotReplaceAnExplicitReimport() = runTest {
        var collections = listOf(saved())
        val gate = CompletableDeferred<SpotifyPlaylistPreview>()
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { gate.await() })
        val job = launch { sync.refresh() }
        runCurrent()
        collections = listOf(collections.single().copy(lastSpotifySync = 5_000, name = "Reimported"))
        val latest = collections
        gate.complete(preview)
        job.join()
        assertEquals(latest, collections)
    }

    @Test fun cancellationOnLeavingForegroundKeepsTheSavedPlaylist() = runTest {
        var collections = listOf(saved())
        val before = collections
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { throw CancellationException() })
        val job = launch { sync.refresh() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(before, collections)
    }

    @Test fun overlappingRefreshRequestsShareTheThrottleAndIncompleteRefreshDoesNotWrite() = runTest {
        var collections = listOf(saved())
        var calls = 0
        val gate = CompletableDeferred<SpotifyPlaylistPreview>()
        val sync = SpotifyPlaylistSync({ collections }, { collections = it }, { calls++; gate.await() }, { 1_000L })
        val first = launch { sync.refresh() }
        val second = launch { sync.refresh() }
        runCurrent()
        assertEquals(1, calls)
        gate.complete(preview.copy(totalTracks = 2))
        first.join()
        second.join()
        assertTrue(collections.single().tracks.isEmpty())
        assertEquals(0L, collections.single().lastSpotifySync)
    }
}
