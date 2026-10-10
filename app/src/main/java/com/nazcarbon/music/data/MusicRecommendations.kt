package com.nazcarbon.music.data

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

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

    fun discovery(seed: MusicTrack, candidates: List<MusicTrack>, history: List<MusicTrack>, random: Random = Random.Default): List<MusicTrack> {
        val ids = history.map { it.id }.toSet()
        val urls = history.map { it.pageUrl }.toSet()
        val recordings = history.map { tokens(it.title) to tokens(it.artist) }.toSet()
        return radio(seed, candidates.filter {
            it.id !in ids && it.pageUrl !in urls && (tokens(it.title) to tokens(it.artist)) !in recordings
        }, random)
    }

    fun radio(seed: MusicTrack, candidates: List<MusicTrack>, random: Random = Random.Default): List<MusicTrack> {
        val seenSongs = mutableSetOf(tokens(seed.title) to tokens(seed.artist))
        val pool = candidates.distinctBy { it.id }.distinctBy { it.pageUrl }.filter { candidate ->
            candidate.id != seed.id && candidate.pageUrl != seed.pageUrl && isMusic(candidate) &&
                seenSongs.add(tokens(candidate.title) to tokens(candidate.artist))
        }.mapIndexed { rank, track -> rank to track }.toMutableList()
        val artistCounts = mutableMapOf<Set<String>, Int>()
        var previousArtist = tokens(seed.artist)
        val result = mutableListOf<MusicTrack>()
        while (pool.isNotEmpty() && result.size < 30) {
            val alternatives = pool.filter { tokens(it.second.artist) != previousArtist }
            val eligible = alternatives.ifEmpty { pool }
            val chosen = eligible.minBy { (rank, track) ->
                val repeats = artistCounts.getOrDefault(tokens(track.artist), 0)
                val weight = 1.0 / ((1.0 + rank * 0.15) * (1.0 + repeats * 1.5))
                -ln(random.nextDouble().coerceAtLeast(1e-12)) / weight
            }
            pool.remove(chosen)
            result.add(chosen.second)
            previousArtist = tokens(chosen.second.artist)
            artistCounts[previousArtist] = artistCounts.getOrDefault(previousArtist, 0) + 1
        }
        return result
    }
}
