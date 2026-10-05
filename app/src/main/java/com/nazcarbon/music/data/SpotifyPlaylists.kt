package com.nazcarbon.music.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class SpotifyPlaylistTrack(val title: String, val artist: String)
data class SpotifyPlaylistPreview(
    val name: String,
    val tracks: List<SpotifyPlaylistTrack>,
    val totalTracks: Int,
)

/** Imports the rows Spotify exposes on a public playlist page. */
object SpotifyPlaylists {
    private val spotifyId = Regex("[A-Za-z0-9]{22}")

    fun isPlaylistLink(value: String): Boolean {
        val input = value.trim()
        if (input.startsWith("spotify:playlist:", ignoreCase = true)) return true
        val uri = runCatching { URI(withScheme(input)) }.getOrNull() ?: return false
        return uri.host?.lowercase() in setOf("open.spotify.com", "spotify.com", "www.spotify.com") &&
            uri.path.orEmpty().split('/').contains("playlist")
    }

    fun load(value: String): SpotifyPlaylistPreview = parsePage(readUrl(canonicalUrl(value)))

    fun playlistId(value: String): String {
        val input = value.trim()
        if (input.startsWith("spotify:playlist:", ignoreCase = true)) {
            return input.substringAfterLast(':').also { require(spotifyId.matches(it)) { "Paste a Spotify playlist link." } }
        }
        val uri = URI(withScheme(input))
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("open.spotify.com", "spotify.com", "www.spotify.com")) {
            "Paste a Spotify playlist link."
        }
        val parts = uri.path.orEmpty().trim('/').split('/')
        val index = parts.indexOf("playlist")
        require(index >= 0 && index + 1 < parts.size && spotifyId.matches(parts[index + 1])) {
            "Paste a Spotify playlist link."
        }
        return parts[index + 1]
    }

    internal fun parsePage(html: String): SpotifyPlaylistPreview {
        val name = Regex("<meta property=\"og:title\" content=\"(.*?)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.decodeHtml().orEmpty()
        val total = Regex("<meta property=\"og:description\" content=\".*?([0-9]+) items\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val row = Regex(
            "data-testid=\"track-row\".*?href=\"/track/[A-Za-z0-9]{22}\".*?data-encore-id=\"listRowTitle\".*?<span[^>]*>(.*?)</span>.*?data-testid=\"internal-artist-link\".*?<a[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val tracks = row.findAll(html).map { match ->
            SpotifyPlaylistTrack(match.groupValues[1].stripTags().decodeHtml(), match.groupValues[2].stripTags().decodeHtml())
        }.filter { it.title.isNotBlank() && it.artist.isNotBlank() }.distinct().toList()
        require(name.isNotBlank() && tracks.isNotEmpty()) {
            "Couldn't read this Spotify playlist. It may be private or unavailable."
        }
        return SpotifyPlaylistPreview(name, tracks, total.coerceAtLeast(tracks.size))
    }

    private fun canonicalUrl(value: String): String {
        val input = value.trim()
        if (input.startsWith("spotify:playlist:", ignoreCase = true)) {
            val id = input.substringAfterLast(':')
            require(spotifyId.matches(id)) { "Paste a Spotify playlist link." }
            return "https://open.spotify.com/playlist/$id"
        }
        val uri = URI(withScheme(input))
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("open.spotify.com", "spotify.com", "www.spotify.com")) {
            "Paste a Spotify playlist link."
        }
        val parts = uri.path.orEmpty().trim('/').split('/')
        val index = parts.indexOf("playlist")
        require(index >= 0 && index + 1 < parts.size && spotifyId.matches(parts[index + 1])) {
            "Paste a Spotify playlist link."
        }
        return "https://open.spotify.com/playlist/${parts[index + 1]}"
    }

    private fun String.stripTags() = replace(Regex("<[^>]+>"), "")
    private fun String.decodeHtml() = replace("&amp;", "&").replace("&#39;", "'")
        .replace("&#x27;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
    private fun withScheme(value: String): String =
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) value else "https://$value"

    private fun readUrl(value: String): String {
        val connection = (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        return try {
            require(connection.responseCode == 200) { "Couldn't open that Spotify playlist. Check it and retry." }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}
