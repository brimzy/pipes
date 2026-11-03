package com.nazcarbon.music.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Called on the UI dispatcher so updates always merge with the latest collection edits. */
class SpotifyPlaylistSync(
    private val read: () -> List<MusicPlaylist>,
    private val write: (List<MusicPlaylist>) -> Unit,
    private val fetch: suspend (String) -> SpotifyPlaylistPreview = { id ->
        SpotifyPlaylists.load("spotify:playlist:$id", forceRefresh = true)
    },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val attempted = mutableMapOf<String, Long>()

    suspend fun refresh(id: String? = null, force: Boolean = false): Map<String, String> = mutex.withLock {
        val failures = mutableMapOf<String, String>()
        val ids = read().map { it.spotifyPlaylistId }.filter { it.isNotBlank() && (id == null || it == id) }.distinct()
        for (source in ids) {
            val before = read().firstOrNull { it.spotifyPlaylistId == source } ?: continue
            val last = maxOf(before.lastSpotifySync, attempted[source] ?: 0)
            if (!force && last > 0 && now() - last in 0 until INTERVAL_MS) continue
            attempted[source] = now()
            try {
                val preview = withTimeout(90_000) { fetch(source) }
                val latest = read()
                val current = latest.firstOrNull { it.spotifyPlaylistId == source } ?: continue
                // An explicit re-import completed while this request was in flight.
                if (current.lastSpotifySync != before.lastSpotifySync) continue
                val updated = SpotifyPlaylistUpdates.refresh(current, source, preview, now())
                write(latest.map { if (it === current) updated else it })
            } catch (_: TimeoutCancellationException) {
                failures[source] = "Spotify refresh timed out. Your saved songs were kept."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures[source] = failure.message ?: "Couldn't refresh Spotify. Your saved songs were kept."
            }
        }
        failures
    }

    companion object { const val INTERVAL_MS = 60_000L }
}
