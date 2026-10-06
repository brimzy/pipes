package com.nazcarbon.music.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpotifyGuestPlaylistTest {
    private val songId = "2zfRapW1M3LQ6zngjaANgp"
    private fun page(offset: Int, total: Int = 1464, revision: String = "revision") : SpotifyGuestPlaylist.Page {
        val count = minOf(343, total - offset)
        val tracks = (offset until offset + count).map { SpotifyPlaylistTrack("Song $it", "Artist", songId, 200, position = it) }
        return SpotifyGuestPlaylist.Page("Favorites", total, offset, count, revision,
            (offset until offset + count).map { "row$it" }, tracks, 0)
    }

    @Test fun `pagination preserves order across out of order completion and limits concurrency`() = runTest {
        var active = 0
        var peak = 0
        val requested = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        val result = SpotifyGuestPlaylist.loadPages({ count, _ -> progress += count }) { offset ->
            requested += offset
            active++
            peak = maxOf(peak, active)
            delay(if (offset == 343) 100 else 10)
            active--
            page(offset)
        }
        assertEquals(listOf(0, 343, 686, 1029, 1372), requested)
        assertEquals(3, peak)
        assertEquals(1464, result.tracks.size)
        assertEquals((0 until 1464).toList(), result.tracks.map { it.position })
        assertEquals(progress.sorted(), progress)
        assertEquals(1464, progress.last())
    }

    @Test fun `missing or changed pages fail instead of returning a partial import`() = runTest {
        suspend fun failure(fetch: suspend (Int) -> SpotifyGuestPlaylist.Page) {
            var failed = false
            try { SpotifyGuestPlaylist.loadPages(fetch = fetch) } catch (_: IllegalArgumentException) { failed = true }
            assertTrue("Incomplete import must fail", failed)
        }
        failure { offset -> if (offset == 343) page(offset).copy(rowCount = 0, tracks = emptyList()) else page(offset) }
        failure { offset -> page(offset, revision = if (offset == 343) "changed" else "revision") }
        failure { offset -> page(offset).copy(offset = 0) }
        failure { offset -> page(offset).copy(rowIds = page(0).rowIds.take(minOf(343, 1464 - offset))) }
    }

    @Test fun `guest configuration is discovered from current public bundle and JS escapes decoded`() {
        val hash = "a".repeat(64)
        val js = """[{secret:'older',version:1},{secret:'new\x21\u0041\\\'"',version:2}];x("fetchPlaylist","query","$hash",null)"""
        val bootstrap = SpotifyGuestPlaylist.parseBootstrap("current-version", js)
        assertEquals("new!A\\'\"", bootstrap.publicValue)
        assertEquals(2, bootstrap.valueVersion)
        assertEquals(hash, bootstrap.playlistHash)
        assertEquals("392919", SpotifyGuestPlaylist.guestCode("public-client-value", 1234567890))
    }

    @Test fun `parser preserves duplicate tracks positions collaborations and skips restricted content`() {
        val json = JSONObject("""{"data":{"playlistV2":{"__typename":"Playlist","name":"Favorites","revisionId":"revision","content":{"totalCount":3,"pagingInfo":{"offset":343},"items":[
            {"uid":"first","itemV2":{"data":{"__typename":"Track","uri":"spotify:track:$songId","name":"Song","trackDuration":{"totalMilliseconds":210000},"artists":{"items":[{"profile":{"name":"Artist"}},{"profile":{"name":"Guest"}}]},"albumOfTrack":{"coverArt":{"sources":[{"width":64,"url":"small"},{"width":640,"url":"large"}]}}}}},
            {"uid":"restricted","itemV2":{"data":{"__typename":"RestrictedContent"}}},
            {"uid":"duplicate","itemV2":{"data":{"__typename":"Track","uri":"spotify:track:$songId","name":"Song","artists":{"items":[{"profile":{"name":"Artist"}}]}}}}
        ]}}}}""")
        val parsed = SpotifyGuestPlaylist.parsePage(json, 343)
        assertEquals(3, parsed.rowCount)
        assertEquals(1, parsed.skipped)
        assertEquals(listOf(343, 345), parsed.tracks.map { it.position })
        assertEquals("Artist, Guest", parsed.tracks.first().artist)
        assertEquals("large", parsed.tracks.first().thumbnailUrl)
        assertEquals(210L, parsed.tracks.first().durationSeconds)
    }
}
