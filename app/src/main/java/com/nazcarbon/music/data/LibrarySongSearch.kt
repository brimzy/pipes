package com.nazcarbon.music.data

import java.util.Locale

object LibrarySongSearch {
    fun merge(query: String, collections: List<MusicPlaylist>, online: List<MusicTrack>): List<MusicTrack> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty() || YouTubeLinks.parse(query) != null || SpotifyLinks.isSpotifyLink(query)) return online
        val saved = collections.flatMap { it.tracks }.filter { track ->
            val text = "${track.title} ${track.artist}".lowercase(Locale.ROOT)
            terms.all { it in text }
        }
        return (saved + online).distinctBy { it.id }.distinctBy { it.pageUrl }
    }
}
