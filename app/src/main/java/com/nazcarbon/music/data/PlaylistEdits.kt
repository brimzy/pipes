package com.nazcarbon.music.data

object PlaylistEdits {
    fun name(value: String, collections: List<MusicPlaylist>, replacing: String? = null): String {
        val clean = value.trim()
        require(clean.isNotEmpty()) { "Enter a name." }
        require(clean.length <= 80) { "Use 80 characters or fewer." }
        require(collections.none { it.name != replacing && it.name.equals(clean, true) }) { "That name is already taken." }
        return clean
    }

    fun add(collection: MusicPlaylist, tracks: List<MusicTrack>): MusicPlaylist {
        val ids = collection.tracks.mapTo(mutableSetOf()) { it.id }
        return collection.copy(tracks = collection.tracks + tracks.filter { ids.add(it.id) }.map { it.copy(streamUrl = "") })
    }

    fun downloadAlbum(collections: List<MusicPlaylist>, title: String, tracks: List<MusicTrack>): List<MusicPlaylist> {
        val ids = tracks.map { it.id }.distinct()
        if (ids.isEmpty() || collections.any { it.isAlbum && it.tracks.map { track -> track.id }.distinct() == ids }) return collections
        val base = title.trim().ifBlank { "Downloaded album" }.take(70)
        var name = base
        var suffix = 2
        while (collections.any { it.name.equals(name, ignoreCase = true) }) name = "$base (${suffix++})"
        return collections + add(MusicPlaylist(name, emptyList(), isAlbum = true), tracks)
    }

    fun remove(collection: MusicPlaylist, ids: Set<String>): MusicPlaylist =
        collection.copy(tracks = collection.tracks.filterNot { it.id in ids })
}
