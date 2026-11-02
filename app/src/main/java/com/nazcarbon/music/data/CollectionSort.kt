package com.nazcarbon.music.data

import java.util.Locale

enum class CollectionSort(val label: String) {
    SAVED("Saved order"), TITLE("Title A–Z"), TITLE_DESC("Title Z–A"), ARTIST("Artist"), DURATION("Duration");

    fun tracks(tracks: List<MusicTrack>): List<MusicTrack> = when (this) {
        SAVED -> tracks
        TITLE -> tracks.sortedBy { it.title.lowercase(Locale.ROOT) }
        TITLE_DESC -> tracks.sortedByDescending { it.title.lowercase(Locale.ROOT) }
        ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
        DURATION -> tracks.sortedBy { it.durationSeconds }
    }

    companion object {
        fun from(value: String?): CollectionSort = entries.firstOrNull { it.name == value } ?: SAVED
    }
}
