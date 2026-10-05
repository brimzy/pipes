package com.nazcarbon.music.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PlaylistStore {
    fun load(preferences: SharedPreferences, key: String = KEY): List<MusicPlaylist> =
        runCatching {
            val playlists = JSONArray(preferences.getString(key, "[]"))
            (0 until playlists.length()).mapNotNull { index ->
                val playlist = playlists.optJSONObject(index) ?: return@mapNotNull null
                val songs = playlist.optJSONArray("tracks") ?: JSONArray()
                val tracks = (0 until songs.length()).mapNotNull { songIndex ->
                    songs.optJSONObject(songIndex)?.toMusicTrack()
                }
                playlist.optString("name").takeIf(String::isNotBlank)?.let {
                    MusicPlaylist(it, tracks)
                }
            }
        }.getOrDefault(emptyList())

    fun save(preferences: SharedPreferences, playlists: List<MusicPlaylist>, key: String = KEY) {
        val encoded = JSONArray()
        playlists.forEach { playlist ->
            val songs = JSONArray()
            playlist.tracks.forEach { track ->
                songs.put(
                    JSONObject()
                        .put("id", track.id)
                        .put("title", track.title)
                        .put("artist", track.artist)
                        .put("duration", track.durationSeconds)
                        .put("thumbnail", track.thumbnailUrl)
                        .put("page", track.pageUrl),
                )
            }
            encoded.put(JSONObject().put("name", playlist.name).put("tracks", songs))
        }
        preferences.edit().putString(key, encoded.toString()).apply()
    }

    private fun JSONObject.toMusicTrack() = runCatching {
        val id = getString("id")
        MusicTrack(
            id = id,
            title = getString("title"),
            artist = getString("artist"),
            durationSeconds = optLong("duration"),
            thumbnailUrl = optString("thumbnail"),
            streamUrl = "",
            pageUrl = optString("page", id),
        )
    }.getOrNull()

    private const val KEY = "playlists"
}
