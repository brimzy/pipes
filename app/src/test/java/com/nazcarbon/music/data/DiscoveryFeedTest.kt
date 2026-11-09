package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class DiscoveryFeedTest {
    private fun song(id: String, artist: String = "Artist $id") = MusicTrack(id, "Song $id", artist, 210, "", "", "https://song/$id")

    @Test fun `cold home uses saved playlists when nothing is currently playing`() {
        val saved = song("saved", "Dream pop artist")
        val seeds = ListeningProfile.seeds(null, emptyList(), emptyList(), listOf(MusicPlaylist("Mine", listOf(saved))))
        val requested = mutableListOf<MusicTrack>()
        val feed = DiscoveryFeed({ seed, region ->
            requested += seed
            assertEquals("PH", region)
            listOf(song("recommended", "Related dream pop artist"))
        }, { error("Generic searches should not replace the saved music") })
        feed.refresh(seeds, emptyList(), "PH")
        assertEquals(listOf(saved), requested)
        assertEquals(listOf("recommended"), feed.snapshot().map { it.id })
    }

    @Test fun `recent listening leads likes and each saved collection contributes seeds`() {
        val recent = song("recent")
        val liked = song("liked")
        val first = (0..99).map { song("first-$it") }
        val second = song("second")
        val seeds = ListeningProfile.seeds(null, listOf(recent), listOf(liked),
            listOf(MusicPlaylist("Large", first), MusicPlaylist("Small", listOf(second))))
        assertEquals(listOf(recent, liked), seeds.take(2))
        assertTrue(second in seeds.take(4))
        assertTrue(first.last() !in seeds) // Sample large collections, don't fill the profile with one import.
    }

    @Test fun `unplayed feed suggestions never become recommendation seeds`() {
        val selected = song("selected")
        val requests = mutableListOf<String>()
        val searches = mutableListOf<String>()
        val feed = DiscoveryFeed({ seed, _ -> requests += seed.id; listOf(song("suggested")) }, { query ->
            searches += query
            listOf(song("search-${searches.size}"))
        })
        feed.refresh(listOf(selected), emptyList())
        feed.next(listOf(selected), emptyList())
        assertEquals(listOf("selected"), requests)
        assertTrue(searches.all { it == selected.artist })
    }

    @Test fun `taste changes discard unrelated home cards and never fall back to generic moods`() {
        val feed = DiscoveryFeed({ seed, _ -> listOf(song("related-${seed.id}")) }, { error("Unexpected generic feed") })
        feed.refresh(listOf(song("old")), emptyList())
        feed.refresh(listOf(song("new")), emptyList())
        assertEquals(listOf("related-new"), feed.snapshot().map { it.id })
    }

    @Test fun `one unavailable source does not hide other personal recommendations`() {
        val feed = DiscoveryFeed({ seed, _ ->
            if (seed.id == "unavailable") error("network")
            listOf(song("good"))
        }, { error("No generic fallback") })
        assertEquals(listOf("good"), feed.refresh(listOf(song("unavailable"), song("available")), emptyList()).map { it.id })
    }

    @Test fun `history persists original identities without storing stream credentials`() {
        val history = (0..110).map { song("$it") }
        val latest = song("50").copy(streamUrl = "https://temporary-stream")
        val recorded = ListeningProfile.record(history, latest)
        val decoded = PlaylistStore.decode(PlaylistStore.encode(listOf(MusicPlaylist("Recent", recorded)))).single().tracks
        assertEquals(100, decoded.size)
        assertEquals(latest.copy(streamUrl = ""), decoded.first())
        assertEquals(1, decoded.count { it.id == "50" })
        assertTrue(ListeningProfile.seeds(null, listOf(song("local").copy(pageUrl = "content://audio/1")), emptyList(), emptyList()).isEmpty())
    }
}
