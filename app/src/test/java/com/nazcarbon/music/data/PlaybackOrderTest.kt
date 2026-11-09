package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackOrderTest {
    private fun song(id: Int) = MusicTrack("$id", "Song $id", "Artist", 200, "", "", "https://song/$id")

    @Test fun `shuffle tail is counted in playback order rather than physical index`() {
        val order = listOf(0, 5, 2, 4, 1, 3)
        assertEquals(listOf(2, 4, 1, 3), PlaybackOrder.upcoming(order, 5))
        assertTrue(PlaybackOrder.upcoming(order, 3).isEmpty())
        assertEquals(1, PlaybackOrder.existingUpcoming((0..5).map(Int::toString), order, 5, "1"))
        assertNull(PlaybackOrder.existingUpcoming((0..5).map(Int::toString), order, 3, "5"))
    }

    @Test fun `shuffled playlist includes all thousand songs even when starting near the end`() {
        val songs = (0..999).map(::song)
        val queue = PlaybackOrder.sequence(songs, songs[990], shuffle = true)
        assertEquals(1000, queue.size)
        assertEquals(songs[990], queue.first())
        assertEquals(songs.toSet(), queue.toSet())
    }

    @Test fun `normal playlist starts at selected song and preserves displayed order`() {
        val songs = (0..30).map(::song)
        assertEquals(songs.drop(10), PlaybackOrder.sequence(songs, songs[10], shuffle = false))
    }

    @Test fun `disabling shuffle retains played prefix and every unplayed song`() {
        val shuffled = listOf(0, 5, 2, 4, 1, 3)
        val desired = shuffled.take(2) + PlaybackOrder.upcoming(shuffled, 5).sorted()
        val physical = shuffled.indices.toMutableList()
        PlaybackOrder.movesTo(desired).forEach { (from, to) -> physical.add(to, physical.removeAt(from)) }
        assertEquals(listOf(0, 5, 1, 2, 3, 4), physical)
        assertEquals(setOf(1, 2, 3, 4), PlaybackOrder.upcoming(physical, 5).toSet())
    }

    @Test fun `upcoming does not wrap or fabricate entries for empty queues`() {
        assertTrue(PlaybackOrder.upcoming(emptyList(), -1).isEmpty())
        assertTrue(PlaybackOrder.upcoming(listOf(0), 0).isEmpty())
    }
}
