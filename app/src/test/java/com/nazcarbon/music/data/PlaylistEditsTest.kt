package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class PlaylistEditsTest {
    private fun song(id: String) = MusicTrack(id, id, "Artist", 123, "art", "expiring-stream", "https://example.com/$id")

    @Test fun bulkCopyKeepsOrderSkipsDuplicatesAndClearsTemporaryStreams() {
        val original = MusicPlaylist("My mix", listOf(song("a").copy(streamUrl = "")))
        val result = PlaylistEdits.add(original, listOf(song("b"), song("a"), song("c"), song("b")))
        assertEquals(listOf("a", "b", "c"), result.tracks.map { it.id })
        assertTrue(result.tracks.all { it.streamUrl.isEmpty() })
        assertEquals(1, original.tracks.size)
    }

    @Test fun bulkRemovalOnlyChangesTheChosenCollectionAndKeepsItsType() {
        val album = MusicPlaylist("Album", listOf(song("a"), song("b"), song("c")), isAlbum = true)
        val result = PlaylistEdits.remove(album, setOf("a", "c", "unknown"))
        assertEquals(listOf("b"), result.tracks.map { it.id })
        assertTrue(result.isAlbum)
        assertEquals(3, album.tracks.size)
    }

    @Test fun createAndAddRetainsTheEntireSelectionForAlbumsAndPlaylists() {
        for (album in listOf(false, true)) {
            val result = PlaylistEdits.add(MusicPlaylist("New", emptyList(), isAlbum = album), listOf(song("a"), song("b")))
            assertEquals(listOf("a", "b"), result.tracks.map { it.id })
            assertEquals(album, result.isAlbum)
        }
    }

    @Test fun namesAreTrimmedAndUniqueButCanBeRenamedWithNewCasing() {
        val collections = listOf(MusicPlaylist("Favorites", emptyList()))
        assertEquals("Road trip", PlaylistEdits.name(" Road trip ", collections))
        assertEquals("FAVORITES", PlaylistEdits.name("FAVORITES", collections, "Favorites"))
        for (invalid in listOf(" ", "favorites", "a".repeat(81))) {
            assertTrue(runCatching { PlaylistEdits.name(invalid, collections) }.isFailure)
        }
    }
}
