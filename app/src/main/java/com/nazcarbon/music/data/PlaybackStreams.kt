package com.nazcarbon.music.data

import org.json.JSONObject
import java.io.IOException
import java.util.Base64

/** Stable queue entries carry source metadata, never an expiring CDN address. */
internal object PlaybackStreams {
    private const val PREFIX = "pipes-audio:"

    fun encode(track: MusicTrack): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
        JSONObject().put("id", track.id).put("title", track.title).put("artist", track.artist)
            .put("duration", track.durationSeconds).put("thumbnail", track.thumbnailUrl)
            .put("page", track.pageUrl).toString().toByteArray(Charsets.UTF_8),
    )

    fun decode(uri: String): MusicTrack? {
        if (!uri.startsWith(PREFIX)) return null
        return try {
            val json = JSONObject(String(Base64.getUrlDecoder().decode(uri.removePrefix(PREFIX)), Charsets.UTF_8))
            MusicTrack(json.getString("id"), json.getString("title"), json.getString("artist"),
                json.optLong("duration"), json.optString("thumbnail"), "", json.getString("page"))
        } catch (error: Exception) {
            throw IOException("Couldn't read this queued recording.", error)
        }
    }
}

/** Reopening a byte range reuses a fresh URL; old queued tracks resolve when loaded. */
internal class PlaybackStreamCache(
    private val now: () -> Long = System::currentTimeMillis,
    private val resolve: (MusicTrack) -> MusicTrack,
) {
    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?) = size > 64
    }

    fun resolve(uri: String): String {
        val track = PlaybackStreams.decode(uri) ?: return uri
        synchronized(cache) {
            cache[uri]?.takeIf { now() - it.first < 5 * 60_000 }?.let { return it.second }
        }
        val stream = try { resolve(track).streamUrl }
        catch (error: Exception) { throw IOException("Couldn't load ${track.title}.", error) }
        if (stream.isBlank()) throw IOException("No audio is available for ${track.title}.")
        synchronized(cache) { cache[uri] = now() to stream }
        return stream
    }

    fun invalidate(uri: String) { synchronized(cache) { cache.remove(uri) } }
}
