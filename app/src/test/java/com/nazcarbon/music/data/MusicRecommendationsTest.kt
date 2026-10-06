package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import kotlin.random.Random

class MusicRecommendationsTest {
    private fun song(id: String, title: String = "Song", artist: String = "Artist", duration: Long = 200) =
        MusicTrack(id, title, artist, duration, "", "", "https://www.youtube.com/watch?v=$id")

    @Test fun `matching favors original recording and artist over covers`() {
        val wanted = song("spotify", "Blinding Lights", "The Weeknd")
        val original = song("a", "Blinding Lights (Official Audio)", "The Weeknd - Topic")
        val cover = song("b", "Blinding Lights cover", "Other Artist")
        assertTrue(MusicRecommendations.matchScore(wanted, original) > 0.9)
        assertTrue(MusicRecommendations.matchScore(wanted, original) > MusicRecommendations.matchScore(wanted, cover))
    }

    @Test fun `radio excludes seed duplicates spoken content and long mixes`() {
        val seed = song("seed", "Seed")
        val candidates = listOf(seed, song("a", "Fresh song"), song("b", "Fresh song"),
            song("c", "Artist interview"), song("d", "Hour mix", duration = 3600))
        val radio = MusicRecommendations.radio(seed, candidates, Random(7))
        assertEquals(listOf("a"), radio.map { it.id })
    }

    @Test fun `radio randomizes while preserving its candidate set`() {
        val candidates = (0..19).map { song("id$it", "Song $it", "Artist $it") }
        val a = MusicRecommendations.radio(song("seed", "Seed"), candidates, Random(1))
        val b = MusicRecommendations.radio(song("seed", "Seed"), candidates, Random(2))
        assertEquals(candidates.map { it.id }.toSet(), a.map { it.id }.toSet())
        assertNotEquals(a.map { it.id }, b.map { it.id })
    }

    @Test fun `music radio parser rejects user videos and reads song metadata`() {
        fun row(type: String, id: String) = """{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"runs":[{"text":"Song"}]},"longBylineText":{"runs":[{"text":"Artist • 1M views"}]},"lengthText":{"runs":[{"text":"3:25"}]},"navigationEndpoint":{"watchEndpoint":{"watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}}}}"""
        val data = JSONObject("""{"contents":[${row("MUSIC_VIDEO_TYPE_ATV", "song1234567")},${row("MUSIC_VIDEO_TYPE_UGC", "video123456") }]}""")
        val tracks = NewPipeMusicSource().parseMusicRadio(data)
        assertEquals(1, tracks.size)
        assertEquals("Artist", tracks.single().artist)
        assertEquals(205L, tracks.single().durationSeconds)
    }
}
