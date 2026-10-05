package com.nazcarbon.music.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Serializes UI updates even if a canceled extractor call finishes late. Call from the UI thread. */
class SongSearch(
    private val scope: CoroutineScope,
    private val search: suspend (String) -> List<MusicTrack>,
    private val update: (State) -> Unit,
) {
    data class State(val tracks: List<MusicTrack> = emptyList(), val loading: Boolean = false, val error: String? = null)
    private var job: Job? = null
    private var generation = 0

    fun submit(query: String) {
        val request = ++generation
        job?.cancel()
        update(State(loading = query.isNotBlank()))
        if (query.isBlank()) return
        job = scope.launch {
            try {
                delay(400)
                val tracks = search(query.trim())
                if (request == generation) update(State(tracks.distinctBy { it.id }))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == generation) update(State(error = if (failure is IllegalArgumentException) {
                    failure.message ?: "That link couldn't be read."
                } else "Search couldn't load. Check your connection and retry."))
            }
        }
    }
}
