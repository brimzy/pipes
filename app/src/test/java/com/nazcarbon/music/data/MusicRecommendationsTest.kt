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

    @Test fun `radio avoids consecutive artists when alternatives exist`() {
        val seed = song("seed", "Seed", "Artist A")
        val candidates = (0..9).map { song("a$it", "A $it", "Artist A") } +
            (0..9).map { song("b$it", "B $it", "Artist B") }
        val radio = MusicRecommendations.radio(seed, candidates, Random(7))
        assertEquals("Artist B", radio.first().artist)
        assertTrue(radio.zipWithNext().all { (a, b) -> a.artist != b.artist })
    }

    @Test fun `discovery excludes recordings from previous pages even with new source ids`() {
        val heard = song("heard", "Fresh song (Official Audio)", "Artist - Topic")
        val fresh = song("fresh", "Another song", "Other Artist")
        val candidates = listOf(heard, song("upload", "Fresh song", "Artist"),
            song("same-url", "Different metadata").copy(pageUrl = heard.pageUrl), fresh)
        val batch = MusicRecommendations.discovery(song("seed", "Seed"), candidates, listOf(heard), Random(7))
        assertEquals(listOf(fresh), batch)
    }

    @Test fun `discovery returns empty when all candidates were already shown`() {
        val heard = song("heard", "Fresh song")
        assertTrue(MusicRecommendations.discovery(song("seed", "Seed"), listOf(heard), listOf(heard), Random(7)).isEmpty())
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
