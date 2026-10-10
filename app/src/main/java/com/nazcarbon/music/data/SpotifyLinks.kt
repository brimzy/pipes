package com.nazcarbon.music.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL

object SpotifyLinks {
    private val trackId = Regex("[A-Za-z0-9]{22}")

    fun isSpotifyLink(value: String): Boolean {
        val input = value.trim()
        if (input.startsWith("spotify:", ignoreCase = true)) return true
        val uri = runCatching { URI(withScheme(input)) }.getOrNull() ?: return false
        return uri.host?.lowercase() in setOf("open.spotify.com", "spotify.com", "www.spotify.com", "spoti.fi", "spotify.link")
    }

    fun searchQuery(value: String): String {
        require(isSpotifyLink(value)) { "Enter a Spotify track link." }
        val canonical = canonicalTrackUrl(value)
        val encoded = URLEncoder.encode(canonical, "UTF-8")
        val metadata = JSONObject(readUrl("https://open.spotify.com/oembed?url=$encoded"))
        val title = metadata.optString("title").trim()
        require(title.isNotEmpty()) { "Couldn't find that Spotify track. Check the link and retry." }
        val artist = runCatching { artistFromPage(readUrl(canonical)) }.getOrNull().orEmpty()
        return listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
    }

    internal fun canonicalTrackUrl(value: String): String {
        val input = value.trim()
        if (input.startsWith("spotify:", ignoreCase = true)) {
            val parts = input.split(':')
            require(parts.size == 3 && parts[1] == "track" && trackId.matches(parts[2])) {
                "Paste a Spotify song link. Albums and playlists aren't supported here."
            }
            return "https://open.spotify.com/track/${parts[2]}"
        }
        var uri = URI(withScheme(input))
        repeat(5) {
            val host = uri.host?.lowercase()
            if (host == "spoti.fi" || host == "spotify.link") {
                val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                try {
                    val location = connection.getHeaderField("Location")
                    require(connection.responseCode in 300..399 && !location.isNullOrBlank()) {
                        "Couldn't open that Spotify link. Check it and retry."
                    }
                    uri = uri.resolve(location)
                } finally { connection.disconnect() }
            } else return@repeat
        }
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("open.spotify.com", "spotify.com", "www.spotify.com")) {
            "Enter a Spotify track link."
        }
        val parts = uri.path.orEmpty().trim('/').split('/')
        val index = parts.indexOf("track")
        require(index >= 0 && index + 1 < parts.size && trackId.matches(parts[index + 1])) {
            "Paste a Spotify song link. Albums and playlists aren't supported here."
        }
        return "https://open.spotify.com/track/${parts[index + 1]}"
    }

    internal fun artistFromPage(html: String): String =
        Regex("<title>[^<]*? - song(?: and lyrics)? by (.*?) \\| Spotify</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&quot;", "\"")
            .orEmpty()

    private fun withScheme(value: String): String =
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) value else "https://$value"

    private fun readUrl(value: String): String {
        val connection = (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        return try {
            require(connection.responseCode == 200) { "Couldn't find that Spotify track. Check the link and retry." }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}
