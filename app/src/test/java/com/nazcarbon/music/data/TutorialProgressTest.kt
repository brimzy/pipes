package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test
import com.nazcarbon.music.data.TutorialProgress.Mode

class TutorialProgressTest {
    @Test fun firstLaunchOffersTheGuideAndLaterIsRemembered() {
        val first = TutorialProgress.decode(null)
        assertEquals(Mode.WELCOME, first.mode)
        val postponed = TutorialProgress.decode(first.pause().encode())
        assertEquals(Mode.CLOSED, postponed.mode)
        assertFalse(postponed.completed)
        assertEquals(0, postponed.open().lesson)
    }

    @Test fun practicePauseAndRestartPreserveTheLesson() {
        val practicing = TutorialProgress().open().next().next().practice()
        val restored = TutorialProgress.decode(practicing.encode())
        assertEquals(Mode.PRACTICE, restored.mode)
        assertEquals(2, restored.lesson)
        val paused = TutorialProgress.decode(restored.pause().encode())
        assertEquals(2, paused.open().lesson)
        assertEquals(Mode.GUIDE, paused.open().mode)
        assertEquals(1, paused.open().back().lesson)
    }

    @Test fun finishStaysDismissedAcrossRestartsAndReplayStartsAtTheBeginning() {
        var progress = TutorialProgress().open()
        repeat(TutorialProgress.LESSON_COUNT) { progress = progress.next() }
        val finished = TutorialProgress.decode(progress.encode())
        assertTrue(finished.completed)
        assertEquals(Mode.CLOSED, finished.mode)
        assertEquals(0, finished.open().lesson)
        assertEquals(Mode.GUIDE, finished.open().mode)
        assertEquals(0, finished.open().back().lesson)
        val replayPaused = TutorialProgress.decode(finished.open().next().pause().encode())
        assertEquals(1, replayPaused.open().lesson)
        assertFalse(replayPaused.completed)
    }

    @Test fun damagedOrOutOfRangeSavedProgressCannotCrashOrTrapTheUser() {
        assertEquals(Mode.CLOSED, TutorialProgress.decode("invalid").mode)
        assertEquals(0, TutorialProgress.decode("""{"lesson":-10,"mode":"GUIDE"}""").lesson)
        assertEquals(TutorialProgress.LESSON_COUNT - 1, TutorialProgress.decode("""{"lesson":999,"mode":"GUIDE"}""").lesson)
        assertEquals(Mode.CLOSED, TutorialProgress.decode("""{"lesson":2,"mode":"removed"}""").mode)
    }
}
