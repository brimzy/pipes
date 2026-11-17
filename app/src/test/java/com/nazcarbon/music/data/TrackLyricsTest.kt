package com.nazcarbon.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLyricsTest {
    @Test
    fun appliesRepeatedTimestampsAndOffsetsInPlaybackOrder() {
        val lyrics = TrackLyrics.parse(
            """
            [ar:Example Artist]
            [00:03.5][00:01.25] First line
            [00:03.500] Second line
            [00:04]
            [offset:-100]
            """.trimIndent(),
            "",
        )

        assertEquals(
            listOf(LyricLine(1150, "First line"), LyricLine(3400, "First line\nSecond line"), LyricLine(3900, "")),
            lyrics.timed,
        )
        assertEquals(-1, lyrics.activeLine(1149))
        assertEquals(0, lyrics.activeLine(1150))
        assertEquals(1, lyrics.activeLine(3899))
        assertEquals(2, lyrics.activeLine(3900))
        assertEquals(0, lyrics.activeLine(1500))
    }

    @Test
    fun preservesPlainLyricsWhenTimedLyricsAreMissing() {
        val lyrics = TrackLyrics.parse("[00:00]\n[00:12] ", " Example lyrics. ")
        assertTrue(lyrics.timed.isEmpty())
        assertEquals("Example lyrics.", lyrics.plain)
        assertEquals(-1, lyrics.activeLine(12000))
    }
}
