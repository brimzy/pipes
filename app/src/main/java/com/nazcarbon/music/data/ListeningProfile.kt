package com.nazcarbon.music.data

/** Recent listening leads; likes and sampled collections broaden the same musical neighbourhood. */
internal object ListeningProfile {
    fun seeds(current: MusicTrack?, recent: List<MusicTrack>, liked: List<MusicTrack>, playlists: List<MusicPlaylist>): List<MusicTrack> {
        val collections = playlists.map { playlist ->
            val step = (playlist.tracks.size / 6).coerceAtLeast(1)
            playlist.tracks.filterIndexed { index, _ -> index % step == 0 }.take(6)
        }
        val sampled = (0 until 6).flatMap { index -> collections.mapNotNull { it.getOrNull(index) } }
        return (listOfNotNull(current) + recent.take(12) + liked.take(12) + sampled)
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() && it.title != "Saved song" &&
                (it.pageUrl.startsWith("https://") || it.pageUrl.startsWith("spotify-search:")) }
            .distinctBy { it.pageUrl }.take(48)
    }

    fun record(history: List<MusicTrack>, track: MusicTrack): List<MusicTrack> =
        (listOf(track.copy(streamUrl = "")) + history.filterNot { it.id == track.id }).take(100)
}
