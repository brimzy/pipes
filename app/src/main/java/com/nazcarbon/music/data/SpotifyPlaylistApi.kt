package com.nazcarbon.music.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyFullTrack(val id: String, val title: String, val artist: String, val position: Int)
data class SpotifyFullPlaylist(val name: String, val tracks: List<SpotifyFullTrack>, val totalTracks: Int)

object SpotifyPlaylistApi {
    private const val PAGE_SIZE = 50

    fun load(accessToken: String, playlistId: String): SpotifyFullPlaylist {
        val metadata = requestJson(
            URL("https://api.spotify.com/v1/playlists/$playlistId?fields=name,items.total,tracks.total"),
            accessToken,
        )
        val name = metadata.optString("name").trim()
        require(name.isNotBlank()) { "Spotify didn't return this playlist's name." }
        val declaredTotal = metadata.optJSONObject("items")?.optInt("total")
            ?: metadata.optJSONObject("tracks")?.optInt("total")
        val tracks = mutableListOf<SpotifyFullTrack>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val page = requestPage(accessToken, playlistId, offset)
            total = page.optInt("total", offset)
            val items = page.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                val item = entry.optJSONObject("item") ?: entry.optJSONObject("track") ?: continue
                if (item.optString("type") != "track") continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("name").trim().takeIf { it.isNotBlank() } ?: continue
                val artists = item.optJSONArray("artists") ?: continue
                val artist = (0 until artists.length()).mapNotNull { artistIndex ->
                    artists.optJSONObject(artistIndex)?.optString("name")?.takeIf { it.isNotBlank() }
                }.joinToString(", ")
                if (artist.isNotBlank()) tracks += SpotifyFullTrack(id, title, artist, offset + index)
            }
            offset += items.length()
        }
        return SpotifyFullPlaylist(name, tracks, total.takeIf { it != Int.MAX_VALUE } ?: declaredTotal ?: tracks.size)
    }

    private fun requestPage(accessToken: String, playlistId: String, offset: Int): JSONObject {
        val url = URL("https://api.spotify.com/v1/playlists/$playlistId/items?limit=$PAGE_SIZE&offset=$offset&additional_types=track")
        return requestJson(url, accessToken)
    }

    private fun requestJson(url: URL, accessToken: String): JSONObject {
        var attempt = 0
        while (true) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val status = connection.responseCode
                if (status == 429 && attempt++ < 3) {
                    val seconds = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 30) ?: 2L
                    Thread.sleep(seconds * 1_000)
                    continue
                }
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                require(status in 200..299) {
                    when (status) {
                        401 -> "Spotify sign-in expired. Paste the playlist again to reconnect."
                        403 -> "Spotify only allows full playlist import when your signed-in account owns or collaborates on this playlist."
                        404 -> "Spotify couldn't find this playlist. Check that the link is still available."
                        429 -> "Spotify is rate limiting playlist imports. Wait a moment and try again."
                        else -> "Spotify playlist request failed ($status). Try again."
                    }
                }
                return JSONObject(body)
            } finally { connection.disconnect() }
        }
    }
}
