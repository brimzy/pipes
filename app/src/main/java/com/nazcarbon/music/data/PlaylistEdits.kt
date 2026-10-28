package com.nazcarbon.music.data

/** Collection edits keep recording order and never persist expiring stream URLs. */
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

    fun remove(collection: MusicPlaylist, ids: Set<String>): MusicPlaylist =
        collection.copy(tracks = collection.tracks.filterNot { it.id in ids })
}
