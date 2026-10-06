package com.nazcarbon.music.data

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/** Linear scoring over catalog candidates; no audio analysis or extra network requests. */
object MusicRecommendations {
    private val noise = Regex("\\b(podcast|interview|reaction|tutorial|gameplay|review|explained|documentary|compilation|full album|full concert|playlist|hour mix)\\b", RegexOption.IGNORE_CASE)
    private val variants = Regex("\\b(live|cover|karaoke|nightcore|sped up|slowed|remix)\\b", RegexOption.IGNORE_CASE)
    private fun tokens(value: String): Set<String> = value.lowercase()
        .replace(Regex("\\b(official|audio|video|lyrics|lyric|topic|music)\\b"), " ")
        .split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }.toSet()
    private fun similarity(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

    fun matchScore(wanted: MusicTrack, candidate: MusicTrack): Double {
        val title = similarity(tokens(wanted.title), tokens(candidate.title))
        val artist = similarity(tokens(wanted.artist), tokens(candidate.artist))
        if (title < 0.45 || artist == 0.0) return 0.0
        val duration = if (wanted.durationSeconds > 0 && candidate.durationSeconds > 0)
            (1.0 - abs(wanted.durationSeconds - candidate.durationSeconds).toDouble() / wanted.durationSeconds).coerceAtLeast(0.0) else 0.5
        val unwantedVariant = variants.findAll(candidate.title).map { it.value.lowercase() }.any { !wanted.title.contains(it, true) }
        return 0.65 * title + 0.30 * artist + 0.05 * duration - if (unwantedVariant) 0.3 else 0.0
    }

    fun isMusic(track: MusicTrack): Boolean = track.durationSeconds in 30..1200 && !noise.containsMatchIn(track.title)

    /** Weighted sampling without replacement; catalog relevance with varied artists. */
    fun radio(seed: MusicTrack, candidates: List<MusicTrack>, random: Random = Random.Default): List<MusicTrack> {
        val seenSongs = mutableSetOf(tokens(seed.title) to tokens(seed.artist))
        val artistCounts = mutableMapOf<String, Int>()
        return candidates.distinctBy { it.pageUrl }.filter { candidate ->
            candidate.pageUrl != seed.pageUrl && isMusic(candidate) && seenSongs.add(tokens(candidate.title) to tokens(candidate.artist))
        }.mapIndexed { rank, track ->
            val artist = tokens(track.artist).sorted().joinToString(" ")
            val repeats = artistCounts.getOrDefault(artist, 0)
            artistCounts[artist] = repeats + 1
            val weight = (1.0 / (1.0 + rank * 0.025)) / (1.0 + repeats * 0.6)
            track to (-ln(random.nextDouble().coerceAtLeast(1e-12)) / weight)
        }.sortedBy { it.second }.map { it.first }.take(30)
    }
}
