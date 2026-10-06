package com.nazcarbon.music.data

import java.util.UUID

/** Activity and playback service share a process. Pass a small ID over Binder, never a whole playlist. */
object PlaybackQueueRequests {
    private data class Request(val session: String, val tracks: List<MusicTrack>)
    private val requests = LinkedHashMap<String, Request>()

    @Synchronized
    fun register(session: String, tracks: List<MusicTrack>): String {
        val id = UUID.randomUUID().toString()
        requests[id] = Request(session, tracks.toList())
        while (requests.size > 4) requests.remove(requests.keys.first())
        return id
    }

    @Synchronized
    fun consume(id: String, session: String): List<MusicTrack>? =
        requests.remove(id)?.takeIf { it.session == session }?.tracks
}
