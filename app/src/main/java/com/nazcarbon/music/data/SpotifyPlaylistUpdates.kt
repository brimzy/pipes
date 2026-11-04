package com.nazcarbon.music.data

object SpotifyPlaylistUpdates {
    fun refresh(collection: MusicPlaylist, id: String, preview: SpotifyPlaylistPreview, syncedAt: Long): MusicPlaylist {
        require(preview.totalTracks == preview.tracks.size + preview.skippedItems) {
            "Spotify returned an incomplete playlist. Your saved songs were kept."
        }
        val owned = if (collection.spotifyPlaylistId.isBlank()) {
            collection.tracks.filter { it.id.startsWith("spotify:") }.mapTo(mutableSetOf()) { it.id }
        } else collection.spotifyTrackIds.toSet()
        val previous = collection.tracks.filter { it.id in owned }
            .groupBy { it.id.split(':').getOrNull(1) }.mapValues { (_, tracks) -> ArrayDeque(tracks) }
        val used = collection.tracks.mapTo(mutableSetOf()) { it.id }
        val imported = preview.tracks.mapIndexed { position, track ->
            val existing = previous[track.id]?.removeFirstOrNull()
            val base = "spotify:${track.id}:${track.position.takeIf { it >= 0 } ?: position}"
            var trackId = existing?.id ?: base
            if (existing == null) {
                var suffix = 2
                while (!used.add(trackId)) trackId = "$base:${suffix++}"
            }
            MusicTrack(trackId, track.title, track.artist, track.durationSeconds, track.thumbnailUrl,
                "", "spotify-search:${track.id}")
        }
        return collection.copy(tracks = imported + collection.tracks.filterNot { it.id in owned },
            sourceTotal = preview.totalTracks, sourceSkipped = preview.skippedItems,
            spotifyPlaylistId = id, spotifyTrackIds = imported.map { it.id }, lastSpotifySync = syncedAt)
    }

    fun import(collections: List<MusicPlaylist>, id: String, preview: SpotifyPlaylistPreview, syncedAt: Long): MusicPlaylist {
        val existing = collections.firstOrNull { it.spotifyPlaylistId == id }
            ?: collections.firstOrNull { it.spotifyPlaylistId.isBlank() && it.name.equals(preview.name, true) &&
                (it.sourceTotal > 0 || it.tracks.any { track -> track.id.startsWith("spotify:") }) }
        val collection = existing ?: run {
            val base = preview.name
            var name = base
            var suffix = 2
            while (collections.any { it.name.equals(name, true) }) name = "$base (${suffix++})"
            MusicPlaylist(name, emptyList())
        }
        return refresh(collection, id, preview, syncedAt)
    }
}
