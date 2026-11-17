package com.nazcarbon.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSwipeTest {
    @Test
    fun dispatchesOnlyTheActionAtTheFinalDragDistance() {
        val addThreshold = 64f
        val nextThreshold = 112f
        val distances = listOf(-63f, -64f, -112f, -110f, 0f, 63f, 64f, 150f)
        val actions = distances.map { queueSwipeAction(it, addThreshold, nextThreshold) }

        assertEquals(QueueSwipeAction.ADD, actions[1])
        assertEquals(QueueSwipeAction.PLAY_NEXT, actions[2])
        assertEquals(QueueSwipeAction.ADD, actions[3])
        assertEquals(QueueSwipeAction.REMOVE, actions[6])
        assertEquals(QueueSwipeAction.REMOVE, actions[7])
        listOf(0, 4, 5).forEach { assertNull(actions[it]) }
    }
}
