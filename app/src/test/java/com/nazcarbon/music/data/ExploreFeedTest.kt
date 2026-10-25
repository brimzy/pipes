package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class ExploreFeedTest {
    private fun track(id: String) = MusicTrack(id, id, "Artist", 120, "", "", "https://example.com/$id")

    @Test fun `returning to a sound reuses its cached recordings`() {
        var calls = 0
        val feed = ExploreFeed { calls++; listOf(track("a")) }
        assertEquals(feed.load(" Jazz "), feed.load("jazz"))
        assertEquals(1, calls)
    }

    @Test fun `refresh replaces the cached selection`() {
        var revision = 0
        val feed = ExploreFeed { listOf(track("revision-${++revision}")) }
        assertEquals("revision-1", feed.load("soul").single().id)
        assertEquals("revision-2", feed.load("soul", refresh = true).single().id)
        assertEquals("revision-2", feed.load("soul").single().id)
        assertEquals(2, revision)
    }

    @Test fun `preview excludes duplicate recordings and limits its size`() {
        val feed = ExploreFeed { (1..20).flatMap { listOf(track("$it"), track("$it")) } }
        assertEquals((1..12).map { "$it" }, feed.load("indie").map { it.id })
    }

    @Test fun `failed refresh preserves the previous cache and can retry`() {
        var fail = false
        val feed = ExploreFeed { if (fail) error("offline") else listOf(track("saved")) }
        feed.load("rock")
        fail = true
        assertThrows(IllegalStateException::class.java) { feed.load("rock", refresh = true) }
        assertEquals("saved", feed.load("rock").single().id)
        fail = false
        assertEquals("saved", feed.load("rock", refresh = true).single().id)
    }

    @Test fun `empty results remain retryable`() {
        var calls = 0
        val feed = ExploreFeed { calls++; if (calls == 1) emptyList() else listOf(track("recovered")) }
        assertTrue(feed.load("ambient").isEmpty())
        assertEquals("recovered", feed.load("ambient").single().id)
        assertEquals(2, calls)
    }

    @Test fun `cache evicts the least recently visited sound`() {
        val calls = mutableListOf<String>()
        val feed = ExploreFeed { calls += it; listOf(track(it)) }
        (1..8).forEach { feed.load("sound$it") }
        feed.load("sound1")
        feed.load("sound9")
        feed.load("sound1")
        assertEquals(9, calls.size)
        feed.load("sound2")
        assertEquals(10, calls.size)
    }
}
