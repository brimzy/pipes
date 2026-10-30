package com.nazcarbon.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerNavigationTest {
    @Test fun playingFromEachSourceReturnsToThatSource() {
        for (page in listOf("Home", "Search", "Explore", "Library", "Playlist", "Release")) {
            assertEquals(page, PlayerNavigation.origin(page, "Home"))
        }
    }

    @Test fun radioFromQueueAndReopeningPlayerPreserveTheBrowsingOrigin() {
        assertEquals("Library", PlayerNavigation.origin("Up next", "Library"))
        assertEquals("Search", PlayerNavigation.origin("Now playing", "Search"))
    }

    @Test fun stalePlayerOriginCannotReturnToAnEmptyPlayerAfterStop() {
        assertEquals("Home", PlayerNavigation.origin("Up next", "Now playing"))
        assertEquals("Home", PlayerNavigation.origin("Now playing", "Up next"))
    }
}
