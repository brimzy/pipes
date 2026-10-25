package com.nazcarbon.music.data

/** A bounded session cache: browsing back to a scene preserves its music until refresh. */
class ExploreFeed(private val search: (String) -> List<MusicTrack>) {
    private val cache = object : LinkedHashMap<String, List<MusicTrack>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MusicTrack>>?) = size > 8
    }

    fun load(query: String, refresh: Boolean = false): List<MusicTrack> {
        val key = query.trim().lowercase(java.util.Locale.ROOT)
        require(key.isNotBlank())
        if (!refresh) synchronized(cache) { cache[key]?.let { return it } }
        val tracks = search(query.trim()).distinctBy { it.id }.take(12)
        // Empty responses and errors stay retryable; an unsuccessful refresh retains the cache.
        if (tracks.isNotEmpty()) synchronized(cache) { cache[key] = tracks }
        return tracks
    }
}
