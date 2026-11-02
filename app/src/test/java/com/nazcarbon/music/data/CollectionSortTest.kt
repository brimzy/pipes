package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class CollectionSortTest {
    private val tracks = listOf(
        MusicTrack("a", "zebra", "Beta", 300, "", "", "a"),
        MusicTrack("b", "Alpha", "alpha", 180, "", "", "b"),
        MusicTrack("c", "alpha", "Beta", 100, "", "", "c"),
    )

    @Test fun sortingIsCaseInsensitiveAndPreservesEqualTitleOrder() {
        assertEquals(listOf("b", "c", "a"), CollectionSort.TITLE.tracks(tracks).map { it.id })
        assertEquals(listOf("a", "b", "c"), CollectionSort.TITLE_DESC.tracks(tracks).map { it.id })
        assertEquals(listOf("b", "c", "a"), CollectionSort.ARTIST.tracks(tracks).map { it.id })
        assertEquals(listOf("c", "b", "a"), CollectionSort.DURATION.tracks(tracks).map { it.id })
        assertEquals(listOf("a", "b", "c"), tracks.map { it.id })
        assertEquals(tracks, CollectionSort.SAVED.tracks(tracks))
    }

    @Test fun persistedSortRestoresAndUnknownPreferencesUseSavedOrder() {
        CollectionSort.entries.forEach { assertEquals(it, CollectionSort.from(it.name)) }
        assertEquals(CollectionSort.SAVED, CollectionSort.from("unknown"))
        assertEquals(CollectionSort.SAVED, CollectionSort.from(null))
        assertTrue(CollectionSort.TITLE.tracks(emptyList()).isEmpty())
    }
}
