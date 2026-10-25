package com.nazcarbon.music.data

import java.util.Base64

/** Opaque, context-specific IDs ensure controllers can select only saved library entries. */
class CarCatalog(liked: List<MusicTrack>, playlists: List<MusicPlaylist>, downloads: List<MusicTrack>) {
    data class Folder(val id: String, val title: String)
    data class Entry(val id: String, val track: MusicTrack, val following: List<MusicTrack>)
    private val playlistFolders = playlists.map { Folder("pipes:playlist:${encode(it.name)}", it.name) }
    private val groups = linkedMapOf(LIKED to liked.distinctBy { it.id }, DOWNLOADS to downloads.distinctBy { it.id }).apply {
        playlists.zip(playlistFolders).forEach { (playlist, folder) -> put(folder.id, playlist.tracks.distinctBy { it.id }) }
    }
    private val entries = groups.mapValues { (parent, tracks) -> tracks.mapIndexed { index, track ->
        Entry("pipes:track:${encode(parent)}:${encode(track.id)}", track, tracks.subList(index + 1, tracks.size))
    } }
    fun folders(parent: String): List<Folder> = when (parent) {
        ROOT -> listOf(Folder(LIKED, "Liked songs"), Folder(PLAYLISTS, "Playlists"), Folder(DOWNLOADS, "Downloads"))
        PLAYLISTS -> playlistFolders
        else -> emptyList()
    }
    fun containsFolder(id: String) = id == ROOT || id == PLAYLISTS || id in groups
    fun folder(id: String) = when (id) {
        ROOT -> Folder(ROOT, "Pipes")
        else -> (folders(ROOT) + playlistFolders).firstOrNull { it.id == id }
    }
    fun children(parent: String): List<Entry> = entries[parent].orEmpty()
    fun find(id: String) = entries.values.asSequence().flatten().firstOrNull { it.id == id }
    fun search(query: String): List<Entry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.values.flatten().distinctBy { it.track.id }.filter { entry ->
            val text = "${entry.track.title} ${entry.track.artist}".lowercase()
            words.all { it in text }
        }.take(200)
    }
    companion object {
        const val ROOT = "pipes:root"
        const val LIKED = "pipes:liked"
        const val PLAYLISTS = "pipes:playlists"
        const val DOWNLOADS = "pipes:downloads"
        private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
            if (page < 0 || pageSize <= 0) return emptyList()
            val start = page.toLong() * pageSize
            if (start >= items.size) return emptyList()
            return items.subList(start.toInt(), (start + pageSize).coerceAtMost(items.size.toLong()).toInt())
        }
    }
}
