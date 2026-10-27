package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class PlayNextQueueTest {
    @Test fun `only reuse an occurrence after the current song`() {
        val ids = listOf("chosen", "current", "other", "chosen")
        assertEquals(3, PlayNextQueue.existingUpcomingIndex(ids, 1, "chosen"))
        assertNull(PlayNextQueue.existingUpcomingIndex(ids, 3, "chosen"))
        assertNull(PlayNextQueue.existingUpcomingIndex(ids, 1, "current"))
        assertNull(PlayNextQueue.existingUpcomingIndex(ids, 1, "new"))
    }

    @Test fun `shuffle plays the requested song next and preserves every other entry`() {
        val order = listOf(4, 2, 0, 3, 1)
        val updated = PlayNextQueue.prioritize(order, current = 2, next = 1)
        assertEquals(listOf(4, 2, 1, 0, 3), updated)
        assertEquals(order.toSet(), updated.toSet())
        assertEquals(order.filterNot { it == 1 }, updated.filterNot { it == 1 })
    }

    @Test fun `play next works from the last position in the shuffled order`() {
        assertEquals(listOf(4, 0, 3, 1, 2),
            PlayNextQueue.prioritize(listOf(4, 2, 0, 3, 1), current = 1, next = 2))
    }

    @Test fun `prioritizing the next song again does not duplicate or reorder the queue`() {
        val order = listOf(3, 0, 2, 1)
        assertEquals(order, PlayNextQueue.prioritize(order, current = 0, next = 2))
    }
}
