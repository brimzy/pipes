package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class SpotifyPlaylistUpdatesTest {
    private fun preview(vararg ids: String) = SpotifyPlaylistPreview("Spotify name",
        ids.mapIndexed { index, id -> SpotifyPlaylistTrack("Song $id", "Artist", id, 123, "art", index) }, ids.size)
    private fun imported(vararg ids: String) = SpotifyPlaylistUpdates.refresh(
        MusicPlaylist("My name", emptyList(), artworkUri = "custom.gif"), "source", preview(*ids), 1_000)

    @Test fun additionsReordersAndRemovalsKeepRecordingIdentityAndLocalEdits() {
        val original = imported("a", "b", "c")
        val local = MusicTrack("local", "Local song", "Me", 1, "", "", "file://local")
        val before = original.copy(tracks = original.tracks + local, name = "Renamed", isAlbum = true)
        val updated = SpotifyPlaylistUpdates.refresh(before, "source", preview("new", "c", "a"), 2_000)
        assertEquals(listOf("Song new", "Song c", "Song a", "Local song"), updated.tracks.map { it.title })
        assertEquals(original.tracks[0].id, updated.tracks[2].id)
        assertEquals(original.tracks[2].id, updated.tracks[1].id)
        assertEquals("Renamed", updated.name)
        assertEquals("custom.gif", updated.artworkUri)
        assertTrue(updated.isAlbum)
        assertEquals(2_000L, updated.lastSpotifySync)
        assertEquals(3, updated.sourceTotal)
        assertEquals(updated.tracks.take(3).map { it.id }, updated.spotifyTrackIds)
    }

    @Test fun duplicateRecordingsRetainDistinctStableRowsAndNewOccurrencesCannotCollide() {
        val original = imported("a", "b", "a")
        val updated = SpotifyPlaylistUpdates.refresh(original, "source", preview("a", "a", "a", "b"), 2_000)
        assertEquals(original.tracks[0].id, updated.tracks[0].id)
        assertEquals(original.tracks[2].id, updated.tracks[1].id)
        assertEquals(original.tracks[1].id, updated.tracks[3].id)
        assertEquals(4, updated.tracks.map { it.id }.distinct().size)
        assertEquals(updated.tracks, SpotifyPlaylistUpdates.refresh(updated, "source", preview("a", "a", "a", "b"), 3_000).tracks)
    }

    @Test fun anIndependentlyAddedSpotifySongIsRetained() {
        val original = imported("a")
        val extra = MusicTrack("spotify:extra:0", "Extra", "Artist", 2, "", "", "spotify-search:extra")
        val updated = SpotifyPlaylistUpdates.refresh(original.copy(tracks = original.tracks + extra), "source", preview("b"), 2_000)
        assertEquals(extra, updated.tracks.last())
        assertEquals(1, updated.spotifyTrackIds.size)
    }

    @Test fun incompleteResponsesAreRejectedButEmptyAndSkippedOnlyPlaylistsAreValid() {
        val original = imported("a", "b")
        assertTrue(runCatching {
            SpotifyPlaylistUpdates.refresh(original, "source", preview("a").copy(totalTracks = 2), 2_000)
        }.isFailure)
        assertEquals(2, original.tracks.size)
        assertTrue(SpotifyPlaylistUpdates.refresh(original, "source", preview(), 2_000).tracks.isEmpty())
        val skipped = preview().copy(totalTracks = 1, skippedItems = 1)
        assertEquals(1, SpotifyPlaylistUpdates.refresh(original, "source", skipped, 2_000).sourceSkipped)
    }

    @Test fun reimportsUseSourceIdentityAndNeverReplaceUnrelatedSameNameCollections() {
        val original = imported("a")
        val unrelated = MusicPlaylist("Spotify name", emptyList(), artworkUri = "other.gif")
        val updated = SpotifyPlaylistUpdates.import(listOf(unrelated, original), "source", preview("a", "b"), 2_000)
        assertEquals("My name", updated.name)
        assertEquals("custom.gif", updated.artworkUri)
        val other = SpotifyPlaylistUpdates.import(listOf(unrelated, original), "different", preview("c"), 2_000)
        assertEquals("Spotify name (2)", other.name)
        assertEquals("different", other.spotifyPlaylistId)
    }

    @Test fun legacyImportReconnectsInPlaceAndPersistenceRetainsItsSourceAcrossRestarts() {
        val original = imported("a", "b")
        val legacy = original.copy(name = "Spotify name", spotifyPlaylistId = "", spotifyTrackIds = emptyList(), lastSpotifySync = 0)
        val updated = SpotifyPlaylistUpdates.import(listOf(legacy), "source", preview("b", "a", "c"), 2_000)
        assertEquals(legacy.name, updated.name)
        assertEquals(legacy.tracks[0].id, updated.tracks[1].id)
        assertEquals(listOf(updated), PlaylistStore.decode(PlaylistStore.encode(listOf(updated))))
        val old = PlaylistStore.decode("""[{"name":"Old playlist","tracks":[],"sourceTotal":10}]""").single()
        assertEquals("", old.spotifyPlaylistId)
        assertTrue(old.spotifyTrackIds.isEmpty())
        assertEquals(0L, old.lastSpotifySync)
    }
}
