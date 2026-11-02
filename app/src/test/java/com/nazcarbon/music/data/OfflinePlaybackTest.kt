package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class OfflinePlaybackTest {
    private val saved = MusicTrack("spotify:track:one", "Title", "Artist", 180, "cover", "expired-stream", "spotify-search:title")

    @Test fun downloadedSongPlaysWithoutCallingTheOnlineSourceAndKeepsItsLibraryIdentity() {
        val result = OfflinePlayback.resolve(saved, { id ->
            assertEquals(saved.id, id)
            "content://downloads/my_downloads/42"
        }, { error("Offline playback must not contact the source") })
        assertEquals(saved.id, result.id)
        assertEquals(saved.pageUrl, result.pageUrl)
        assertEquals(saved.title, result.title)
        assertEquals(saved.thumbnailUrl, result.thumbnailUrl)
        assertEquals("content://downloads/my_downloads/42", result.streamUrl)
        assertTrue(result.audioQuality.startsWith("Offline"))
    }

    @Test fun absentOrDeletedDownloadsResolveOnlineInsteadOfUsingAnExpiredStream() {
        var requests = 0
        val result = OfflinePlayback.resolve(saved, { null }, {
            requests++
            it.copy(streamUrl = "fresh-stream")
        })
        assertEquals(1, requests)
        assertEquals("fresh-stream", result.streamUrl)
    }

    @Test fun localFilesBypassDownloadLookupAndOnlineResolution() {
        for (uri in listOf("content://music/1", "file:///music/song.flac")) {
            val result = OfflinePlayback.resolve(saved.copy(pageUrl = uri), { error("No lookup for local files") }, { error("No online request") })
            assertEquals(uri, result.streamUrl)
        }
    }

    @Test fun queuedPlaylistSongsResolveLocallyInTheRequestedOrder() {
        val tracks = (1..12).map { saved.copy(id = "song:$it", title = "Song $it") }
        val resolved = tracks.map { track -> OfflinePlayback.resolve(track, { "content://downloads/${it.substringAfter(':')}" }, { error("Queue must work offline") }) }
        assertEquals(tracks.map { it.id }, resolved.map { it.id })
        assertEquals((1..12).map { "content://downloads/$it" }, resolved.map { it.streamUrl })
    }
}
