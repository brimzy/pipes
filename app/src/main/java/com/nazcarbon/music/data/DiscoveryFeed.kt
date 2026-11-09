package com.nazcarbon.music.data

/** Recommendations always start from the user's music; feed suggestions never become taste signals. */
class DiscoveryFeed(
    private val related: (MusicTrack, String) -> List<MusicTrack>,
    private val search: (String) -> List<MusicTrack>,
) {
    constructor(source: NewPipeMusicSource) : this({ track, region -> source.relatedSongs(track, region) }, { query -> source.searchSongs(query) })

    @Volatile private var seen: List<MusicTrack> = emptyList()
    private val visitedSeeds = mutableSetOf<String>()
    private var queryIndex = 0
    private var profile = emptyList<String>()
    private val moods = listOf("indie pop", "alternative r&b", "dream pop", "electronic", "soul", "jazz", "new music", "ambient")

    fun snapshot(): List<MusicTrack> = seen

    @Synchronized
    fun refresh(seeds: List<MusicTrack>, artists: List<String>, region: String = "AUTO"): List<MusicTrack> {
        val signature = listOf(region) + seeds.map { it.pageUrl } + artists.map { it.trim().lowercase() }.sorted()
        if (signature != profile) {
            profile = signature
            seen = emptyList()
            visitedSeeds.clear()
            queryIndex = 0
        } else seeds.firstOrNull()?.let { visitedSeeds.remove(it.pageUrl) }
        return next(seeds, artists, region)
    }

    @Synchronized
    fun next(seeds: List<MusicTrack>, artists: List<String>, region: String = "AUTO"): List<MusicTrack> {
        val batch = mutableListOf<MusicTrack>()
        var lastFailure: Exception? = null
        val availableSeeds = seeds.filterNot { it.pageUrl in visitedSeeds }.take(3)
        for (seed in availableSeeds) {
            try {
                val candidates = related(seed, region)
                visitedSeeds += seed.pageUrl
                batch += MusicRecommendations.discovery(seed, candidates, seen + batch).take(10)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { lastFailure = error }
        }
        if (batch.isEmpty()) {
            val queries = (artists + seeds.map { it.artist }).map { it.trim() }.filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }.ifEmpty { moods }
            for (attempt in 0 until 3) {
                val query = queries[queryIndex++ % queries.size]
                try {
                    val candidates = search(query)
                    val anchor = seeds.firstOrNull() ?: candidates.firstOrNull()
                    if (anchor != null) batch += MusicRecommendations.discovery(anchor, candidates, seen + batch).take(10)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { lastFailure = error }
                if (batch.isNotEmpty()) break
            }
        }
        if (batch.isEmpty() && lastFailure != null) throw lastFailure
        // New pages are deduplicated against the full session, including alternate uploads.
        seen = (seen + batch).distinctBy { it.pageUrl }
        return batch
    }
}
