package com.nazcarbon.music.data

class DiscoveryFeed(private val source: NewPipeMusicSource) {
    @Volatile private var seen: List<MusicTrack> = emptyList()
    private val visitedSeeds = mutableSetOf<String>()
    private var moodIndex = 0
    private val moods = listOf("indie pop", "alternative r&b", "dream pop", "electronic", "soul", "jazz", "new music", "ambient")

    fun snapshot(): List<MusicTrack> = seen

    @Synchronized
    fun refresh(current: MusicTrack?, artists: List<String>): List<MusicTrack> {
        current?.let { visitedSeeds.remove(it.pageUrl) }
        val batch = next(current, artists)
        seen = (batch + seen).distinctBy { it.id }.take(240)
        return batch
    }

    @Synchronized
    fun next(current: MusicTrack?, artists: List<String>): List<MusicTrack> {
        repeat(3) {
            val seed = (listOfNotNull(current) + seen.asReversed()).firstOrNull {
                it.pageUrl.startsWith("https://") && it.pageUrl !in visitedSeeds
            }
            val candidates = if (seed != null) {
                source.relatedSongs(seed).also { visitedSeeds += seed.pageUrl }
            } else {
                val queries = artists.distinct().take(8) + moods
                val query = queries[moodIndex % queries.size]
                source.searchSongs(query).also { moodIndex++ }
            }
            val anchor = seed ?: candidates.firstOrNull() ?: return@repeat
            val batch = MusicRecommendations.discovery(anchor, candidates, seen)
            if (batch.isNotEmpty()) {
                seen = seen + batch
                return batch
            }
        }
        return emptyList()
    }
}
