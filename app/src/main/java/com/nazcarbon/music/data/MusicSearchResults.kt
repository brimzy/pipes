package com.nazcarbon.music.data

import java.util.Locale

/** Merge music and video search without burying exact matches behind a whole source batch. */
object MusicSearchResults {
    private fun words(value: String): List<String> = value.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    fun merge(query: String, batches: List<List<MusicTrack>>): List<MusicTrack> {
        val terms = words(query).distinct()
        val interleaved = (0 until (batches.maxOfOrNull { it.size } ?: 0)).flatMap { index ->
            batches.mapNotNull { it.getOrNull(index) }
        }.distinctBy { it.pageUrl }
        return interleaved.sortedByDescending { track ->
            val metadata = words("${track.title} ${track.artist}").toSet()
            terms.count { it in metadata }
        }
    }
}
