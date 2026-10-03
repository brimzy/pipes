package com.nazcarbon.music.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongSearchTest {
    private fun track(id: String) = MusicTrack(id, id, "Artist", 120, "", "", id)

    @Test fun `late old result cannot replace a newer search`() = runTest {
        var state = SongSearch.State()
        val search = SongSearch(this, { query ->
            withContext(NonCancellable) { delay(if (query == "old") 2000 else 100) }
            listOf(track(query))
        }) { state = it }
        search.submit("old")
        advanceTimeBy(401)
        search.submit("new")
        advanceUntilIdle()
        assertEquals(listOf("new"), state.tracks.map { it.id })
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test fun `clearing search prevents a late failure and loading reset`() = runTest {
        var state = SongSearch.State()
        val search = SongSearch(this, {
            withContext(NonCancellable) { delay(1000); error("offline") }
        }) { state = it }
        search.submit("old")
        advanceTimeBy(401)
        search.submit("")
        advanceUntilIdle()
        assertEquals(SongSearch.State(), state)
    }

    @Test fun `rapid input is debounced and duplicate songs have one row`() = runTest {
        val calls = mutableListOf<String>()
        var state = SongSearch.State()
        val search = SongSearch(this, { calls += it; listOf(track("same"), track("same")) }) { state = it }
        search.submit("a")
        advanceTimeBy(200)
        search.submit("ab")
        advanceTimeBy(200)
        search.submit(" abc ")
        advanceUntilIdle()
        assertEquals(listOf("abc"), calls)
        assertEquals(1, state.tracks.size)
    }

    @Test fun `retry recovers after a network failure`() = runTest {
        var fail = true
        var state = SongSearch.State()
        val search = SongSearch(this, { if (fail) error("offline") else listOf(track(it)) }) { state = it }
        search.submit("song")
        advanceUntilIdle()
        assertNotNull(state.error)
        assertFalse(state.loading)
        fail = false
        search.submit("song")
        runCurrent()
        assertTrue(state.loading)
        assertNull(state.error)
        advanceUntilIdle()
        assertEquals("song", state.tracks.single().id)
    }
}
