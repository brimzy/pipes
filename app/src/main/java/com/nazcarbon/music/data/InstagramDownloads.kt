package com.nazcarbon.music.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

object InstagramDownloads {
    fun isInstagram(value: String): Boolean = runCatching {
        URI(value.trim()).host?.lowercase() in setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    }.getOrDefault(false)

    fun canonicalUrl(value: String): String {
        val uri = URI(value.trim())
        require(value.length <= 8192 && isInstagram(value) && uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1) {
            "Use an HTTPS instagram.com Reel or post link."
        }
        require(Regex("/(reel|reels|p|tv)/[A-Za-z0-9_-]{1,64}/?").matches(uri.path.orEmpty())) {
            "Paste a link to one public Instagram Reel or video post."
        }
        return "https://www.instagram.com${uri.path.trimEnd('/')}/"
    }

    fun validateMediaUrl(value: String): String {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        require(value.length <= 8192 && uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            (host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net"))) {
            "Instagram returned an unsupported media address."
        }
        return uri.toASCIIString()
    }

    fun mediaUrl(html: String): String {
        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributes = Regex("([\\w:]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
        for (tag in tags.findAll(html)) {
            val attrs = attributes.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
            if ((attrs["property"] ?: attrs["name"]) in setOf("og:video", "og:video:url", "og:video:secure_url")) {
                val value = attrs["content"] ?: continue
                return validateMediaUrl(value.replace("&amp;", "&").replace("&#38;", "&"))
            }
        }
        val video = Regex("\"video_url\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").find(html)
        if (video != null) return validateMediaUrl(JSONObject("{\"url\":${video.groupValues[1]}}").getString("url"))
        throw IllegalArgumentException("Instagram did not expose a public video. Private posts, login-required posts, photos, and Stories are unsupported.")
    }

    fun resolve(value: String): String {
        val connection = URI(canonicalUrl(value)).toURL().openConnection() as HttpURLConnection
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.setRequestProperty("Accept", "text/html")
        try {
            require(connection.responseCode == 200) { "Instagram blocked this request or requires login. Try a public video link." }
            require(connection.contentType.orEmpty().startsWith("text/html", true)) { "Instagram returned an unsupported response." }
            val body = connection.inputStream.use { it.readBytesLimited(2_000_000) }
            return mediaUrl(body.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            require(output.size() + count <= limit) { "Instagram's response is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
