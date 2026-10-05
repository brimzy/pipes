package com.nazcarbon.music.data

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PersistenceTest {
    private fun preferences(values: MutableMap<String, String>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args[0] as String] = args[1] as String; proxy }
                "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0]] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun savedMetadataSurvivesRecreationWithoutOverwritingPlaylistsOrSavingStreamUrls() {
        val disk = mutableMapOf<String, String>()
        val track = MusicTrack("song", "Title", "Artist", 123, "art", "expiring-secret-stream", "https://example.com/song")
        PlaylistStore.save(preferences(disk), listOf(MusicPlaylist("Existing playlist", listOf(track))))
        PlaylistStore.save(preferences(disk), listOf(MusicPlaylist("Liked songs", listOf(track))), "saved_tracks")
        val restored = PlaylistStore.load(preferences(disk), "saved_tracks").single().tracks.single()
        assertEquals(track.copy(streamUrl = ""), restored)
        assertEquals("Existing playlist", PlaylistStore.load(preferences(disk)).single().name)
        assertFalse(disk.values.any { "expiring-secret-stream" in it })
    }

    @Test fun historyRoundTripDeletionAndClearSurviveReload() {
        var history = SearchHistory.add(listOf("older", "Artist"), " artist ")
        assertEquals(listOf("artist", "older"), history)
        history = SearchHistory.decode(SearchHistory.encode(history.filterNot { it == "artist" }))
        assertEquals(listOf("older"), history)
        assertTrue(SearchHistory.decode(SearchHistory.encode(emptyList())).isEmpty())
    }

    @Test fun historyIsBoundedAndToleratesMalformedData() {
        assertEquals(30, SearchHistory.add((1..40).map { "query $it" }, "new").size)
        assertTrue(SearchHistory.decode("broken").isEmpty())
        assertEquals(listOf("valid"), SearchHistory.decode("[null,3,\"valid\",\"VALID\",\"\"]"))
        assertEquals(listOf("keep"), SearchHistory.add(listOf("keep"), "  "))
    }
}
