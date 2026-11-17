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

    @Test
    fun highlightsTimedWordsAndClearsDuringGaps() {
        val lyrics = TrackLyrics.parse(
            synced = "[00:01.00] Light the night",
            plain = "",
            lyricsFile = """
                version: "1.0"
                lines:
                - text: Light the night
                  end_ms: 2100
                  words:
                  - text: 'Light '
                    start_ms: 1000
                    end_ms: 1350
                  - text: 'the '
                    start_ms: 1400
                  - text: night
                    start_ms: 1600
            """.trimIndent(),
        )

        val line = lyrics.timed.single()
        assertEquals("Light the night", line.text)
        assertEquals(-1, lyrics.activeLine(999))
        assertEquals(0, lyrics.activeLine(1000))
        assertEquals(listOf("Light", "the", "night"), line.words.map { line.text.substring(it.range) })
        assertEquals(0, line.activeWord(1000))
        assertEquals(-1, line.activeWord(1350))
        assertEquals(1, line.activeWord(1599))
        assertEquals(2, line.activeWord(1600))
        assertEquals(-1, line.activeWord(2100))
        assertEquals(0, line.activeWord(1200))
    }

    @Test
    fun usesLineTimingsWhenLyricsFileCannotBeParsed() {
        val lyrics = TrackLyrics.parse("[00:01] Light the night", "", "lines: [")
        assertEquals(listOf(LyricLine(1000, "Light the night")), lyrics.timed)
    }
}
